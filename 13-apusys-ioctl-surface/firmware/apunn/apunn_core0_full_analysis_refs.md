# APUNN Xtensa ELF summary

## ELF

- entry: `0x70006794`
- machine: `0x5e`
- flags: `0x300`

## Xtensa Core Info

- `CORE_NAME`: `MVPU6F_1214_Prod`
- `HW_VERSION`: `LX7.0.10`
- `RELEASE_NAME`: `RG-2018.10`
- `RELEASE_VERSION`: `12.0.10`
- `ABI`: `0`
- `USE_ABSOLUTE_LITERALS`: `0`
- `SW_FLOATING_POINT_ABI`: `1`

## Sections

| name | addr | file offset | size | flags |
|---|---:|---:|---:|---:|
| `` | `0x0` | `0x0` | `0x0` | `0x0` |
| `.data` | `0x7ff04000` | `0x26aea4` | `0xb4` | `0x3` |
| `.dram0.data` | `0x7ff040c0` | `0x26af64` | `0x17c4` | `0x3` |
| `.bss` | `0x7ff05888` | `0x26c728` | `0x98` | `0x3` |
| `.dram_op.data` | `0x7ff3b000` | `0x26c728` | `0x218` | `0x3` |
| `.dram1_resvdma.bss` | `0x7ff3efc0` | `0x26c940` | `0x8` | `0x3` |
| `.rodata` | `0x70000000` | `0x3b4` | `0x6500` | `0x2` |
| `.text` | `0x70006500` | `0x68b4` | `0x2645f0` | `0x6` |
| `.xt.prop` | `0x0` | `0x26c940` | `0xb9aa8` | `0x0` |
| `.comment` | `0x0` | `0x3263e8` | `0x65c5` | `0x30` |
| `.xtensa.info` | `0x0` | `0x32c9ad` | `0x204` | `0x0` |
| `.shstrtab` | `0x0` | `0x32cbb1` | `0x65` | `0x0` |

## Xtensa Property Counts

- `total`: 63374
- `insn`: 54282
- `data`: 21806
- `loop_target`: 1154
- `branch_target`: 14383
- `unreachable`: 8838

## Entry Property Neighborhood

| addr | size | flags |
|---:|---:|---|
| `0x7000677b` | `0x17` | `insn|no_reorder` |
| `0x70006792` | `0x2` | `insn|branch_target|no_reorder` |
| `0x70006794` | `0x0` | `unreachable|align|align2` |
| `0x70006794` | `0xac` | `insn|no_reorder` |

## Function Entry Candidates

- `.xt.prop` constrained `entry` prologues: 1019

### First Candidates

| addr | prop size | next entry delta | entry bytes | flags |
|---:|---:|---:|---|---|
| `0x70006520` | `0x1b` | `0x4c` | `364100` | `insn|no_reorder` |
| `0x7000656c` | `0x20` | `0x24` | `364100` | `insn|no_reorder` |
| `0x70006590` | `0xe` | `0x10` | `364100` | `insn|no_reorder` |
| `0x700065a0` | `0x25` | `0x38` | `366100` | `insn|no_reorder` |
| `0x700065d8` | `0x16` | `0x30` | `364100` | `insn|no_reorder` |
| `0x70006608` | `0x14` | `0x2c` | `364100` | `insn|no_reorder` |
| `0x70006634` | `0x24` | `0x3c` | `364100` | `insn|no_reorder` |
| `0x70006670` | `0x24` | `0x3c` | `364100` | `insn|no_reorder` |
| `0x700066ac` | `0x41` | `0x5c` | `36a100` | `insn|no_reorder` |
| `0x70006708` | `0x24` | `0x3c` | `364100` | `insn|no_reorder` |
| `0x70006744` | `0x2f` | `0x50` | `364100` | `insn|no_reorder` |
| `0x70006794` | `0xac` | `0xec` | `364100` | `insn|no_reorder` |
| `0x70006880` | `0x16` | `0x18` | `364100` | `insn|no_reorder` |
| `0x70006898` | `0x11` | `0x14` | `364100` | `insn|no_reorder` |
| `0x700068ac` | `0x11` | `0x14` | `364100` | `insn|no_reorder` |
| `0x700068c0` | `0x3` | `0x8` | `364100` | `insn|no_reorder` |
| `0x700068c8` | `0xf` | `0x14` | `364100` | `insn|no_reorder` |
| `0x700068dc` | `0x34` | `0xb4` | `368100` | `insn|no_reorder` |
| `0x70006990` | `0x50` | `0x6c` | `368100` | `insn|no_reorder` |
| `0x700069fc` | `0x1e` | `0x7c` | `364100` | `insn|no_reorder` |
| `0x70006a78` | `0x56` | `0x460` | `36a107` | `insn|no_reorder` |
| `0x70006ed8` | `0x40` | `0x568` | `360101` | `insn|no_reorder` |
| `0x70007440` | `0x50` | `0x460` | `36c100` | `insn|no_reorder` |
| `0x700078a0` | `0x33` | `0x1f0` | `366100` | `insn|no_reorder` |
| `0x70007a90` | `0x36` | `0x1f8` | `366100` | `insn|no_reorder` |
| `0x70007c88` | `0x1e` | `0x19c` | `36a100` | `insn|no_reorder` |
| `0x70007e24` | `0x52` | `0x1d9c` | `366104` | `insn|no_reorder` |
| `0x70009bc0` | `0x33` | `0x1f0` | `366100` | `insn|no_reorder` |
| `0x70009db0` | `0x26` | `0x13c` | `366100` | `insn|no_reorder` |
| `0x70009eec` | `0x52` | `0x1694` | `360104` | `insn|no_reorder` |
| `0x7000b580` | `0x34` | `0x34` | `366100` | `insn|no_reorder` |
| `0x7000b5b4` | `0x32e` | `0x84c` | `368103` | `insn|no_reorder` |

### Largest Next-Entry Gaps

| addr | next entry delta | prop size | entry bytes | flags |
|---:|---:|---:|---|---|
| `0x702161d0` | `0x7c50` | `0x61` | `360105` | `insn|no_reorder` |
| `0x70065b10` | `0x6bf0` | `0x4a` | `366111` | `insn|no_reorder` |
| `0x70149790` | `0x6ba0` | `0xc9` | `36410e` | `insn|no_reorder` |
| `0x70210050` | `0x6180` | `0x61` | `362105` | `insn|no_reorder` |
| `0x700304f8` | `0x5588` | `0x44` | `36c108` | `insn|no_reorder` |
| `0x70109e50` | `0x4ec0` | `0x40` | `362102` | `insn|no_reorder` |
| `0x701ea4b0` | `0x4d10` | `0x91` | `364103` | `insn|no_reorder` |
| `0x70061134` | `0x49dc` | `0x47` | `36e109` | `insn|no_reorder` |
| `0x70200630` | `0x44e0` | `0x99` | `362106` | `insn|no_reorder` |
| `0x700b3d00` | `0x4400` | `0x12` | `36c110` | `insn|no_reorder` |
| `0x700b8100` | `0x43f0` | `0x12` | `36c110` | `insn|no_reorder` |
| `0x70208620` | `0x3bc0` | `0x2b` | `36c103` | `insn|no_reorder` |
| `0x7000dcc4` | `0x3b1c` | `0x57` | `36810b` | `insn|no_reorder` |
| `0x701ae580` | `0x3400` | `0xb2` | `36c106` | `insn|no_reorder` |
| `0x70204b10` | `0x33d0` | `0x74` | `364105` | `insn|no_reorder` |
| `0x700414d0` | `0x3380` | `0x44` | `366108` | `insn|no_reorder` |
| `0x70076d50` | `0x3370` | `0x44` | `36e107` | `insn|no_reorder` |
| `0x7004b3e8` | `0x32b8` | `0x55` | `364110` | `insn|no_reorder` |
| `0x70071ef0` | `0x30b0` | `0x56` | `36e107` | `insn|no_reorder` |
| `0x7006cb90` | `0x2fd0` | `0x47` | `36e108` | `insn|no_reorder` |

## Key Address Owners

| label | addr | section | owner entry | owner delta | prop |
|---|---:|---|---:|---:|---|
| `elf_entry_INFO16` | `0x70006794` | `.text` | `0x70006794` | `0x0` | `insn|no_reorder:0xac` |
| `early_helper` | `0x70006590` | `.text` | `0x70006590` | `0x0` | `insn|no_reorder:0xe` |
| `early_dynamic_dispatch` | `0x70007440` | `.text` | `0x70007440` | `0x0` | `insn|no_reorder:0x50` |
| `flk_pointer_target_cluster` | `0x70017d40` | `.text` | `0x70015e98` | `0x1ea8` | `insn|data|no_reorder|no_transform:0xb` |
| `flk_pointer_table_owner` | `0x70015e98` | `.text` | `0x70015e98` | `0x0` | `insn|data|no_reorder|no_transform:0xb09` |
| `dispatcher_like_locateBuffer` | `0x700301d8` | `.text` | `0x700301d8` | `0x0` | `insn|no_reorder:0x30` |
| `flix_assisted_INFO13_record_lead` | `0x7003b468` | `.text` | `0x7003b468` | `0x0` | `insn|no_reorder:0x50` |
| `flix_assisted_INFO13_record_loop_target` | `0x7003c102` | `.text` | `0x7003b468` | `0xc9a` | `insn|loop_target|no_reorder:0xaa` |
| `buffer_record_high_field_validator_candidate` | `0x7003ce3c` | `.text` | `0x7003ce3c` | `0x0` | `insn|no_reorder:0x194` |
| `large_auto_function` | `0x7003b424` | `.text` | `0x7003b424` | `0x0` | `insn|no_reorder:0x6` |
| `top_dmaif_l32r_owner_cluster` | `0x70044b74` | `.text` | `0x70044b74` | `0x0` | `insn|no_reorder:0x44` |
| `ann_pointer_table_owner` | `0x70081d50` | `.text` | `0x70081d50` | `0x0` | `insn|no_reorder:0xa3` |
| `ann_pointer_target_cluster` | `0x70081ee7` | `.text` | `0x70081d50` | `0x196` | `insn|branch_target|no_reorder:0x5` |
| `ann_type_helper` | `0x70083068` | `.text` | `0x70083068` | `0x0` | `insn|no_reorder:0x17` |

## Verified Standard Xtensa Islands

These are narrow byte-verified standard-instruction islands inside extension-heavy ranges; they are not complete function decompilations.

### `elf_entry_context_pack` `0x70006794`-`0x700067d8` verified=True

Stable standard Xtensa island inside the 0x70006794 INFO16 entry; copies preload/context fields into the a10 scratch/context object.

| addr | bytes | mnemonic | effect |
|---:|---|---|---|
| `0x70006794` | `36 41 00` | `entry sp, 0x20` | open a 0x20-byte stack frame |
| `0x700067b1` | `98 0c` | `l32i.n a9, a12, 0x00` | load dword from a12+0x00 |
| `0x700067b3` | `99 1a` | `s32i.n a9, a10, 0x04` | store a12+0x00 value to a10+0x04 |
| `0x700067b5` | `88 1c` | `l32i.n a8, a12, 0x04` | load dword from a12+0x04 |
| `0x700067b7` | `89 2a` | `s32i.n a8, a10, 0x08` | store a12+0x04 value to a10+0x08 |
| `0x700067b9` | `f8 2c` | `l32i.n a15, a12, 0x08` | load dword from a12+0x08 |
| `0x700067bb` | `f9 3a` | `s32i.n a15, a10, 0x0c` | store a12+0x08 value to a10+0x0c |
| `0x700067bd` | `e8 3c` | `l32i.n a14, a12, 0x0c` | load dword from a12+0x0c |
| `0x700067bf` | `e9 4a` | `s32i.n a14, a10, 0x10` | store a12+0x0c value to a10+0x10 |
| `0x700067c1` | `d8 4c` | `l32i.n a13, a12, 0x10` | load dword from a12+0x10 |
| `0x700067c3` | `d9 5a` | `s32i.n a13, a10, 0x14` | store a12+0x10 value to a10+0x14 |
| `0x700067c5` | `c8 5c` | `l32i.n a12, a12, 0x14` | load dword from a12+0x14 |
| `0x700067c7` | `c9 6a` | `s32i.n a12, a10, 0x18` | store a12+0x14 value to a10+0x18 |
| `0x700067c9` | `d2 22 11` | `l32i a13, a2, 0x44` | load dword from a2+0x44 |
| `0x700067cc` | `d9 aa` | `s32i.n a13, a10, 0x28` | store a2+0x44 value to a10+0x28 |
| `0x700067ce` | `b2 22 13` | `l32i a11, a2, 0x4c` | load dword from a2+0x4c |
| `0x700067d1` | `b9 7a` | `s32i.n a11, a10, 0x1c` | store a2+0x4c value to a10+0x1c |
| `0x700067d3` | `92 22 14` | `l32i a9, a2, 0x50` | load dword from a2+0x50 |
| `0x700067d6` | `99 8a` | `s32i.n a9, a10, 0x20` | store a2+0x50 value to a10+0x20 |

### `early_helper_dispatch` `0x70006590`-`0x7000659e` verified=True

Small helper that forwards a context-derived pointer to 0x70007440 and returns 0.

| addr | bytes | mnemonic | effect |
|---:|---|---|---|
| `0x70006590` | `36 41 00` | `entry sp, 0x20` | open a 0x20-byte stack frame |
| `0x70006593` | `a8 c2` | `l32i.n a10, a2, 0x30` | load pointer from a2+0x30 |
| `0x70006595` | `a8 6a` | `l32i.n a10, a10, 0x18` | load pointer from previous+0x18 |
| `0x70006597` | `a5 ea 00` | `call8 0x70007440` | call early dynamic dispatch helper |
| `0x7000659a` | `0c 02` | `movi.n a2, 0` | set return value to 0 |
| `0x7000659c` | `1d f0` | `retw.n` | return |

### `early_dynamic_dispatch_standard_island` `0x70007440`-`0x700074fc` verified=True

Standard instructions visible inside extension-heavy 0x70007440; shows ccount timing and repeated indirect calls through *(a12+0).

