# MT6893 Kernel Write Primitives: CVE Search Results (2024–2026)

Generated: 2026-06-16
Target: MT6893 / MT8797, Android 13, SPL 2023-06-05, uid=1000 / system_app

All CVEs listed are unpatched on the target device (fix SPL > 2023-06-05).

## Write-What-Where Primitives (CWE-123 — Best Quality)

| CVE | Component | Type | Fix SPL | Affects MT6893 | Notes |
|---|---|---|---|---|---|
| CVE-2024-20037 | pq | write-what-where (bounds check) | Mar 2024 | YES | ALPS08495937; entry confirmed |
| CVE-2024-20118 | mms | write-what-where (bounds check) | Nov 2024 | YES | ALPS09065746 (MSV-1576); 2nd-best primitive |
| CVE-2024-20119 | mms | write-what-where (bounds check) | Nov 2024 | YES | separate mms bug; same driver |

## OOB Write Primitives (CWE-787 — Standard)

| CVE | Component | CWE | Fix SPL | Affects MT6893 | Notes |
|---|---|---|---|---|---|
| CVE-2023-32849 | cmdq | 787 (type confusion) | Oct 2023 | YES | Command Queue driver; display/GPU path |
| CVE-2023-32866 | mmp | 787 | Oct 2023 | YES | Multimedia processor |
| CVE-2023-32885 | display drm | 119 | Jan 2024 | YES | Different from 08-analyzed paths |
| CVE-2024-20010 | keyInstall | 843 (type confusion) | Feb 2024 | YES | TEE key bridge |
| CVE-2024-20057 | keyInstall | 787 | May 2024 | YES | ALPS08587881 |
| CVE-2024-20074 | dmc | 787 | Jun 2024 | YES | Dynamic memory controller |
| CVE-2024-20075 | eemgpu | 787 | Jun 2024 | YES | GPU power/thermal |
| CVE-2024-20079 | gnss | 787 | Jul 2024 | YES | CISA-ADP 9.8 CRITICAL |
| CVE-2024-20081 | gnss | 787 | Jul 2024 | YES | Second gnss bug |
| CVE-2024-20098 | power | 787 | Oct 2024 | YES | DVFS ioctl |
| CVE-2024-20108 | atci | 787 | Nov 2024 | YES | AT command modem bridge |
| CVE-2024-20109 | ccu | 787 | Nov 2024 | YES | Camera ISP coprocessor |
| CVE-2024-20110 | ccu | 787 | Nov 2024 | YES | Camera ISP coprocessor |
| CVE-2024-20111 | ccu | 787 | Nov 2024 | YES | Camera ISP coprocessor |
| CVE-2024-20114 | ccu | 787 | Nov 2024 | YES | Camera ISP coprocessor |
| CVE-2024-20115 | ccu | 787 | Nov 2024 | YES | Camera ISP coprocessor |
| CVE-2024-20120 | keyInstall | 787 | Nov 2024 | YES | ALPS08956986 (MSV-1575) |
| CVE-2024-20125 | vdec | 787 | Dec 2024 | YES | Video decoder; ALPS09046782 |
| CVE-2024-20105 | m4u | 787 | Jan 2025 | YES | IOMMU driver — dangerous |
| CVE-2024-20140 | power | 787 | Jan 2025 | YES | ALPS09270402 |
| CVE-2025-20636 | secmem | 787 | Feb 2025 | YES | Secure memory driver; TEE boundary |

## Privilege Bypass / Logic Error Primitives

| CVE | Component | Type | Fix SPL | Affects MT6893 | Notes |
|---|---|---|---|---|---|
| CVE-2024-20032 | aee | Permission bypass (CWE-862) | Mar 2024 | YES | Android Exception Engine; logic bypass |
| CVE-2024-20021 | atf spm | Phys→virt memory remap (CWE-269) | May 2024 | YES | Cross-EL; ALPS08584568 |

## Info Leak Primitives (KASLR Defeat)

| CVE | Component | Type | Fix SPL | Affects MT6893 | Notes |
|---|---|---|---|---|---|
| CVE-2024-20084 | power | OOB read | Sep 2024 | YES | KASLR defeat candidate |
| CVE-2024-20085 | power | OOB read | Sep 2024 | YES | KASLR defeat candidate |
| CVE-2024-20088 | keyinstall | OOB read | Sep 2024 | YES | KASLR defeat candidate |
| CVE-2024-20095 | m4u | OOB read | Oct 2024 | YES | IOMMU info leak |
| CVE-2024-20096 | m4u | OOB read | Oct 2024 | YES | IOMMU info leak |
| CVE-2024-20116 | cmdq | OOB read | Dec 2024 | MT8797 | KASLR defeat; confirm MT6893 |

## Excluded (Wrong Chipset or Privilege Level)

| CVE | Reason |
|---|---|
| CVE-2024-20078 | venc OOB write — MT8797 only, not MT6893 |
| CVE-2024-20083 | venc OOB write — MT8797 only |
| CVE-2025-20645 | keyInstall OOB write — Android 14/15 only |

## Research Priority Ranking (system_app, uid=1000)

1. **CVE-2024-20037** (pq, write-what-where) — entry confirmed, kernel IDB exists
   → Active: `16-cve-2024-20037-pq/`

2. **CVE-2024-20118/119** (mms, write-what-where ×2) — strongest unconfirmed pair
   → Stub: `18-cve-2024-20118-mms/`

3. **CVE-2023-32849** (cmdq, type confusion OOB write) — stable ioctl surface
   → Stub: `19-cve-2023-32849-cmdq/`

4. **CVE-2024-20032** (aee, permission bypass) — no heap exploit needed
   → Stub: `20-cve-2024-20032-aee/`

5. **CVE-2024-20021** (atf spm, phys→virt remap) — cross-EL; very powerful if reachable
   → Stub: `21-cve-2024-20021-atf-spm/`

6. **CVE-2024-20109–20115** (ccu ×6, OOB write) — camera HAL accessible
   → Stub: `22-cve-2024-20109-ccu/`

7. **KASLR defeat cluster** (power/keyinstall/m4u info leaks) — pair with write primitives
   → Track in `23-kaslr-info-leaks/`

## Source Links

- MediaTek bulletins: https://corp.mediatek.com/product-security-bulletin/
- NVD search: https://nvd.nist.gov/vuln/search?query=mediatek+mt6893
