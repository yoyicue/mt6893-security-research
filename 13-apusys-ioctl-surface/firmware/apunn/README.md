# APUNN Firmware Artifacts

Date: 2026-06-15

This directory keeps the V260523 `apu_lib_apunn` core-0 firmware analysis
artifacts outside `/tmp`.

| File | SHA-256 | Notes |
|---|---|---|
| `apunn_core0_full.elf` | `69658bfe18e8084e44da165ebc326c01ce9a2e672a059ae5a706ce5e397c3c88` | Full embedded Xtensa ELF carved from `cam_vpu2.img` at partition offset `0x5e8` |
| `apunn_core0_full.elf.i64` | `4da91a806b7adf894f027adddec0bb60ed5b107409b4f4c8b86e4049fd247e09` | IDA Pro database file loaded as ELF for Xtensa, with `.xt.prop` metadata applied and saved |
| `apunn_core0_full.elf.id0` | `954704c3211ffb98cd79a2456245cfd4e5744b06a023f4c23c228dff5c22af36` | IDA database sidecar |
| `apunn_core0_full.elf.id1` | `564b8c28f69bfda7e80f33571bc8f408d1a29112e28abf910304dacfbc58b4b9` | IDA database sidecar |
| `apunn_core0_full.elf.id2` | `c2bc91b3f6437172dd7c4f2172e15e1933a0494176879713a5df169d80a3548b` | IDA database sidecar |
| `apunn_core0_full.elf.nam` | `d3a444497d1fb99a24af8d6c8376feed97dc5ed93e1ac3f1d45512610f41a275` | IDA database sidecar |
| `apunn_core0_full.elf.til` | `513e9d059ab03cfc3088999d32eb2fb26a8a3f338a7f95720e81075256443d4b` | IDA database sidecar |
| `apunn_core0_full_analysis_refs.json` | `a53bd6519ccd675e7887bf064a2ced935656c64aab16a2b54a36261a7d1a13a5` | Analyzer JSON used by `tools/ida_apply_apunn_xt_prop.py` |
| `apunn_core0_full_analysis_refs.md` | `8adf839c190449351f962c413a3ee3111259279810f056e058ddbc924d28c956` | Human-readable analyzer summary |

## Source

The source OTA partition is:

```text
/tmp/ota_V260523_cam_vpu/cam_vpu2.img
```

The APUNN core-0 main PROG segment begins at raw partition offset `0x3b4`. The
embedded ELF header is `+0x234` inside that segment, so the ELF starts at
partition file offset `0x5e8`.

Recreate the ELF:

```sh
dd if=/tmp/ota_V260523_cam_vpu/cam_vpu2.img \
  of=/tmp/apunn_core0_full.elf bs=1 skip=$((0x3b4+0x234)) status=none
```

Regenerate the analysis JSON/Markdown:

```sh
13-apusys-ioctl-surface/tools/analyze_apunn_elf.py \
  /tmp/apunn_core0_full.elf \
  --json /tmp/apunn_core0_full_analysis_refs.json \
  --markdown /tmp/apunn_core0_full_analysis_refs.md
```

## IDA Notes

Open `apunn_core0_full.elf` as **ELF for Xtensa**, not as raw `Binary File`.
Verify `ida_ida.inf_get_procname()` returns `XTENSA` and that `0x70006794`
decodes as `entry sp, 0x20`.

Apply the saved metadata in IDA:

```python
exec(open("13-apusys-ioctl-surface/tools/ida_apply_apunn_xt_prop.py").read())
```

The persisted IDA database set (`.i64`, `.id0`, `.id1`, `.id2`, `.nam`, `.til`)
has the current `.xt.prop`-assisted state saved: bounded function creation, key
names/comments, pointer-run dwords/xrefs, critical-string annotations, and
manually repaired early function boundaries around `0x70006590`, `0x70006794`,
and `0x70007440`.

The current MCP-connected IDB path is:

```text
/Users/biu/mtk/mt6893-security-research/13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf.i64
```

IDA still reports the original `input_path` as `/private/tmp/apunn_core0_full.elf`
because the database was first created from the temporary carve. The ELF bytes
are identical to `apunn_core0_full.elf` in this directory.