| addr | bytes | mnemonic | effect |
|---:|---|---|---|
| `0x70007440` | `36 c1 00` | `entry sp, 0x60` | open a 0x60-byte stack frame |
| `0x7000744f` | `90 ea 03` | `rsr.ccount a9` | read cycle counter before dispatch |
| `0x70007452` | `82 2c 00` | `l32i a8, a12, 0x00` | load callback/function pointer from a12+0x00 |
| `0x70007455` | `bc 78` | `beqz.n a8, 0x70007490` | skip indirect call if function pointer is null |
| `0x7000746d` | `e0 08 00` | `callx8 a8` | call function pointer |
| `0x700074b7` | `e0 08 00` | `callx8 a8` | call function pointer again in the timed dispatch path |
| `0x700074d8` | `65 3e ff` | `call8 0x700068c0` | call local helper in the return path |
| `0x700074e6` | `a9 0b` | `s32i.n a10, a11, 0x00` | store callback/helper result through a11 |
| `0x700074e8` | `ad 02` | `mov.n a10, a2` | move saved argument/result into a10 before callback |
| `0x700074f0` | `e0 08 00` | `callx8 a8` | second function-pointer call site |
| `0x700074f5` | `c0 ea 03` | `rsr.ccount a12` | read cycle counter after dispatch |
| `0x700074f8` | `28 51` | `l32i.n a2, sp, 0x14` | load return value from stack |
| `0x700074fa` | `1d f0` | `retw.n` | return |

### `early_dynamic_dispatch_callback_loop` `0x7000750c`-`0x700075c4` verified=True

Second standard-instruction island inside the 0x70007440 owner. It confirms the early dynamic dispatcher has a repeated callback/polling loop after the first return-shaped island, not just one function-pointer call.

| addr | bytes | mnemonic | effect |
|---:|---|---|---|
| `0x7000750c` | `58 c8` | `l32i.n a5, a8, 0x30` | load callback/state field from a8+0x30 |
| `0x7000750e` | `59 00` | `s32i.n a5, a0, 0x00` | store callback/state field through a0 |
| `0x70007516` | `e0 08 00` | `callx8 a8` | call function pointer |
| `0x7000752f` | `e0 08 00` | `callx8 a8` | call function pointer |
| `0x70007550` | `e5 36 ff` | `call8 0x700068c0` | call local wait/spin helper |
| `0x7000755e` | `38 32` | `l32i.n a3, a2, 0x0c` | load field from returned object at a2+0x0c |
| `0x70007560` | `08 02` | `l32i.n a0, a2, 0x00` | load field from returned object at a2+0x00 |
| `0x70007573` | `b8 ff` | `l32i.n a11, a15, 0x3c` | load field from a15+0x3c before callback |
| `0x70007575` | `e0 08 00` | `callx8 a8` | call function pointer |
| `0x7000757e` | `a9 14` | `s32i.n a10, a4, 0x04` | store callback result to a4+0x04 |
| `0x70007586` | `e0 08 00` | `callx8 a8` | call function pointer |
| `0x70007589` | `0c 2b` | `movi.n a11, 2` | load constant 2 |
| `0x7000758b` | `b0 aa 63` | `minu a10, a10, a11` | clamp callback result to at most 2 |
| `0x7000758e` | `a9 c1` | `s32i.n a10, sp, 0x30` | save clamped callback result on stack |
| `0x70007590` | `a9 04` | `s32i.n a10, a4, 0x00` | store clamped callback result to a4+0x00 |
| `0x700075a9` | `dc 58` | `bnez.n a8, 0x700075c2` | branch on callback/state pointer |
| `0x700075c1` | `e0 08 00` | `callx8 a8` | call function pointer |

### `dispatcher_locateBuffer_trampoline` `0x700301d8`-`0x70030208` verified=True

Byte-verified standard island at the dispatcher-like locateBuffer candidate. The l32r at 0x700301e3 loads the rodata suffix `locateBuffer` from 0x70001884, then the island reaches a branch toward the 0x70030240/0x70030a0c owner and an indirect call.

| addr | bytes | mnemonic | effect |
|---:|---|---|---|
| `0x700301d8` | `36 61 00` | `entry sp, 0x30` | open a 0x30-byte stack frame |
| `0x700301e3` | `31 a8 45` | `l32r a3, 0x70001884` | load `locateBuffer` string suffix into a3 |
| `0x700301ef` | `88 b8` | `l32i.n a8, a8, 0x2c` | load dispatch table/context field from a8+0x2c |
| `0x700301f6` | `38 d8` | `l32i.n a3, a8, 0x34` | load dispatch/context field from a8+0x34 |
| `0x700301f8` | `06 04 02` | `j 0x70030a0c` | jump into the larger 0x70030240 owner |
| `0x70030201` | `e0 08 00` | `callx8 a8` | call function pointer |
| `0x70030204` | `2d 0a` | `mov.n a2, a10` | move callback return value into a2 |
| `0x70030206` | `1d f0` | `retw.n` | return |

### `dispatcher_operand_record_field_decode` `0x70030a0c`-`0x70030d90` verified=True

Byte-verified standard instructions reached from the locateBuffer trampoline landing path. They read byte/halfword-shaped fields from the a2 record at offsets matching the DSP command operation/operand area, including +0x49/+0x4a near the first operand slot, then test the extracted 4-bit value against 1, 5, 6, and 9. This ties the 0x700301d8 dispatcher path to command/operand parsing; it is not yet the native INFO13 vpu_buffer-array parser.

| addr | bytes | mnemonic | effect |
|---:|---|---|---|
| `0x70030a0c` | `66 12 00` | `bnei a2, 1, 0x70030a10` | branch on a2 != 1 special case |
| `0x70030a18` | `cc 13` | `bnez.n a3, 0x70030a1d` | skip first high-byte load when a3 is nonzero |
| `0x70030a1a` | `a2 02 4a` | `l8ui a10, a2, 0x4a` | load byte field from a2+0x4a |
| `0x70030a1d` | `c2 02 49` | `l8ui a12, a2, 0x49` | load byte field from a2+0x49 |
| `0x70030a20` | `80 aa 11` | `slli a10, a10, 8` | shift high byte into a 16-bit field |
| `0x70030a23` | `c0 aa 20` | `or a10, a10, a12` | combine bytes from a2+0x49/+0x4a |
| `0x70030a26` | `a0 a2 34` | `extui a10, a10, 2, 4` | extract bits 2..5 from combined 16-bit field |
| `0x70030a3c` | `86 58 c0` | `j 0x70020ba2` | dispatch based on decoded operand field |
| `0x70030a46` | `06 04 02` | `j 0x7003125a` | alternate jump into deeper operand parser |
| `0x70030a54` | `38 d8` | `l32i.n a3, a8, 0x34` | load table/context field before alternate parser |
| `0x70030b09` | `b2 02 0f` | `l8ui a11, a2, 0x0f` | load byte field from a2+0x0f |
| `0x70030b0c` | `c2 02 0e` | `l8ui a12, a2, 0x0e` | load byte field from a2+0x0e |
| `0x70030b17` | `c0 bb 20` | `or a11, a11, a12` | combine bytes from a2+0x0e/+0x0f |
| `0x70030b48` | `c2 02 13` | `l8ui a12, a2, 0x13` | load byte field from a2+0x13 |
| `0x70030b4b` | `d2 02 12` | `l8ui a13, a2, 0x12` | load byte field from a2+0x12 |
| `0x70030b56` | `d0 cc 20` | `or a12, a12, a13` | combine bytes from a2+0x12/+0x13 |
| `0x70030bab` | `b9 0e` | `s32i.n a11, a14, 0x00` | store decoded field to output/result slot +0x00 |
| `0x70030bad` | `b9 1e` | `s32i.n a11, a14, 0x04` | store decoded field to output/result slot +0x04 |
| `0x70030baf` | `b9 2e` | `s32i.n a11, a14, 0x08` | store decoded field to output/result slot +0x08 |
| `0x70030bb1` | `b9 3e` | `s32i.n a11, a14, 0x0c` | store decoded field to output/result slot +0x0c |
| `0x70030bb3` | `b9 4e` | `s32i.n a11, a14, 0x10` | store decoded field to output/result slot +0x10 |
| `0x70030c13` | `26 1a 32` | `beqi a10, 1, 0x70030c49` | special-case decoded field value 1 |
| `0x70030c16` | `26 5a 2f` | `beqi a10, 5, 0x70030c49` | special-case decoded field value 5 |
| `0x70030c19` | `26 6a 2c` | `beqi a10, 6, 0x70030c49` | special-case decoded field value 6 |
| `0x70030c1c` | `0c 9f` | `movi.n a15, 9` | load special-case decoded field value 9 |
| `0x70030c1e` | `f7 1a 27` | `beq a10, a15, 0x70030c49` | special-case decoded field value 9 |
| `0x70030ca0` | `62 12 00` | `l16ui a6, a2, 0x00` | load 16-bit field from a2+0x00 |
| `0x70030cd2` | `82 02 2f` | `l8ui a8, a2, 0x2f` | load byte field from a2+0x2f |
| `0x70030cd5` | `92 02 2e` | `l8ui a9, a2, 0x2e` | load byte field from a2+0x2e |
| `0x70030ce0` | `90 88 20` | `or a8, a8, a9` | combine bytes from a2+0x2e/+0x2f |
| `0x70030cee` | `80 88 11` | `slli a8, a8, 8` | shift combined field for later use |
| `0x70030d71` | `92 02 07` | `l8ui a9, a2, 0x07` | load byte field from a2+0x07 |
| `0x70030d74` | `a2 02 06` | `l8ui a10, a2, 0x06` | load byte field from a2+0x06 |
| `0x70030d7f` | `a0 99 20` | `or a9, a9, a10` | combine bytes from a2+0x06/+0x07 |
| `0x70030d8d` | `80 99 11` | `slli a9, a9, 8` | shift combined field for later use |

### `buffer_record_high_field_validator_candidate` `0x7003ce3c`-`0x7003cfef` verified=True

Byte-verified standard-instruction island in the 0x7003ce3c owner. It performs null/zero checks on an a2-based record using offsets +0x08, +0x0c, +0x10, +0x1c, +0x20, +0x24, +0x28, +0x34, and +0x38, plus byte fields at +0x39/+0x3a/+0x3b. These offsets overlap the high half of a 0x40-byte VPU buffer-shaped record, including plane length/pointer fields, but this island by itself does not prove the INFO12/INFO13 array loop or its iteration bound.

| addr | bytes | mnemonic | effect |
|---:|---|---|---|
| `0x7003ce3c` | `36 c1 03` | `entry sp, 0x1e0` | open a 0x1e0-byte stack frame |
| `0x7003ce3f` | `82 af c0` | `movi a8, -0x40` | prepare 64-byte stack alignment mask |
| `0x7003ce42` | `80 81 10` | `and a8, sp, a8` | align stack pointer down to 64 bytes |
| `0x7003ce45` | `10 18 00` | `movsp sp, a8` | install aligned stack pointer |
| `0x7003ce95` | `16 e3 5f` | `beqz a3, 0x7003d497` | reject null/zero context field |
| `0x7003ce98` | `62 2a 21` | `l32i a6, a10, 0x84` | load context field from a10+0x84 |
| `0x7003ce9b` | `16 86 5f` | `beqz a6, 0x7003d497` | reject null context field |
| `0x7003ce9e` | `16 04 62` | `beqz a4, 0x7003d4c2` | reject null record-related pointer |
| `0x7003ced8` | `d8 22` | `l32i.n a13, a2, 0x08` | load a2 record field +0x08 |
| `0x7003ceda` | `16 ed 58` | `beqz a13, 0x7003d46c` | reject zero field +0x08 |
| `0x7003ceed` | `f8 32` | `l32i.n a15, a2, 0x0c` | load a2 record field +0x0c |
| `0x7003ceef` | `16 9f 57` | `beqz a15, 0x7003d46c` | reject zero field +0x0c |
| `0x7003cf02` | `48 42` | `l32i.n a4, a2, 0x10` | load a2 record field +0x10 |
| `0x7003cf04` | `16 44 56` | `beqz a4, 0x7003d46c` | reject zero field +0x10 |
| `0x7003cf1d` | `d2 02 3a` | `l8ui a13, a2, 0x3a` | load high byte field from a2+0x3a |
| `0x7003cf36` | `f2 02 3b` | `l8ui a15, a2, 0x3b` | load high byte field from a2+0x3b |
| `0x7003cf39` | `c8 72` | `l32i.n a12, a2, 0x1c` | load a2 record field +0x1c |
| `0x7003cf5e` | `c2 22 08` | `l32i a12, a2, 0x20` | load a2 record field +0x20 |
| `0x7003cf61` | `16 bc 60` | `beqz a12, 0x7003d570` | reject zero field +0x20 |
| `0x7003cf64` | `a2 22 09` | `l32i a10, a2, 0x24` | load a2 record field +0x24 |
| `0x7003cf67` | `16 0a 63` | `beqz a10, 0x7003d59b` | reject zero field +0x24 |
| `0x7003cf6a` | `82 22 0a` | `l32i a8, a2, 0x28` | load a2 record field +0x28 |
| `0x7003cf6d` | `16 58 65` | `beqz a8, 0x7003d5c6` | reject zero field +0x28 |
| `0x7003cf70` | `a2 22 0d` | `l32i a10, a2, 0x34` | load a2 record field +0x34 |
| `0x7003cf73` | `16 aa 67` | `beqz a10, 0x7003d5f1` | reject zero field +0x34 |
| `0x7003cf76` | `92 02 38` | `l8ui a9, a2, 0x38` | load byte field from a2+0x38 |
| `0x7003cf79` | `8c e9` | `beqz.n a9, 0x7003cf8b` | branch on zero byte field +0x38 |
| `0x7003cf8b` | `42 02 39` | `l8ui a4, a2, 0x39` | load byte field from a2+0x39 |
| `0x7003cf8e` | `8c e4` | `beqz.n a4, 0x7003cfa0` | branch on zero byte field +0x39 |
| `0x7003cfb5` | `62 12 00` | `l16ui a6, a2, 0x00` | reload low 16-bit field from a2+0x00 |
| `0x7003cfec` | `b2 02 38` | `l8ui a11, a2, 0x38` | reload byte field from a2+0x38 |

## Standard Field-Access Clusters

These clusters are produced by a lightweight standard Xtensa 24-bit load/store decoder (`l8ui/l16ui/l32i/s8i/s16i/s32i`) and grouped by function owner plus base register. They are leads for record-shape analysis, not decompiler output.

