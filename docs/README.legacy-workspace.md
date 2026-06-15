# MTK Exploit Research Workspace

**Status: CLOSED (2026-04-18)**

This is the historical workspace overview retained after the repository was
reorganized. For the current root index, use [../README.md](../README.md).

## Summary

ARM64 kernel 4.19.191 privilege escalation research targeting 学而思 xpad2 pro (MT6893).

### Findings

- **CVE-2023-4622** (af_unix sendpage UAF): SOCK_DEAD 逻辑硬约束, IDA 二进制验证确认不可绕过. 440,000+ 次实验 0 bypass.
- **Mali r32p1 pivot**: 8 条攻击路径全部阻断 (no_user_free_count 保护, shrinker PTE 拆除顺序, import PERMISSION_FAULT 等).
- **内核 CVE 审计**: 所有高价值本地提权 CVE 均不可用 (未编译/已补丁/缺权限).
- **结论**: MT6893 + SPL 2023-06-05 从 uid=2000 无法通过已知 CVE 提权.

## Device

| Item | Value |
|---|---|
| SoC | MT6893 (Dimensity 1200), aarch64 |
| Kernel | 4.19.191+ SMP PREEMPT |
| Mali | Valhall r32p1-00bet5 (UK 11.31) |
| Android | 13 (first_api_level=33) |
| SELinux | enforcing |
| SPL | 2023-06-05 |

## Directory Layout

```
mt6893/
├── README.md                        # Current root index
├── docs/README.legacy-workspace.md  # This historical overview
├── 00-cve-2023-4622-arm64/             # Main exploit research workspace
│   ├── PLAN.md                      #   Master plan with all phase details
│   ├── HARDCODED_OFFSETS_ANALYSIS.md #   Kernel offset risk analysis
│   ├── phase1_trigger/              #   UAF race trigger (confirmed 21.5h crash)
│   ├── phase2_kaslr/                #   KASLR leak via Mali timeline
│   ├── phase3_heap/                 #   Heap feng shui (msg_msg overread)
│   ├── phase4_chain/                #   Full exploit chain (CLOSED: SOCK_DEAD)
│   │   └── docs/ASSESSMENT.md       #     Final assessment + Mali CVE matrix
│   ├── driver_research/             #   Mali + ION driver probes (30+ tools)
│   ├── notes/                       #   Research notes (extracted from sessions)
│   ├── reverse/                     #   vmlinux rebuild + IDA analysis
│   └── forensics/                   #   Device crash evidence
├── exploit-gatebench/               # Exploit effectiveness benchmarking framework
└── scripts/                         # Repository maintenance scripts
```

## Key Documents

| Document | Description |
|---|---|
| `../00-cve-2023-4622-arm64/PLAN.md` | 完整研究计划与各阶段结果 |
| `../00-cve-2023-4622-arm64/phase4_chain/docs/ASSESSMENT.md` | SOCK_DEAD 硬约束证明 + Mali 攻击面分析 |
| `../00-cve-2023-4622-arm64/HARDCODED_OFFSETS_ANALYSIS.md` | 9 个硬编码内核偏移的验证状态 |
| `../00-cve-2023-4622-arm64/reverse/SELINUX_STATE_EVIDENCE.md` | selinux_state 偏移推导证据链 |
| `../00-cve-2023-4622-arm64/reverse/VMLINUX_CHECKLIST_DELIVERABLES.md` | 重建 vmlinux 产物校验 |
