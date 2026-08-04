# m68k Optimizations & Correctness Review

Audit of the Prog8 compiler for the m68k targets (Amiga500 = M68000,
Qemu68k = M68020). m68k is big-endian, has 32-bit registers, and native
`addq`/`subq`/`tst`/`cmp`/`lsl`/`lsr`/`swap`/`scc`/`dbra` instructions.
Pointers are 4 bytes (`POINTER_MEM_SIZE = 4`).

Two optimizer layers run for every target:
- The compiler-AST optimizer: `codeOptimizers/src/prog8/optimizer/`
  (`StatementOptimizer.kt`, `ExpressionSimplifier.kt`, `Inliner.kt`,
  `ConstantFoldingOptimizer.kt`, `ConstantIdentifierReplacer.kt`,
  `ConstExprEvaluator.kt`, `UnusedCodeRemover.kt`).
- The simplified-AST optimizer: `simpleAst/src/prog8/code/optimize/`
  (`ExpressionOptimizers.kt`, `ComparisonOptimizers.kt`,
  `BooleanOptimizers.kt`, `MemoryOptimizers.kt`, `VariableOptimizers.kt`,
  `ControlFlowOptimizers.kt`).

The IR backend (`codeGenIntermediate`) and the m68k backend
(`codeGenM68k`) then lower the result.

Gating reference: `options.compTarget.cpu.is6502` (6502/65C02 only) or
`cpu.isBigEndian` (m68k only). The `CpuType` enum lives in
`codeCore/src/prog8/code/core/ICompilationTarget.kt`.

---

## 3. New m68k codegen optimization opportunities

Lowering improvements that would shrink m68k output (no correctness risk):

- **`lsl`/`lsr` with immediate count** instead of materializing the count
  into a register (`LSLN`/`LSRN` emit a variable shift). Also
  **`x << 16` / `x >> 16` -> `swap`** (the backend already knows this trick
  in `MSIGW`).
  - Sites that emit the suboptimal `LOAD #N + LSLN/LSRN/ASRN` sequence
    (m68k immediate count 1..8 = single instruction; 6502 unrolls to N
    1-bit shifts): `IRCodeGen.kt:1078, 1106, 1175, 1216`
    (multiplyByConst / divideByConst and their inplace variants),
    `ExpressionGen.kt:1422, 1441` (operatorShiftRight / operatorShiftLeft
    for any non-1 right operand),
    `AssignmentGen.kt:2073, 2132` (in-place array `>>=`/ `<<= const`).
    The peephole optimizer at `IRPeepholeOptimizer.kt:606-619` currently
    only removes `shift by 0`; a new pass `foldShiftByConstant` could
    rewrite these into N 1-bit `LSL`/`LSR`/`ASR` (works on 6502 + m68k)
    or directly into the m68k immediate form.
  - **Recommended structural fix: add immediate-count IR opcodes
    `LSLI` / `LSRI` / `ASRI`** (with a literal count operand, format
    `BWL,>r1,<i`). The IR builder then emits `LSLI rX, #N` directly
    instead of `LOAD rN,#N; LSLN rX,rN`. The m68k backend emits
    `lsl.b #N, d0` (1 instruction, 6+2N cycles) for N=1..8 and
    `lsl.b #8, d0; lsl.b #(N-8), d0` (2 instructions) for N=9..15 on a
    word. The 6502 backend unrolls to N `asl`/`lsr`/`cmp+ror`. The new
    opcodes require changes in: `IRInstructions.kt` (3 new enum values
    + 3 format-map entries), the m68k and 6502 backend opcode handlers
    (in `InstrBitwise.kt` for both), and the IR builder sites listed
    above. The peephole optimizer then just rewrites any leftover
    `LOAD + LSLN` to `LSLI`. This is the cleanest fix because it lets
    the IR express the actual intent (shift by N) instead of forcing
    the backend to either unroll, emit a runtime loop, or have a
    target-specific peephole to re-fuse the unrolled form.
- **`addq`/`subq` for small constants in the register-form ADD/SUB path.**
  The m68k memory form already uses ADDQ/SUBQ for 1..8
  (`InstrArithmetic.kt:72-94, 117-139`) but the **register** form
  (`InstrArithmetic.kt:58-62` ADD, `103-107` SUB) emits
  `add.b #N, d0` / `sub.b #N, d0` for any N. ADDQ/SUBQ are 2 bytes
  vs 4-6 for `add`/`sub` immediate. The gap propagates from the IR:
  `addConstByteToReg` (`IRCodeGen.kt:934-961`) has special cases for
  value 1 (INC) and 2 (INC INC) but falls through to `ADD #N` for
  N = 3..8; many `ExpressionGen`/`AssignmentGen`/`BuiltinFuncGen`
  sites emit `ADD #const` for pointer offsets, field offsets, and
  array indexing. Rotates are not affected — the IR has no
  `ROLN`/`RORN` opcodes and any constant-count rotate is already
  unrolled at IR-build time.

  Low-priority candidates surveyed and rejected:
  - Spill+reload pairs (`move d0, p8_regfile; ...; move p8_regfile, d0`)
    occur ~9 times in 1946 lines, all are required (next op modified
    the value), and eliminating them needs a "d0 still holds last-store"
    tracker.
  - LOAD+cmpi same-reg pairs: 0 occurrences in the IR; the constant
    fast path in `operatorEquals`/`GreaterThan`/`LessThan` already
    folds the LOAD into the cmpi at IR-build time.
  - Same-slot double-writes: 11 occurrences, all from
    `b = cond ? 0 : 1` lowered to if/else with a jump between writes
    (mutually exclusive basic blocks, not redundant).
  - `load + storei` pairs: 0 occurrences in the IR. `storei`
    (register-indirect) is only used for non-constant stores; an
    indirect-immediate variant was considered and rejected as dead
    code (no real peephole target).

---

