# MT6893 Security Research

Five security research findings from a privilege escalation study on a MediaTek MT6893 (Dimensity 1200) Android tablet.

The target device (kernel 4.19.191, Mali Valhall r32p1, SPL 2023-06-05, SELinux enforcing) proved unexploitable from `uid=2000` via all known CVEs. These writeups document the specific technical reasons why.

## Device

| Item | Value |
|------|-------|
| SoC | MT6893 (Dimensity 1200), aarch64 |
| Kernel | 4.19.191+ SMP PREEMPT |
| Mali GPU | Valhall r32p1-00bet5 (UK 11.31) |
| Android | 13 (first_api_level=33) |
| SELinux | enforcing |
| Security Patch | 2023-06-05 |

## Findings

| # | Topic | TL;DR |
|---|-------|-------|
| [01](01-cve-2023-4622-sock-dead/) | CVE-2023-4622 SOCK_DEAD | The sendpage UAF is logically unexploitable: `SOCK_DEAD` check precedes the UAF entry in every execution path. 440,000+ experiments, 0 bypass. |
| [02](02-mali-no-user-free-count/) | Mali `no_user_free_count` | An undocumented permanent guard in r32p1: incremented once at alloc, never decremented. Blocks CVE-2022-38181 and CVE-2023-4211. |
| [03](03-cve-2022-36449-retraction/) | CVE-2022-36449 retraction | `MEM_FREE` doesn't free the physical page — user mmap holds refcount=1. We retract our initial "UAF confirmed" claim. |
| [04](04-arm64-skb-offset-divergence/) | ARM64 `sk_buff` +0x10 trap | Android vendor kernels shift `sk_buff` fields by +0x10. Using x86 offsets causes `shinfo=0xffffff96` and instant DABT. |
| [05](05-gpu-write-value-boundary/) | GPU WRITE_VALUE boundary | GPU WRITE_VALUE bypasses CPU `mprotect(PROT_READ)` but only reaches SAME_VA allocations — kernel memory stays unreachable. |

## Building the PoCs

Requires Android NDK r25+ with `aarch64-linux-android33-clang`.

```bash
cd 01-cve-2023-4622-sock-dead/poc
make -f ../../common/Makefile.ndk

# Or push to device:
make -f ../../common/Makefile.ndk push
```

The PoCs are diagnostic/verification tools only. They do not contain exploit payloads.

## Structure

```
mt6893-security-research/
├── README.md
├── LICENSE
├── common/
│   ├── mali.h              # Minimal Mali UAPI definitions
│   └── Makefile.ndk        # NDK cross-compilation template
├── 01-cve-2023-4622-sock-dead/
│   ├── README.md           # SOCK_DEAD logical constraint proof
│   └── poc/
│       └── trigger_race.c  # UAF race trigger + EPIPE statistics
├── 02-mali-no-user-free-count/
│   ├── README.md           # Permanent guard counter analysis
│   └── poc/
│       ├── vuln_check_jit.c    # JIT DONT_NEED acceptance test
│       └── diag_dont_need.c    # Non-JIT DONT_NEED diagnostic
├── 03-cve-2022-36449-retraction/
│   ├── README.md           # Retraction of incorrect UAF claim
│   └── poc/
│       └── diag_refcount.c # 8-step page lifecycle diagnostic
├── 04-arm64-skb-offset-divergence/
│   ├── README.md           # sk_buff offset drift documentation
│   └── poc/
│       └── skb_spray_demo.c    # Correct vs incorrect spray layout
└── 05-gpu-write-value-boundary/
    ├── README.md           # WRITE_VALUE capability and limits
    └── poc/
        ├── probe_write_value.c     # Basic WRITE_VALUE primitive test
        └── probe_mprotect_bypass.c # mprotect bypass + import failure
```

## Ethics

This research was conducted on a personally-owned device for educational purposes. All findings document defensive knowledge — why attacks fail, not how to make them succeed.

## License

MIT
