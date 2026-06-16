# FLIX Bundle Learning Baseline

This report is evidence, not a disassembly. It is meant to decide
whether table targets are bundle starts, landing pads, or another
kind of FLIX/TIE entry before assigning opcode semantics.

## Anchor Corpus

* Dispatch anchors: 67
* Manual anchors: 5
* Dense regions: `0x700169a2..0x70018190`, `0x70081ec5..0x70082b2c`
* Learner profile: `bucket_fields_broad`
* Score version: `v10_bucket_fields`
* Fixed `flix_score`: `2874`

## Fixed Scorecard

* `resolved_boundary_ratio`: 1.0000
* `ambiguous_count`: 0
* `c1_candidate_bundle_start`: 1
* `abc_landing_ratio`: 1.0000
* `d_template_ratio`: 1.0000
* `strong_template_families`: 5
* `d_fixed29_template_families`: 3
* `control_vote_boundaries`: 15
* `pcrel_usable_candidates`: 149
* `pcrel_rejected_candidates`: 90
* `pcrel_supported_boundaries`: 15
* `internal_control_boundaries`: 133
* `internal_strong_boundaries`: 103
* `internal_medium_boundaries`: 30
* `internal_prop_groups`: 20
* `accepted_slot_templates`: 4
* `high_volume_slot_templates`: 1
* `slot_template_internal_edges`: 133
* `operand_model_rows`: 4
* `accepted_operand_models`: 1
* `negative_operand_models`: 1
* `operand_model_internal_edges`: 133
* `operand_model_delta_match_ratio`: 1.0000
* `cfg_edges`: 149
* `cfg_strong_edges`: 103
* `cfg_medium_edges`: 30
* `cfg_landing_edges`: 16
* `cfg_nodes`: 149
* `cfg_strong_nodes`: 103
* `cfg_medium_nodes`: 30
* `accepted_cfg_clusters`: 20
* `basic_blocks`: 176
* `terminated_blocks`: 66
* `fallthrough_blocks`: 109
* `strong_blocks`: 30
* `medium_blocks`: 24
* `block_edges`: 175
* `block_components`: 52
* `linear_components`: 40
* `branch_components`: 12
* `mapped_dispatch_anchors`: 67
* `close_dispatch_anchors`: 59
* `far_dispatch_anchors`: 8
* `exact_dispatch_blocks`: 9
* `inside_dispatch_blocks`: 0
* `successor_dispatch_nodes`: 10
* `kernel_buckets`: 9
* `strong_kernel_buckets`: 7
* `kernel_component_maps`: 52
* `dispatch_kernel_maps`: 67
* `dispatch_kernel_close`: 59
* `abc_landing_buckets`: 5
* `d_ann_stub_buckets`: 1
* `body_kernel_buckets`: 2
* `control_anchor_buckets`: 1
* `bucket_field_samples`: 67
* `bucket_templates`: 69
* `stable_bucket_templates`: 4
* `strong_bucket_templates`: 0
* `bucket_field_rows`: 1288
* `fixed_bucket_fields`: 447
* `variable_bucket_fields`: 841
* `bucket_variable_ranges`: 122
* `d_stub_rows`: 31
* `d_stub_exact_rows`: 9
* `d_stub_close_rows`: 14
* `d_stub_far_rows`: 8
* `op_registry_entries`: 63
* `op_static_joins`: 0
* `op_unresolved_static_joins`: 63
* `ghidra_focus_targets`: 119
* `ghidra_p0_focus_targets`: 9
* `false_bundle_abc`: 0
* `false_template_non_d`: 0
* `d_missed_templates`: 0

## Table Stride Highlights

* Table `A`: targets `0x70017d41`..`0x70017dba`, next deltas -0xb:10, 0x79:1
  mod8 residues: 0:1, 1:2, 2:2, 3:1, 4:2, 5:1, 6:1, 7:2
  mod16 residues: 1:1, 10:1, 12:1, 13:1, 14:1, 15:1, 2:1, 3:1, 4:1, 7:1, 8:1, 9:1
* Table `B`: targets `0x70017dc5`..`0x70017e3e`, next deltas -0xb:10, 0x79:1
  mod8 residues: 0:2, 1:1, 2:1, 3:2, 4:1, 5:2, 6:2, 7:1
  mod16 residues: 0:1, 1:1, 11:1, 12:1, 13:1, 14:1, 2:1, 3:1, 5:1, 6:1, 7:1, 8:1
* Table `C`: targets `0x700169a4`..`0x70017d36`, next deltas -0xb:9, -0x1324:1, 0x1392:1
  mod8 residues: 0:2, 1:1, 2:1, 3:2, 4:2, 5:1, 6:2, 7:1
  mod16 residues: 0:1, 10:1, 11:1, 14:1, 15:1, 3:1, 4:2, 5:1, 6:1, 8:1, 9:1
* Table `D`: targets `0x70081ec5`..`0x70082aac`, next deltas -0x68:12, -0x75:7, -0x77:3, -0x76:2, 0xbc5:1, -0x3ce:1
  mod8 residues: 0:3, 2:1, 3:4, 4:14, 5:3, 6:3, 7:3
  mod16 residues: 11:1, 12:7, 14:3, 15:1, 2:1, 3:3, 4:7, 5:3, 7:2, 8:3

## Stable Byte Masks

* Table `A`: fixed positions=6
  `?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? 70 ?? ?? ?? ?? 01 ?? ??`
* Table `B`: fixed positions=3
  `?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fa ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??`
* Table `C`: fixed positions=2
  `?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??`
* Table `D`: fixed positions=0
  `?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??`

## Best 8/16-byte Phase Scores

* `flk_dense` width=8:
  phase=1 base=0x700169a3 score=306 anchors_on=6/40 duplicates=38 instances=102
  phase=6 base=0x700169a8 score=285 anchors_on=5/40 duplicates=39 instances=93
  phase=0 base=0x700169a2 score=278 anchors_on=5/40 duplicates=37 instances=87
  phase=4 base=0x700169a6 score=276 anchors_on=5/40 duplicates=37 instances=89
* `flk_dense` width=16:
  phase=1 base=0x700169a3 score=91 anchors_on=4/40 duplicates=6 instances=13
  phase=0 base=0x700169a2 score=81 anchors_on=3/40 duplicates=6 instances=13
  phase=2 base=0x700169a4 score=79 anchors_on=3/40 duplicates=6 instances=13
  phase=6 base=0x700169a8 score=70 anchors_on=3/40 duplicates=5 instances=11
* `ann_dense` width=8:
  phase=7 base=0x70081ecc score=564 anchors_on=14/31 duplicates=49 instances=236
  phase=6 base=0x70081ecb score=458 anchors_on=4/31 duplicates=46 instances=243
  phase=0 base=0x70081ec5 score=452 anchors_on=3/31 duplicates=44 instances=251
  phase=1 base=0x70081ec6 score=423 anchors_on=3/31 duplicates=43 instances=247
* `ann_dense` width=16:
  phase=7 base=0x70081ecc score=198 anchors_on=7/31 duplicates=16 instances=64
  phase=15 base=0x70081ed4 score=197 anchors_on=7/31 duplicates=14 instances=59
  phase=14 base=0x70081ed3 score=165 anchors_on=3/31 duplicates=16 instances=65
  phase=3 base=0x70081ec8 score=162 anchors_on=3/31 duplicates=17 instances=71

