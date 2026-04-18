/*
 * Mali GPU WRITE_VALUE Primitive Probe
 *
 * Verifies that GPU WRITE_VALUE (HW job type 2) can write an immediate
 * value into a SAME_VA allocation owned by the calling process.
 *
 * Each sub-test opens a fresh Mali context so a GPU fault in one test
 * cannot invalidate the address-space used by later tests.
 *
 * Build: make -f ../../common/Makefile.ndk
 * Run:   ./probe_write_value
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
#define MALI_WR_ZERO          3
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

static void *mali_alloc_coherent(int fd, uint64_t pages)
{
    union kbase_ioctl_mem_alloc alloc = {};
    alloc.in.va_pages = pages;
    alloc.in.commit_pages = pages;
    alloc.in.flags = BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR
                   | BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR
                   | BASE_MEM_SAME_VA | BASE_MEM_COHERENT_SYSTEM;

    if (ioctl(fd, KBASE_IOCTL_MEM_ALLOC, &alloc)) return NULL;

    void *p = mmap(NULL, pages * PAGE_SZ, PROT_READ | PROT_WRITE,
                   MAP_SHARED, fd, alloc.out.gpu_va);
    return (p == MAP_FAILED) ? NULL : p;
}

static int submit_and_wait(int fd, uint64_t jc, uint32_t core_req,
                           uint8_t atom_nr)
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
    atom.core_req = core_req;
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

    const char *name = "?";
    if (ev.event_code == BASE_JD_EVENT_DONE) name = "DONE";
    else if (ev.event_code == BASE_JD_EVENT_TERMINATED) name = "TERMINATED";

    printf("event=0x%x (%s) ", ev.event_code, name);
    return (int)ev.event_code;
}

struct test_cfg {
    const char *name;
    int job_type;
    uint32_t core_req;
    uint32_t write_type;
};

static void run_test(const struct test_cfg *t, uint64_t magic, int idx)
{
    int fd = mali_open_ctx();
    if (fd < 0) { printf("  [%s] ctx open failed\n", t->name); return; }

    void *buf = mali_alloc_coherent(fd, 2);
    if (!buf) { close(fd); return; }

    uint32_t *desc = (uint32_t *)buf;
    volatile uint64_t *target = (volatile uint64_t *)((char *)buf + PAGE_SZ);
    uint64_t desc_addr = (uint64_t)buf;
    uint64_t target_addr = (uint64_t)target;

    memset(desc, 0, 64);
    desc[4] = (1 << 0) | (t->job_type << 1);

    if (t->job_type == MALI_JOB_TYPE_WRITE_VALUE) {
        uint32_t *payload = desc + 8;
        payload[0] = (uint32_t)(target_addr);
        payload[1] = (uint32_t)(target_addr >> 32);
        payload[2] = t->write_type;
        payload[4] = (uint32_t)(magic);
        payload[5] = (uint32_t)(magic >> 32);
    }

    *target = 0xDEADDEADDEADDEADULL;

    printf("  [%s] jc=%p tgt=%p -> ",
           t->name, (void *)desc_addr, (void *)target_addr);
    fflush(stdout);

    int ev = submit_and_wait(fd, desc_addr, t->core_req, idx + 1);

    if (t->job_type == MALI_JOB_TYPE_WRITE_VALUE && ev >= 0) {
        uint64_t val = *target;
        if (t->write_type == MALI_WR_ZERO)
            printf("target=0x%llx %s\n", (unsigned long long)val,
                   val == 0 ? "*** ZERO CONFIRMED ***" : "(unexpected)");
        else
            printf("target=0x%llx %s\n", (unsigned long long)val,
                   val == magic ? "*** WRITE CONFIRMED ***" :
                   val == 0xDEADDEADDEADDEADULL ? "(unchanged)" : "(unexpected)");
    } else {
        printf("\n");
    }

    close(fd);
}

int main(void)
{
    setvbuf(stdout, NULL, _IONBF, 0);
    printf("=== Mali GPU WRITE_VALUE Primitive Probe ===\n");
    printf("[*] uid=%d\n", getuid());
    printf("[*] SAME_VA + COHERENT_SYSTEM, fresh context per test\n\n");

    uint64_t magic = 0x4141424243434444ULL;

    struct test_cfg tests[] = {
        { "NULL_JOB,CS",           1, BASE_JD_REQ_CS, 0 },
        { "WRITE_IMM64,CS",       2, BASE_JD_REQ_CS, MALI_WR_IMMEDIATE_64 },
        { "WRITE_IMM64,T",        2, BASE_JD_REQ_T,  MALI_WR_IMMEDIATE_64 },
        { "WRITE_ZERO,CS",        2, BASE_JD_REQ_CS, MALI_WR_ZERO },
    };
    int n = sizeof(tests) / sizeof(tests[0]);

    for (int i = 0; i < n; i++)
        run_test(&tests[i], magic, i);

    printf("\n=== Legend ===\n");
    printf("event 0x01 = DONE (success)\n");
    printf("event 0x04 = TERMINATED (HW fault)\n");

    return 0;
}
