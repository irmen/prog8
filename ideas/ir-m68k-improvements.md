# IR Improvements for m68k Codegen

This plan collects obvious, low-risk improvements to the Intermediate Representation (IR) that directly benefit the m68k backends (`amiga500`, `qemu68k`). The IR is currently 6502-centric (2-byte pointers, byte index, flat `p8_regfile` memory model, pessimistic status flags). Each item below removes a 6502 tax that the m68k backend currently works around with extra moves, shifts, or memory traffic.

Related documents:
- `ideas/scaled-indexing-IR.md` - full design for #1
- `ideas/m68k-register-allocation.md` - full design for #3.10 (deferred)
- `ideas/m68k-stack-memory-model.md` - stack frame for #3.10
- `ideas/remove_array_pointer_plan.md` - split-word cleanup
- `docs/source/todo.rst:7,43-58` - open IR/m68k TODOs
- `intermediate/src/prog8/intermediate/IRInstructions.kt` - opcode and contract definition
- `codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt` - IR emission
- `codeGenM68k/src/prog8/codegen/m68k/` - m68k lowering

---

## 1. Goals and non-goals

Goals:
- No breaking change to user Prog8 programs.
- IR changes are target-neutral where possible; m68k simply exploits the richer info, 6502/VM ignore or use a cheap fallback.
- Keep 6502 impact neutral or positive - old `codeGenCpu6502` (SimpleAST) is unaffected, `codeGenNew6502` (IR) gets a cheap fallback per item (see section 5).
- `.p8ir` format may change freely (no backward compat needed).
- Each improvement measurable via `prog8c -target virtual -compareir` and `asm` line counts / `AsmOptimizer` counts.

Non-goals:
- New language features.
- Changing the Prog8 calling convention for normal subs (still param-variables, no stack args).
- Replacing the whole IR - incremental enrichment only.

---

## 2. Current IR limitations that hurt m68k

| Area | IR today | Cost on m68k | Reference |
|------|----------|--------------|-----------|
| Index scaling | Pre-scaled byte offset in `LOADX/STOREX/STOREZX` | Blocks 68020 `(An,Xn*2/4)`, forces `lsl` on Amiga500 | `IRCodeGen.kt:2228,2243,2263` `InstrLoadStore.kt:84` |
| Index width | Byte index (6502 X), guessed from `POINTER_MEM_SIZE` | Extra `moveq/move.w` + guessing workaround `indexGuessRegs` | `IRInstructions.kt:1053` `IRProgram.kt:318` `todo.rst:46` |
| Register model | Flat `p8_regfile` BSS, monotonic `RegisterPool` | Every ALU op = `load mem / op / store mem` (2 extra moves) | `AsmGen.kt:100,157,188` |
| Status flags | Only `CMP/CMPI/SGN/BITTST` set Z/N/C | Redundant `cmpi #0` before every branch, `andi #$ee` for X/C sync | `IRInstructions.kt:52-91,592` `IRPeepholeOptimizer.kt:372` |
| Pointer type | `POINTER` aliases `WORD` in `InstructionFormat` | `equalsSize` treats POINTER as WORD, scattered `POINTER_MEM_SIZE` branches | `IRInstructions.kt:699` `DataTypes.kt:48` `todo.rst:24` |
| Byte slices | `LSIGB/MSIGB/LSIGW/BSIGB/MIDB/CONCAT` | Shifts/masks via `p8_regfile` byte lanes, big-endian `regAddrByte` | `IRInstructions.kt:326` `AsmGen.kt:193,205` |
| Addressing | Only absolute / `(a0,d0.w)` / `(a0,off 0-65535)` | No `(An)+`, `-(An)`, `d16(An)` -> loops stay as `loadx+inc+cmp` | `InstrLoadStore.kt:104` |
| Extension | `ext b->w`, `ext w->l` + `extl b->l` (`extl` is `b->l` in 1 IR step) | `byte mul/div` still widens via `and #$ff` instead of `ext`/`extl`; `m68k` `extb.l` needs `M68000` 2-insn fallback | `IRInstructions.kt:194` `InstrArithmetic.kt:370` `AsmGen.kt:407` |
| Div/mod | `divmodr` exists but `DIV+MOD` not fused | Two `divs.l` instead of one `divsl.l d1:d0` | `IRInstructions.kt:228` `PeepholeOptimizer` |
| Float | Separate `fr` regfile, bounce via `fp0/fp1` + `p8_fregfile` | Every float op = 4 `fmove.s` through memory | `AsmGen.kt:172` `InstrLoadStore.kt:268` |