## Current Boundary Read

* Tables `A`, `B`, and most of `C` have a dominant `-0xb` target stride and cycle through many mod8/mod16 residues. That is weak evidence for 8/16-byte bundle-start targets and stronger evidence for short landing pads or slot-level branch targets.
* Table `C[1]` at `0x700169a4` is special: it has an explicit `.xt.prop` `insn|data|no_reorder|no_transform:0x8` run, while the later dense `0x70017cxx` targets are mostly `.xt.prop` zero-size `data|unreachable|no_transform` markers.
* Table `D` has repeated prologue bytes across many entries and dominant `-0x68` / `-0x75` / `-0x77` spacing. This looks like a family of generated ANN stubs/templates rather than independent arbitrary function starts.
* The ANN dense region has much stronger repeated 8/16-byte chunks than the FLK dense region; it should be the easier first target for raw-template learning, while `0x700169a4` remains the best FLK control anchor.

## Control-Flow Constraint Candidates

* Candidate byte-pattern hits: 445 total; high-score hits: 225
* Actionable hits for FLIX boundary learning: 165; actionable target votes: 164
* Kind counts: j:355, callx8:61, beqz:21, bnez:7, loop:1
* Top actionable target votes:
  target=0x70017e49 votes=2 score=58 kinds=bnez:2 prop=0x4:insn|data|branch_target|no_reorder|no_transform nearest_anchor=11
  target=0x70016da8 votes=1 score=32 kinds=loop:1 prop=0x35e:insn|data|branch_target|no_reorder|no_transform nearest_anchor=133
  target=0x70017cd0 votes=1 score=31 kinds=j:1 prop=0xb:insn|data|no_reorder|no_transform nearest_anchor=1
  target=0x70017d00 votes=1 score=31 kinds=j:1 prop=0xb:insn|data|no_reorder|no_transform nearest_anchor=1
  target=0x70017d58 votes=1 score=31 kinds=j:1 prop=0xb:insn|data|no_reorder|no_transform nearest_anchor=1
  target=0x70016fc3 votes=1 score=29 kinds=beqz:1 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=406
  target=0x700171e0 votes=1 score=29 kinds=j:1 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=947
  target=0x700171f8 votes=1 score=29 kinds=j:1 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=971
  target=0x70017003 votes=1 score=29 kinds=bnez:1 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=470
  target=0x7001706b votes=1 score=29 kinds=bnez:1 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=574

Control-flow rows are constraints, not decoded instructions. For bundle learning, `actionable` means the target lands in a dense FLIX region or within 8 bytes of a dispatch/manual anchor; other `.text` targets stay in the CSV but are not used as boundary evidence yet.

## PC-relative Slot Candidate Read

* PC-relative-looking slot candidates: 355; usable as constraints: 149
* Candidate classes: weak_or_unusable:115, strong_dense_control_target:103, reject_repeated_out_of_region:90, dense_insn_target:30, near_anchor_landing:13, anchor_adjacent_landing:3, repeated_060402_dense_target:1
* Strongest usable PC-relative slot constraints:
  source=0x700169ce raw=060402 target=0x700171e0 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=947
  source=0x700169e6 raw=060402 target=0x700171f8 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=971
  source=0x70016a0c raw=060402 target=0x70017220 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1011
  source=0x70016a1c raw=060402 target=0x70017230 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1027
  source=0x70016a35 raw=060402 target=0x70017248 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1051
  source=0x70016a48 raw=060402 target=0x7001725c class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1071
  source=0x70016a61 raw=060402 target=0x70017274 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1095
  source=0x70016a74 raw=060402 target=0x70017288 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1115
  source=0x70016a8d raw=060402 target=0x700172a0 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1139
  source=0x70016aa5 raw=060402 target=0x700172b8 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1163
  source=0x70016abe raw=060402 target=0x700172d0 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1187
  source=0x70016ad4 raw=060402 target=0x700172e8 class=strong_dense_control_target score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nearest_anchor=1211

The repeated `060402` pattern is useful only as a constrained PC-relative slot signal. Treat it as a real standard jump only when the target also lands on dense-region `.xt.prop` control metadata or a dispatch-adjacent landing pad.

## PC-relative Operand Models

* Accepted operand models: 1
* Negative operand models: 1
* Top operand model families:
  low=0x6 high=0x0 status=accepted_operand_model rows=250 raws=35 usable=149 internal=133 rejects=62 reject_bps=2480 delta_match=250/250 classes=strong_dense_control_target:103 reject_repeated_out_of_region:62 weak_or_unusable:38 dense_insn_target:30 near_anchor_landing:13 anchor_adjacent_landing:3 repeated_060402_dense_target:1
  low=0x6 high=0x4 status=rejected_low_usable rows=18 raws=18 usable=0 internal=0 rejects=0 reject_bps=0 delta_match=18/18 classes=weak_or_unusable:18
  low=0x6 high=0x8 status=rejected_low_usable rows=38 raws=38 usable=0 internal=0 rejects=0 reject_bps=0 delta_match=38/38 classes=weak_or_unusable:38
  low=0x6 high=0xc status=rejected_negative_operand_family rows=49 raws=22 usable=0 internal=0 rejects=28 reject_bps=5714 delta_match=49/49 classes=reject_repeated_out_of_region:28 weak_or_unusable:21

## PC-relative CFG Edges

* CFG edges accepted from operand models: 149
* CFG nodes: 149
* Accepted CFG clusters: 20
* Top CFG nodes:
  target=0x700171e0 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x700169ce..0x700169ce raws=060402:1
  target=0x700171f8 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x700169e6..0x700169e6 raws=060402:1
  target=0x70017220 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a0c..0x70016a0c raws=060402:1
  target=0x70017230 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a1c..0x70016a1c raws=060402:1
  target=0x70017248 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a35..0x70016a35 raws=060402:1
  target=0x7001725c confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a48..0x70016a48 raws=060402:1
  target=0x70017274 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a61..0x70016a61 raws=060402:1
  target=0x70017288 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a74..0x70016a74 raws=060402:1
  target=0x700172a0 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a8d..0x70016a8d raws=060402:1
  target=0x700172b8 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016aa5..0x70016aa5 raws=060402:1
  target=0x700172d0 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016abe..0x70016abe raws=060402:1
  target=0x700172e8 confidence=strong incoming=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016ad4..0x70016ad4 raws=060402:1
* Top CFG clusters:
  region=flk_dense prop=0x5f6:insn|data|loop_target|no_reorder|no_transform nodes=30 edges=30 targets=0x700171e0..0x700174e0 accepted=1 conf=strong:30
  region=flk_dense prop=0x1e1:insn|data|branch_target|no_reorder|no_transform nodes=18 edges=18 targets=0x700175e0..0x700177b8 accepted=1 conf=strong:18
  region=ann_dense prop=0x52:insn|no_reorder nodes=16 edges=16 targets=0x700826e8..0x70082ad8 accepted=1 conf=medium:14 landing:2
  region=flk_dense prop=0xb:insn|data|no_reorder|no_transform nodes=13 edges=13 targets=0x70017cd0..0x70017e48 accepted=1 conf=landing:12 medium:1
  region=flk_dense prop=0xd1:insn|data|branch_target|no_reorder|no_transform nodes=11 edges=11 targets=0x70017944..0x700179f4 accepted=1 conf=strong:11
  region=flk_dense prop=0x3f:insn|data|branch_target|no_reorder|no_transform nodes=10 edges=10 targets=0x70017818..0x70017904 accepted=1 conf=strong:10
  region=flk_dense prop=0x2f:insn|data|no_reorder|no_transform nodes=10 edges=10 targets=0x70017e74..0x70017f8c accepted=1 conf=medium:10
  region=flk_dense prop=0xdc:insn|data|branch_target|no_reorder|no_transform nodes=8 edges=8 targets=0x70017510..0x700175d0 accepted=1 conf=strong:8

