# MT6893 Security Research

Security research findings from a privilege escalation study on a MediaTek MT6893 (Dimensity 1200) Android tablet.

The target device (kernel 4.19.191, Mali Valhall r32p1, SPL 2023-06-05, SELinux enforcing) resisted the tested kernel and Mali escalation paths from `uid=2000`. These writeups document the specific technical reasons why those paths fail, plus later framework-level triage where applicable.

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
| [06](06-cve-2024-31317-zygote-injection/) | CVE-2024-31317 Zygote injection | Android Framework bug: `hidden_api_blacklist_exemptions` was serialized into Zygote's line protocol without control-character filtering. Target framework static check shows the vulnerable direct-write pattern is present. |
| [07](07-cve-2023-32836-display-overflow/) | CVE-2023-32836 display overflow | `/dev/dri/card0` is reachable from `system_app`, but the tested `CREATE_DUMB` path uses MTK 64-bit size calculation and direct atomic commit is permission-gated. |
| [08](08-cve-2023-32863-display-drm-oob-read/) | CVE-2023-32863 display-drm OOB read | MTK private DRM getters are only partially reachable; tested handlers do not currently show a user-controlled OOB read. |
| [09](09-cve-2023-32864-display-drm-oob-write/) | CVE-2023-32864 display-drm OOB write | MTK register write ioctls are reachable, but current `WRITE_REG` validates physical addresses and invalid write probes are rejected. |
| [10](10-cve-2023-32865-display-drm-oob-write/) | CVE-2023-32865 display-drm OOB write | The color-transform validation ioctl is reachable from `system_app` and rejects unsupported matrices; the exact vulnerable write path is not yet identified. |
| [11](11-cve-2022-22706-mali-write-readonly/) | CVE-2022-22706 Mali write-readonly | Mali WRITE_VALUE can modify GPU-mapped userspace pages but does not cross into kernel memory on this target. |
| [12](12-cve-2023-33200-mali-race-uaf/) | CVE-2023-33200 Mali imported-buffer race | Patched/dead on this target: `kbase_vmap_prot` rejects non-NATIVE imported USER_BUF allocations before the race window can open. |
| [13](13-apusys-ioctl-surface/) | APUSYS ioctl surface | `/dev/apusys` opens from `system_app`; provider opcode-0 dispatch is live, HardwareBuffer under `app_process64` supplies an importable dmabuf, normal VPU `ucmd` reaches `0x8001 + key`, `apu_lib_apunn` returns success, and `run_cmd_async` reaches `mdw_cmd_sc_valid invalid type(32)`. |

## Building the PoCs

Requires Android NDK r25+ with `aarch64-linux-android33-clang`.

```bash
cd 01-cve-2023-4622-sock-dead/poc
make -f ../../common/Makefile.ndk

# Or push to device:
make -f ../../common/Makefile.ndk push
```

The 01-05 kernel/Mali PoCs are diagnostic/verification tools. Later directories document the `uid=1000` framework bridge and non-destructive display reachability probes separately.

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
├── 05-gpu-write-value-boundary/
│   ├── README.md           # WRITE_VALUE capability and limits
│   └── poc/
│       ├── probe_write_value.c     # Basic WRITE_VALUE primitive test
│       └── probe_mprotect_bypass.c # mprotect bypass + import failure
├── 06-cve-2024-31317-zygote-injection/
│   ├── README.md           # Zygote command injection analysis
│   └── poc/
│       └── check_patch_state.sh    # Non-invasive patch-level triage
├── 07-cve-2023-32836-display-overflow/
│   ├── README.md           # DRM overflow analysis and runtime probes
│   └── poc/
│       └── DrmTrigger.java # Pure-Java syscall-based DRM probe
├── 08-cve-2023-32863-display-drm-oob-read/
│   └── README.md           # Display-drm OOB read triage
├── 09-cve-2023-32864-display-drm-oob-write/
│   └── README.md           # Display-drm OOB write triage
├── 10-cve-2023-32865-display-drm-oob-write/
│   ├── README.md           # Display-drm color-transform / OOB write triage
│   └── poc/
│       └── Mtk32865Probe.java # Non-destructive color-transform probe
├── 11-cve-2022-22706-mali-write-readonly/
│   └── README.md           # Mali WRITE_VALUE read-only boundary
├── 12-cve-2023-33200-mali-race-uaf/
│   ├── README.md           # Imported USER_BUFFER race triage
│   └── poc/
│       └── mali33200_state_probe.c
└── 13-apusys-ioctl-surface/
    ├── README.md           # APUSYS midware ioctl surface map
    └── poc/
        ├── ApusysIoctlProbe.java # system_app Java reachability probe
        └── apusys_ioctl_probe.c  # Native lab-context variant
```

## Ethics

This research was conducted on a personally-owned device for educational purposes. The notes record tested entry points, runtime results, and firmware-specific technical conclusions.

## License

MIT
