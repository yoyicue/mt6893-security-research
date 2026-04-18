/*
 * DONT_NEED Flag Acceptance Diagnostic
 *
 * Tests MEM_FLAGS_CHANGE(DONT_NEED) on various non-JIT allocation
 * configurations. Key insight: alloc.out.gpu_va is a cookie, not
 * a GPU virtual address. For SAME_VA allocs, the real GPU VA is
 * the CPU mmap return address.
 *
 * Build: make -f ../../common/Makefile.ndk
 * Run:   ./diag_dont_need
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

static int mali_init(void) {
    mali_fd = open("/dev/mali0", O_RDWR);
    if (mali_fd < 0) { perror("open"); return -1; }
    struct kbase_ioctl_version_check ver = {0, 0};
    if (ioctl(mali_fd, KBASE_IOCTL_VERSION_CHECK, &ver)) return -1;
    struct kbase_ioctl_set_flags sf = {0};
    if (ioctl(mali_fd, KBASE_IOCTL_SET_FLAGS, &sf)) return -1;
    mmap(NULL, 0x1000, PROT_NONE, MAP_SHARED, mali_fd,
         BASE_MEM_MAP_TRACKING_HANDLE);
    printf("[*] Mali %u.%u fd=%d\n", ver.major, ver.minor, mali_fd);
    return 0;
}

static uint64_t do_alloc(uint64_t pages, uint64_t flags) {
    union kbase_ioctl_mem_alloc a = {};
    a.in.va_pages = pages;
    a.in.commit_pages = pages;
    a.in.flags = flags;
    if (ioctl(mali_fd, KBASE_IOCTL_MEM_ALLOC, &a)) return 0;
    return a.out.gpu_va;
}

static int do_dont_need(uint64_t gva) {
    struct kbase_ioctl_mem_flags_change chg = {
        .gpu_va = gva, .flags = BASE_MEM_DONT_NEED,
        .mask = BASE_MEM_DONT_NEED
    };
    return ioctl(mali_fd, KBASE_IOCTL_MEM_FLAGS_CHANGE, &chg);
}

struct test { const char *name; uint64_t flags; int do_munmap; };

static void run_test(int idx, struct test *t) {
    printf("\n[%d] %s (flags=0x%llx)\n", idx, t->name, (unsigned long long)t->flags);

    uint64_t gva = do_alloc(4, t->flags);
    if (!gva) { printf("    ALLOC FAILED: %s\n", strerror(errno)); return; }
    printf("    cookie=0x%llx\n", (unsigned long long)gva);

    void *cpu = mmap(NULL, 4 * 0x1000, PROT_READ | PROT_WRITE,
                     MAP_SHARED, mali_fd, gva);
    if (cpu == MAP_FAILED) {
        printf("    mmap FAILED: %s\n", strerror(errno));
        cpu = NULL;
    } else {
        printf("    mmap OK at %p (real GPU VA for SAME_VA)\n", cpu);
    }

    uint64_t real_va = cpu ? (uint64_t)cpu : gva;

    if (t->do_munmap && cpu) {
        munmap(cpu, 4 * 0x1000);
        printf("    munmap done\n");
        cpu = NULL;
    }

    int ret = do_dont_need(real_va);
    if (ret == 0)
        printf("    DONT_NEED: *** SUCCESS ***\n");
    else
        printf("    DONT_NEED: FAILED errno=%d (%s)\n", errno, strerror(errno));

    if (cpu) munmap(cpu, 4 * 0x1000);
}

int main(void) {
    setvbuf(stdout, NULL, _IONBF, 0);
    printf("=== DONT_NEED Diagnostic ===\n");
    printf("Key: gpu_va from MEM_ALLOC is a cookie.\n");
    printf("For SAME_VA, real GPU VA = mmap return address.\n\n");

    if (mali_init()) return 1;

    struct test tests[] = {
        { "CPU+GPU+SAME_VA (mmap kept)",
          BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR |
          BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR | BASE_MEM_SAME_VA, 0 },
        { "CPU+GPU+SAME_VA (mmap+munmap)",
          BASE_MEM_PROT_CPU_RD | BASE_MEM_PROT_CPU_WR |
          BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR | BASE_MEM_SAME_VA, 1 },
        { "GPU-only+SAME_VA (mmap fails)",
          BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR | BASE_MEM_SAME_VA, 0 },
        { "GPU-only (no SAME_VA, cookie)",
          BASE_MEM_PROT_GPU_RD | BASE_MEM_PROT_GPU_WR, 0 },
    };

    for (int i = 0; i < (int)(sizeof(tests)/sizeof(tests[0])); i++)
        run_test(i, &tests[i]);

    close(mali_fd);
    return 0;
}