## PC-relative Basic Block Extents

* Basic block hypotheses: 176
* Block successor edges: 175
* Top block extents:
  ann_dense 0x70081ec7..0x70081eda size=0x13 status=terminated_by_pcrel_source conf=medium term=0x70081ed7 succ=0x700826e8 prop=0x15:insn|branch_target|no_reorder
  ann_dense 0x70081ee7..0x70081f1c size=0x35 status=terminated_by_pcrel_source conf=medium term=0x70081f19 succ=0x7008272c prop=0x5f:insn|no_reorder
  ann_dense 0x70081f46..0x70081f56 size=0x10 status=terminated_by_pcrel_source conf=landing term=0x70081f53 succ=0x70082764 prop=0x10:insn|data|no_reorder|no_transform
  ann_dense 0x70081f5e..0x70081f6e size=0x10 status=terminated_by_pcrel_source conf=medium term=0x70081f6b succ=0x7008277c prop=0x5f:insn|no_reorder
  ann_dense 0x70081fbd..0x70081fcd size=0x10 status=terminated_by_pcrel_source conf=landing term=0x70081fca succ=0x700827dc prop=0x10:insn|data|no_reorder|no_transform
  ann_dense 0x70081fd3..0x70082008 size=0x35 status=terminated_by_pcrel_source conf=medium term=0x70082005 succ=0x70082818 prop=0x5f:insn|no_reorder
  ann_dense 0x70082032..0x70082042 size=0x10 status=terminated_by_pcrel_source conf=medium term=0x7008203f succ=0x70082850 prop=0x10:insn|data|no_reorder|no_transform
  ann_dense 0x70082048..0x7008207d size=0x35 status=terminated_by_pcrel_source conf=medium term=0x7008207a succ=0x7008288c prop=0x5f:insn|no_reorder
  ann_dense 0x700820a7..0x700820b7 size=0x10 status=terminated_by_pcrel_source conf=medium term=0x700820b4 succ=0x700828c8 prop=0x10:insn|data|no_reorder|no_transform
  ann_dense 0x700820be..0x700820f3 size=0x35 status=terminated_by_pcrel_source conf=landing term=0x700820f0 succ=0x70082904 prop=0x5f:insn|no_reorder
  ann_dense 0x7008211d..0x7008212d size=0x10 status=terminated_by_pcrel_source conf=medium term=0x7008212a succ=0x7008293c prop=0x10:insn|data|no_reorder|no_transform
  ann_dense 0x70082133..0x70082168 size=0x35 status=terminated_by_pcrel_source conf=landing term=0x70082165 succ=0x70082978 prop=0x5f:insn|no_reorder
  ann_dense 0x70082192..0x700821a2 size=0x10 status=terminated_by_pcrel_source conf=medium term=0x7008219f succ=0x700829b0 prop=0x10:insn|data|no_reorder|no_transform
  ann_dense 0x700821a8..0x700821dd size=0x35 status=terminated_by_pcrel_source conf=medium term=0x700821da succ=0x700829ec prop=0x5f:insn|no_reorder

## Block Components And Dispatch Map

* Block components: 52
* Dispatch anchors mapped to components: 67/67
* Top components:
  comp_000 kind=branch_or_join nodes=12 known=11 unresolved=1 edges=11 addr=0x700169ac..0x70017a84 entries=4 terminals=0 conf=entry:6 strong:5
  comp_001 kind=branch_or_join nodes=10 known=9 unresolved=1 edges=9 addr=0x70016bb0..0x70017c30 entries=3 terminals=0 conf=entry:5 strong:4
  comp_002 kind=branch_or_join nodes=5 known=4 unresolved=1 edges=4 addr=0x70016e2d..0x70017eab entries=2 terminals=0 conf=entry:2 medium:1 strong:1
  comp_003 kind=branch_or_join nodes=5 known=4 unresolved=1 edges=4 addr=0x70016f0e..0x70017f65 entries=2 terminals=0 conf=open:1 medium:1 entry:1 strong:1
  comp_004 kind=branch_or_join nodes=16 known=15 unresolved=1 edges=15 addr=0x70017274..0x70017b73 entries=5 terminals=0 conf=entry:10 strong:5
  comp_005 kind=linear_chain nodes=4 known=3 unresolved=1 edges=3 addr=0x70017410..0x70017c70 entries=1 terminals=0 conf=strong:1 open:1 entry:1
  comp_006 kind=branch_or_join nodes=9 known=8 unresolved=1 edges=8 addr=0x70017460..0x70017e49 entries=2 terminals=0 conf=entry:5 landing:1 strong:1 medium:1
  comp_007 kind=linear_chain nodes=5 known=4 unresolved=1 edges=4 addr=0x70017480..0x70017cbd entries=1 terminals=0 conf=entry:3 strong:1
  comp_008 kind=linear_chain nodes=3 known=2 unresolved=1 edges=2 addr=0x700174bc..0x70017cd1 entries=1 terminals=0 conf=landing:1 entry:1
  comp_009 kind=linear_chain nodes=3 known=2 unresolved=1 edges=2 addr=0x700174cc..0x70017ce9 entries=1 terminals=0 conf=landing:1 entry:1
  comp_010 kind=linear_chain nodes=3 known=2 unresolved=1 edges=2 addr=0x700174e0..0x70017d0a entries=1 terminals=0 conf=landing:1 entry:1
  comp_011 kind=linear_chain nodes=4 known=3 unresolved=1 edges=3 addr=0x70017510..0x70017d4c entries=1 terminals=0 conf=entry:2 landing:1
* Dispatch component map samples:
  A[0] target=0x70017d41 relation=nearest_block_start component=comp_011 block=0x70017d48..0x70017d4c conf=entry
  A[1] target=0x70017dba relation=nearest_block_start component=comp_013 block=0x70017da0..0x70017da4 conf=entry
  A[2] target=0x70017daf relation=nearest_block_start component=comp_013 block=0x70017da0..0x70017da4 conf=entry
  A[3] target=0x70017da4 relation=successor_node component=comp_013 block=.. conf=
  A[4] target=0x70017d99 relation=nearest_block_start component=comp_013 block=0x70017da0..0x70017da4 conf=entry
  A[5] target=0x70017d8e relation=nearest_block_start component=comp_013 block=0x70017da0..0x70017da4 conf=entry
  A[6] target=0x70017d83 relation=nearest_block_start component=comp_013 block=0x70017da0..0x70017da4 conf=entry
  A[7] target=0x70017d78 relation=nearest_block_start component=comp_012 block=0x70017d58..0x70017d62 conf=entry
  A[8] target=0x70017d6d relation=nearest_block_start component=comp_012 block=0x70017d58..0x70017d62 conf=entry
  A[9] target=0x70017d62 relation=successor_node component=comp_012 block=.. conf=
  A[10] target=0x70017d57 relation=nearest_block_start component=comp_012 block=0x70017d58..0x70017d62 conf=entry
  A[11] target=0x70017d4c relation=successor_node component=comp_011 block=.. conf=
  B[0] target=0x70017dc5 relation=nearest_block_start component=comp_013 block=0x70017da0..0x70017da4 conf=entry
  B[1] target=0x70017e3e relation=nearest_block_start component=comp_006 block=0x70017e40..0x70017e48 conf=entry
  B[2] target=0x70017e33 relation=nearest_block_start component=comp_006 block=0x70017e40..0x70017e48 conf=entry
  B[3] target=0x70017e28 relation=successor_node component=comp_016 block=.. conf=