---

## 3. Improvements (prioritized)

### P0 - High impact, isolated, already designed

#### 3.1 Scaled index operand for LOADX/STOREX/STOREZX

Add `scale:Int=1` to `IRInstruction` (`IRInstructions.kt:918`), allowed only on the three indexed ops, serialized as `,S=N` in `IRFileWriter/IRFileReader` and `Utils.kt:92,248`. Stop pre-multiplying in `loadIndexReg` (`IRCodeGen.kt:2228`) and at all `AssignmentGen.kt:759,802,822,843` `ExpressionGen.kt:647,682` `BuiltinFuncGen.kt:937` sites. VM multiplies (`VirtualMachine.kt:590,666,723`), m68k emits `(a0,d0.w*2/*4)` on 68020 vs `lsl` on 68000, 6502 does `asl` helper.

Closes `todo.rst:7,45`. Full spec in `ideas/scaled-indexing-IR.md`. Also fixes latent loop bug where word/long arrays currently use `len*size` with byte `subq #1`.

Effort: small. Risk: low (mechanical). 6502 impact: 1-2 `asl` for scale 2/4, byte-budget (`AstChecker.kt:1107`) guarantees offset fits in byte (max 254) so no 16-bit carry - net neutral.

6502 fallback: `InstrLoadStore.kt:675` helper multiplies byte index by `scale` via `asl` / `asl;asl` / `TODO` for scale 5, `labelSymbolOffset` folds into base via `resolveAddress`.

#### 3.2 Fix index register type guessing -- Completed

`IRInstructions.kt:1053-1085` `determineReg2Type` guesses `WORD` vs `BYTE` from `POINTER_MEM_SIZE` and tracks `indexGuessRegs` to avoid `register given multiple types`. With #3.1, canonicalize the index to `WORD` on 32-bit targets with an explicit `EXT` in `loadIndexReg` before emit, or add explicit `indexRegType` field so `registersUsed()` (`IRProgram.kt:318`) is factual and `regFileLayout` (`AsmGen.kt:164`) sizing is definitive.

Implemented: `codeGenIntermediate/IRCodeGen.kt:2228` `loadIndexReg` now canonicalizes to expected width (`WORD` on 32-bit, `BYTE` on 8-bit) via `EXT` / `LSIGW` / `LSIGB` for `BYTE<->WORD<->LONG` cases, so `LOADX/STOREX/STOREZX` never reuse a mismatched register and `indexGuessRegs` guessing always matches definitive type.

Effort: small. Depends on #3.1 or standalone. 6502 impact: fixes same `register given multiple types` bug on 6502 when `uword` loop counter reused as `loadx.b` index; otherwise neutral.

#### 3.3 Pointer datatype cleanup -- Completed (equalsSize)

Make `IRDataType.POINTER` size = `LONG` on m68k/VM and `WORD` on 6502, fix `InstructionFormat.from()` where `W` registers both `WORD+POINTER` (`IRInstructions.kt:701`), fix `equalsSize` (`DataTypes.kt:48`, `todo.rst:24`). Remove `ARRAY_SPLITW` split-word paths for m68k/VM builds. Removes `CONCAT/_lsb/_msb` handling on m68k and every `if (POINTER_MEM_SIZE>2)` branch in `IRCodeGen.kt:34,42,693`, `AssignmentGen.kt`, `BuiltinFuncGen.kt:873`, `IRUnusedCodeRemover.kt:83`.