| owner | base | hits | unique offsets | VPU-buffer-shaped offsets | samples |
|---:|---:|---:|---:|---|---|
| `0x70015e98` | `a9` | 41 | 40 | 0x0, 0x2, 0x8, 0xc, 0x10, 0x14, 0x18, 0x1c, 0x20, 0x24, 0x28, 0x30, 0x34 | 0x7001603d:l8ui a15,a9+0x8; 0x70016040:l8ui a8,a9+0x7; 0x700160a5:l8ui a3,a9+0xd; 0x700160a8:l8ui a8,a9+0xc; 0x700160ff:l8ui a4,a9+0x11; 0x70016102:l8ui a8,a9+0x10; 0x70016159:l8ui a5,a9+0x15; 0x7001615c:l8ui a8,a9+0x14 |
| `0x7003b468` | `a2` | 40 | 33 | 0x2, 0x14, 0x18, 0x1c, 0x20, 0x24, 0x28, 0x2c, 0x30, 0x34, 0x38, 0x39 | 0x7003b484:l8ui a8,a2+0x3; 0x7003b487:l8ui a9,a2+0x2; 0x7003b63a:l8ui a8,a2+0x12; 0x7003b687:l8ui a10,a2+0x15; 0x7003b6e0:l8ui a5,a2+0xf; 0x7003b6e3:l8ui a8,a2+0xe; 0x7003b712:l8ui a11,a2+0xb; 0x7003b715:l8ui a14,a2+0xa |
| `0x70065b10` | `a6` | 28 | 28 | 0x0, 0x4, 0x8, 0xc, 0x10, 0x14, 0x18, 0x1c, 0x24, 0x2c, 0x30, 0x38 | 0x7006714a:s32i a15,a6+0x18; 0x7006714d:s32i a15,a6+0x14; 0x70067150:s32i a15,a6+0x10; 0x70067153:s32i a15,a6+0xc; 0x70067156:s32i a15,a6+0x8; 0x70067159:s32i a15,a6+0x4; 0x7006715c:s32i a15,a6+0x0; 0x70068141:s16i a3,a6+0x32 |
| `0x7008e220` | `a12` | 12 | 12 | 0xc, 0x10, 0x14, 0x18, 0x1c, 0x20, 0x24, 0x28, 0x2c, 0x30, 0x34, 0x38 | 0x7008e266:s32i a11,a12+0xc; 0x7008e269:s32i a11,a12+0x10; 0x7008e26c:s32i a11,a12+0x14; 0x7008e26f:s32i a11,a12+0x18; 0x7008e272:s32i a11,a12+0x1c; 0x7008e275:s32i a11,a12+0x20; 0x7008e278:s32i a11,a12+0x24; 0x7008e27b:s32i a11,a12+0x28 |
| `0x70039cfc` | `a2` | 37 | 29 | 0x2, 0xc, 0x20, 0x24, 0x2c, 0x30, 0x34, 0x38, 0x39, 0x3a | 0x70039d18:l8ui a8,a2+0x3; 0x70039d1b:l8ui a9,a2+0x2; 0x70039df9:l8ui a10,a2+0x1f; 0x70039dfc:l8ui a11,a2+0x1e; 0x70039e41:l8ui a3,a2+0x7; 0x70039e44:l8ui a8,a2+0x6; 0x70039e83:l8ui a4,a2+0xb; 0x70039e86:l8ui a8,a2+0xa |
| `0x7003ce3c` | `a2` | 12 | 9 | 0x1c, 0x20, 0x24, 0x28, 0x34, 0x38, 0x39, 0x3a, 0x3b | 0x7003cf1d:l8ui a13,a2+0x3a; 0x7003cf36:l8ui a15,a2+0x3b; 0x7003cf5e:l32i a12,a2+0x20; 0x7003cf64:l32i a10,a2+0x24; 0x7003cf6a:l32i a8,a2+0x28; 0x7003cf70:l32i a10,a2+0x34; 0x7003cf76:l8ui a9,a2+0x38; 0x7003cf8b:l8ui a4,a2+0x39 |
| `0x7000c284` | `a2` | 42 | 33 | 0x2, 0x24, 0x28, 0x2c, 0x30, 0x34, 0x38, 0x3b | 0x7000c2a5:l8ui a8,a2+0x3; 0x7000c2a8:l8ui a9,a2+0x2; 0x7000c384:l8ui a13,a2+0x1f; 0x7000c387:l8ui a15,a2+0x1e; 0x7000c3cb:l8ui a10,a2+0x7; 0x7000c3ce:l8ui a15,a2+0x6; 0x7000c40d:l8ui a15,a2+0xb; 0x7000c410:l8ui a8,a2+0xa |
| `0x70011ab8` | `a10` | 17 | 17 | 0x0, 0x4, 0xc, 0x10, 0x18, 0x30, 0x34, 0x38 | 0x70011e83:l8ui a10,a10+0x26; 0x700122f6:s32i a15,a10+0xc; 0x700122f9:s32i a15,a10+0x10; 0x700122fc:s32i a15,a10+0x50; 0x70012322:s16i a2,a10+0x34; 0x70012335:s32i a14,a10+0x30; 0x7001234e:s32i a6,a10+0x4; 0x70012351:s32i a6,a10+0x48 |
| `0x7001b040` | `a10` | 14 | 14 | 0x0, 0x4, 0x8, 0xc, 0x10, 0x30, 0x34, 0x38 | 0x7001b704:s32i a14,a10+0x38; 0x7001b707:s32i a14,a10+0x7c; 0x7001b736:s32i a7,a10+0xc; 0x7001b739:s32i a7,a10+0x10; 0x7001b73c:s32i a7,a10+0x50; 0x7001b74f:s16i a8,a10+0x34; 0x7001b75a:s32i a9,a10+0x44; 0x7001b76d:s32i a11,a10+0x4 |
| `0x700130c0` | `a11` | 13 | 10 | 0x0, 0x2, 0x4, 0x8, 0xc, 0x10, 0x14, 0x18 | 0x70013e09:s32i a3,a11+0x0; 0x70013e0c:s32i a3,a11+0x4; 0x70013e0f:s32i a3,a11+0x8; 0x70013e12:s32i a3,a11+0xc; 0x70013e15:s32i a3,a11+0x10; 0x70013e18:s32i a3,a11+0x14; 0x700140c6:s16i a6,a11+0x1a; 0x700140c9:s16i a5,a11+0x18 |
| `0x700130c0` | `a2` | 50 | 45 | 0x2, 0x10, 0x14, 0x1c, 0x20, 0x2c, 0x30 | 0x700130d3:l8ui a5,a2+0x3; 0x700130d6:l8ui a8,a2+0x2; 0x7001326b:l8ui a5,a2+0x10; 0x70013280:l8ui a13,a2+0x14; 0x70013283:l8ui a5,a2+0x13; 0x700132b2:l8ui a9,a2+0x1c; 0x700132b5:l8ui a13,a2+0x1b; 0x7001330c:l8ui a5,a2+0x1d |
| `0x70011ab8` | `a8` | 26 | 25 | 0x4, 0xc, 0x10, 0x18, 0x20, 0x28, 0x2c | 0x70011c00:l8ui a14,a8+0x1f; 0x70011c03:l8ui a13,a8+0x1e; 0x70011c4b:l8ui a13,a8+0x7; 0x70011c4e:l8ui a9,a8+0x6; 0x70011c90:l8ui a15,a8+0xb; 0x70011c93:l8ui a9,a8+0xa; 0x70011cd5:l8ui a7,a8+0xf; 0x70011cd8:l8ui a9,a8+0xe |
| `0x700130c0` | `a13` | 8 | 8 | 0x0, 0x1, 0x4, 0x8, 0xc, 0x10, 0x2c | 0x70013dea:s32i a3,a13+0x0; 0x70013ded:s32i a3,a13+0x4; 0x70013df0:s32i a3,a13+0x8; 0x70013df3:s32i a3,a13+0xc; 0x70013df6:s32i a3,a13+0x10; 0x700146b4:s16i a14,a13+0x2a; 0x700146c7:s16i a12,a13+0x2c; 0x70014e74:l8ui a15,a13+0x1 |
| `0x70065b10` | `a2` | 167 | 52 | 0x2, 0x4, 0xc, 0x10, 0x3a, 0x3b | 0x70065b29:l8ui a8,a2+0x3; 0x70065b2c:l8ui a9,a2+0x2; 0x70065ef3:l8ui a13,a2+0x7; 0x70065ef6:l8ui a8,a2+0x6; 0x70065f64:l8ui a11,a2+0xb; 0x70065f67:l8ui a8,a2+0xa; 0x70065fb2:l8ui a15,a2+0xf; 0x70065fb5:l8ui a8,a2+0xe |
| `0x70071ef0` | `a10` | 37 | 32 | 0x0, 0x4, 0xc, 0x10, 0x18, 0x34 | 0x700725b7:l8ui a5,a10+0x5c; 0x700725ba:l8ui a8,a10+0x5b; 0x7007265b:l8ui a5,a10+0x64; 0x7007265e:l8ui a8,a10+0x63; 0x70072d54:s32i a3,a10+0x4; 0x70072d57:s32i a3,a10+0x48; 0x70072d6d:s32i a11,a10+0xc; 0x70072d70:s32i a11,a10+0x10 |
| `0x7005f2b8` | `a2` | 34 | 24 | 0x2, 0x20, 0x28, 0x30, 0x38, 0x39 | 0x7005f2d4:l8ui a8,a2+0x3; 0x7005f2d7:l8ui a9,a2+0x2; 0x7005f3b9:l8ui a11,a2+0x1f; 0x7005f3bc:l8ui a13,a2+0x1e; 0x7005f400:l8ui a13,a2+0x7; 0x7005f403:l8ui a15,a2+0x6; 0x7005f444:l8ui a15,a2+0xb; 0x7005f447:l8ui a8,a2+0xa |
| `0x7000dcc4` | `a13` | 17 | 15 | 0x14, 0x28, 0x2c, 0x38, 0x3a, 0x3b | 0x7000e079:l8ui a11,a13+0x3b; 0x7000e07c:l8ui a8,a13+0x3a; 0x7000e0be:l8ui a11,a13+0x3f; 0x7000e0c1:l8ui a8,a13+0x3e; 0x7000e103:l8ui a11,a13+0x43; 0x7000e106:l8ui a15,a13+0x42; 0x7000e148:l8ui a11,a13+0x49; 0x7000e14b:l8ui a13,a13+0x48 |
| `0x7001dffc` | `a5` | 12 | 12 | 0x0, 0x8, 0xc, 0x10, 0x34, 0x38 | 0x7001ea78:s32i a9,a5+0x50; 0x7001ea8b:s32i a10,a5+0x0; 0x7001ea8e:s32i a10,a5+0x8; 0x7001ea9c:s32i a11,a5+0x38; 0x7001eac1:s16i a12,a5+0x48; 0x7001ead7:s32i a12,a5+0xc; 0x7001eada:s32i a12,a5+0x10; 0x7001eadd:s32i a12,a5+0x5c |
| `0x70065b10` | `a14` | 10 | 9 | 0x0, 0x2, 0x10, 0x1c, 0x38, 0x3a | 0x7006613f:l32i a14,a14+0x10; 0x700661ae:l32i a14,a14+0x0; 0x70068166:s16i a3,a14+0x2; 0x70068169:s16i a3,a14+0x1e; 0x7006816c:s16i a3,a14+0x3a; 0x700682db:s16i a8,a14+0x0; 0x700682de:s16i a8,a14+0x1c; 0x700682e1:s16i a8,a14+0x38 |
| `0x70039cfc` | `a15` | 6 | 6 | 0x0, 0x4, 0xc, 0x10, 0x14, 0x18 | 0x7003a371:s32i a14,a15+0x0; 0x7003a374:s32i a14,a15+0x4; 0x7003a379:s32i a14,a15+0xc; 0x7003a37c:s32i a14,a15+0x10; 0x7003a37f:s32i a14,a15+0x14; 0x7003a382:s32i a14,a15+0x18 |

## Loop Targets Near Field Clusters

- `.xt.prop` loop-target properties in `.text`: 1154
- table is limited to loop targets whose owner also has a VPU-buffer-shaped field-access cluster.

| addr | owner | owner delta | field bases | prop |
|---:|---:|---:|---|---|
| `0x70016f0e` | `0x70015e98` | `0x1076` | `a9` | `insn|data|loop_target|no_reorder|no_transform:0x5f6` |
| `0x7001f0e0` | `0x7001dffc` | `0x10e4` | `a5` | `insn|loop_target|no_reorder:0x9` |
| `0x7001f590` | `0x7001dffc` | `0x1594` | `a5` | `insn|loop_target|no_reorder:0x6` |
| `0x7001f8c0` | `0x7001dffc` | `0x18c4` | `a5` | `insn|loop_target|no_reorder:0xa` |
| `0x7003c102` | `0x7003b468` | `0xc9a` | `a2` | `insn|loop_target|no_reorder:0xaa` |
| `0x7003d423` | `0x7003ce3c` | `0x5e6` | `a2` | `insn|loop_target|no_reorder:0x3` |

### Focused Loop Investigations

`0x7003c102` remains the strongest record-shaped loop-target lead. After FLIX correction, the target body exposes stack-spill core ops and FLIX bundles; the ABI count is INFO12/buffer_count, while the record stride is the 0x40 INFO13 descriptor layout. Naming the firmware-local count register still requires FLIX/TIE slot semantics. `0x7003d423` is kept as a downgraded local-control-flow lead.

#### `flix_assisted_INFO13_record_lead` `0x7003b468` target `0x7003c102`