## Kernel Buckets

* Kernel buckets: 9
* Component kernel maps: 52
* Dispatch kernel maps: 67
* Top kernel buckets:
  D_ann_generated_stub_family conf=strong components=13 dispatch=31 close=23 far=8 tables=D:31 addr=0x70081ec7..0x70082b20
  B_flk_landing_series conf=strong components=4 dispatch=10 close=10 far=0 tables=B:10 addr=0x70017460..0x70017e49
  C_flk_landing_series conf=strong components=5 dispatch=10 close=10 far=0 tables=C:10 addr=0x700174bc..0x70017d15
  AB_flk_landing_series conf=strong components=1 dispatch=8 close=8 far=0 tables=A:6 B:2 addr=0x7001756c..0x70017da4
  A_flk_landing_series conf=strong components=1 dispatch=4 close=4 far=0 tables=A:4 addr=0x70017540..0x70017d62
  AC_flk_landing_series conf=strong components=1 dispatch=3 close=3 far=0 tables=A:2 C:1 addr=0x70017510..0x70017d4c
  C_flk_control_anchor conf=strong components=1 dispatch=1 close=1 far=0 tables=C:1 addr=0x700169ac..0x70017a84
  ann_body_component conf=inferred components=2 dispatch=0 close=0 far=0 tables= addr=0x700820a7..0x7008295e
  flk_body_component conf=inferred components=24 dispatch=0 close=0 far=0 tables= addr=0x70016bb0..0x70018174
* Dispatch kernel map samples:
  A[0] target=0x70017d41 relation=nearest_block_start close=1 component=comp_011 bucket=AC_flk_landing_series conf=strong
  A[1] target=0x70017dba relation=nearest_block_start close=1 component=comp_013 bucket=AB_flk_landing_series conf=strong
  A[2] target=0x70017daf relation=nearest_block_start close=1 component=comp_013 bucket=AB_flk_landing_series conf=strong
  A[3] target=0x70017da4 relation=successor_node close=1 component=comp_013 bucket=AB_flk_landing_series conf=strong
  A[4] target=0x70017d99 relation=nearest_block_start close=1 component=comp_013 bucket=AB_flk_landing_series conf=strong
  A[5] target=0x70017d8e relation=nearest_block_start close=1 component=comp_013 bucket=AB_flk_landing_series conf=strong
  A[6] target=0x70017d83 relation=nearest_block_start close=1 component=comp_013 bucket=AB_flk_landing_series conf=strong
  A[7] target=0x70017d78 relation=nearest_block_start close=1 component=comp_012 bucket=A_flk_landing_series conf=strong
  A[8] target=0x70017d6d relation=nearest_block_start close=1 component=comp_012 bucket=A_flk_landing_series conf=strong
  A[9] target=0x70017d62 relation=successor_node close=1 component=comp_012 bucket=A_flk_landing_series conf=strong
  A[10] target=0x70017d57 relation=nearest_block_start close=1 component=comp_012 bucket=A_flk_landing_series conf=strong
  A[11] target=0x70017d4c relation=successor_node close=1 component=comp_011 bucket=AC_flk_landing_series conf=strong
  B[0] target=0x70017dc5 relation=nearest_block_start close=1 component=comp_013 bucket=AB_flk_landing_series conf=strong
  B[1] target=0x70017e3e relation=nearest_block_start close=1 component=comp_006 bucket=B_flk_landing_series conf=strong
  B[2] target=0x70017e33 relation=nearest_block_start close=1 component=comp_006 bucket=B_flk_landing_series conf=strong
  B[3] target=0x70017e28 relation=successor_node close=1 component=comp_016 bucket=B_flk_landing_series conf=strong
  B[4] target=0x70017e1d relation=nearest_block_start close=1 component=comp_016 bucket=B_flk_landing_series conf=strong
  B[5] target=0x70017e12 relation=successor_node close=1 component=comp_015 bucket=B_flk_landing_series conf=strong

## Bucket Field Learning

* Bucket field samples: 67
* Bucket templates: 69
* Variable ranges: 122
* Strong bucket templates:
  AB_flk_landing_series width=8 samples=8 fixed_bytes=2 variable_bits=22 mask=`?? ?? 70 ?? ?? ?? ?? 01`
  AB_flk_landing_series width=16 samples=8 fixed_bytes=3 variable_bits=45 mask=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? 70 ?? ??`
  AB_flk_landing_series width=32 samples=8 fixed_bytes=6 variable_bits=98 mask=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? 70 ?? ?? ?? ?? 01 ?? ??`
  AC_flk_landing_series width=8 samples=3 fixed_bytes=2 variable_bits=21 mask=`?? ?? 70 ?? ?? ?? ?? 01`
  AC_flk_landing_series width=16 samples=3 fixed_bytes=4 variable_bits=37 mask=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fb ?? ?? 70 ?? ??`
  AC_flk_landing_series width=32 samples=3 fixed_bytes=9 variable_bits=77 mask=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fb ?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fb ?? 1a 70 ?? ?? ?? ?? 01 ?? ??`
  A_flk_landing_series width=8 samples=4 fixed_bytes=3 variable_bits=18 mask=`?? 1a 70 ?? ?? ?? ?? 01`
  A_flk_landing_series width=16 samples=4 fixed_bytes=6 variable_bits=34 mask=`?? 1a 70 ?? ?? ?? ?? 01 ?? ?? fb ?? 1a 70 ?? ??`
  A_flk_landing_series width=32 samples=4 fixed_bytes=11 variable_bits=73 mask=`?? 1a 70 ?? ?? ?? ?? 01 ?? ?? fb ?? 1a 70 ?? ?? ?? ?? 01 ?? ?? fb ?? 1a 70 ?? ?? ?? ?? 01 ?? ??`
  B_flk_landing_series width=8 samples=10 fixed_bytes=2 variable_bits=22 mask=`?? ?? 70 ?? ?? ?? ?? 01`
  B_flk_landing_series width=16 samples=10 fixed_bytes=3 variable_bits=53 mask=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fa ?? ?? ?? ?? ??`
  B_flk_landing_series width=32 samples=10 fixed_bytes=3 variable_bits=151 mask=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fa ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??`
