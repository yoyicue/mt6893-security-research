# GPU WRITE_VALUE Bypasses CPU `mprotect` But Can't Reach Kernel Memory

## Summary

Mali GPU's WRITE_VALUE hardware job (type 2) can write an arbitrary 64-bit immediate to a GPU-mapped address from an unprivileged process (uid=2000, shell). It operates through the GPU's own page tables, completely independent of CPU `mprotect` permissions. This means a GPU write succeeds even after the CPU-side mapping is downgraded to `PROT_READ`.

However, this capability is strictly bounded: WRITE_VALUE only reaches SAME_VA allocations where the GPU page tables have a valid mapping. USER_BUFFER imports receive `PERMISSION_FAULT` or `TRANSLATION_FAULT` because their GPU page table entries are at an internal `gpu_va` (e.g., `0x41000`) that doesn't map the CPU virtual address. The GPU cannot write to arbitrary process memory or kernel memory.

## Background

Mali Valhall GPUs (r32p1, MT6893) expose hardware job submission via `KBASE_IOCTL_JOB_SUBMIT`. Job type 2 (WRITE_VALUE) is a hardware-level operation that writes an immediate value to a GPU virtual address. Unlike software jobs (JIT_ALLOC, DEBUG_COPY), WRITE_VALUE executes on the GPU hardware and uses the GPU's own MMU page tables.

The job descriptor format (from Panfrost genxml for G77/G78):

```
Offset  Size  Field
0       32B   Job header (includes job type at DW4[3:1])
32      8B    Target address (GPU VA)
40      4B    Write type (3=ZERO, 7=IMM64)
44      4B    Padding
48      8B    Immediate value
```

## Key Insight: `atom.jc` Is a CPU VA, Not `gpu_va`

This was the most confusing discovery. The UAPI documents `jc` (job chain) as a "GPU address." But on this device:

- `alloc.out.gpu_va` returns `0x41000` (internal cookie)
- The CPU mmap returns `0x7fa4c00000` (actual VA)
- Setting `atom.jc = gpu_va` (`0x41000`) causes `TRANSLATION_FAULT`
- Setting `atom.jc = cpu_va` (`0x7fa4c00000`) returns `DONE`

For SAME_VA allocations, the GPU MMU maps the process address space so that GPU virtual addresses match CPU virtual addresses. The `gpu_va` from `MEM_ALLOC` is an internal handle, not the usable GPU-side address.

## Test Matrix

| Test | Setup | Target Address | Result |
|------|-------|---------------|--------|
| T1 | SAME_VA alloc, RW | CPU VA | **WRITE CONFIRMED** |
| T2a | USER_BUFFER import, Mali mmap | mmap addr | TRANSLATION_FAULT |
| T2b | USER_BUFFER import + SAME_VA flag | CPU VA | Import fails (SAME_VA + import = rejected) |
| T2c | USER_BUFFER import, no SAME_VA | CPU VA | TRANSLATION_FAULT |
| T2c | USER_BUFFER import, no SAME_VA | gpu_va (0x41000) | PERMISSION_FAULT |
| T3 | SAME_VA alloc, CPU_RD only mmap | CPU VA | **WRITE CONFIRMED** (GPU ignores CPU perms) |
| T4 | SAME_VA alloc, mprotect(PROT_READ) | CPU VA | **WRITE CONFIRMED** (GPU ignores mprotect) |

### Key Findings

1. **T1 confirms**: WRITE_VALUE works on SAME_VA allocations from uid=2000.

2. **T2 series confirms**: USER_BUFFER imports are not reachable via WRITE_VALUE. The GPU page tables for imports live at the internal `gpu_va`, not at the CPU VA. Attempting either address fails.

3. **T3 + T4 confirm**: GPU and CPU page table permissions are completely independent. A CPU `PROT_READ` mapping or `mprotect` downgrade does not affect GPU write capability. The GPU writes through its own MMU using the flags set at `MEM_ALLOC` time (`GPU_WR`).

## Why This Can't Reach Kernel Memory

The GPU address space for a Mali context only contains:

1. **SAME_VA regions**: mapped at the process's CPU virtual addresses (userspace range only)
2. **Internal allocations**: at `gpu_va` handles like `0x41000` (not kernel addresses)
3. **No kernel VA mappings**: the GPU MMU never maps `0xffffff80xxxxxxxx` ranges

Even though WRITE_VALUE bypasses CPU page protections, it cannot target addresses outside the GPU's own page table mappings. The GPU's address space is a strict subset of the process's userspace VA range (for SAME_VA allocations).

## CPU/GPU Page Table Independence

```
CPU page tables:              GPU page tables (Mali MMU):
  0x7fa4c00000 → phys_A        0x7fa4c00000 → phys_A  (SAME_VA)
  [PROT_READ after mprotect]    [GPU_RD|GPU_WR unchanged]

CPU write → SIGBUS              GPU WRITE_VALUE → succeeds
                                 (GPU checks GPU page table, not CPU)
```

`mprotect()` only modifies CPU PTEs. The GPU's page table entries are managed by the Mali driver and are only changed via `MEM_FLAGS_CHANGE` or region destruction. This is by design -- the GPU is a separate MMU consumer.

## PoCs

### `probe_write_value.c`

Tests WRITE_VALUE on SAME_VA allocations. Opens a fresh Mali context per test to prevent GPU fault state from contaminating subsequent tests. Tests NULL job, IMM64 writes with different `core_req` slots (CS, T, FS), and ZERO writes.

### `probe_mprotect_bypass.c`

Chain probe testing USER_BUFFER import + WRITE_VALUE and mprotect bypass. Four test cases:
- T1: Baseline SAME_VA write (sanity check)
- T2: USER_BUFFER import with multiple addressing attempts
- T3: SAME_VA alloc with CPU_RD-only mmap + GPU write
- T4: SAME_VA alloc with mprotect downgrade + GPU write

```bash
cd poc && make -f ../../common/Makefile.ndk
adb push probe_write_value probe_mprotect_bypass /data/local/tmp/
adb shell /data/local/tmp/probe_write_value
adb shell /data/local/tmp/probe_mprotect_bypass
```

## Takeaway

GPU WRITE_VALUE is a real write primitive from unprivileged userspace, but it is confined to the GPU's own address space. On SAME_VA configurations, this means the process's own userspace mappings -- powerful for intra-process manipulation, but not a kernel exploit primitive by itself. The mprotect bypass is interesting (CPU protections are irrelevant to GPU operations) but ultimately bounded by SAME_VA's userspace-only scope.

## References

- [Panfrost genxml (Valhall job descriptors)](https://gitlab.freedesktop.org/mesa/mesa/-/tree/main/src/panfrost/genxml)
- [ARM Mali Valhall architecture overview](https://developer.arm.com/documentation/102203/latest)
- [CVE-2022-22706 (FOLL_WRITE bypass in Mali)](https://nvd.nist.gov/vuln/detail/CVE-2022-22706)