- priority: `independent_flix_branch`
- assessment: Clean 0xaa-byte loop_target property run inside the strongest 0x40-record-shaped field cluster. FLIX-correct boundaries show the owner reads descriptor-shaped a2 fields within 0x02..0x3d; the 0x7003c102 loop body itself contains stack spills plus FLIX bundles, so the firmware-local count update is in FLIX/TIE slot semantics rather than a boundary-visible core LOOP.
- target prop: `insn|loop_target|no_reorder:0xaa`
- FLIX framing motif `FLIX128 framing tail 06 04 02` hits: 0x7003b481, 0x7003b4c5, 0x7003b4d5, 0x7003b4e5, 0x7003b502, 0x7003b512, 0x7003b522, 0x7003b532, 0x7003b54b, 0x7003b55b, 0x7003b56b, 0x7003b57b, 0x7003b599, 0x7003b5a9, 0x7003b5b9, 0x7003b5c9
- FLIX framing motif deltas: 0x44, 0x10, 0x10, 0x1d, 0x10, 0x10, 0x10, 0x19, 0x10, 0x10, 0x10, 0x1e, 0x10, 0x10, 0x10, 0x13
- standard loop opcode hits to target: none
- owner boundary counts: core24=253, dens16=20, flix64=57, flix128=136, truncated=0
- loop body: `0x7003c102`..`0x7003c1ac`; core24=6, dens16=0, flix64=1, flix128=9, truncated=0
- count status: firmware-local count register is not boundary-visible after FLIX correction: there is no byte-aligned LOOP/LOOPNEZ/LOOPGTZ to 0x7003c102, and the loop_target body core ops are stack spills. The ABI count source is INFO12/buffer_count; naming the internal loop register requires a FLIX/TIE slot decoder.
- stride status: record stride is closed at the data-layout level: boundary-visible a2 descriptor loads cover offsets 0x2, 0x3, 0xa, 0xb, 0xe, 0xf, 0x12, 0x15, 0x29, 0x2c, 0x2d, 0x30, 0x31, 0x34, 0x35, 0x38, 0x39, 0x3c, 0x3d within a 0x40-byte record, and the kernel/provider copies INFO13 as struct vpu_buffer[INFO12] with 0x40 stride. No boundary-visible a2 += 0x40 instruction appears in this owner.
- next action: Treat stride as closed by the 0x40 descriptor layout and boundary-visible a2 field coverage; decode the FLIX/TIE slot only if firmware-local count-register naming is required.

| visible a2 field access | op | offset |
|---:|---|---:|
| `0x7003b484` | `l8ui a8,a2+0x3` | `0x3` |
| `0x7003b487` | `l8ui a9,a2+0x2` | `0x2` |
| `0x7003b63a` | `l8ui a8,a2+0x12` | `0x12` |
| `0x7003b687` | `l8ui a10,a2+0x15` | `0x15` |
| `0x7003b6e0` | `l8ui a5,a2+0xf` | `0xf` |
| `0x7003b6e3` | `l8ui a8,a2+0xe` | `0xe` |
| `0x7003b712` | `l8ui a11,a2+0xb` | `0xb` |
| `0x7003b715` | `l8ui a14,a2+0xa` | `0xa` |
| `0x7003b7a6` | `l8ui a9,a2+0x29` | `0x29` |
| `0x7003b7d9` | `l8ui a11,a2+0x2d` | `0x2d` |
| `0x7003b7dc` | `l8ui a12,a2+0x2c` | `0x2c` |
| `0x7003b828` | `l8ui a12,a2+0x31` | `0x31` |
| `0x7003b82b` | `l8ui a13,a2+0x30` | `0x30` |
| `0x7003b85a` | `l8ui a13,a2+0x39` | `0x39` |
| `0x7003b85d` | `l8ui a14,a2+0x38` | `0x38` |
| `0x7003b896` | `l8ui a14,a2+0x3d` | `0x3d` |
| `0x7003b899` | `l8ui a15,a2+0x3c` | `0x3c` |
| `0x7003ba30` | `l8ui a11,a2+0x35` | `0x35` |
| `0x7003ba33` | `l8ui a13,a2+0x34` | `0x34` |

| loop-body boundary core mem | op | offset |
|---:|---|---:|
| `0x7003c102` | `s32i a6,a1+0x28c` | `0x28c` |
| `0x7003c105` | `s32i a5,a1+0x288` | `0x288` |
| `0x7003c108` | `s32i a4,a1+0x284` | `0x284` |
| `0x7003c10b` | `s32i a3,a1+0x2bc` | `0x2bc` |
| `0x7003c11e` | `s32i a12,a1+0x254` | `0x254` |
| `0x7003c121` | `s32i a11,a1+0x250` | `0x250` |

| prop | size | flags | first bytes |
|---:|---:|---|---|
| `0x7003b468` | `0x50` | `insn|no_reorder` | `36 e1 05 82 af c0 80 81 10 10 18 00 9e 62 20 70` |
| `0x7003b4b8` | `0x10` | `insn|data|no_reorder|no_transform` | `de b4 55 32 58 c4 41 06 31 a8 85 02 c8 06 04 02` |
| `0x7003b4c8` | `0x2de` | `insn|no_reorder` | `9e 98 1c 70 5a 66 12 00 30 d0 05 b7 f0 06 04 02` |
| `0x7003b7a6` | `0x33` | `insn|branch_target|no_reorder` | `92 02 29 9e 69 20 70 5a 60 12 00 30 d0 05 76 f4` |
| `0x7003b7d9` | `0x4f` | `insn|branch_target|no_reorder` | `b2 02 2d c2 02 2c 4f 8b 76 b7 8a 08 82 00 c0 bb` |
| `0x7003b828` | `0xb7` | `insn|branch_target|no_reorder` | `c2 02 31 d2 02 30 4f 8c b8 f7 8a 08 82 00 d0 cc` |
| `0x7003b8df` | `0xc` | `unreachable` | `00 00 00 00 00 00 00 00 00 00 00 00` |
| `0x7003b8ed` | `0x63` | `insn|no_reorder` | `f2 02 14 ef 82 a4 12 00 08 82 00 ef 63 c2 22 91` |
| `0x7003b950` | `0x10` | `insn|data|no_reorder|no_transform` | `ae c1 2e 33 01 e6 04 1a 17 34 08 02 40 08 04 02` |
| `0x7003b960` | `0x10` | `insn|no_reorder` | `8e d2 bc b2 00 d4 04 0a 37 33 08 02 40 08 04 02` |
| `0x7003b970` | `0x20` | `insn|data|no_reorder|no_transform` | `9e b2 14 41 59 c0 4d 02 31 a8 85 37 d8 06 04 02` |
| `0x7003b990` | `0x10` | `insn|no_reorder` | `5e c2 19 a2 5c e0 9d 00 31 a8 85 37 f8 06 04 02` |
| `0x7003b9a0` | `0x10` | `insn|data|no_reorder|no_transform` | `9e 82 1a 46 5d c0 d1 02 31 a8 85 37 f8 06 04 02` |
| `0x7003b9b0` | `0x20` | `insn|no_reorder` | `8e d2 e2 82 01 d4 80 0a 37 33 10 02 40 08 04 02` |
| `0x7003b9d0` | `0x10` | `insn|data|no_reorder|no_transform` | `4e 82 21 a2 5c c0 9d 00 31 a8 85 37 f8 06 04 02` |
| `0x7003b9e0` | `0x50` | `insn|no_reorder` | `9e f1 74 47 5d c4 d1 02 31 a8 85 38 f8 06 04 02` |
| `0x7003ba30` | `0x19` | `insn|branch_target|no_reorder` | `b2 02 35 d2 02 34 4f 8b 36 37 8e 08 82 00 cf 72` |
| `0x7003ba49` | `0x10` | `insn|data|no_reorder|no_transform` | `7e c1 23 b7 5c 64 10 00 30 d0 05 37 f8 06 04 02` |
| `0x7003ba59` | `0x70` | `insn|no_reorder` | `4f 37 7c 27 94 0a 82 00 6f ec 39 b2 94 ea 82 00` |
| `0x7003bac9` | `0x10` | `insn|data|no_reorder|no_transform` | `7e c1 23 b7 5c 64 10 00 30 d0 05 37 f8 06 04 02` |
| `0x7003bad9` | `0x56` | `insn|no_reorder` | `30 66 82 d2 0d 00 c0 c6 82 80 bb 11 cf fc b9 dd` |
| `0x7003bb2f` | `0x10` | `insn|data|no_reorder|no_transform` | `7e c1 23 b7 5c 64 10 00 30 d0 05 37 f8 06 04 02` |
| `0x7003bb3f` | `0x9` | `insn|no_reorder` | `d2 0d 00 c0 c3 82 80 bb 11` |
| `0x7003bb48` | `0x10` | `insn|data|no_reorder|no_transform` | `9e c1 57 c3 5c e4 dd 02 31 a8 85 38 f8 06 04 02` |

#### `downgraded_error_tail_loop_target` `0x7003ce3c` target `0x7003d423`

- priority: `downgraded`
- assessment: Loop-target property is real but surrounding props contain short branch targets, unreachable gaps, and insn\|data mixed runs. Treat as switch/error-tail lead, not a descriptor-array walk.
- target prop: `insn|loop_target|no_reorder:0x3`
- FLIX framing motif `FLIX128 framing tail 06 04 02` hits: 0x7003d3b9, 0x7003d3df, 0x7003d3fd, 0x7003d40d, 0x7003d41d, 0x7003d44d
- FLIX framing motif deltas: 0x26, 0x1e, 0x10, 0x10, 0x30
- standard loop opcode hits to target: 0x7003d3ea
- owner boundary counts: core24=25, dens16=7, flix64=3, flix128=6, truncated=0
- loop body: `0x7003d423`..`0x7003d426`; core24=1, dens16=0, flix64=0, flix128=0, truncated=0
- count status: byte-aligned hardware LOOP is present for this downgraded local control-flow target, but it is not the INFO13 descriptor walk.
- stride status: downgraded local-control-flow lead; do not use it as the INFO13 descriptor stride proof.
- next action: Keep as secondary local-control-flow evidence only; do not use this downgraded target to drive INFO12/INFO13 closure.

| visible a2 field access | op | offset |
|---:|---|---:|

| loop-body boundary core mem | op | offset |
|---:|---|---:|

| prop | size | flags | first bytes |
|---:|---:|---|---|
| `0x7003d3a9` | `0x1c` | `insn|branch_target|no_reorder` | `d2 21 68 8e db 96 02 5a a6 19 01 31 a8 05 4e d8` |
| `0x7003d3c5` | `0x8` | `insn|data|no_reorder|no_transform` | `2f 66 89 9d 33 04 5a 00` |
| `0x7003d3cd` | `0x15` | `insn|branch_target|no_reorder` | `e2 21 6c 3d f0 be e5 70 35 5c 60 10 00 30 d0 85` |
| `0x7003d3e2` | `0x41` | `insn|branch_target|no_reorder` | `4f 90 01 1b 08 c8 59 00 76 80 35 16 99 ec 9e 09` |
| `0x7003d423` | `0x3` | `insn|loop_target|no_reorder` | `c6 f0 ff` |
| `0x7003d426` | `0x0` | `unreachable` | `` |
| `0x7003d427` | `0x5` | `insn|branch_target|no_reorder` | `a5 d0 62 fc aa` |
| `0x7003d42c` | `0x4` | `insn|branch_target|no_reorder` | `0c 02 1d f0` |
| `0x7003d430` | `0x6` | `unreachable` | `00 00 00 00 00 00` |
| `0x7003d436` | `0x5` | `insn|branch_target|no_reorder` | `22 a1 00 1d f0` |
| `0x7003d43b` | `0x0` | `unreachable` | `` |
| `0x7003d440` | `0x10` | `insn|data|branch_target|no_reorder|no_transform` | `0e d0 c8 42 28 f9 51 07 31 a8 c5 3e c8 06 04 02` |
| `0x7003d450` | `0x9` | `insn|no_reorder` | `25 10 62 16 5a b9 86 03 00` |
| `0x7003d459` | `0x6` | `unreachable` | `00 00 00 00 00 00` |
| `0x7003d460` | `0x8` | `insn|branch_target|no_reorder` | `0c 0f f2 61 67 c6 65 ff` |
| `0x7003d468` | `0x0` | `unreachable` | `` |
| `0x7003d468` | `0x4` | `insn|branch_target|no_reorder` | `2d 0a 1d f0` |
| `0x7003d46c` | `0x0` | `unreachable` | `` |
| `0x7003d46c` | `0x2b` | `insn|no_reorder` | `a4 00 70 94 00 70 d4 00 70 6f d8 19 0e 09 50 83` |

### FLIX-Correct Boundary Sweeps

These ranges use the `.xt.prop`-validated hybrid length rule: `0x0..0x7 -> 3`, `0x8..0xd -> 2`, `0xe -> 16`, `0xf -> 8`. The `06 04 02` motif is treated as FLIX128 framing, not as an independent selector.

#### `info13_record_lead_corrected_boundaries` `0x7003c0ee`..`0x7003c14c`

Length-correct sweep across the 0x7003c102 loop-target neighborhood. Core LSAI-shaped accesses are interleaved with FLIX128/64 bundles instead of being swallowed by 2-byte base-Xtensa sizing.

- start prop: `insn|branch_target|no_reorder:0x12` at `0x7003c0ee`
- counts: core24=6, dens16=2, flix64=1, flix128=4, truncated=0
- bad framing: 0
- next action: Use these boundaries before inspecting descriptor-field core ops. The 06 04 02 bytes are FLIX128 framing tails, not an independent selector.

| addr | len | kind | raw | fmt | framing | decoded core mem |
|---:|---:|---|---|---|---|---|
| `0x7003c0ee` | 2 | `dens16` | `4bdd` | `` | `` | `` |
| `0x7003c0f0` | 16 | `flix128` | `7e9dc11a59e0f50331a88538b8060402` | `0xe` | `ok` | `` |
| `0x7003c100` | 2 | `dens16` | `3df0` | `` | `` | `` |
| `0x7003c102` | 3 | `core24` | `6261a3` | `` | `` | `s32i a6,a1+0x28c` |
| `0x7003c105` | 3 | `core24` | `5261a2` | `` | `` | `s32i a5,a1+0x288` |
| `0x7003c108` | 3 | `core24` | `4261a1` | `` | `` | `s32i a4,a1+0x284` |
| `0x7003c10b` | 3 | `core24` | `3261af` | `` | `` | `s32i a3,a1+0x2bc` |
| `0x7003c10e` | 16 | `flix128` | `9e3b02705a66120030d0852bf4060402` | `0xe` | `ok` | `` |
| `0x7003c11e` | 3 | `core24` | `c26195` | `` | `` | `s32i a12,a1+0x254` |
| `0x7003c121` | 3 | `core24` | `b26194` | `` | `` | `s32i a11,a1+0x250` |
| `0x7003c124` | 8 | `flix64` | `0f503e021b268200` | `0x0f` | `ok` | `` |
| `0x7003c12c` | 16 | `flix128` | `3e815c1f5a64100030d08538b8060402` | `0xe` | `ok` | `` |
| `0x7003c13c` | 16 | `flix128` | `2e5c9c2f5840648330608502b8060402` | `0xe` | `ok` | `` |

#### `downgraded_error_tail_corrected_boundaries` `0x7003d3e2`..`0x7003d460`