* Variable field ranges:
  AB_flk_landing_series class=all width=32 off=0..1 size=2 var_bits=0x73 kinds=high_nibble_variable:1 low_nibble_variable:1
  AB_flk_landing_series class=all width=32 off=3..6 size=4 var_bits=0xfb kinds=high_nibble_variable:3 variable_byte:1
  AB_flk_landing_series class=all width=32 off=8..12 size=5 var_bits=0xff kinds=high_nibble_variable:2 low_nibble_variable:2 variable_byte:1
  AB_flk_landing_series class=all width=32 off=14..17 size=4 var_bits=0xfb kinds=high_nibble_variable:3 variable_byte:1
  AB_flk_landing_series class=all width=32 off=19..23 size=5 var_bits=0xff kinds=high_nibble_variable:2 low_nibble_variable:2 variable_byte:1
  AB_flk_landing_series class=all width=32 off=25..28 size=4 var_bits=0xfb kinds=high_nibble_variable:3 variable_byte:1
  AB_flk_landing_series class=all width=32 off=30..31 size=2 var_bits=0xff kinds=high_nibble_variable:1 variable_byte:1
  AB_flk_landing_series class=close width=32 off=0..1 size=2 var_bits=0x73 kinds=high_nibble_variable:1 low_nibble_variable:1
  AB_flk_landing_series class=close width=32 off=3..6 size=4 var_bits=0xfb kinds=high_nibble_variable:3 variable_byte:1
  AB_flk_landing_series class=close width=32 off=8..12 size=5 var_bits=0xff kinds=high_nibble_variable:2 low_nibble_variable:2 variable_byte:1
  AB_flk_landing_series class=close width=32 off=14..17 size=4 var_bits=0xfb kinds=high_nibble_variable:3 variable_byte:1
  AB_flk_landing_series class=close width=32 off=19..23 size=5 var_bits=0xff kinds=high_nibble_variable:2 low_nibble_variable:2 variable_byte:1
  AB_flk_landing_series class=close width=32 off=25..28 size=4 var_bits=0xfb kinds=high_nibble_variable:3 variable_byte:1
  AB_flk_landing_series class=close width=32 off=30..31 size=2 var_bits=0xff kinds=high_nibble_variable:1 variable_byte:1

## D Stub Deep Dive

* D stub evidence classes: close:14, exact:9, far:8
  D[0] target=0x70081ee7 class=exact relation=exact_block_start dist=0 prev= next=0xbc5 sha16=e7981891 note=strong_generated_stub_sample
  D[1] target=0x70082aac class=close relation=nearest_block_start dist=16 prev=0xbc5 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[2] target=0x70082a44 class=close relation=nearest_block_start dist=28 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[3] target=0x700829dc class=close relation=nearest_block_start dist=16 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[4] target=0x70082974 class=close relation=nearest_block_start dist=4 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[5] target=0x7008290c class=close relation=nearest_block_start dist=8 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[6] target=0x700828a4 class=close relation=nearest_block_start dist=24 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[7] target=0x7008283c class=close relation=nearest_block_start dist=20 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[8] target=0x700827d4 class=close relation=nearest_block_start dist=8 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[9] target=0x7008276c class=close relation=nearest_block_start dist=8 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[10] target=0x70082704 class=close relation=nearest_block_start dist=28 prev=-0x68 next=-0x68 sha16=e7981891 note=strong_generated_stub_sample
  D[11] target=0x7008269c class=far relation=nearest_block_start dist=76 prev=-0x68 next=-0x68 sha16=e7981891 note=weak_far_nearest_sample
  D[12] target=0x70082634 class=far relation=nearest_block_start dist=180 prev=-0x68 next=-0x68 sha16=e7981891 note=weak_far_nearest_sample
  D[13] target=0x700825cc class=far relation=nearest_block_start dist=284 prev=-0x68 next=-0x75 sha16=e7981891 note=weak_far_nearest_sample
  D[14] target=0x70082557 class=far relation=nearest_block_start dist=401 prev=-0x75 next=-0x75 sha16=e7981891 note=weak_far_nearest_sample
  D[15] target=0x700824e2 class=far relation=nearest_block_start dist=496 prev=-0x75 next=-0x77 sha16=e7981891 note=weak_far_nearest_sample

## OP Registry Join Status

* OP registry entries: 63
* Proven static OP-to-bucket joins: 0
* Unresolved static joins: 63
The `.dram_op.data` table is kept as an op-name vocabulary until an indexed parser/runtime join is found.
  op=0 name=CONV2D status=unresolved_no_indexed_static_join
  op=1 name=DWCONV2D status=unresolved_no_indexed_static_join
  op=2 name=POOL2D status=unresolved_no_indexed_static_join
  op=3 name=LOGISTIC status=unresolved_no_indexed_static_join
  op=4 name=RELU status=unresolved_no_indexed_static_join
  op=5 name=SOFTMAX status=unresolved_no_indexed_static_join
  op=6 name=RESHAPE status=unresolved_no_indexed_static_join
  op=7 name=CONCAT status=unresolved_no_indexed_static_join
  op=8 name=ELEWISE status=unresolved_no_indexed_static_join
  op=9 name=L2NORM status=unresolved_no_indexed_static_join

## Ghidra Focus Pack

* Focus targets: 119
  P0_exact_dispatch_start dispatch_target 0x70081ee7..0x70081ee7 bucket=D_ann_generated_stub_family comp=comp_038 table=D[0]
  P0_exact_dispatch_start dispatch_target 0x70081f5e..0x70081f5e bucket=D_ann_generated_stub_family comp=comp_040 table=D[30]
  P0_exact_dispatch_start dispatch_target 0x70081fd3..0x70081fd3 bucket=D_ann_generated_stub_family comp=comp_041 table=D[29]
  P0_exact_dispatch_start dispatch_target 0x70082048..0x70082048 bucket=D_ann_generated_stub_family comp=comp_042 table=D[27]
  P0_exact_dispatch_start dispatch_target 0x700820be..0x700820be bucket=D_ann_generated_stub_family comp=comp_044 table=D[26]
  P0_exact_dispatch_start dispatch_target 0x70082133..0x70082133 bucket=D_ann_generated_stub_family comp=comp_046 table=D[25]
  P0_exact_dispatch_start dispatch_target 0x700821a8..0x700821a8 bucket=D_ann_generated_stub_family comp=comp_047 table=D[24]
  P0_exact_dispatch_start dispatch_target 0x7008221e..0x7008221e bucket=D_ann_generated_stub_family comp=comp_048 table=D[23]
  P0_exact_dispatch_start dispatch_target 0x70082293..0x70082293 bucket=D_ann_generated_stub_family comp=comp_050 table=D[20]
  P1_close_dispatch_landing dispatch_target 0x700169a4..0x700169a4 bucket=C_flk_control_anchor comp=comp_000 table=C[1]
  P1_close_dispatch_landing dispatch_target 0x70017cc8..0x70017cc8 bucket=C_flk_landing_series comp=comp_008 table=C[0]
  P1_close_dispatch_landing dispatch_target 0x70017cd3..0x70017cd3 bucket=C_flk_landing_series comp=comp_008 table=C[11]
  P1_close_dispatch_landing dispatch_target 0x70017cde..0x70017cde bucket=C_flk_landing_series comp=comp_009 table=C[10]
  P1_close_dispatch_landing dispatch_target 0x70017ce9..0x70017ce9 bucket=C_flk_landing_series comp=comp_009 table=C[9]
  P1_close_dispatch_landing dispatch_target 0x70017cf4..0x70017cf4 bucket=C_flk_landing_series comp=comp_035 table=C[8]
  P1_close_dispatch_landing dispatch_target 0x70017cff..0x70017cff bucket=C_flk_landing_series comp=comp_010 table=C[7]

## Internal Control Boundary Candidates

