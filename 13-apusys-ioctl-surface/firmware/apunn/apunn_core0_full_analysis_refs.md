# APUNN Xtensa ELF summary

## ELF

- entry: `0x70006794`
- machine: `0x5e`
- flags: `0x300`

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
| `buffer_record_high_field_validator_candidate` | `0x7003ce3c` | `.text` | `0x7003ce3c` | `0x0` | `insn|no_reorder:0x194` |
| `large_auto_function` | `0x7003b424` | `.text` | `0x7003b424` | `0x0` | `insn|no_reorder:0x6` |
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
| `0x7003b468` | `a2` | 63 | 42 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x14, 0x18, 0x1c, 0x20, 0x24, 0x28, 0x2c, 0x30, 0x34, 0x38, 0x39 | 0x7003b484:l8ui a8,a2+0x3; 0x7003b487:l8ui a9,a2+0x2; 0x7003b4dd:l16ui a6,a2+0x0; 0x7003b573:l16ui a6,a2+0x0; 0x7003b5a1:l16ui a6,a2+0x0; 0x7003b5b1:l16ui a6,a2+0x0; 0x7003b5c1:l16ui a6,a2+0x0; 0x7003b5d4:l16ui a6,a2+0x0 |
| `0x70039cfc` | `a2` | 57 | 35 | 0x0, 0x2, 0x4, 0xc, 0x10, 0x18, 0x20, 0x24, 0x2c, 0x30, 0x34, 0x38, 0x39, 0x3a | 0x70039d0d:l16ui a6,a2+0x0; 0x70039d18:l8ui a8,a2+0x3; 0x70039d1b:l8ui a9,a2+0x2; 0x70039d1c:l8ui a0,a2+0x4f; 0x70039d5e:l16ui a6,a2+0x0; 0x70039da5:l16ui a6,a2+0x0; 0x70039df9:l8ui a10,a2+0x1f; 0x70039dfc:l8ui a11,a2+0x1e |
| `0x70015e98` | `a9` | 41 | 40 | 0x0, 0x2, 0x8, 0xc, 0x10, 0x14, 0x18, 0x1c, 0x20, 0x24, 0x28, 0x30, 0x34 | 0x7001603d:l8ui a15,a9+0x8; 0x70016040:l8ui a8,a9+0x7; 0x700160a5:l8ui a3,a9+0xd; 0x700160a8:l8ui a8,a9+0xc; 0x700160ff:l8ui a4,a9+0x11; 0x70016102:l8ui a8,a9+0x10; 0x70016159:l8ui a5,a9+0x15; 0x7001615c:l8ui a8,a9+0x14 |
| `0x70065b10` | `a6` | 56 | 46 | 0x0, 0x4, 0x8, 0xc, 0x10, 0x14, 0x18, 0x1c, 0x24, 0x2c, 0x30, 0x38 | 0x70065ba1:s8i a0,a6+0x1a; 0x70065bb6:l8ui a0,a6+0x15; 0x70065be1:s8i a0,a6+0xa; 0x70065e67:s8i a0,a6+0x80; 0x70065e7c:l8ui a0,a6+0x7b; 0x70065ec3:s8i a0,a6+0x69; 0x70065eef:s8i a0,a6+0x5e; 0x70065ef7:l8ui a0,a6+0x80 |
| `0x7000c284` | `a2` | 66 | 38 | 0x0, 0x2, 0x8, 0x10, 0x18, 0x24, 0x28, 0x2c, 0x30, 0x34, 0x38, 0x3b | 0x7000c2a5:l8ui a8,a2+0x3; 0x7000c2a8:l8ui a9,a2+0x2; 0x7000c2a9:l8ui a0,a2+0x4f; 0x7000c342:l16ui a6,a2+0x0; 0x7000c384:l8ui a13,a2+0x1f; 0x7000c387:l8ui a15,a2+0x1e; 0x7000c3ab:l16ui a6,a2+0x0; 0x7000c3cb:l8ui a10,a2+0x7 |
| `0x7003ce3c` | `a2` | 20 | 12 | 0x0, 0x2, 0x18, 0x1c, 0x20, 0x24, 0x28, 0x34, 0x38, 0x39, 0x3a, 0x3b | 0x7003cef7:l16ui a6,a2+0x0; 0x7003cf1d:l8ui a13,a2+0x3a; 0x7003cf36:l8ui a15,a2+0x3b; 0x7003cf5e:l32i a12,a2+0x20; 0x7003cf64:l32i a10,a2+0x24; 0x7003cf6a:l32i a8,a2+0x28; 0x7003cf70:l32i a10,a2+0x34; 0x7003cf76:l8ui a9,a2+0x38 |
| `0x7008e220` | `a12` | 12 | 12 | 0xc, 0x10, 0x14, 0x18, 0x1c, 0x20, 0x24, 0x28, 0x2c, 0x30, 0x34, 0x38 | 0x7008e266:s32i a11,a12+0xc; 0x7008e269:s32i a11,a12+0x10; 0x7008e26c:s32i a11,a12+0x14; 0x7008e26f:s32i a11,a12+0x18; 0x7008e272:s32i a11,a12+0x1c; 0x7008e275:s32i a11,a12+0x20; 0x7008e278:s32i a11,a12+0x24; 0x7008e27b:s32i a11,a12+0x28 |
| `0x7005f2b8` | `a2` | 68 | 32 | 0x0, 0x2, 0x8, 0x10, 0x18, 0x20, 0x24, 0x28, 0x30, 0x38, 0x39 | 0x7005f2d4:l8ui a8,a2+0x3; 0x7005f2d7:l8ui a9,a2+0x2; 0x7005f32d:l16ui a6,a2+0x0; 0x7005f3a5:s8i a0,a2+0x24; 0x7005f3ae:l16ui a6,a2+0x0; 0x7005f3b9:l8ui a11,a2+0x1f; 0x7005f3bc:l8ui a13,a2+0x1e; 0x7005f400:l8ui a13,a2+0x7 |
| `0x70076d50` | `a2` | 129 | 54 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x18, 0x20, 0x3a, 0x3b | 0x70076d63:l8ui a8,a2+0x3; 0x70076d66:l8ui a9,a2+0x2; 0x70076d67:l8ui a0,a2+0x4f; 0x70076db9:l16ui a6,a2+0x0; 0x70076e27:l16ui a6,a2+0x0; 0x70076e31:s16i a0,a2+0x46; 0x70076e6e:l16ui a6,a2+0x0; 0x70076e81:l16ui a6,a2+0x0 |
| `0x700130c0` | `a2` | 80 | 48 | 0x0, 0x2, 0x4, 0x10, 0x14, 0x1c, 0x20, 0x2c, 0x30 | 0x700130d2:s16i a0,a2+0x4; 0x700130d3:l8ui a5,a2+0x3; 0x700130d6:l8ui a8,a2+0x2; 0x700130d7:l8ui a0,a2+0x4f; 0x70013196:l16ui a6,a2+0x0; 0x700131df:l16ui a6,a2+0x0; 0x700131f1:l16ui a6,a2+0x0; 0x70013260:l16ui a6,a2+0x0 |
| `0x70011ab8` | `a8` | 40 | 27 | 0x2, 0x4, 0xc, 0x10, 0x18, 0x20, 0x28, 0x2c, 0x30 | 0x70011c00:l8ui a14,a8+0x1f; 0x70011c03:l8ui a13,a8+0x1e; 0x70011c4b:l8ui a13,a8+0x7; 0x70011c4e:l8ui a9,a8+0x6; 0x70011c90:l8ui a15,a8+0xb; 0x70011c93:l8ui a9,a8+0xa; 0x70011cd5:l8ui a7,a8+0xf; 0x70011cd8:l8ui a9,a8+0xe |
| `0x70149790` | `a2` | 60 | 17 | 0x0, 0x2, 0x4, 0x8, 0xc, 0x10, 0x18, 0x2c, 0x30 | 0x701497c6:l16ui a6,a2+0x0; 0x701498c3:l16ui a6,a2+0x0; 0x70149904:l16ui a6,a2+0x0; 0x70149914:l16ui a6,a2+0x0; 0x70149a44:l16ui a6,a2+0x8; 0x70149a79:l16ui a6,a2+0x0; 0x70149adb:l16ui a6,a2+0x0; 0x70149bf2:l16ui a6,a2+0x0 |
| `0x700b8100` | `a2` | 43 | 17 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x18, 0x20, 0x30, 0x3a | 0x700b817a:l16ui a15,a2+0x2a; 0x700b817d:l16ui a14,a2+0x18; 0x700b81ae:l16ui a8,a2+0x1a; 0x700b8210:l16ui a6,a2+0x0; 0x700b82e0:l16ui a6,a2+0x0; 0x700b838e:l16ui a6,a2+0x10; 0x700b83b1:l16ui a6,a2+0x0; 0x700b8603:s8i a0,a2+0x61 |
| `0x700b3d00` | `a2` | 48 | 16 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x18, 0x20, 0x30, 0x3a | 0x700b3d58:l32i a9,a2+0x10; 0x700b3d7b:l16ui a15,a2+0x2a; 0x700b3d7e:l16ui a14,a2+0x18; 0x700b3da4:l16ui a6,a2+0x0; 0x700b3daf:l16ui a8,a2+0x1a; 0x700b3e12:l16ui a6,a2+0x0; 0x700b3e31:l16ui a6,a2+0x0; 0x700b3e84:l16ui a6,a2+0x0 |
| `0x700130c0` | `a11` | 15 | 12 | 0x0, 0x2, 0x4, 0x8, 0xc, 0x10, 0x14, 0x18, 0x38 | 0x700135eb:l8ui a0,a11+0x52; 0x70013e09:s32i a3,a11+0x0; 0x70013e0c:s32i a3,a11+0x4; 0x70013e0f:s32i a3,a11+0x8; 0x70013e12:s32i a3,a11+0xc; 0x70013e15:s32i a3,a11+0x10; 0x70013e18:s32i a3,a11+0x14; 0x700140c6:s16i a6,a11+0x1a |
| `0x70056290` | `a2` | 128 | 70 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x18, 0x3a, 0x3b | 0x700562a6:l16ui a6,a2+0x0; 0x700562b1:l8ui a8,a2+0x3; 0x700562b4:l8ui a9,a2+0x2; 0x700562b5:l8ui a0,a2+0x4f; 0x7005634e:l16ui a6,a2+0x0; 0x70056360:l16ui a6,a2+0x0; 0x70056391:l8ui a11,a2+0x7; 0x70056394:l8ui a12,a2+0x6 |
| `0x7006cb90` | `a2` | 132 | 56 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x18, 0x3a, 0x3b | 0x7006cba6:l8ui a8,a2+0x3; 0x7006cba9:l8ui a9,a2+0x2; 0x7006cbaa:l8ui a0,a2+0x4f; 0x7006cc33:l16ui a6,a2+0x0; 0x7006cc74:s16i a0,a2+0x46; 0x7006cc9f:l16ui a6,a2+0x0; 0x7006ccb1:l16ui a6,a2+0x0; 0x7006cccf:l8ui a3,a2+0x2f |
| `0x700304f8` | `a2` | 151 | 55 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x18, 0x3a, 0x3b | 0x7003050b:l8ui a8,a2+0x3; 0x7003050e:l8ui a9,a2+0x2; 0x7003050f:l8ui a0,a2+0x4f; 0x7003065b:l8ui a15,a2+0x2f; 0x7003065e:l8ui a8,a2+0x2e; 0x700306a8:l8ui a5,a2+0x48; 0x700306c4:l8ui a11,a2+0x23; 0x700306c7:l8ui a8,a2+0x22 |
| `0x700414d0` | `a2` | 124 | 51 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x20, 0x3a, 0x3b | 0x700414e3:l8ui a8,a2+0x3; 0x700414e6:l8ui a9,a2+0x2; 0x700414e7:l8ui a0,a2+0x4f; 0x70041539:l16ui a6,a2+0x0; 0x70041567:s16i a0,a2+0x46; 0x700415a7:l16ui a6,a2+0x0; 0x70041656:l8ui a12,a2+0x2f; 0x70041659:l8ui a8,a2+0x2e |
| `0x70011ab8` | `a10` | 18 | 17 | 0x0, 0x4, 0xc, 0x10, 0x18, 0x30, 0x34, 0x38 | 0x70011e83:l8ui a10,a10+0x26; 0x70012035:s16i a0,a10+0x0; 0x700122f6:s32i a15,a10+0xc; 0x700122f9:s32i a15,a10+0x10; 0x700122fc:s32i a15,a10+0x50; 0x70012322:s16i a2,a10+0x34; 0x70012335:s32i a14,a10+0x30; 0x7001234e:s32i a6,a10+0x4 |
| `0x70061134` | `a12` | 35 | 16 | 0x1, 0x2, 0x8, 0xc, 0x18, 0x20, 0x28, 0x30 | 0x700618af:l8ui a0,a12+0x9; 0x70061c98:l8ui a3,a12+0x5a; 0x700620fd:l8ui a0,a12+0xc; 0x70062505:s16i a13,a12+0x80; 0x700627c3:l8ui a1,a12+0x30; 0x700627d3:l8ui a1,a12+0x30; 0x700627e3:l8ui a1,a12+0x30; 0x700629ee:l8ui a0,a12+0xc |
| `0x70015e98` | `a2` | 42 | 15 | 0x0, 0x2, 0x4, 0x8, 0x10, 0x18, 0x20, 0x24 | 0x70015f74:l16ui a6,a2+0x0; 0x70015fa0:s8i a0,a2+0x2b; 0x70016026:l32i a13,a2+0x8; 0x70016027:l8ui a2,a2+0x4; 0x7001602f:l16ui a6,a2+0x0; 0x700160e8:s8i a0,a2+0x61; 0x700160fe:s8i a13,a2+0x9; 0x70016138:l16ui a6,a2+0x0 |
| `0x700ae090` | `a2` | 29 | 15 | 0x0, 0x8, 0xc, 0x10, 0x18, 0x20, 0x30, 0x34 | 0x700ae109:l16ui a6,a2+0x0; 0x700ae129:l16ui a15,a2+0x1a; 0x700ae169:l16ui a6,a2+0x0; 0x700ae1c8:s16i a0,a2+0x34; 0x700ae381:l16ui a6,a2+0x0; 0x700ae394:l16ui a6,a2+0x0; 0x700ae3c2:l16ui a6,a2+0x0; 0x700ae439:s16i a0,a2+0x42 |
| `0x7001b040` | `a10` | 15 | 14 | 0x0, 0x4, 0x8, 0xc, 0x10, 0x30, 0x34, 0x38 | 0x7001b6ee:s16i a0,a10+0x0; 0x7001b704:s32i a14,a10+0x38; 0x7001b707:s32i a14,a10+0x7c; 0x7001b736:s32i a7,a10+0xc; 0x7001b739:s32i a7,a10+0x10; 0x7001b73c:s32i a7,a10+0x50; 0x7001b74f:s16i a8,a10+0x34; 0x7001b75a:s32i a9,a10+0x44 |

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