Length-correct sweep around the downgraded 0x7003d423 loop-target property. The target itself is a core24 item, but the surrounding block remains switch/error-tail shaped.

- start prop: `insn|branch_target|no_reorder:0x41` at `0x7003d3e2`
- counts: core24=15, dens16=5, flix64=1, flix128=4, truncated=0
- bad framing: 0
- next action: Keep as secondary local-control-flow evidence; corrected boundaries do not promote it back into the INFO13 mainline.

| addr | len | kind | raw | fmt | framing | decoded core mem |
|---:|---:|---|---|---|---|---|
| `0x7003d3e2` | 8 | `flix64` | `4f90011b08c85900` | `0x0f` | `ok` | `` |
| `0x7003d3ea` | 3 | `core24` | `768035` | `` | `` | `` |
| `0x7003d3ed` | 3 | `core24` | `1699ec` | `` | `` | `` |
| `0x7003d3f0` | 16 | `flix128` | `9e0922705864120030d085f6f7060402` | `0xe` | `ok` | `` |
| `0x7003d400` | 16 | `flix128` | `9e092c705866120030d085f8f7060402` | `0xe` | `ok` | `` |
| `0x7003d410` | 16 | `flix128` | `9e0910705a60120030d085fbf7060402` | `0xe` | `ok` | `` |
| `0x7003d420` | 3 | `core24` | `2699a9` | `` | `` | `` |
| `0x7003d423` | 3 | `core24` | `c6f0ff` | `` | `` | `` |
| `0x7003d426` | 3 | `core24` | `00a5d0` | `` | `` | `` |
| `0x7003d429` | 3 | `core24` | `62fcaa` | `` | `` | `` |
| `0x7003d42c` | 2 | `dens16` | `0c02` | `` | `` | `` |
| `0x7003d42e` | 2 | `dens16` | `1df0` | `` | `` | `` |
| `0x7003d430` | 3 | `core24` | `000000` | `` | `` | `` |
| `0x7003d433` | 3 | `core24` | `000000` | `` | `` | `` |
| `0x7003d436` | 3 | `core24` | `22a100` | `` | `` | `` |
| `0x7003d439` | 2 | `dens16` | `1df0` | `` | `` | `` |
| `0x7003d43b` | 3 | `core24` | `000000` | `` | `` | `` |
| `0x7003d43e` | 3 | `core24` | `00000e` | `` | `` | `` |
| `0x7003d441` | 3 | `core24` | `d0c842` | `` | `` | `` |
| `0x7003d444` | 2 | `dens16` | `28f9` | `` | `` | `` |
| `0x7003d446` | 3 | `core24` | `510731` | `` | `` | `` |
| `0x7003d449` | 2 | `dens16` | `a8c5` | `` | `` | `` |
| `0x7003d44b` | 16 | `flix128` | `3ec8060402251062165ab98603000000` | `0xe` | `ok` | `` |
| `0x7003d45b` | 3 | `core24` | `000000` | `` | `` | `` |
| `0x7003d45e` | 3 | `core24` | `00000c` | `` | `` | `` |

#### `dmaif_owner_entry_prefix_corrected_boundaries` `0x70044b74`..`0x70044c50`

Length-correct sweep at the top iDMA schedule/wait owner. The owner starts with a standard entry core op followed by dense FLIX128/64 bundles and sparse core/density items.

- start prop: `insn|no_reorder:0x44` at `0x70044b74`
- counts: core24=6, dens16=4, flix64=3, flix128=11, truncated=0
- bad framing: 0
- next action: Use this as the Q1 owner boundary map before any FLIX/iDMA instrumentation; string ownership alone still does not prove completion-store timing.

| addr | len | kind | raw | fmt | framing | decoded core mem |
|---:|---:|---|---|---|---|---|
| `0x70044b74` | 3 | `core24` | `364107` | `` | `` | `` |
| `0x70044b77` | 16 | `flix128` | `9e6220705864120030d005e7f4060402` | `0xe` | `ok` | `` |
| `0x70044b87` | 3 | `core24` | `820203` | `` | `` | `l8ui a8,a2+0x3` |
| `0x70044b8a` | 3 | `core24` | `920202` | `` | `` | `l8ui a9,a2+0x2` |
| `0x70044b8d` | 8 | `flix64` | `4f88b0128a088200` | `0x0f` | `ok` | `` |
| `0x70044b95` | 3 | `core24` | `908820` | `` | `` | `` |
| `0x70044b98` | 16 | `flix128` | `8ed07e020736000a3733000240080402` | `0xe` | `ok` | `` |
| `0x70044ba8` | 8 | `flix64` | `af6080c5401c5800` | `0x0f` | `ok` | `` |
| `0x70044bb0` | 8 | `flix64` | `0f60218207e08200` | `0x0f` | `ok` | `` |
| `0x70044bb8` | 16 | `flix128` | `7ed4545258c4410631a88502c8060402` | `0xe` | `ok` | `` |
| `0x70044bc8` | 16 | `flix128` | `9e983c705862120030d00529f1060402` | `0xe` | `ok` | `` |
| `0x70044bd8` | 16 | `flix128` | `9e6320705860120030d0851bf5060402` | `0xe` | `ok` | `` |
| `0x70044be8` | 2 | `dens16` | `9803` | `` | `` | `` |
| `0x70044bea` | 16 | `flix128` | `9e592a705a66120030d0851af5060402` | `0xe` | `ok` | `` |
| `0x70044bfa` | 2 | `dens16` | `a813` | `` | `` | `` |
| `0x70044bfc` | 16 | `flix128` | `9e6a20705866120030d0051af5060402` | `0xe` | `ok` | `` |
| `0x70044c0c` | 3 | `core24` | `e22321` | `` | `` | `l32i a14,a3+0x84` |
| `0x70044c0f` | 16 | `flix128` | `9e6e00705a64120030d08519f5060402` | `0xe` | `ok` | `` |
| `0x70044c1f` | 2 | `dens16` | `b823` | `` | `` | `` |
| `0x70044c21` | 16 | `flix128` | `9e6b00705864120030d00519f5060402` | `0xe` | `ok` | `` |
| `0x70044c31` | 3 | `core24` | `722322` | `` | `` | `l32i a7,a3+0x88` |
| `0x70044c34` | 16 | `flix128` | `9e6720705862120030d08518f5060402` | `0xe` | `ok` | `` |
| `0x70044c44` | 2 | `dens16` | `c833` | `` | `` | `` |
| `0x70044c46` | 16 | `flix128` | `9e6c20705a60120030d00518f5060402` | `0xe` | `ok` | `` |

#### `dmaif_dram_validation_tail_corrected_boundaries` `0x700452c4`..`0x70045330`

Length-correct sweep around the DRAM data-buffer validation string owner tail inside the same iDMA cluster.

- start prop: `insn|no_reorder:0x3a` at `0x700452c4`
- counts: core24=12, dens16=3, flix64=1, flix128=4, truncated=0
- bad framing: 0
- next action: Correlate these core24 checks with the DRAM validation strings before treating the FLIX bundles as DMA movement.

| addr | len | kind | raw | fmt | framing | decoded core mem |
|---:|---:|---|---|---|---|---|
| `0x700452c4` | 3 | `core24` | `8261d2` | `` | `` | `s32i a8,a1+0x348` |
| `0x700452c7` | 3 | `core24` | `f62f3b` | `` | `` | `` |
| `0x700452ca` | 3 | `core24` | `8221b7` | `` | `` | `l32i a8,a1+0x2dc` |
| `0x700452cd` | 3 | `core24` | `e62835` | `` | `` | `` |
| `0x700452d0` | 3 | `core24` | `9221b8` | `` | `` | `l32i a9,a1+0x2e0` |
| `0x700452d3` | 3 | `core24` | `e6292f` | `` | `` | `` |
| `0x700452d6` | 3 | `core24` | `a221b5` | `` | `` | `l32i a10,a1+0x2d4` |
| `0x700452d9` | 3 | `core24` | `e62a29` | `` | `` | `` |
| `0x700452dc` | 2 | `dens16` | `0c0c` | `` | `` | `` |
| `0x700452de` | 16 | `flix128` | `1ec1c6025866100430d08538c8060402` | `0xe` | `ok` | `` |
| `0x700452ee` | 16 | `flix128` | `1eb184015866100430d08538c8060402` | `0xe` | `ok` | `` |
| `0x700452fe` | 2 | `dens16` | `0c0d` | `` | `` | `` |
| `0x70045300` | 3 | `core24` | `d261d2` | `` | `` | `s32i a13,a1+0x348` |
| `0x70045303` | 3 | `core24` | `460800` | `` | `` | `` |
| `0x70045306` | 2 | `dens16` | `0c0f` | `` | `` | `` |
| `0x70045308` | 16 | `flix128` | `1ef186035866100430d08538c8060402` | `0xe` | `ok` | `` |
| `0x70045318` | 16 | `flix128` | `1ee184015866100430d08538c8060402` | `0xe` | `ok` | `` |
| `0x70045328` | 3 | `core24` | `920207` | `` | `` | `l8ui a9,a2+0x7` |
| `0x7004532b` | 3 | `core24` | `a20206` | `` | `` | `l8ui a10,a2+0x6` |
| `0x7004532e` | 8 | `flix64` | `4f89f2528a088200` | `0x0f` | `ok` | `` |

## L32R Literal References

These references are decoded only inside `.xt.prop` instruction-covered `.text` ranges. `literal` is the PC-relative L32R literal address; `value` is the 32-bit word stored there when it is readable.
Because a property range can contain extension bundles, these are section-filtered leads; high-value owners still need local byte or IDA validation before treating them as control-flow facts.

- valid L32R refs with in-ELF literal address: 161731
- interesting L32R refs emitted: 256

