# `no_user_free_count`: An Undocumented Permanent Guard in Mali r32p1

## Summary

ARM Mali GPU driver r32p1 contains an undocumented protection mechanism at `kbase_va_region + 0x9C`: a counter called `no_user_free_count`. IDA Pro analysis of the entire Mali driver code range reveals **exactly one `STR` instruction** that writes to this field (an increment during region allocation when flags include bit 7). There are **zero decrement sites** across the entire driver. This makes the counter a permanent, one-way guard that blocks `MEM_FREE`, `MEM_FLAGS_CHANGE`, and `MEM_ALIAS` on JIT regions for the lifetime of the allocation.

This counter single-handedly defeats the classic [CVE-2022-38181](https://github.blog/security/vulnerability-research/pwning-the-all-google-phone-with-a-non-google-bug/) exploit path and likely blocks [CVE-2023-4211](https://nvd.nist.gov/vuln/detail/CVE-2023-4211) as well.

## Background

CVE-2022-38181 (discovered by Man Yue Mo, GitHub Security Lab) is a use-after-free in Mali's JIT memory subsystem. The exploit flow:

1. JIT allocate → region assigned to `jit_alloc[id]`
2. `MEM_FLAGS_CHANGE(DONT_NEED)` on JIT region → marks evictable
3. Kernel shrinker frees backing pages while `jit_alloc[id]` still references the region
4. Controlled replacement of the freed region struct → arbitrary page free primitive

Step 2 is the entry point. If `MEM_FLAGS_CHANGE(DONT_NEED)` can be blocked on JIT regions, the entire chain is dead.

## IDA Findings

**Device**: MT6893, Mali Valhall r32p1-00bet5, UK 11.31. Analysis performed on `vmlinux_rebuilt` (IDA Pro).

### The Only Increment Site

At `sub_FFFFFF8009C85348` (flags sanitization during region creation), address `0xffffff8009c85504`:

```asm
LDR  W8, [X19, #0x9C]     ; load current count
ADD  W8, W8, #1            ; count + 1
STR  W8, [X19, #0x9C]     ; store back
```

This executes when `alloc_flags & 0x80` (bit 7 = `BASEP_MEM_NO_USER_FREE`). JIT allocation flags `0xA8D` include bit 7, so all JIT regions get `no_user_free_count = 1` at creation time.

### Zero Decrement Sites

Exhaustive search of the entire Mali code range (`0xffffff8009c00000` – `0xffffff8009d00000`) found zero `SUB`/`STR` patterns that decrement `[Xn, #0x9C]`. The `kbase_jit_free` function (`sub_FFFFFF8009C87684`) moves the region to a dormant list but **never touches the counter**.

### Protection Chain

Five functions read `no_user_free_count` as a guard:

| Function | Access | Effect |
|----------|--------|--------|
| `kbase_mem_free_region` (`sub_..85030`) | LDR, check ≥ 1 | Blocks `MEM_FREE` with `EINVAL` |
| Flags sanitization (`sub_..85348`) | LDR + STR | Increments on alloc |
| `kbase_jit_allocate` (`sub_..86AE0`) | LDR, check > 1 | Rejects dormant reuse if count > 1 |
| `kbase_mem_flags_change` (`sub_..954B8`) | LDR, check > 0 | Blocks `DONT_NEED` on JIT regions |
| `kbase_mem_alias` (`sub_..95F90`) | LDR, check > 0 | Blocks alias creation from JIT source |

### Impact on CVE-2022-38181

**Path A (classic JIT UAF)**: `no_user_free_count = 1` on all JIT regions → `kbase_mem_flags_change` rejects `DONT_NEED` → cannot trigger shrinker eviction → **DEAD**.

**Path B (non-JIT DONT_NEED + shrinker)**: `DONT_NEED` succeeds on non-JIT SAME_VA allocs (their count = 0). However, the shrinker's `scan_objects` (`sub_FFFFFF8009C94EE4`) calls `kbase_mmu_teardown_pages` to remove GPU PTEs and flush the TLB **before** freeing physical pages. After eviction, GPU WRITE_VALUE targeting the old VA hits invalid PTEs → `TRANSLATION_FAULT`, not a write to the freed page → **DEAD**.

### Impact on CVE-2023-4211

CVE-2023-4211 targets JIT region memory. `no_user_free_count = 1` blocks `MEM_FREE` on JIT regions permanently → the user cannot trigger the free path needed for this vulnerability → **likely protected**.

## PoCs

### `vuln_check_jit.c`

Tests CVE-2022-38181 directly: allocates a JIT region via `JOB_SUBMIT`, then attempts `MEM_FLAGS_CHANGE(DONT_NEED)` on it.

- If `DONT_NEED` returns 0 → **VULNERABLE**
- If `DONT_NEED` returns `EINVAL` → **PATCHED** (no_user_free_count blocks it)

On our device: returns `EINVAL`.

### `diag_dont_need.c`

Tests DONT_NEED on various non-JIT allocation configurations to understand the overall attack surface:

| Config | DONT_NEED Result |
|--------|-----------------|
| CPU+GPU+SAME_VA, mmap kept | **SUCCESS** |
| CPU+GPU+SAME_VA, munmap first | FAILED (munmap destroys region) |
| GPU-only+SAME_VA | FAILED (no mmap possible → no real VA) |
| GPU-only, no SAME_VA | FAILED (cookie VA not in region tree) |

Key insight: `alloc.out.gpu_va` is a **cookie**, not a GPU virtual address. For SAME_VA allocations, the real GPU VA is the CPU mmap return address.

```bash
# Build
cd poc && make -f ../../common/Makefile.ndk
```

## References

- [CVE-2022-38181 Writeup (Man Yue Mo)](https://github.blog/security/vulnerability-research/pwning-the-all-google-phone-with-a-non-google-bug/)
- [CVE-2023-4211 NVD](https://nvd.nist.gov/vuln/detail/CVE-2023-4211)