Implemented (equalsSize): `DataTypes.kt:41` fixed `BaseDataType.equalsSize` - `POINTER` now `equalsSize` `WORD` and `LONG` (and vice versa) so `POINTER` 4B on m68k/VM correctly equals `LONG` and 2B on 6502 equals `WORD`; `InstructionFormat` alias `W->POINTER` kept for now (format same for `WORD`/`POINTER`), `ARRAY_SPLITW` already non-6502 returns `arrayFor` (no split), remaining `POINTER_MEM_SIZE` branches for `wordArrayIndex`/`indexRegType` kept (correct for `WORD` index on 32-bit) - full `W`/`P` split to be done with #3.1 `P` typespec.

Effort: small-medium. Unblocks #3.6. 6502 impact: removes `POINTER==WORD` alias hack (`DataTypes.kt:48`) and scattered `POINTER_MEM_SIZE` branches for both targets; `ARRAY_SPLITW` stays explicit for 6502, removed for m68k/VM.

### P1 - Low hanging fruits (continued)

#### 3.4 Status flag / branch fusion -- Completed

Contract `IRInstructions.kt:52-91` says only `CMP/CMPI/SGN/BITTST/PUSHST/POPST` set flags (`OpcodesThatSetStatusbits:592`), so IR emits `cmpi #0 / bsteq` before every `if/while/for` (`IRCodeGen.kt:739,838`, `ExpressionGen.kt:272`). On 68000 `move/add/sub/and` already set Z/N.

Implemented: added `OpcodesThatSetZeroFlagOnM68k` (`IRInstructions.kt:603`) - expanded Z/N set on m68k (LOAD/LOADM/LOADX/LOADI/INC/DEC/ADDR/ADD/SUB/AND/OR/XOR/INV/EXT/MUL/DIV/SHIFTx/LSIG/MSIG/CONCAT plus the strict set). All branch emission sites now gate on `CpuType.statusBitsOnMultiByteOps` (`IRCodeGen.kt:1519,1570`, `ExpressionGen.kt:276,1384`): on m68k a `CMPI #0` before `BSTEQ/BSTNE` is skipped when the previous instruction is in the expanded set and compares against zero; peephole `IRPeepholeOptimizer.kt:387` also uses the expanded set (gated via same flag at emission time). On 6502 `statusBitsOnMultiByteOps=false` so the pessimistic `CMPI` is always emitted. Further work (composite `CMP+branch`, `SGN+tst`, `ROL` X/C) left for follow-up.

Effort: small (statusBits gate + expanded set). 6502 impact: none (guarded by `statusBitsOnMultiByteOps=false`).

#### 3.5 Replace byte-slice ops with proper trunc/extract

`LSIGB/MSIGB/LSIGW/BSIGB/MIDB/CONCAT` (`IRInstructions.kt:326-333,516,892`, `AsmGen.kt:205-251`, `InstrControl.kt:327`) use memory-offset semantics for what on m68k is shifts/masks. With #3.10 (values in Dn) they become `move.b / lsr.w #8 / swap / and`. IR alternative: `TRUNC`/`EXTRACT` with shift/mask semantics, or keep opcodes but let register-allocated path emit `lsl/lsr/and` without `p8_regfile` round-trip. Already partly handled by `AsMOptimizer.optimizeMsigbSpill:350`.

Effort: medium. Depends on #3.10 for max benefit. 6502 impact: neutral - 6502 lacks wide registers so `LSIGB/CONCAT` keep memory-offset semantics; register path (`lsl/lsr/and`) only taken when allocated to Dn which 6502 rarely does. `AsMOptimizer.optimizeMsigbSpill:350` already handles 6502.

### P2 - Medium impact, follow-ups

#### 3.6 Post-increment / displacement / memcopy -- Completed (post-inc only)