| addr | reg | literal | value | owner | literal string | value string |
|---:|---:|---:|---:|---:|---|---|
| `0x700301e3` | `a3` | `0x70001884` `.rodata` | `0x61636f6c` `` | `0x700301d8` | `0x70001880+0x4` `xvAllocateBuffer` |  |
| `0x7000947a` | `a2` | `0x70005f60` `.rodata` | `0x203e2063` `` | `0x70007e24` | `0x70005f5c+0x4` `sDesc > eDesc` |  |
| `0x70017b22` | `a3` | `0x70005928` `.rodata` | `0x20726566` `` | `0x70015e98` | `0x70005920+0x8` `Data buffer does not fit in DRAM` |  |
| `0x7001972a` | `a8` | `0x70005f98` `.rodata` | `0x65206f4e` `` | `0x700184b4` | `0x70005f98` `No error` |  |
| `0x7001be9b` | `a8` | `0x70005f60` `.rodata` | `0x203e2063` `` | `0x7001b040` | `0x70005f5c+0x4` `sDesc > eDesc` |  |
| `0x7001beab` | `a0` | `0x70005f70` `.rodata` | `0x3d3e2063` `` | `0x7001b040` | `0x70005f6c+0x4` `eDesc >= TM_DMA_DESC_IDX_MAX` |  |
| `0x7001fd67` | `a2` | `0x70005f94` `.rodata` | `0x4c4c` `.xt.prop` | `0x7001dffc` | `0x70005f8c+0x8` `_DMA_STALL` |  |
| `0x700247ad` | `a3` | `0x70005e50` `.rodata` | `0x414d4469` `` | `0x70024710` | `0x70005e50` `iDMA schedule error` |  |
| `0x700247bd` | `a3` | `0x70005e60` `.rodata` | `0x726f72` `` | `0x70024710` | `0x70005e50+0x10` `iDMA schedule error` |  |
| `0x700247cd` | `a3` | `0x70005e70` `.rodata` | `0x726f72` `` | `0x70024710` | `0x70005e64+0xc` `iDMA wait error` |  |
| `0x700248de` | `a3` | `0x70005f80` `.rodata` | `0x5844495f` `` | `0x700248a0` | `0x70005f6c+0x14` `eDesc >= TM_DMA_DESC_IDX_MAX` |  |
| `0x700248ee` | `a3` | `0x70005f90` `.rodata` | `0x4154535f` `` | `0x700248a0` | `0x70005f8c+0x4` `_DMA_STALL` |  |
| `0x70025111` | `a9` | `0x70005934` `.rodata` | `0x74696620` `` | `0x70024b10` | `0x70005920+0x14` `Data buffer does not fit in DRAM` |  |
| `0x700260ef` | `a6` | `0x70005908` `.rodata` | `0x73656f64` `` | `0x70024b10` | `0x700058fc+0xc` `Data buffer does not start in DRAM` |  |
| `0x7002de1f` | `a2` | `0x70005f90` `.rodata` | `0x4154535f` `` | `0x7002d88c` | `0x70005f8c+0x4` `_DMA_STALL` |  |
| `0x700335e2` | `a11` | `0x70005918` `.rodata` | `0x5244206e` `` | `0x700304f8` | `0x700058fc+0x1c` `Data buffer does not start in DRAM` |  |
| `0x70035e60` | `a8` | `0x70005e60` `.rodata` | `0x726f72` `` | `0x70035c64` | `0x70005e50+0x10` `iDMA schedule error` |  |
| `0x70036861` | `a2` | `0x70005e68` `.rodata` | `0x69617720` `` | `0x70036110` | `0x70005e64+0x4` `iDMA wait error` |  |
| `0x7003693e` | `a2` | `0x70005f44` `.rodata` | `0x6372732f` `` | `0x70036110` | `0x70005f30+0x14` `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c` |  |
| `0x70038f14` | `a6` | `0x700057a0` `.rodata` | `0x41432054` `` | `0x70038378` | `0x70005798+0x8` `INTERRUPT CALLBACK : processing iDMA interrupt\n` |  |
| `0x70039b66` | `a6` | `0x70005f6c` `.rodata` | `0x73654465` `` | `0x70039b40` | `0x70005f6c` `eDesc >= TM_DMA_DESC_IDX_MAX` |  |
| `0x70043834` | `a0` | `0x70005934` `.rodata` | `0x74696620` `` | `0x700414d0` | `0x70005920+0x14` `Data buffer does not fit in DRAM` |  |
| `0x7004429c` | `a0` | `0x70005e5c` `.rodata` | `0x72652065` `` | `0x700414d0` | `0x70005e50+0xc` `iDMA schedule error` |  |
| `0x7004445b` | `a0` | `0x70005f7c` `.rodata` | `0x43534544` `` | `0x700414d0` | `0x70005f6c+0x10` `eDesc >= TM_DMA_DESC_IDX_MAX` |  |
| `0x7004489d` | `a3` | `0x70005f40` `.rodata` | `0x6e6f6d6d` `` | `0x70044850` | `0x70005f30+0x10` `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c` |  |
| `0x700448dd` | `a3` | `0x70005f80` `.rodata` | `0x5844495f` `` | `0x70044850` | `0x70005f6c+0x14` `eDesc >= TM_DMA_DESC_IDX_MAX` |  |
| `0x70044e3a` | `a15` | `0x70005e54` `.rodata` | `0x68637320` `` | `0x70044b74` | `0x70005e50+0x4` `iDMA schedule error` |  |
| `0x70044e53` | `a15` | `0x70005e6c` `.rodata` | `0x72652074` `` | `0x70044b74` | `0x70005e64+0x8` `iDMA wait error` |  |
| `0x70044f1f` | `a15` | `0x70005f38` `.rodata` | `0x6c2f6e6e` `` | `0x70044b74` | `0x70005f30+0x8` `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c` |  |
| `0x70044f45` | `a15` | `0x70005f60` `.rodata` | `0x203e2063` `` | `0x70044b74` | `0x70005f5c+0x4` `sDesc > eDesc` |  |
| `0x700452ef` | `a11` | `0x70005900` `.rodata` | `0x66756220` `` | `0x70044b74` | `0x700058fc+0x4` `Data buffer does not start in DRAM` |  |
| `0x70045319` | `a14` | `0x7000592c` `.rodata` | `0x73656f64` `` | `0x70044b74` | `0x70005920+0xc` `Data buffer does not fit in DRAM` |  |
| `0x700065d1` | `a0` | `0x70002648` `.rodata` | `0x6d207475` `` | `0x700065a0` | `0x7000263c+0xc` `Invalid output multiplier` |  |
| `0x70006a9b` | `a8` | `0x7000269c` `.rodata` | `0x6e6f6d6d` `` | `0x70006a78` | `0x7000268c+0x10` `../vp6-ann/libcommon/include/cnnrt_xi_p6.h` |  |
| `0x70006c06` | `a4` | `0x700030c0` `.rodata` | `0x6e6f6974` `` | `0x70006a78` | `0x700030b0+0x10` `../vp6-ann/operations/depthnspace.c` |  |
| `0x70006e5e` | `a10` | `0x700035c0` `.rodata` | `0x65206562` `` | `0x70006a78` | `0x700035a4+0x1c` `Inconsistent order must not be equal` |  |
| `0x7000702d` | `a1` | `0x70003340` `.rodata` | `0x61746f74` `` | `0x70006ed8` | `0x70003338+0x8` `Invalid total number of rois` |  |
| `0x7000703f` | `a14` | `0x70000144` `.rodata` | `0x77746c65` `` | `0x70006ed8` | `0x70000140+0x4` `flk_eltwiseCompare` |  |
| `0x700071e8` | `a11` | `0x70002568` `.rodata` | `0x2064696c` `` | `0x70006ed8` | `0x70002564+0x4` `Invalid element size` |  |
| `0x7000728e` | `a12` | `0x70003660` `.rodata` | `0x74757074` `` | `0x70006ed8` | `0x70003650+0x10` `Invalid input/output buffer size` |  |
| `0x700074f4` | `a5` | `0x70001ff4` `.rodata` | `0x4e485449` `` | `0x70007440` | `0x70001ff0+0x4` `BOXWITHNMS` |  |
| `0x700074f9` | `a5` | `0x70003570` `.rodata` | `0x70736e61` `` | `0x70007440` | `0x70003558+0x18` `../vp6-ann/operations/transpose4D.c` |  |
| `0x700076d9` | `a0` | `0x7000489c` `.rodata` | `0x5f564358` `` | `0x70007440` | `0x70004868+0x34` `Unsupported srcElemSize = %d or dstElemSize = %d forXCV_THRESH_TOZERO\n` |  |
| `0x700076f4` | `a0` | `0x700048b4` `.rodata` | `0x726f7070` `` | `0x70007440` | `0x700048b0+0x4` `Unsupported thresholdType = %d\n` |  |
| `0x7000780b` | `a0` | `0x700049cc` `.rodata` | `0x61726570` `` | `0x70007440` | `0x700049c0+0xc` `../vp6-ann/operations/cv/xcvFrameLib/imgproc/src/xcvErode.c` |  |
| `0x70007a91` | `a6` | `0x70000294` `.rodata` | `0x635f7265` `` | `0x70007a90` | `0x70000280+0x14` `flk_depthwise_conv_per_chan` |  |
| `0x70007d3f` | `a7` | `0x70005450` `.rodata` | `0x6e6f6974` `` | `0x70007c88` | `0x70005440+0x10` `../vp6-ann/operations/cv/xcvFrameLib/imgproc/src/xcvGaussian.c` |  |
| `0x70008560` | `a1` | `0x7000491c` `.rodata` | `0x61757165` `` | `0x70007e24` | `0x70004910+0xc` `: %d is not equal to ` |  |
| `0x70008668` | `a4` | `0x70001908` `.rodata` | `0x6172466c` `` | `0x70007e24` | `0x70001900+0x8` `xvFreeAllFrames` |  |
| `0x70008703` | `a12` | `0x700019a4` `.rodata` | `0x496b6365` `` | `0x70007e24` | `0x700019a0+0x4` `xvCheckInputTileFree` |  |
| `0x70008770` | `a4` | `0x70001a10` `.rodata` | `0x7865746e` `` | `0x70007e24` | `0x70001a00+0x10` `xvGetArgParamsContext` |  |
| `0x7000880b` | `a12` | `0x70001aac` `.rodata` | `0x4b` `.xt.prop` | `0x70007e24` | `0x70001a80+0x2c` `xvProcessTileWiseFastSequential_AccOutput_MTK` |  |
| `0x70008971` | `a1` | `0x70004234` `.rodata` | `0x73697774` `` | `0x70007e24` | `0x7000421c+0x18` `../vp6-ann/operations/eltwiseOp.c` |  |
| `0x700089cf` | `a10` | `0x700019d8` `.rodata` | `0x656c69` `` | `0x70007e24` | `0x700019d0+0x8` `xvSetupTile` |  |
| `0x700089ea` | `a5` | `0x700019f0` `.rodata` | `0x72467678` `` | `0x70007e24` | `0x700019f0` `xvFreeBuffers` |  |
| `0x70008a0d` | `a9` | `0x70001a14` `.rodata` | `0x74` `.xt.prop` | `0x70007e24` | `0x70001a00+0x14` `xvGetArgParamsContext` |  |
| `0x70008a3d` | `a4` | `0x70003f00` `.rodata` | `0x6f2f6e6e` `` | `0x70007e24` | `0x70003ef8+0x8` `../vp6-ann/operations/pool.c` |  |
| `0x70008c55` | `a2` | `0x70003638` `.rodata` | `0x75706e69` `` | `0x70007e24` | `0x70003630+0x8` `Invalid input/output dimensions` |  |
| `0x700090e1` | `a9` | `0x70001920` `.rodata` | `0x72467678` `` | `0x70007e24` | `0x70001920` `xvFreeTile` |  |
| `0x700091d8` | `a12` | `0x70005d08` `.rodata` | `0x2079726f` `` | `0x70007e24` | `0x70005ce0+0x28` `Cannot find free space for requested memory allocation` |  |
| `0x700091f8` | `a3` | `0x70001af8` `.rodata` | `0x646574` `` | `0x70007e24` | `0x70001ae0+0x18` `arena_check_space_allocated` |  |
| `0x70009228` | `a2` | `0x700053f0` `.rodata` | `0x61662074` `` | `0x70007e24` | `0x700053d0+0x20` `xiConnectedComponents_FIK_FoldLut fail in %s\n` |  |
| `0x700092ec` | `a15` | `0x70005df4` `.rodata` | `0x6d756772` `` | `0x70007e24` | `0x70005de8+0xc` `free_space argument cannot be NULL` |  |
| `0x700093db` | `a2` | `0x70005bdc` `.rodata` | `0x746e656d` `` | `0x70007e24` | `0x70005bd0+0xc` `Bank 0 alignment must be power of two` |  |
| `0x70009548` | `a6` | `0x700005a0` `.rodata` | `0x5f6b6c66` `` | `0x70007e24` | `0x700005a0` `flk_softmax` |  |
| `0x700096ed` | `a8` | `0x70001fbc` `.rodata` | `0x4432564e` `` | `0x70007e24` | `0x70001fb4+0x8` `DIPCDWCONV2D` |  |
| `0x70009ec5` | `a14` | `0x70003548` `.rodata` | `0x6e6f6974` `` | `0x70009db0` | `0x70003534+0x14` `Inconsistent convolution parameters` |  |
| `0x70009ece` | `a1` | `0x7000108c` `.rodata` | `0x6666416c` `` | `0x70009db0` | `0x70001080+0xc` `ProcessKernelAffine_Nearest` |  |
| `0x7000a17a` | `a1` | `0x70005b7c` `.rodata` | `0x65636170` `` | `0x70009eec` | `0x70005b70+0xc` `Not enough space to allocate` |  |
| `0x7000a345` | `a1` | `0x70001948` `.rodata` | `0x656c6954` `` | `0x70009eec` | `0x70001940+0x8` `xvCreateTileManager` |  |
| `0x7000a464` | `a0` | `0x70003708` `.rodata` | `0x74756d72` `` | `0x70009eec` | `0x70003704+0x4` `xiPermuteA3D_U8_ref: input failed\n` |  |
| `0x7000a4af` | `a4` | `0x70003754` `.rodata` | `0x44334165` `` | `0x70009eec` | `0x7000374c+0x8` `xiPermuteA3D_I8_ref: output failed\n` |  |
| `0x7000a4fa` | `a8` | `0x700037a0` `.rodata` | `0x6f2f7475` `` | `0x70009eec` | `0x70003790+0x10` `Inconsistent input/output dimensions` |  |
| `0x7000a54f` | `a12` | `0x700037f4` `.rodata` | `0x6f622f73` `` | `0x70009eec` | `0x700037e0+0x14` `../vp6-ann/operations/boxNMSlimit.c` |  |
| `0x7000a632` | `a1` | `0x70004f2c` `.rodata` | `0x76632f73` `` | `0x70009eec` | `0x70004f18+0x14` `../vp6-ann/operations/cv/xcvFrameLib/imgproc/src/mtk_UVResize.c` |  |
| `0x7000a804` | `a9` | `0x700030d0` `.rodata` | `0x632e65` `` | `0x70009eec` | `0x700030b0+0x20` `../vp6-ann/operations/depthnspace.c` |  |
| `0x7000a927` | `a5` | `0x7000078c` `.rodata` | `0x68` `.xt.prop` | `0x70009eec` | `0x70000780+0xc` `flk_hardSwish` |  |
| `0x7000ab65` | `a11` | `0x700033c8` `.rodata` | `0x2064696c` `` | `0x70009eec` | `0x700033c4+0x4` `Invalid input bbox delta size` |  |
| `0x7000ad67` | `a1` | `0x700024e8` `.rodata` | `0x6e617571` `` | `0x70009eec` | `0x700024e0+0x8` `Invalid quant scale` |  |
| `0x7000ad96` | `a1` | `0x70002150` `.rodata` | `0x72726520` `` | `0x70009eec` | `0x70002138+0x18` `%s: op[%d] %s encounters error: %s(%d)\n` |  |

### Critical String L32R References

| pattern | string | hits | samples |
|---|---:|---:|---|
| `add idma request fail in %s` | `0x70004adc` | 0 |  |
| `ERROR CALLBACK: iDMA in Error` | `0x70005778` | 0 |  |
| `INTERRUPT CALLBACK : processing iDMA interrupt` | `0x70005798` | 1 | 0x70038f14:a6 lit=0x700057a0+0x8 owner=0x70038378 |
| `iDMA error` | `0x70005e44` | 0 |  |
| `iDMA schedule error` | `0x70005e50` | 5 | 0x700247ad:a3 lit=0x70005e50+0x0 owner=0x70024710; 0x700247bd:a3 lit=0x70005e60+0x10 owner=0x70024710; 0x70035e60:a8 lit=0x70005e60+0x10 owner=0x70035c64; 0x7004429c:a0 lit=0x70005e5c+0xc owner=0x700414d0 |
| `iDMA wait error` | `0x70005e64` | 3 | 0x700247cd:a3 lit=0x70005e70+0xc owner=0x70024710; 0x70036861:a2 lit=0x70005e68+0x4 owner=0x70036110; 0x70044e53:a15 lit=0x70005e6c+0x8 owner=0x70044b74 |
| `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c` | `0x70005f30` | 3 | 0x7003693e:a2 lit=0x70005f44+0x14 owner=0x70036110; 0x7004489d:a3 lit=0x70005f40+0x10 owner=0x70044850; 0x70044f1f:a15 lit=0x70005f38+0x8 owner=0x70044b74 |
| `sDesc > eDesc` | `0x70005f5c` | 3 | 0x7000947a:a2 lit=0x70005f60+0x4 owner=0x70007e24; 0x7001be9b:a8 lit=0x70005f60+0x4 owner=0x7001b040; 0x70044f45:a15 lit=0x70005f60+0x4 owner=0x70044b74 |
| `eDesc >= TM_DMA_DESC_IDX_MAX` | `0x70005f6c` | 5 | 0x7001beab:a0 lit=0x70005f70+0x4 owner=0x7001b040; 0x700248de:a3 lit=0x70005f80+0x14 owner=0x700248a0; 0x70039b66:a6 lit=0x70005f6c+0x0 owner=0x70039b40; 0x7004445b:a0 lit=0x70005f7c+0x10 owner=0x700414d0 |
| `_DMA_STALL` | `0x70005f8c` | 3 | 0x7001fd67:a2 lit=0x70005f94+0x8 owner=0x7001dffc; 0x700248ee:a3 lit=0x70005f90+0x4 owner=0x700248a0; 0x7002de1f:a2 lit=0x70005f90+0x4 owner=0x7002d88c |
| `No error` | `0x70005f98` | 1 | 0x7001972a:a8 lit=0x70005f98+0x0 owner=0x700184b4 |
| `Data buffer does not start in DRAM` | `0x700058fc` | 3 | 0x700260ef:a6 lit=0x70005908+0xc owner=0x70024b10; 0x700335e2:a11 lit=0x70005918+0x1c owner=0x700304f8; 0x700452ef:a11 lit=0x70005900+0x4 owner=0x70044b74 |
| `Data buffer does not fit in DRAM` | `0x70005920` | 4 | 0x70017b22:a3 lit=0x70005928+0x8 owner=0x70015e98; 0x70025111:a9 lit=0x70005934+0x14 owner=0x70024b10; 0x70043834:a0 lit=0x70005934+0x14 owner=0x700414d0; 0x70045319:a14 lit=0x7000592c+0xc owner=0x70044b74 |

