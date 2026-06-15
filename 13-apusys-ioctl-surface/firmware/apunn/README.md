# APUNN Firmware Artifacts

Date: 2026-06-15

This directory keeps the V260523 `apu_lib_apunn` core-0 firmware analysis
artifacts outside `/tmp`.

| File | Notes |
|---|---|
| `apunn_core0_full.elf` | Full embedded Xtensa ELF carved from `cam_vpu2.img` at partition offset `0x5e8` |
| `apunn_core0_full.elf.i64` | IDA Pro database file loaded as ELF for Xtensa, with `.xt.prop` metadata applied and saved |
| `apunn_core0_full_analysis_refs.json` | Analyzer JSON used by `tools/ida_apply_apunn_xt_prop.py`; regenerated as analysis annotations evolve |
| `apunn_core0_full_analysis_refs.md` | Human-readable analyzer summary; regenerated as analysis annotations evolve |

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

The persisted `.i64` database has the current `.xt.prop`-assisted state saved:
bounded function creation, key names/comments, pointer-run dwords/xrefs,
critical-string annotations, and verified standard-island comments plus manually
repaired early function boundaries around `0x70006590`, `0x70006794`, and
`0x70007440`. IDA may create local `.id0`, `.id1`, `.id2`, `.nam`, or `.til`
sidecars beside the `.i64`; those are optional local artifacts.

Repository-relative IDB path:

```text
13-apusys-ioctl-surface/firmware/apunn/apunn_core0_full.elf.i64
```

IDA still reports the original `input_path` as `/private/tmp/apunn_core0_full.elf`
because the database was first created from the temporary carve. The ELF bytes
are identical to `apunn_core0_full.elf` in this directory.