No `(An)+`, `-(An)`, `d16(An)` in IR. Pointer traversal is `LOADI/STOREI` + `ADDR`, loops are `loadx+inc+cmp`. Add `LOAD_INC/STORE_INC` or annotate sequential `LOADX`, or introduce higher-level `MEMCOPY` IR (`todo.rst:55` HLIR). Lets m68k emit `move.w (a0)+,d0` / `movem` and `AsmOptimizer.optimizeDbraRepeatLoops:386` gets `repeat N` semantics directly instead of pattern-matching `move #N / subq / bne`.

Implemented (post-inc): added `LOADP_INC`/`STOREP_INC` (`IRInstructions.kt:103,359,766`, `Utils.kt:89` `([a-z_]+)`), VM `VirtualMachine.kt:266`, m68k `InstrLoadStore.kt:100` lowering to `move.s (a0)+`, 6502 `InstrLoadStore.kt:81` fallback via `$22`, peephole `IRPeepholeOptimizer.kt:60` `fusePointerPostInc` fusing `loadm+loadi/storei+incm` on same pointer variable -> single post-inc op. Displacement `d16(An)`, `-(An)` and `MEMCOPY` HLIR remain future work. Test `TestM68k.kt:271` verifies `loadp_inc`/`storep_inc` and `(a0)+`.

Effort: medium (post-inc small, rest medium). 6502 impact: minimal - `loadp_inc` lowers to `$22` indirect + `inc` fallback, no `(An)+` on 6502, but `MEMCOPY` HLIR still future.

#### 3.7 Typed extension and widening (single type specifier only - no `ext.b.l` syntax) -- Completed

`IRInstruction` has a single `type` (src) `IRInstructions.kt:936`; dst is implied by `Opcode`: `EXT/EXTS B`=`b->w`, `EXT/EXTS W`=`w->l` (`IRInstructions.kt:834`), `EXTL/EXTLS B`=`b->l` in 1 IR step (`IRInstructions.kt:836`). The `b->l` "2 steps" is `M68000` machine fallback `ext.w; ext.l` (`AsmGen.kt:407` `emitSignExtendByteToLong`, `68020` has `extb.l`), not IR. Current cost is byte `MULR/DIVR` widening via `and #$ff` instead of `EXT`/`EXTL` (`InstrArithmetic.kt:370-397,459-518`).

Constraint: do not add a second type signifier (`ext.b.l` is forbidden). Fix under single-specifier rule: keep 4 opcodes, make `IRCodeGen.kt:721` `emitWidening` and all `ExpressionGen/BuiltinFuncGen` byte `MUL/DIV` paths always emit the correct `EXT`/`EXTL` (`b->l` via `EXTL`, not `EXT+EXT`); keep `IRPeepholeOptimizer.kt:894` collapse `ext.b+ext.w->extl.b` as safety net; define `mulu.b->w` widening via `EXT` so IR does not truncate.

Implemented: `AssignmentGen.kt:591` fixed `BYTE->LONG`/`POINTER` 32-bit extension - `isPointerLong` + `extOpcode` selects `EXTL/EXTLS` single step (`ubyte->long` `extl.b`, `uword->long` `ext.w`), so `ubyte/word->pointer` on m68k emits correct `LONG` dest; `M68k` byte `MUL/DIV` still via `and #$ff`/`extb.l` backend fallback - no new IR type.

Effort: small. 6502 impact: none - 6502 lowers `EXT`/`EXTL` to `lda #0 / sta hi` (`InstrControl.kt:436`).

#### 3.8 DIVMOD fusion and flag returns -- Low priority (seldom)

Pattern `q=x/10; r=x%10` on same `x`/`d` is rare outside explicit `divmod()` (`BuiltinFunctions.kt:66`, `TestCompilerVirtual.kt:1050`); separate `DIV`+`MOD` stay as 2× `divu.w`/`divs.l` (`InstrArithmetic.kt:625`). Only fuse is explicit `divmod()` -> `DIVMODR` (`IRInstructions.kt:228`). Seldom triggered (e.g. `circles.p8:68` `index%10`/`index/10` separate, `textelite:1003` only `%10`), so low priority. Also `todo.rst:69` `bool @Pz,@Pc` multi-flag returns (`InstrControl.kt:101`).