### Critical L32R Owner Clusters

Owners are ranked by how many distinct DMA/iDMA-related strings are referenced through L32R. This is a string-cluster lead, not full control flow.

| owner | patterns | hits | assessment | samples |
|---:|---:|---:|---|---|
| `0x70044b74` | 4 | 4 | top DMA/iDMA owner candidate | iDMA schedule error@0x70044e3a; iDMA wait error@0x70044e53; ../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c@0x70044f1f; sDesc > eDesc@0x70044f45 |
| `0x70024710` | 2 | 3 | secondary DMA/iDMA owner candidate | iDMA schedule error@0x700247ad; iDMA schedule error@0x700247bd; iDMA wait error@0x700247cd |
| `0x7001b040` | 2 | 2 | secondary DMA/iDMA owner candidate | sDesc > eDesc@0x7001be9b; eDesc >= TM_DMA_DESC_IDX_MAX@0x7001beab |
| `0x700248a0` | 2 | 2 | secondary DMA/iDMA owner candidate | eDesc >= TM_DMA_DESC_IDX_MAX@0x700248de; _DMA_STALL@0x700248ee |
| `0x70036110` | 2 | 2 | secondary DMA/iDMA owner candidate | iDMA wait error@0x70036861; ../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c@0x7003693e |
| `0x700414d0` | 2 | 2 | secondary DMA/iDMA owner candidate | iDMA schedule error@0x7004429c; eDesc >= TM_DMA_DESC_IDX_MAX@0x7004445b |
| `0x70044850` | 2 | 2 | secondary DMA/iDMA owner candidate | ../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c@0x7004489d; eDesc >= TM_DMA_DESC_IDX_MAX@0x700448dd |
| `0x70007e24` | 1 | 1 | single-string lead | sDesc > eDesc@0x7000947a |
| `0x700184b4` | 1 | 1 | single-string lead | No error@0x7001972a |
| `0x7001dffc` | 1 | 1 | single-string lead | _DMA_STALL@0x7001fd67 |
| `0x7002d88c` | 1 | 1 | single-string lead | _DMA_STALL@0x7002de1f |
| `0x70035c64` | 1 | 1 | single-string lead | iDMA schedule error@0x70035e60 |
| `0x70038378` | 1 | 1 | single-string lead | INTERRUPT CALLBACK : processing iDMA interrupt@0x70038f14 |
| `0x70039b40` | 1 | 1 | single-string lead | eDesc >= TM_DMA_DESC_IDX_MAX@0x70039b66 |

### DMA/iDMA Owner Investigations

These records promote the string-cluster leads into explicit Q1 owner investigations. They identify schedule/wait ownership, not completion write timing.

#### `top_dmaif_schedule_wait_owner` `0x70044b74`

- range: `0x70044b74`..`0x70045380`
- string-cluster rank: 1; patterns=4; hits=4
- string-cluster patterns: ../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c, iDMA schedule error, iDMA wait error, sDesc > eDesc
- assessment: Top iDMA schedule/wait wrapper candidate selected by L32R string-cluster ranking. The same .xt.prop owner contains refs to schedule/wait errors, dmaif.c, descriptor range validation, and data-buffer DRAM validation strings.
- Q1 status: owner_closed_for_static_schedule_wait_anchor; FLIX-correct boundaries separate core/density items from bundles in the owner. Completion-write burst timing remains a runtime/slot-semantics question.
- FLIX framing motif `FLIX128 framing tail 06 04 02` hits: 83 (first 0x70044b84, 0x70044bc5, 0x70044bd5, 0x70044be5, 0x70044bf7, 0x70044c09, 0x70044c1c, 0x70044c2e, 0x70044c41, 0x70044c53, 0x70044c66, 0x70044c78)
- standard a2 access signals: 34 hits, offsets 0x2, 0x3, 0x6, 0x7, 0xa, 0xb, 0x16, 0x17, 0x1a, 0x1b, 0x1e, 0x1f, 0x22, 0x23, 0x26, 0x27, 0x2a, 0x2b, 0x2e, 0x2f, 0x3a, 0x3b, 0x3e, 0x3f, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49
- next action: Use this owner as the Q1 firmware anchor for DMA schedule/wait. Close timing with runtime instrumentation or deeper FLIX/TIE slot semantics; do not infer inter-store timing from string ownership alone.

| evidence | ref | delta | literal | prop |
|---|---:|---:|---:|---|
| iDMA schedule error | `0x70044e3a` | `0x2c6` | `a15 -> 0x70005e54` | `insn|no_reorder:0x361` |
| iDMA wait error | `0x70044e53` | `0x2de` | `a15 -> 0x70005e6c` | `insn|no_reorder:0x361` |
| ../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c | `0x70044f1f` | `0x3aa` | `a15 -> 0x70005f38` | `insn|no_reorder:0x361` |
| sDesc > eDesc | `0x70044f45` | `0x3d0` | `a15 -> 0x70005f60` | `insn|no_reorder:0x1d3` |
| Data buffer does not start in DRAM | `0x700452ef` | `0x77a` | `a11 -> 0x70005900` | `insn|no_reorder:0x3a` |
| Data buffer does not fit in DRAM | `0x70045319` | `0x7a4` | `a14 -> 0x7000592c` | `insn|branch_target|no_reorder:0x22` |

| prop | size | flags | first bytes |
|---:|---:|---|---|
| `0x70044b74` | `0x44` | `insn|no_reorder` | `36 41 07 9e 62 20 70 58 64 12 00 30 d0 05 e7 f4` |
| `0x70044bb8` | `0x10` | `insn|data|no_reorder|no_transform` | `7e d4 54 52 58 c4 41 06 31 a8 85 02 c8 06 04 02` |
| `0x70044bc8` | `0x361` | `insn|no_reorder` | `9e 98 3c 70 58 62 12 00 30 d0 05 29 f1 06 04 02` |
| `0x70044f29` | `0x10` | `insn|data|no_reorder|no_transform` | `3e 89 12 62 5e 64 10 04 30 d0 85 50 d8 06 04 02` |
| `0x70044f39` | `0x1d3` | `insn|no_reorder` | `9e 98 16 70 5a 64 12 00 30 d0 05 03 f1 06 04 02` |
| `0x7004510c` | `0x10` | `insn|data|no_reorder|no_transform` | `3e 61 88 90 5f 84 f9 8d 31 60 05 37 d8 06 04 02` |
| `0x7004511c` | `0x13` | `insn|no_reorder` | `42 61 de 6e 32 87 89 5a a4 fd 81 31 60 85 50 f8` |
| `0x7004512f` | `0x10` | `insn|data|no_reorder|no_transform` | `2e 42 04 51 5e c4 79 86 31 60 85 50 d8 06 04 02` |
| `0x7004513f` | `0x6` | `insn|no_reorder` | `60 88 c2 82 61 b7` |
| `0x70045145` | `0x10` | `insn|data|no_reorder|no_transform` | `ce 6e 90 f9 5e a4 4e 0f 31 a8 85 50 b8 06 04 02` |
| `0x70045155` | `0x11` | `insn|no_reorder` | `8f 6c 0d cb fc 05 5a 00 40 99 c2 80 84 82 82 61` |
| `0x70045166` | `0x10` | `insn|data|no_reorder|no_transform` | `ce 91 b0 19 13 f5 c8 32 cf a9 a4 38 b8 06 04 02` |
| `0x70045176` | `0x2b` | `insn|no_reorder` | `2e 4c 49 fa 5f a4 9e 0c 31 a8 85 50 b8 06 04 02` |
| `0x700451a1` | `0x20` | `insn|data|no_reorder|no_transform` | `3e 32 c4 40 5e 64 10 0c 30 d0 85 50 d8 06 04 02` |
| `0x700451c1` | `0x43` | `insn|no_reorder` | `22 61 ac 3e e3 06 e1 5a 64 12 0c 30 d0 85 50 d8` |
| `0x70045204` | `0x87` | `insn|branch_target|no_reorder` | `0c 0a a9 41 a9 51 3e a1 4c 72 5c 60 10 0c 30 d0` |
| `0x7004528b` | `0x6` | `insn|branch_target|no_reorder` | `b0 9a 82 86 8b 05` |
| `0x70045291` | `0xc` | `unreachable` | `00 00 00 00 00 00 00 00 00 00 00 00` |
| `0x7004529d` | `0x1f` | `insn|branch_target|no_reorder` | `f2 02 2f 82 02 2e 4f 8f 7e d6 8a 08 82 00 80 ff` |
| `0x700452bc` | `0x8` | `insn|data|no_reorder|no_transform` | `8f 81 80 fd 78 c8 59 00` |
| `0x700452c4` | `0x3a` | `insn|no_reorder` | `82 61 d2 f6 2f 3b 82 21 b7 e6 28 35 92 21 b8 e6` |
| `0x700452fe` | `0x8` | `insn|branch_target|no_reorder` | `0c 0d d2 61 d2 46 08 00` |
| `0x70045306` | `0x0` | `unreachable` | `` |
| `0x70045306` | `0x22` | `insn|branch_target|no_reorder` | `0c 0f 1e f1 86 03 58 66 10 04 30 d0 85 38 c8 06` |

### `.dram_op.data` L32R References

| addr | reg | literal | value | owner |
|---:|---:|---:|---:|---:|

## Rodata String References

- `.text` 32-bit references into `.rodata` strings: 180
- interesting refs: 45 (table below is capped at 80)

| ref | value | owner | string |
|---:|---:|---:|---|
| `0x70007a24` | `0x70005a05` | `0x700078a0` | `0x700059f8+0xd` `Error processes function` |
| `0x70009d44` | `0x70005a05` | `0x70009bc0` | `0x700059f8+0xd` `Error processes function` |
| `0x7003e310` | `0x70002a9e` | `0x7003da10` | `0x70002a9c+0x2` `Invalid flagFP16` |
| `0x700527d4` | `0x70005a05` | `0x70052650` | `0x700059f8+0xd` `Error processes function` |
| `0x70055b14` | `0x70005a05` | `0x70055990` | `0x700059f8+0xd` `Error processes function` |
| `0x70079bf0` | `0x70005c9e` | `0x70076d50` | `0x70005c8c+0x12` `Invalid allocation alignment` |
| `0x7008fec4` | `0x70005a05` | `0x7008fe80` | `0x700059f8+0xd` `Error processes function` |
| `0x700978a8` | `0x70001a9e` | `0x70096d90` | `0x70001a80+0x1e` `xvProcessTileWiseFastSequential_AccOutput_MTK` |
| `0x7009c0dc` | `0x70002f9e` | `0x7009b5f0` | `0x70002f8c+0x12` `Inconsistent output height` |
| `0x7009c33c` | `0x70002f9e` | `0x7009b5f0` | `0x70002f8c+0x12` `Inconsistent output height` |
| `0x700a7ce8` | `0x70002446` | `0x700a7be0` | `0x70002444+0x2` `Inconsistent output size` |
| `0x700b75d0` | `0x7000388e` | `0x700b3d00` | `0x70003880+0xe` `Inconsistent batch index size` |
| `0x700bb9c0` | `0x7000388e` | `0x700b8100` | `0x70003880+0xe` `Inconsistent batch index size` |
| `0x700c1438` | `0x70005995` | `0x700c13b0` | `0x70005974+0x21` `../vp6-ann/operations/cv/tileManager/src/ProcessTileWise.c` |
| `0x700d41a4` | `0x70005a02` | `0x700d4130` | `0x700059f8+0xa` `Error processes function` |
| `0x70103bb0` | `0x7000309e` | `0x70102b20` | `0x70003094+0xa` `Invalid output score shift` |
| `0x70104f70` | `0x7000309e` | `0x70103ed0` | `0x70003094+0xa` `Invalid output score shift` |
| `0x70106ee0` | `0x70005a05` | `0x70106c70` | `0x700059f8+0xd` `Error processes function` |
| `0x7011bec4` | `0x70005995` | `0x7011be20` | `0x70005974+0x21` `../vp6-ann/operations/cv/tileManager/src/ProcessTileWise.c` |
| `0x7011c2c8` | `0x70005a05` | `0x7011be20` | `0x700059f8+0xd` `Error processes function` |
| `0x7011e460` | `0x70005a8e` | `0x7011ce40` | `0x70005a78+0x16` `%s: refcount leak on buffer group\n` |
| `0x7012acb4` | `0x70002a9e` | `0x7012ab00` | `0x70002a9c+0x2` `Invalid flagFP16` |
| `0x70132460` | `0x70005c9e` | `0x70131c80` | `0x70005c8c+0x12` `Invalid allocation alignment` |
| `0x70132810` | `0x70005c9e` | `0x70131c80` | `0x70005c8c+0x12` `Invalid allocation alignment` |
| `0x70132830` | `0x70005c9e` | `0x70131c80` | `0x70005c8c+0x12` `Invalid allocation alignment` |
| `0x70132c50` | `0x70005c9e` | `0x70131c80` | `0x70005c8c+0x12` `Invalid allocation alignment` |
| `0x7013b640` | `0x70003b9e` | `0x70139860` | `0x70003b8c+0x12` `Invalid output data type` |
| `0x7013b678` | `0x70001a9e` | `0x70139860` | `0x70001a80+0x1e` `xvProcessTileWiseFastSequential_AccOutput_MTK` |
| `0x7013b6c8` | `0x70002b9e` | `0x70139860` | `0x70002b98+0x6` `../vp6-ann/operations/eltwiseCompare.c` |
| `0x7013e430` | `0x7000429e` | `0x7013d8b0` | `0x70004294+0xa` `Invalid multiplier shift` |
| `0x7015e884` | `0x70005a09` | `0x7015d680` | `0x700059f8+0x11` `Error processes function` |
| `0x70161cf8` | `0x70005a09` | `0x7015ffd0` | `0x700059f8+0x11` `Error processes function` |
| `0x701cf938` | `0x70005a02` | `0x701cf890` | `0x700059f8+0xa` `Error processes function` |
| `0x701e0668` | `0x7000299e` | `0x701dfc30` | `0x70002998+0x6` `Invalid post nms top N` |
| `0x701e68c0` | `0x700059e1` | `0x701e51e0` | `0x700059c4+0x1d` `Error processes function with XI_ERR_TYPE return` |
| `0x701ea358` | `0x7000619e` | `0x701e8780` | `0x70006178+0x26` `Invalid normalization divisor or shift value` |
| `0x7021e340` | `0x7000289e` | `0x7021de20` | `0x70002898+0x6` `Inconsistent tile size` |
| `0x7021e380` | `0x7000279e` | `0x7021de20` | `0x70002794+0xa` `Inconsistent coefficients array size` |
| `0x7022010c` | `0x7000299e` | `0x7021fb10` | `0x70002998+0x6` `Invalid post nms top N` |
| `0x7022014c` | `0x70002a9e` | `0x7021fb10` | `0x70002a9c+0x2` `Invalid flagFP16` |
| `0x70237550` | `0x7000328f` | `0x70236700` | `0x7000328c+0x3` `../vp6-ann/operations/topkv2.c` |
| `0x7023ed5c` | `0x70005a05` | `0x7023eb10` | `0x700059f8+0xd` `Error processes function` |
| `0x7024a040` | `0x70005a05` | `0x70249e10` | `0x700059f8+0xd` `Error processes function` |
| `0x702595a0` | `0x7000429e` | `0x70258580` | `0x70004294+0xa` `Invalid multiplier shift` |
| `0x7026271c` | `0x70005995` | `0x70262690` | `0x70005974+0x21` `../vp6-ann/operations/cv/tileManager/src/ProcessTileWise.c` |

