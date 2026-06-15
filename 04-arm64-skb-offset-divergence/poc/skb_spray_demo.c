/*
 * sk_buff Offset Divergence Demo
 *
 * Demonstrates how ARM64 Android vendor kernel sk_buff offsets
 * differ from x86/mainline assumptions by +0x10 bytes, and how
 * using the wrong offsets produces a garbage skb_shared_info pointer.
 *
 * This is a userspace-only demonstration — no kernel interaction.
 * It constructs fake sk_buff objects with both correct and incorrect
 * offsets and shows the resulting shinfo computation.
 *
 * Build: make -f ../../common/Makefile.ndk
 * Run:   ./skb_spray_demo
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

/* x86 / mainline offsets (WRONG on ARM64 vendor kernels) */
#define X86_SKB_OFF_END    0xCC
#define X86_SKB_OFF_HEAD   0xD0
#define X86_SKB_OFF_DATA   0xD8
#define X86_SKB_OFF_USERS  0xE4

/* ARM64 vendor kernel offsets (MT6893, kernel 4.19.191) */
#define ARM64_SKB_OFF_END    0xDC
#define ARM64_SKB_OFF_HEAD   0xE0
#define ARM64_SKB_OFF_DATA   0xE8
#define ARM64_SKB_OFF_USERS  0xF4

#define OBJ_SIZE 256

static void build_spray_x86(uint8_t *obj, uint64_t target, uint32_t fake_end)
{
    uint64_t fake_head = target - (uint64_t)fake_end;
    *(uint32_t *)(obj + X86_SKB_OFF_END)   = fake_end;
    *(uint64_t *)(obj + X86_SKB_OFF_HEAD)  = fake_head;
    *(uint64_t *)(obj + X86_SKB_OFF_DATA)  = fake_head;
    *(uint32_t *)(obj + X86_SKB_OFF_USERS) = 100;
}

static void build_spray_arm64(uint8_t *obj, uint64_t target, uint32_t fake_end)
{
    uint64_t fake_head = target - (uint64_t)fake_end;
    *(uint32_t *)(obj + ARM64_SKB_OFF_END)   = fake_end;
    *(uint64_t *)(obj + ARM64_SKB_OFF_HEAD)  = fake_head;
    *(uint64_t *)(obj + ARM64_SKB_OFF_DATA)  = fake_head;
    *(uint32_t *)(obj + ARM64_SKB_OFF_USERS) = 100;
}

static void show_shinfo(const char *label, uint8_t *obj,
                        uint32_t end_off, uint32_t head_off)
{
    uint32_t end_val  = *(uint32_t *)(obj + end_off);
    uint64_t head_val = *(uint64_t *)(obj + head_off);
    uint64_t shinfo   = head_val + (uint64_t)end_val;

    printf("  %-12s end=0x%08x  head=0x%016llx  shinfo=0x%016llx",
           label, end_val, (unsigned long long)head_val,
           (unsigned long long)shinfo);

    if ((shinfo >> 48) == 0xffffULL)
        printf("  [VALID kernel VA]\n");
    else if (shinfo == 0)
        printf("  [NULL — zeroed slot]\n");
    else
        printf("  [INVALID — DABT crash]\n");
}

int main(void)
{
    printf("=== sk_buff Offset Divergence Demo ===\n\n");

    uint64_t target = 0xffffff8012345678ULL;
    uint32_t fake_end = 63;

    printf("Target address: 0x%016llx\n", (unsigned long long)target);
    printf("fake_end:       %u\n", fake_end);
    printf("Expected shinfo = target = 0x%016llx\n\n",
           (unsigned long long)target);

    /* --- Case 1: x86 offsets, read with x86 offsets (works on x86) --- */
    printf("[1] x86 spray + x86 kernel read (x86 native — correct)\n");
    {
        uint8_t obj[OBJ_SIZE];
        memset(obj, 0, OBJ_SIZE);
        build_spray_x86(obj, target, fake_end);
        show_shinfo("x86→x86:", obj, X86_SKB_OFF_END, X86_SKB_OFF_HEAD);
    }

    /* --- Case 2: x86 offsets, read with ARM64 offsets (the bug) --- */
    printf("\n[2] x86 spray + ARM64 kernel read (WRONG — the +0x10 trap)\n");
    {
        uint8_t obj[OBJ_SIZE];
        memset(obj, 0, OBJ_SIZE);
        build_spray_x86(obj, target, fake_end);
        show_shinfo("x86→ARM64:", obj, ARM64_SKB_OFF_END, ARM64_SKB_OFF_HEAD);

        uint32_t misread_end  = *(uint32_t *)(obj + ARM64_SKB_OFF_END);
        uint64_t misread_head = *(uint64_t *)(obj + ARM64_SKB_OFF_HEAD);
        printf("  Kernel reads end from +0xDC = 0x%08x (high32 of fake_head)\n",
               misread_end);
        printf("  Kernel reads head from +0xE0 = 0x%016llx (zero — never written)\n",
               (unsigned long long)misread_head);
        printf("  → shinfo = 0x%016llx → LDRB [shinfo+2] → DATA ABORT\n",
               (unsigned long long)(misread_head + misread_end));
    }

    /* --- Case 3: ARM64 offsets, read with ARM64 offsets (correct) --- */
    printf("\n[3] ARM64 spray + ARM64 kernel read (CORRECT)\n");
    {
        uint8_t obj[OBJ_SIZE];
        memset(obj, 0, OBJ_SIZE);
        build_spray_arm64(obj, target, fake_end);
        show_shinfo("ARM64→ARM64:", obj, ARM64_SKB_OFF_END, ARM64_SKB_OFF_HEAD);
    }

    printf("\n=== Offset Table ===\n");
    printf("%-10s  %-8s  %-8s  %s\n", "Field", "x86", "ARM64", "Delta");
    printf("%-10s  0x%04X    0x%04X    +0x10\n", "tail",     0xC8, 0xD8);
    printf("%-10s  0x%04X    0x%04X    +0x10\n", "end",      0xCC, 0xDC);
    printf("%-10s  0x%04X    0x%04X    +0x10\n", "head",     0xD0, 0xE0);
    printf("%-10s  0x%04X    0x%04X    +0x10\n", "data",     0xD8, 0xE8);
    printf("%-10s  0x%04X    0x%04X    +0x10\n", "truesize", 0xE0, 0xF0);
    printf("%-10s  0x%04X    0x%04X    +0x10\n", "users",    0xE4, 0xF4);

    printf("\n=== Conclusion ===\n");
    printf("Case [2] shows the failure mode: x86-assumed offsets on an ARM64\n");
    printf("vendor kernel produce shinfo at an unmapped address.\n");
    printf("The kernel will Data Abort on the first read from shinfo.\n");
    printf("Always IDA-confirm struct offsets on the target binary.\n");

    return 0;
}