Effort: small. 6502 impact: small win - 2 helpers -> 1.

#### 3.9 Float constant and register handling

`InstrArithmetic.kt:750` `TODO float ADD without immediate`, `InstrLoadStore` float `loadx` bounce via `FP_ACC/FP_SRC` + `p8_fregfile` (`InstrLoadStore.kt:278`, `AsmGen.kt:172`). With #3.10 keep hot floats in FP2-FP7, materialize float imms via `fmovecr` pool so `TODO` never hit.

Effort: small-medium. 6502 impact: none - float is 5-byte MFLPT via library.

#### 3.10 True register allocation (deferred - largest scope)

Today `AsmGen.kt:100,157-188` spills every vreg to `p8_regfile+off` via `regAddr`. `RegisterPool` never resets per sub, `RegisterPacker.kt` is disabled (`IRCodeGen.kt:101-107`).

IR work: per-sub vreg namespaces (reset `RegisterPool` per sub), class tag `D/A/FP` on vregs or `IRSubroutine` metadata, optional live-interval hints (`IRSubroutine.frameSize`, `IRStFrameSlot` vs `IRStStaticVariable.inBss`). Backend: class-aware graph coloring onto D0-D7/A0-A6/FP0-FP7, `CALL` kills caller-saved D0,D1,A0,A1,FP0,FP1 and preserves D2-D7/A2-A5/FP2-FP7 via `movem.l` prologue/epilogue (`m68k-stack-memory-model.md`). Only spill to `p8_regfile` or system stack under pressure. Immediately removes 2 memory moves per ALU op and enables `dbra`, `mulu.l`, FP residency.

Effort: large, but fully specified in `ideas/m68k-register-allocation.md:1-438`. Phased: liveness+coloring without spilling, then spilling, then prologue/epilogue. Deferred to after low hanging fruits above.

6502 impact: old `codeGenCpu6502` unaffected. For `codeGenNew6502` the m68k design (D/A/FP files, `movem`) degrades to ZP placement - 6502 has only A/X/Y. Same uniform `CALL` convention (kill scratch, preserve callee-saved) removes need for call-graph packing, so per-sub vreg reuse shrinks `p8_regfile` BSS from program-wide to worst-sub. Small size win, no regression; full ZP-allocation design left to separate 6502 doc.

---

## 4. Implementation order (low hanging fruits first)

1. #3.1 Scaled index + #3.2 Index type fix + #3.3 Pointer cleanup - independent, small, unblock others.
2. #3.7 Typed EXT - small, immediate m68k `extb.l` win.
3. #3.4 Status flag peephole for m68k (`honorsContract` path) - small.
4. #3.5 Byte slices, #3.6 displacement/memcopy remaining, #3.9 Float - as follow-ups once values are in registers.
5. #3.8 DIVMOD fusion -- low priority (seldom: only explicit `divmod()` today) - defer.
6. #3.10 True register allocation (deferred) - largest scope, do after all IR cleanups.

---

## 5. Impact on 6502 backend

`codeGenCpu6502` (SimpleAST) does not consume IR - unaffected. Impact below is for `codeGenNew6502` (IR). Net code size/speed delta ~0-2% (within noise), no regression risk if P0 fallbacks implemented as specified. Verify with `gradle :codeGenNew6502:test --console=plain` and `prog8c -target virtual -compareir` on `examples/c64/*`.