## Critical String Direct References

- all-byte refs include unaligned matches and are false-positive prone; absence is useful, presence needs disassembly validation.

| pattern | string | aligned refs | all-byte refs | sample refs |
|---|---:|---:|---:|---|
| `add idma request fail in %s` | `0x70004adc` | 0 | 0 |  |
| `ERROR CALLBACK: iDMA in Error` | `0x70005778` | 0 | 0 |  |
| `INTERRUPT CALLBACK : processing iDMA interrupt` | `0x70005798` | 0 | 0 |  |
| `iDMA error` | `0x70005e44` | 0 | 0 |  |
| `iDMA schedule error` | `0x70005e50` | 0 | 0 |  |
| `iDMA wait error` | `0x70005e64` | 0 | 0 |  |
| `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c` | `0x70005f30` | 0 | 0 |  |
| `sDesc > eDesc` | `0x70005f5c` | 0 | 0 |  |
| `eDesc >= TM_DMA_DESC_IDX_MAX` | `0x70005f6c` | 0 | 0 |  |
| `_DMA_STALL` | `0x70005f8c` | 0 | 0 |  |
| `No error` | `0x70005f98` | 0 | 0 |  |
| `Data buffer does not start in DRAM` | `0x700058fc` | 0 | 0 |  |
| `Data buffer does not fit in DRAM` | `0x70005920` | 0 | 6 | 0x700c14e2->0x700c13b0+0x14, 0x700cc237->0x700cc080+0x14, 0x700cdbd7->0x700cda20+0x14, 0x7016bd27->0x7016bc40+0x1a |

## Pointer Runs

### `0x70000180` count 12

| slot | value | target | owner | prop | string |
|---:|---:|---|---:|---|---|
| `0x70000180` | `0x70017d41` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000184` | `0x70017dba` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000188` | `0x70017daf` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x7000018c` | `0x70017da4` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000190` | `0x70017d99` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000194` | `0x70017d8e` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000198` | `0x70017d83` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x7000019c` | `0x70017d78` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001a0` | `0x70017d6d` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001a4` | `0x70017d62` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001a8` | `0x70017d57` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001ac` | `0x70017d4c` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |

### `0x700001c0` count 12

| slot | value | target | owner | prop | string |
|---:|---:|---|---:|---|---|
| `0x700001c0` | `0x70017dc5` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001c4` | `0x70017e3e` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001c8` | `0x70017e33` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001cc` | `0x70017e28` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001d0` | `0x70017e1d` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001d4` | `0x70017e12` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001d8` | `0x70017e07` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001dc` | `0x70017dfc` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001e0` | `0x70017df1` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001e4` | `0x70017de6` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001e8` | `0x70017ddb` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x700001ec` | `0x70017dd0` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |

### `0x70000200` count 12

| slot | value | target | owner | prop | string |
|---:|---:|---|---:|---|---|
| `0x70000200` | `0x70017cc8` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000204` | `0x700169a4` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000208` | `0x70017d36` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x7000020c` | `0x70017d2b` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000210` | `0x70017d20` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000214` | `0x70017d15` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000218` | `0x70017d0a` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x7000021c` | `0x70017cff` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000220` | `0x70017cf4` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000224` | `0x70017ce9` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x70000228` | `0x70017cde` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |
| `0x7000022c` | `0x70017cd3` | `.text` | `0x70015e98` | `insn|data|no_reorder|no_transform` |  |

### `0x70000b80` count 31

| slot | value | target | owner | prop | string |
|---:|---:|---|---:|---|---|
| `0x70000b80` | `0x70081ee7` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000b84` | `0x70082aac` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000b88` | `0x70082a44` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000b8c` | `0x700829dc` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000b90` | `0x70082974` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000b94` | `0x7008290c` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000b98` | `0x700828a4` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000b9c` | `0x7008283c` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000ba0` | `0x700827d4` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000ba4` | `0x7008276c` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000ba8` | `0x70082704` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bac` | `0x7008269c` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bb0` | `0x70082634` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bb4` | `0x700825cc` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bb8` | `0x70082557` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bbc` | `0x700824e2` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bc0` | `0x7008246b` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bc4` | `0x700823f4` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bc8` | `0x7008237f` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bcc` | `0x70082308` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bd0` | `0x70082293` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bd4` | `0x70081ec5` | `.text` | `0x70081d50` | `insn|branch_target|no_reorder` |  |
| `0x70000bd8` | `0x70081ec5` | `.text` | `0x70081d50` | `insn|branch_target|no_reorder` |  |
| `0x70000bdc` | `0x7008221e` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000be0` | `0x700821a8` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000be4` | `0x70082133` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000be8` | `0x700820be` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bec` | `0x70082048` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bf0` | `0x70081ec5` | `.text` | `0x70081d50` | `insn|branch_target|no_reorder` |  |
| `0x70000bf4` | `0x70081fd3` | `.text` | `0x70081d50` | `insn|no_reorder` |  |
| `0x70000bf8` | `0x70081f5e` | `.text` | `0x70081d50` | `insn|no_reorder` |  |

## ANN Op Name Table

| index | entry | name | string |
|---:|---:|---|---:|
| 0 | `0x7ff3b000` | `CONV2D` | `0x70001fac` |
| 1 | `0x7ff3b004` | `DWCONV2D` | `0x70001fb8` |
| 2 | `0x7ff3b008` | `POOL2D` | `0x70001e94` |
| 3 | `0x7ff3b00c` | `LOGISTIC` | `0x70001e9c` |
| 4 | `0x7ff3b010` | `RELU` | `0x70001ea8` |
| 5 | `0x7ff3b014` | `SOFTMAX` | `0x70001eb0` |
| 6 | `0x7ff3b018` | `RESHAPE` | `0x70001eb8` |
| 7 | `0x7ff3b01c` | `CONCAT` | `0x70001ec0` |
| 8 | `0x7ff3b020` | `ELEWISE` | `0x70001ec8` |
| 9 | `0x7ff3b024` | `L2NORM` | `0x70001ed0` |
| 10 | `0x7ff3b028` | `RESZBILINR` | `0x70001ed8` |
| 11 | `0x7ff3b02c` | `TRANSPOSE` | `0x70001ee4` |
| 12 | `0x7ff3b030` | `DECONV2D` | `0x70001ef0` |
| 13 | `0x7ff3b034` | `PAD` | `0x70001efc` |
| 14 | `0x7ff3b038` | `STRIDESLICE` | `0x70001f00` |
| 15 | `0x7ff3b03c` | `MEAN` | `0x70001f0c` |
| 16 | `0x7ff3b040` | `BATCH2SPACE` | `0x70001f14` |
| 17 | `0x7ff3b044` | `SPACE2BATCH` | `0x70001f20` |
| 18 | `0x7ff3b048` | `DEPTHNSPACE` | `0x70001f2c` |
| 19 | `0x7ff3b04c` | `REQUANT` | `0x70001f38` |
| 20 | `0x7ff3b050` | `DIDWCONV2D` | `0x70001f40` |
| 21 | `0x7ff3b054` | `DICONV2D` | `0x70001f4c` |
| 22 | `0x7ff3b058` | `PRELU` | `0x70001f58` |
| 23 | `0x7ff3b05c` | `TANH` | `0x70001f60` |
| 24 | `0x7ff3b060` | `ARGMINMAX` | `0x70001f68` |
| 25 | `0x7ff3b064` | `GROUPCONV2D` | `0x70001f74` |
| 26 | `0x7ff3b068` | `SHUFFLE` | `0x70001f80` |
| 27 | `0x7ff3b06c` | `REDUCE` | `0x70001f88` |
| 28 | `0x7ff3b070` | `PCCONV2D` | `0x70001f90` |
| 29 | `0x7ff3b074` | `PCDWCONV2D` | `0x70001f9c` |
| 30 | `0x7ff3b078` | `DIPCCONV2D` | `0x70001fa8` |
| 31 | `0x7ff3b07c` | `DIPCDWCONV2D` | `0x70001fb4` |
| 32 | `0x7ff3b080` | `CAST` | `0x70001fc4` |
| 33 | `0x7ff3b084` | `BOXTRANSFORM` | `0x70001fcc` |
| 34 | `0x7ff3b088` | `SPLIT` | `0x70001fdc` |
| 35 | `0x7ff3b08c` | `QUANT16LSTM` | `0x70001fe4` |
| 36 | `0x7ff3b090` | `BOXWITHNMS` | `0x70001ff0` |
| 37 | `0x7ff3b094` | `GENPROPOSALS` | `0x70001ffc` |
| 38 | `0x7ff3b098` | `HEATMAXKEY` | `0x7000200c` |
| 39 | `0x7ff3b09c` | `TOPKV2` | `0x70002018` |
| 40 | `0x7ff3b0a0` | `ROIALIGN` | `0x70002020` |
| 41 | `0x7ff3b0a4` | `RESZNEAR` | `0x7000202c` |
| 42 | `0x7ff3b0a8` | `TILE` | `0x70002038` |
| 43 | `0x7ff3b0ac` | `GATHER` | `0x70002040` |
| 44 | `0x7ff3b0b0` | `SELECT` | `0x70002048` |
| 45 | `0x7ff3b0b4` | `QUANTIZE` | `0x70002050` |
| 46 | `0x7ff3b0b8` | `DEQUANTIZE` | `0x7000205c` |
| 47 | `0x7ff3b0bc` | `INSTANCENORM` | `0x70002068` |
| 48 | `0x7ff3b0c0` | `LAYERNORM` | `0x70002078` |
| 49 | `0x7ff3b0c4` | `DIV` | `0x70002084` |
| 50 | `0x7ff3b0c8` | `SQRT` | `0x70002088` |
| 51 | `0x7ff3b0cc` | `RSQRT` | `0x70002090` |
| 52 | `0x7ff3b0d0` | `QUANTLSTM` | `0x70002098` |
| 53 | `0x7ff3b0d4` | `HARDSWISH` | `0x700020a4` |
| 54 | `0x7ff3b0d8` | `FILL` | `0x700020b0` |
| 55 | `0x7ff3b0dc` | `TYPECONVERT` | `0x700020b8` |
| 56 | `0x7ff3b0e0` | `ELTCOMPARE` | `0x700020c4` |
| 57 | `0x7ff3b0e4` | `ROIALIGNV2` | `0x700020d0` |
| 58 | `0x7ff3b0e8` | `RESZBILINRV2` | `0x700020dc` |
| 59 | `0x7ff3b0ec` | `RESZNEARV2` | `0x700020ec` |
| 60 | `0x7ff3b0f0` | `ARGMINMAX4D` | `0x700020f8` |
| 61 | `0x7ff3b0f4` | `XFL_SQRT_QUANT` | `0x70002104` |
| 62 | `0x7ff3b0f8` | `XFL_RSQRT_QUANT` | `0x70002114` |

## Interesting Strings

- `0x70000010` `execute_op`
- `0x70001ab0` `process_command`
- `0x70001c20` `dma_barrier`
- `0x70001e60` `../algo/apu_lib_apunn/src/d2d_flo.c`
- `0x70001e84` `kernelProcess`
- `0x70002160` `VPU library compatibility check fail\n`
- `0x700021ec` `xrp_open_device failed\n`
- `0x70002204` `xrp_register_namespace for XRP_ANN_NSID failed\n`
- `0x7000236c` `Invalid double buffer`
- `0x70003650` `Invalid input/output buffer size`
- `0x70003988` `Inconsistend double buffer`
- `0x70004450` `Cv major-version: %u is mismatched with algo major-version: %u\n`
- `0x70004ab4` `dma_2d_loc2sys_no_schedule fail in %s\n`
- `0x70004adc` `add idma request fail in %s\n`
- `0x70005778` `ERROR CALLBACK: iDMA in Error\n`
- `0x70005798` `INTERRUPT CALLBACK : processing iDMA interrupt\n`
- `0x700058fc` `Data buffer does not start in DRAM`
- `0x70005920` `Data buffer does not fit in DRAM`
- `0x70005944` `Invalid buffer`
- `0x70005a14` `%s: dsp_buffer[%d].flags = %d\n`
- `0x70005a34` `%s: refcount leak on buffer %d\n`
- `0x70005a54` `%s: map_count leak on buffer %d\n`
- `0x70005a78` `%s: refcount leak on buffer group\n`
- `0x70005e44` `iDMA error`
- `0x70005e50` `iDMA schedule error`
- `0x70005e64` `iDMA wait error`
- `0x70005f30` `../vp6-ann/libcommon/src/idma_mvpu6/dmaif.c`
- `0x70005f5c` `sDesc > eDesc`
- `0x70005f6c` `eDesc >= TM_DMA_DESC_IDX_MAX`
- `0x70005f8c` `_DMA_STALL`
- `0x70006288` `The requested functionality is absent in current version of XI Library`