* Accepted internal boundaries: 133
* Confidence counts: strong:103, medium:30
* Top property groups:
  0x5f6:insn|data|loop_target|no_reorder|no_transform count=30
  0x1e1:insn|data|branch_target|no_reorder|no_transform count=18
  0x52:insn|no_reorder count=14
  0xd1:insn|data|branch_target|no_reorder|no_transform count=11
  0x3f:insn|data|branch_target|no_reorder|no_transform count=10
  0x2f:insn|data|no_reorder|no_transform count=10
  0xdc:insn|data|branch_target|no_reorder|no_transform count=8
  0xfd:insn|data|branch_target|no_reorder|no_transform count=5
* Top internal targets:
  target=0x700171e0 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x700169ce..0x700169ce
  target=0x700171f8 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x700169e6..0x700169e6
  target=0x70017220 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a0c..0x70016a0c
  target=0x70017230 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a1c..0x70016a1c
  target=0x70017248 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a35..0x70016a35
  target=0x7001725c confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a48..0x70016a48
  target=0x70017274 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a61..0x70016a61
  target=0x70017288 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a74..0x70016a74
  target=0x700172a0 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016a8d..0x70016a8d
  target=0x700172b8 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016aa5..0x70016aa5
  target=0x700172d0 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016abe..0x70016abe
  target=0x700172e8 confidence=strong votes=1 score=29 prop=0x5f6:insn|data|loop_target|no_reorder|no_transform sources=0x70016ad4..0x70016ad4

## PC-relative Slot Grammar Templates

* Accepted slot templates: 4
* Top slot templates:
  raw=060402 status=accepted_slot_template rows=206 usable=143 internal=127 rejects=62 delta=0x810 classes=strong_dense_control_target:98 reject_repeated_out_of_region:62 dense_insn_target:29 near_anchor_landing:13 anchor_adjacent_landing:3 repeated_060402_dense_target:1
  raw=062200 status=accepted_slot_template rows=4 usable=4 internal=4 rejects=0 delta=0x88 classes=strong_dense_control_target:4
  raw=066b00 status=accepted_slot_template rows=1 usable=1 internal=1 rejects=0 delta=0x1ac classes=dense_insn_target:1
  raw=06e400 status=accepted_slot_template rows=1 usable=1 internal=1 rejects=0 delta=0x390 classes=strong_dense_control_target:1
  raw=c65cc0 status=rejected_low_usable rows=28 usable=0 internal=0 rejects=28 delta=0x330170 classes=reject_repeated_out_of_region:28
  raw=06230d status=rejected_low_usable rows=4 usable=0 internal=0 rejects=0 delta=0x348c classes=weak_or_unusable:4
  raw=06030d status=rejected_low_usable rows=2 usable=0 internal=0 rejects=0 delta=0x340c classes=weak_or_unusable:2
  raw=06130d status=rejected_low_usable rows=2 usable=0 internal=0 rejects=0 delta=0x344c classes=weak_or_unusable:2
  raw=0631a8 status=rejected_low_usable rows=2 usable=0 internal=0 rejects=0 delta=0x2a0c4 classes=weak_or_unusable:2
  raw=067058 status=rejected_low_usable rows=2 usable=0 internal=0 rejects=0 delta=0x161c0 classes=weak_or_unusable:2
  raw=060110 status=rejected_low_usable rows=1 usable=0 internal=0 rejects=0 delta=0x4004 classes=weak_or_unusable:1
  raw=06014f status=rejected_low_usable rows=1 usable=0 internal=0 rejects=0 delta=0x13c04 classes=weak_or_unusable:1

## Boundary Hypotheses

* Hypothesis counts: likely_landing_pad_or_slot_target:35, generated_template_entry:28, candidate_bundle_start:4
* Strongest landing/template candidates:
  D[4] 0x70082974 -> generated_template_entry bundle=4 landing=8 template=8 evidence=prop=0x0:unreachable;best_prop=0x70082974:0x52:insn|no_reorder;zero_size_unreachable_marker;d_generated_stride_family;aligns_top8_phase_rank_1;aligns_top16_phase_rank_2;repeat8_count_27;repeat16_count_27;control_vote_near:0x70082978:j:1;pcrel_slot_near:0x70082978:0x70082165:near_anchor_landing
  D[5] 0x7008290c -> generated_template_entry bundle=4 landing=8 template=8 evidence=prop=0x0:unreachable;best_prop=0x7008290c:0x52:insn|no_reorder;zero_size_unreachable_marker;d_generated_stride_family;aligns_top8_phase_rank_1;aligns_top16_phase_rank_1;repeat8_count_27;repeat16_count_27;control_vote_near:0x70082904:j:1;pcrel_slot_near:0x70082904:0x700820f0:near_anchor_landing
  D[8] 0x700827d4 -> generated_template_entry bundle=4 landing=8 template=8 evidence=prop=0x0:unreachable;best_prop=0x700827d4:0x52:insn|no_reorder;zero_size_unreachable_marker;d_generated_stride_family;aligns_top8_phase_rank_1;aligns_top16_phase_rank_2;repeat8_count_27;repeat16_count_27;control_vote_near:0x700827dc:j:1;pcrel_slot_near:0x700827dc:0x70081fca:near_anchor_landing
  D[9] 0x7008276c -> generated_template_entry bundle=4 landing=8 template=8 evidence=prop=0x0:unreachable;best_prop=0x7008276c:0x52:insn|no_reorder;zero_size_unreachable_marker;d_generated_stride_family;aligns_top8_phase_rank_1;aligns_top16_phase_rank_1;repeat8_count_27;repeat16_count_27;control_vote_near:0x70082764:j:1;pcrel_slot_near:0x70082764:0x70081f53:near_anchor_landing
  B[3] 0x70017e28 -> likely_landing_pad_or_slot_target bundle=4 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017e28:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;aligns_top8_phase_rank_2;aligns_top16_phase_rank_4;control_vote_near:0x70017e20:j:1;pcrel_slot_near:0x70017e20:0x7001760d:near_anchor_landing
  B[5] 0x70017e12 -> likely_landing_pad_or_slot_target bundle=4 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017e12:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;aligns_top8_phase_rank_3;aligns_top16_phase_rank_2;control_vote_near:0x70017e10:j:1;pcrel_slot_near:0x70017e10:0x700175fd:near_anchor_landing
  A[3] 0x70017da4 -> likely_landing_pad_or_slot_target bundle=2 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017da4:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;aligns_top16_phase_rank_3;control_vote_near:0x70017da0:j:1;pcrel_slot_near:0x70017da0:0x7001758d:near_anchor_landing
  B[1] 0x70017e3e -> likely_landing_pad_or_slot_target bundle=2 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017e3e:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;aligns_top8_phase_rank_4;control_vote_near:0x70017e40:j:1;pcrel_slot_near:0x70017e40:0x7001762c:near_anchor_landing
  C[8] 0x70017cf4 -> likely_landing_pad_or_slot_target bundle=2 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017cf4:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;aligns_top16_phase_rank_3;control_vote_near:0x70017cf0:j:1;pcrel_slot_near:0x70017cf0:0x700174de:near_anchor_landing
  C[10] 0x70017cde -> likely_landing_pad_or_slot_target bundle=2 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017cde:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;aligns_top8_phase_rank_4;control_vote_near:0x70017ce0:j:1;pcrel_slot_near:0x70017ce0:0x700174ce:near_anchor_landing
  A[10] 0x70017d57 -> likely_landing_pad_or_slot_target bundle=0 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017d57:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;control_vote_near:0x70017d58:j:1;pcrel_slot_near:0x70017d58:0x70017547:anchor_adjacent_landing
  A[11] 0x70017d4c -> likely_landing_pad_or_slot_target bundle=0 landing=12 template=0 evidence=prop=0x0:data|unreachable|no_transform;best_prop=0x70017d4c:0xb:insn|data|no_reorder|no_transform;zero_size_unreachable_marker;abc_minus_0xb_stride;control_vote_near:0x70017d48:j:1;pcrel_slot_near:0x70017d48:0x70017537:near_anchor_landing