| Improvement | 6502 cost | 6502 benefit |
|-------------|-----------|--------------|
| #3.1 Scaled index | 1-2 `asl` for scale 2/4, `TODO` for scale 5 (float) | Fixes `setlsb/setmsb`, loop `len*size` bug |
| #3.2 Index width | None | Fixes `register given multiple types` bug |
| #3.3 Pointer cleanup | None | Removes `POINTER==WORD` hack |
| #3.4 Status flags | None (`statusBitsOnMultiByteOps=false`) | None |
| #3.5 Byte slices | Neutral | Keeps 6502 memory-offset path |
| #3.6 Addressing | Minimal | Slight `memcopy` loop improvement |
| #3.7 Typed EXT | None | Explicit `b->w` vs `b->l` |
| #3.8 DIVMOD | None | Two helpers -> one |
| #3.9 Float | None | None |
| #3.10 Reg allocation | ZP placement only (no D/A/FP win) | Shrinks `p8_regfile` BSS, fixes call-graph soundness |

Overall: P0 items ship with explicit 6502 fallback (`asl` helper, `TODO` for scale 5). P1/P2 are either neutral or small wins on 6502; no item pessimizes 6502 beyond the cheap fallback. `TestExecution6502.simulate()` covers scaled word/long access.

---

## 6. Testing

- `intermediate`: `.p8ir` round-trip with `,S=N`.
- VM: `TestCompilerVirtual` word/long/float indexed access, `setlsb`/`setmsb` with offset.
- m68k: `TestM68k` / `TestInstructionSelectionOptimizations` assert `asm` contains `(a0,d0.w*2/*4)` on qemu68k vs `lsl` on amiga500; with #3.10 assert `add.l d1,d0` not `p8_regfile` traffic.
- 6502: `TestExecution6502` `simulate()` for scaled word/long access.
- `gradle :compiler:test --console=plain` and `prog8c -target virtual -compareir` for size/speed deltas.

---

## 7. Out of scope

- `codeGenCpu6502` SimpleAST backend (not IR).
- New Prog8 language features.
- Full HLIR loop/array redesign beyond #3.7.

---

## 8. File reference map

| Concern | File |
|---------|------|
| IR opcodes, formats, status contract, validation | `intermediate/src/prog8/intermediate/IRInstructions.kt` |
| IR text parse/serialize `,S=N` | `intermediate/src/prog8/intermediate/Utils.kt`, `IRFileWriter.kt`, `IRFileReader.kt` |
| IR codegen, `loadIndexReg`, for loops, split words | `codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt` |
| Indexed loads, pointer math | `codeGenIntermediate/src/prog8/codegen/intermediate/ExpressionGen.kt` |
| Scaled stores, `setlsb`/`setmsb` | `codeGenIntermediate/src/prog8/codegen/intermediate/AssignmentGen.kt`, `BuiltinFuncGen.kt` |
| VM execution of indexed ops | `virtualmachine/src/prog8/vm/VirtualMachine.kt`, `VmProgramLoader.kt` |
| m68k load/store, float, `addIndirectOffset` | `codeGenM68k/src/prog8/codegen/m68k/InstrLoadStore.kt` |
| m68k arithmetic, widening, `divmod` | `codeGenM68k/src/prog8/codegen/m68k/InstrArithmetic.kt` |
| m68k branches, calls, `PUSHST`, `CALLFAR` | `codeGenM68k/src/prog8/codegen/m68k/InstrControl.kt` |
| m68k shifts, bit ops, `regAddrByte` | `codeGenM68k/src/prog8/codegen/m68k/InstrBitwise.kt` |
| m68k regfile layout, `regAddr`, `cpu` | `codeGenM68k/src/prog8/codegen/m68k/AsmGen.kt` |
| Peephole, `removeNeedlessCompares`, `collapseConversions` | `codeGenIntermediate/src/prog8/codegen/intermediate/IRPeepholeOptimizer.kt` |
| Asm optimizer, `dbra`, `msigb` spill | `codeGenM68k/src/prog8/codegen/m68k/AsmOptimizer.kt` |
| Pointer size, memsizer | `codeCore/src/prog8/code/core/IMemSizer.kt`, `DataTypes.kt` |
| Array limits, `AstChecker` | `compiler/src/prog8/compiler/astprocessing/AstChecker.kt:1107` |
