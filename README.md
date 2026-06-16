# MT6893 Security Research

Security research findings from a privilege escalation study on a MediaTek MT6893 (Dimensity 1200) Android tablet.

The target device (kernel 4.19.191, Mali Valhall r32p1, SPL 2023-06-05, SELinux enforcing) resisted the tested kernel and Mali escalation paths from `uid=2000`. These writeups document the specific technical reasons why those paths fail, plus later framework-level triage where applicable.

Current experiment closure status is summarized in [docs/EXPERIMENT_CLOSURE.md](docs/EXPERIMENT_CLOSURE.md). Historical CVE-2023-4622 ARM64 work lives in [00-cve-2023-4622-arm64/](00-cve-2023-4622-arm64/); the numbered `01-*` through `22-*` directories are the topic-oriented writeups and PoCs. **KASLR is confirmed** (via `MTK_SET_PQPARAM` KE + `platform_app` AEE DB read); the remaining gap is a kernel write primitive (APUSYS IOVA reuse or mms write-what-where).

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
| [00](00-cve-2023-4622-arm64/) | Legacy CVE-2023-4622 ARM64 workspace | Original exploit-chain workspace with phase notes, driver probes, reverse artifacts, and crash forensics retained for traceability. |
| [01](01-cve-2023-4622-sock-dead/) | CVE-2023-4622 SOCK_DEAD | The sendpage UAF is logically unexploitable: `SOCK_DEAD` check precedes the UAF entry in every execution path. 440,000+ experiments, 0 bypass. |
| [02](02-mali-no-user-free-count/) | Mali `no_user_free_count` | An undocumented permanent guard in r32p1: incremented once at alloc, never decremented. Blocks CVE-2022-38181 and CVE-2023-4211. |
| [03](03-cve-2022-36449-retraction/) | CVE-2022-36449 retraction | `MEM_FREE` doesn't free the physical page - user mmap holds refcount=1. We retract our initial "UAF confirmed" claim. |
| [04](04-arm64-skb-offset-divergence/) | ARM64 `sk_buff` +0x10 trap | Android vendor kernels shift `sk_buff` fields by +0x10. Using x86 offsets causes `shinfo=0xffffff96` and instant DABT. |
| [05](05-gpu-write-value-boundary/) | GPU WRITE_VALUE boundary | GPU WRITE_VALUE bypasses CPU `mprotect(PROT_READ)` but only reaches SAME_VA allocations - kernel memory stays unreachable. |
| [06](06-cve-2024-31317-zygote-injection/) | CVE-2024-31317 Zygote injection | Android Framework bug: `hidden_api_blacklist_exemptions` was serialized into Zygote's line protocol without control-character filtering. Target framework static check shows the vulnerable direct-write pattern is present. |
| [07](07-cve-2023-32836-display-overflow/) | CVE-2023-32836 display overflow | `/dev/dri/card0` is reachable from `system_app`, but the tested `CREATE_DUMB` path uses MTK 64-bit size calculation and direct atomic commit is permission-gated. |
| [08](08-cve-2023-32863-display-drm-oob-read/) | CVE-2023-32863 display-drm OOB read | MTK private DRM getters are only partially reachable; tested handlers do not currently show a user-controlled OOB read. |
| [09](09-cve-2023-32864-display-drm-oob-write/) | CVE-2023-32864 display-drm OOB write | MTK register write ioctls are reachable, but current `WRITE_REG` validates physical addresses and invalid write probes are rejected. |
| [10](10-cve-2023-32865-display-drm-oob-write/) | CVE-2023-32865 display-drm OOB write | The color-transform validation ioctl is reachable from `system_app` and rejects unsupported matrices; the exact vulnerable write path is not yet identified. |
| [11](11-cve-2022-22706-mali-write-readonly/) | CVE-2022-22706 Mali write-readonly | Mali WRITE_VALUE can modify GPU-mapped userspace pages but does not cross into kernel memory on this target. |
| [12](12-cve-2023-33200-mali-race-uaf/) | CVE-2023-33200 Mali imported-buffer race | Patched/dead on this target: `kbase_vmap_prot` rejects non-NATIVE imported USER_BUF allocations before the race window can open. |
| [13](13-apusys-ioctl-surface/) | APUSYS ioctl surface | `/dev/apusys` opens from `system_app`; provider opcode-0 dispatch is live, HardwareBuffer under `app_process64` supplies an importable dmabuf, normal VPU `ucmd` reaches `0x8001 + key`, `apu_lib_apunn` returns success, and full-size normal VPU dispatch now reaches APUNN settings/output completion in the wrapper-shaped `settings5/no-settings` request. Leak behavior and timeout lifetime misuse are not yet proven. |
| [14](14-cve-2023-32834-secmem/) | CVE-2023-32834 secmem | Tracked as a service-mediated secure-memory candidate; direct device-node access is not the current path. |
| [15](15-cve-2023-32835-keyinstall/) | CVE-2023-32835 keyinstall | Target image lacks the named keyinstall HAL; current keymaster path does not expose the expected GZ/UREE bridge. |
| [16](16-cve-2024-20037-pq/) | CVE-2024-20037 PQ/CMDQ/MMP | IDA confirmed all write paths are cmdq display MMIO (not kernel memory). Downgraded: not a kernel write primitive. `MTK_SET_PQPARAM.u4PartialY=0xffffffff` is useful as a KE trigger for KASLR only. |
| [17](17-cve-2024-20005-da/) | CVE-2024-20005 DA/AEE/LK | Tracked as an update/boot-flow entry-discovery candidate until runtime reachability is proven. |
| [18](18-cve-2024-20118-mms/) | CVE-2024-20118/119 mms write-what-where | Two write-what-where primitives in the `mms` multimedia scheduler (NOT GED — attribution corrected). Path: `system_app` → SurfaceFlinger binder → `/dev/mtk-mdp`. Direct node access blocked; entry via SurfaceFlinger API is the open research question. GED CVE-2024-20016 is DoS-only (ring wrap confirmed). |
| [19](19-cve-2023-32849-cmdq/) | CVE-2023-32849 CMDQ type confusion | CMDQ OOB write via type confusion. Stub — entry point and device-node access from `system_app` not yet confirmed. |
| [20](20-cve-2024-20032-aee/) | CVE-2024-20032 AEE permission bypass + **KASLR ✅** | Permission bypass confirmed: `system_app` connects to root-running `@android:aee_aed` (uid=0). Wire protocol reversed (24-byte AE_IND/REQ/RSP). ANR leak and AEE session drive confirmed. **KASLR confirmed** via `MTK_SET_PQPARAM` KE + `platform_app` read of `/data/vendor/aee_exp/db.fatal.*.KE.dbg`. AEE DB decryption tool included. |
| [21](21-cve-2024-20109-ccu/) | CVE-2024-20109–20115 CCU OOB writes | Six OOB write CVEs in camera ISP coprocessor (`ccu`). Stub — reachability via camera HAL path not yet confirmed. |
| [22](22-cve-2024-20075-eemgpu/) | CVE-2024-20075 eemgpu OOB write — **BLOCKED** | All trigger paths (`/proc/eemg/*`, sysfs bind/unbind, vendor path scan) require uid=0. Cannot reach from `system_app`. Blocked until root is obtained. |

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
mt6893/
├── README.md
├── 00-cve-2023-4622-arm64/          # Original phase-based exploit workspace
├── 01-cve-2023-4622-sock-dead/      # Dead-end writeups (01-05)
├── ...
├── 06-cve-2024-31317-zygote-injection/  # Current foothold (system_app + platform_app shells)
├── 07-cve-2023-32836-display-overflow/  # Kernel IDB (vmlinux.bin.i64) + KASLR KE trigger
├── ...
├── 13-apusys-ioctl-surface/         # Active: VPU dispatch, IOVA reuse (write primitive track)
├── ...
├── 18-cve-2024-20118-mms/           # Active: mms write-what-where (via SurfaceFlinger)
├── 19-cve-2023-32849-cmdq/          # Stub: CMDQ type confusion OOB write
├── 20-cve-2024-20032-aee/           # DONE: AEE permission bypass + KASLR confirmed
├── 21-cve-2024-20109-ccu/           # Stub: CCU OOB writes ×6
├── 22-cve-2024-20075-eemgpu/        # Blocked: requires uid=0
├── common/                          # Shared NDK Makefile and minimal Mali UAPI
├── docs/                            # Cross-topic assessments and closure notes
├── exploit-gatebench/               # Experiment scoring/orchestration lab
├── poc-run-results/                 # Captured runtime evidence
└── scripts/                         # Repository maintenance scripts
```

## Ethics

This research was conducted on a personally-owned device for educational purposes. The notes record tested entry points, runtime results, and firmware-specific technical conclusions.

## License

MIT