* Candidate bundle/control starts:
  C[1] 0x700169a4 -> candidate_bundle_start bundle=7 landing=0 template=0 evidence=prop=0x8:insn|data|no_reorder|no_transform;explicit_insn_prop_ge_8;aligns_top16_phase_rank_3;special_c1_control_anchor
  D[16] 0x7008246b -> generated_template_entry bundle=5 landing=0 template=8 evidence=prop=0x5f:insn|no_reorder;explicit_insn_prop_ge_8;d_generated_stride_family;aligns_top8_phase_rank_2;repeat8_count_27;repeat16_count_27
  D[23] 0x7008221e -> generated_template_entry bundle=5 landing=0 template=8 evidence=prop=0x5f:insn|no_reorder;explicit_insn_prop_ge_8;d_generated_stride_family;aligns_top8_phase_rank_4;repeat8_count_27;repeat16_count_27
  D[26] 0x700820be -> generated_template_entry bundle=5 landing=0 template=8 evidence=prop=0x5f:insn|no_reorder;explicit_insn_prop_ge_8;d_generated_stride_family;aligns_top8_phase_rank_4;repeat8_count_27;repeat16_count_27
  D[30] 0x70081f5e -> generated_template_entry bundle=5 landing=0 template=4 evidence=prop=0x5f:insn|no_reorder;explicit_insn_prop_ge_8;d_generated_stride_family;aligns_top8_phase_rank_4
  D[1] 0x70082aac -> generated_template_entry bundle=4 landing=3 template=8 evidence=prop=0x0:unreachable;best_prop=0x70082aac:0x52:insn|no_reorder;zero_size_unreachable_marker;d_generated_stride_family;aligns_top8_phase_rank_1;aligns_top16_phase_rank_1;repeat8_count_27;repeat16_count_27
  D[2] 0x70082a44 -> generated_template_entry bundle=4 landing=3 template=8 evidence=prop=0x0:unreachable;best_prop=0x70082a44:0x52:insn|no_reorder;zero_size_unreachable_marker;d_generated_stride_family;aligns_top8_phase_rank_1;aligns_top16_phase_rank_2;repeat8_count_27;repeat16_count_27
  D[3] 0x700829dc -> generated_template_entry bundle=4 landing=3 template=8 evidence=prop=0x0:unreachable;best_prop=0x700829dc:0x52:insn|no_reorder;zero_size_unreachable_marker;d_generated_stride_family;aligns_top8_phase_rank_1;aligns_top16_phase_rank_1;repeat8_count_27;repeat16_count_27

## Template Families

* `D_stride_minus_0x68`: count=12 addr=0x70082634..0x70082aac fixed32=29 mod8=4:12
  mask32=`ce b0 01 03 98 fc 00 08 08 02 40 02 40 08 04 02 f0 20 00 f0 20 00 ?? ?? ?? 92 02 0d a0 a8 74 a0`
* `A_minus_0xb_landing_series`: count=11 addr=0x70017d4c..0x70017dba fixed32=6 mod8=0:1 1:1 2:2 3:1 4:2 5:1 6:1 7:2
  mask32=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? 70 ?? ?? ?? ?? 01 ?? ?? ?? ?? ?? 70 ?? ?? ?? ?? 01 ?? ??`
* `B_minus_0xb_landing_series`: count=11 addr=0x70017dd0..0x70017e3e fixed32=3 mod8=0:2 1:1 2:1 3:2 4:1 5:1 6:2 7:1
  mask32=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fa ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??`
* `C_minus_0xb_landing_series`: count=10 addr=0x70017cd3..0x70017d36 fixed32=8 mod8=0:1 1:1 2:1 3:2 4:1 5:1 6:2 7:1
  mask32=`?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fb ?? ?? 70 ?? ?? ?? ?? 01 ?? ?? fb ?? ?? 70 ?? ?? ?? ?? 01 ?? ??`
* `D_stride_minus_0x75`: count=9 addr=0x70081f5e..0x700825cc fixed32=17 mod8=0:2 3:3 4:2 6:1 7:1
  mask32=`?? b0 ?? 03 ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? 04 02 f0 20 00 f0 20 00 ?? ?? ?? 92 02 0d a0 a8 74 a0`
* `D_shared_0x70081ec5`: count=3 addr=0x70081ec5..0x70081ec5 fixed32=32 mod8=5:3
  mask32=`1c 25 65 06 1e be 6a 66 25 5e 64 10 00 30 d0 05 8c f4 06 04 02 1d f0 00 00 00 00 00 00 22 a2 00`
* `D_stride_minus_0x76`: count=3 addr=0x70082048..0x7008221e fixed32=29 mod8=0:1 6:2
  mask32=`ce b0 01 03 98 fc 00 08 08 02 40 02 40 08 04 02 f0 20 00 f0 20 00 ?? ?? ?? 92 02 0d a0 a8 74 a0`
* `D_stride_minus_0x77`: count=3 addr=0x7008237f..0x700824e2 fixed32=29 mod8=2:1 3:1 7:1
  mask32=`ce b0 01 03 98 fc 00 08 08 02 40 02 40 08 04 02 f0 20 00 f0 20 00 ?? ?? ?? 92 02 0d a0 a8 74 a0`
* `A_misc`: count=1 addr=0x70017d41..0x70017d41 fixed32=32 mod8=1:1
  mask32=`84 19 70 84 80 d8 89 01 c6 17 fb f4 1a 70 f4 00 b4 f9 01 06 15 fb 84 1a 70 84 a0 a3 89 01 46 12`
* `B_misc`: count=1 addr=0x70017dc5..0x70017dc5 fixed32=32 mod8=5:1
  mask32=`84 18 70 84 20 a6 89 01 c6 f6 fa f4 19 70 f4 d0 b7 f9 01 06 f4 fa 84 19 70 84 70 a5 89 01 46 f1`
* `C_misc`: count=1 addr=0x70017cc8..0x70017cc8 fixed32=32 mod8=0:1
  mask32=`f4 17 70 f4 c0 70 f9 01 06 36 fb 84 18 70 84 b0 84 89 01 46 33 fb f4 18 70 f4 90 72 f9 01 86 30`
* `C_special_0x700169a4`: count=1 addr=0x700169a4..0x700169a4 fixed32=32 mod8=4:1
  mask32=`f4 17 70 f4 e0 82 f9 01 c0 82 82 60 55 82 30 bb 82 40 3d 82 70 4d 82 b0 33 82 50 44 82 1e 8a 16`
* `D_misc`: count=1 addr=0x70081ee7..0x70081ee7 fixed32=32 mod8=7:1
  mask32=`ce b0 01 03 98 fc 00 08 08 02 40 02 40 08 04 02 f0 20 00 f0 20 00 25 ff 11 b2 02 0d a0 a8 74 a0`

## Top Repeated Raw Chunks

* `flk_dense` width=8:
  count=27 sha1=9b4e13ec chunk=`31a88537f8060402` samples=0x70016cb4 0x70016e82 0x70016eb2 0x70016fef 0x700170fb 0x7001713b 0x7001714b 0x7001715b 0x7001716b 0x70017183 0x700171c3 0x700171d3
  count=17 sha1=022d3917 chunk=`30d08538d8060402` samples=0x70016bf7 0x70016d65 0x70016f1f 0x70016f2f 0x70016f47 0x70017093 0x700170a3 0x700170b3 0x70017333 0x700174c9 0x700175f8 0x70017608
  count=16 sha1=05fe4057 chunk=`0000000000000000` samples=0x70017c60 0x70017c61 0x70017c62 0x70017c63 0x70017c64 0x70017c65 0x70017c66 0x70017c67 0x70017c68 0x70018182 0x70018183 0x70018184
  count=15 sha1=1d4976b4 chunk=`d724000240080402` samples=0x70016e42 0x70016e52 0x70016f6f 0x70016fb7 0x70016fc7 0x700170c3 0x700170db 0x7001729d 0x7001730b 0x70017356 0x7001780c 0x7001784b
  count=12 sha1=9b54ee36 chunk=`a40070e40070b400` samples=0x70017e4d 0x70017e7c 0x70017eab 0x70017f05 0x70017f35 0x70017f65 0x70017feb 0x7001801a 0x70018049 0x700180d0 0x700180f9 0x70018128
  count=12 sha1=4dbf4ecb chunk=`0070e40070b40070` samples=0x70017e4e 0x70017e7d 0x70017eac 0x70017f06 0x70017f36 0x70017f66 0x70017fec 0x7001801b 0x7001804a 0x700180d1 0x700180fa 0x70018129
* `flk_dense` width=16:
  count=11 sha1=7a53b1e5 chunk=`a40070e40070b4007084f07fd40070d4` samples=0x70017e4d 0x70017e7c 0x70017eab 0x70017f05 0x70017f35 0x70017f65 0x70017feb 0x7001801a 0x70018049 0x700180f9 0x70018128
  count=9 sha1=4c31ad25 chunk=`1df0a40070e40070b4007084f07fd400` samples=0x70017e4b 0x70017e7a 0x70017ea9 0x70017f03 0x70017fe9 0x70018018 0x70018047 0x700180f7 0x70018126
  count=9 sha1=fc1962bd chunk=`f0a40070e40070b4007084f07fd40070` samples=0x70017e4c 0x70017e7b 0x70017eaa 0x70017f04 0x70017fea 0x70018019 0x70018048 0x700180f8 0x70018127
  count=8 sha1=153ecef7 chunk=`e008000c421df0a40070e40070b40070` samples=0x70017e75 0x70017ea4 0x70017efe 0x70017fe4 0x70018013 0x70018042 0x700180f2 0x70018121
  count=8 sha1=3e81ad27 chunk=`08000c421df0a40070e40070b4007084` samples=0x70017e76 0x70017ea5 0x70017eff 0x70017fe5 0x70018014 0x70018043 0x700180f3 0x70018122
  count=8 sha1=95fa305d chunk=`000c421df0a40070e40070b4007084f0` samples=0x70017e77 0x70017ea6 0x70017f00 0x70017fe6 0x70018015 0x70018044 0x700180f4 0x70018123
* `ann_dense` width=8:
  count=56 sha1=7a9a9920 chunk=`0402f02000f02000` samples=0x70081ef5 0x70081f2a 0x70081f6c 0x70081fa1 0x70081fe1 0x70082016 0x70082056 0x7008208b 0x700820cc 0x70082101 0x70082141 0x70082176
  count=55 sha1=544d7b91 chunk=`40080402f02000f0` samples=0x70081ef3 0x70081f28 0x70081f9f 0x70081fdf 0x70082014 0x70082054 0x70082089 0x700820ca 0x700820ff 0x7008213f 0x70082174 0x700821b4
  count=55 sha1=bf497d54 chunk=`080402f02000f020` samples=0x70081ef4 0x70081f29 0x70081fa0 0x70081fe0 0x70082015 0x70082055 0x7008208a 0x700820cb 0x70082100 0x70082140 0x70082175 0x700821b5
  count=28 sha1=9ec42af2 chunk=`0240080402f02000` samples=0x70081ef2 0x70081f9e 0x70081fde 0x70082053 0x700820c9 0x7008213e 0x700821b3 0x70082229 0x7008229e 0x70082313 0x7008238a 0x700823ff
  count=28 sha1=bcf5e1dd chunk=`84f07f84a4580f90` samples=0x70081f35 0x70081fac 0x70082021 0x70082096 0x7008210c 0x70082181 0x700821f6 0x7008226c 0x700822e1 0x70082356 0x700823cd 0x70082442
  count=28 sha1=18eb9469 chunk=`f07f84a4580f9001` samples=0x70081f36 0x70081fad 0x70082022 0x70082097 0x7008210d 0x70082182 0x700821f7 0x7008226d 0x700822e2 0x70082357 0x700823ce 0x70082443
* `ann_dense` width=16:
  count=28 sha1=7cdb8286 chunk=`84f07f84a4580f90010e185483009450` samples=0x70081f35 0x70081fac 0x70082021 0x70082096 0x7008210c 0x70082181 0x700821f6 0x7008226c 0x700822e1 0x70082356 0x700823cd 0x70082442
  count=28 sha1=3ab79973 chunk=`f07f84a4580f90010e18548300945044` samples=0x70081f36 0x70081fad 0x70082022 0x70082097 0x7008210d 0x70082182 0x700821f7 0x7008226d 0x700822e2 0x70082357 0x700823ce 0x70082443
  count=28 sha1=7157390e chunk=`7f84a4580f90010e185483009450441e` samples=0x70081f37 0x70081fae 0x70082023 0x70082098 0x7008210e 0x70082183 0x700821f8 0x7008226e 0x700822e3 0x70082358 0x700823cf 0x70082444
  count=28 sha1=7c17e76f chunk=`84a4580f90010e185483009450441eb2` samples=0x70081f38 0x70081faf 0x70082024 0x70082099 0x7008210f 0x70082184 0x700821f9 0x7008226f 0x700822e4 0x70082359 0x700823d0 0x70082445
  count=28 sha1=d76b61af chunk=`a4580f90010e185483009450441eb29b` samples=0x70081f39 0x70081fb0 0x70082025 0x7008209a 0x70082110 0x70082185 0x700821fa 0x70082270 0x700822e5 0x7008235a 0x700823d1 0x70082446
  count=28 sha1=16d594d3 chunk=`580f90010e185483009450441eb29bc6` samples=0x70081f3a 0x70081fb1 0x70082026 0x7008209b 0x70082111 0x70082186 0x700821fb 0x70082271 0x700822e6 0x7008235b 0x700823d2 0x70082447

## Interpretation Rules

* A high phase score means raw chunk repetition and anchor alignment agree; it does not prove semantic decoding.
* Low anchor-on-boundary counts with strong target stride suggest dispatch targets may be landing pads inside bundles.
* Stable byte masks identify candidate format/register/immediate fields for a later minimal decoder.
