# M68k Register Allocation - Stage 1B: Indexed/Indirect Addressing-Mode Re-selection

**Parent design:** `ideas/m68k-register-allocation.md`  
**Prerequisite:** Stage 1A gate green (byte-identical output with empty allocation map).  
**Additional prerequisite:** §7.1 decision #2 (class-constraint mechanism) recorded and implemented. **DONE** — implemented as IR-level annotation: `AllocationHint` on `RegisterOperand`, with role-based default resolution (`resolvedAllocationHint` on `RegisterOperand`; `parseRegisterOperand` derives the hint from the slot role).  
**Status:** Implemented (Stage 1B complete). Empty-map output byte-identical.  
**Gate to next stage:** With an empty allocation map, generated assembly remains byte-identical to pre-change output for the test corpus. Compiler builds and tests pass.

## Background

Stage 1A made instruction selection location-agnostic for ordinary operands:
`operand()` returns either a hardware register name or a regfile address. Most
translators only needed that string swap.

A minority of opcodes do not fit that pattern. They *select* addressing modes
around hardcoded scratch registers (`d0` for indices, `a0` for pointers) rather
than merely naming an operand. With a real allocator, an index vreg may live in
any data register and a pointer vreg in any address register; the translator
must emit the addressing mode that matches the allocated register.

This is genuine per-opcode re-selection, not a string substitution. It is split
out from Stage 1A so the large mechanical refactor can land independently.

## Affected opcodes

- `LOADX` / `STOREX`: indexed memory access `(base,index*scale)`.
- `LOADI` / `STOREI` / `STOREZX`: indirect memory access through a pointer vreg.
- Float variants that use the same patterns (`emitFloatMemBinary`,
  `emitFloatMemUnary`, etc.): they currently load a pointer into `a0` and read
  through `p8_fregfile`; the same re-selection applies.

## Register-class constraints

The IR already carries `AllocationHint` on `RegisterOperand`:

- `INDEX` role → `PREFER_DATA` (must use a data register for `(An,Dn.w)`).
- `POINTER_BASE` role → `PREFER_ADDRESS` (must use an address register for
  `(An)` / `(off,An)`).
- `INDIRECT_TARGET` role → `PREFER_ADDRESS` (jump/call targets).

The Stage 2 allocator must honor these constraints. Stage 1B assumes the
constraint is available: if the allocator puts an index vreg in a data register
and a pointer vreg in an address register, the translators below produce the
optimal addressing mode. If a vreg is spilled, Stage 1B falls back to the same
scratch-register staging the backend uses today, keeping empty-map output
byte-identical.

## Per-opcode re-selection

### `LOADX` / `STOREX`

Current code (Stage 1A):

```asm
    lea     base, a0
    moveq   #0, d0              ; if byte index
    move.b  p8_regfile+IDX, d0  ; loadIndexToD0 fallback
    move.s  (a0,d0.w), d0
    move.s  d0, p8_regfile+DST
```

Allocated index in `d3`:

```asm
    lea     base, a0
    move.s  (a0,d3.w), d0
    move.s  d0, p8_regfile+DST
```

Allocated index in `d3` with scale 4 on 68020+:

```asm
    lea     base, a0
    move.s  (a0,d3.w*4), d0
```

Scaling must target the allocated data register, not hardcoded `d0`.

### `LOADI` / `STOREI` / `STOREZX`

Current code (Stage 1A):

```asm
    movea.l  p8_regfile+PTR, a0
    move.s   (off,a0), d0
```

Allocated pointer in `a2`:

```asm
    move.s   (off,a2), d0
```

### Float memory operations

Current code funnels float memory operands through `a0` and `fp0` scratch.
With an allocated pointer in `a2`:

```asm
    fmove.s  (off,a2), fp0
```

The float regfile address fallback stays for spilled pointers.

### Float indexed operations

Float indexed operands also re-select addressing modes. With an allocated index
in `d3` and scale 1:

```asm
    lea     base, a0
    fmove.s (0,a0,d3.l), fp0
```

**Scale handling:** when the index is allocated and the scale is not 1, the
allocated path emits `(0,a0,dX.l*scale)` on 68020+ (hardware-encoded scale).
The non-allocated path still scales correctly in software, keeping empty-map
output byte-identical.

## Implementation order

1. Ensure `AsmGen.operand()` can report whether a `RegisterOperand` is allocated
   to a register of the expected class, or spilled.
2. Add helper `operandAsDataReg(op): String?` returning the register name only
   if allocated to a data register; otherwise `null` (fall back to scratch).
3. Add helper `operandAsAddressReg(op): String?` similarly for address registers.
4. Rewrite `LOADX` / `STOREX` in `InstrLoadStore.kt` to use the allocated index
   register directly when possible, falling back to `loadIndexToD0` when spilled.
5. Rewrite `LOADI` / `STOREI` / `STOREZX` to use the allocated pointer register
   directly when possible, falling back to `loadPointerToA0` when spilled.
6. Rewrite float memory helpers (`emitFloatMemBinary`, `emitFloatMemUnary`, etc.)
   with the same pointer-register direct / scratch fallback pattern.
7. Add tests for allocated and spilled cases.

## Implementation status

**Implemented.** `resolveMemory()` now checks `operandAsDataReg`/`operandAsAddressReg` for
indexed and indirect references. When the index/pointer vreg is allocated to a
register of the correct class, the addressing mode uses it directly (hardware-encoded
scales on 68020+ for indexed (including float); `(off, reg)` for indirect).
Otherwise it falls back to the existing scratch staging (`d0`/`a0`), keeping
empty-map output byte-identical. Float indirect uses the allocated pointer when
available; float indexed uses the allocated data register with hardware scale.
Helpers `operandAsDataReg`/`operandAsAddressReg` added to `AsmGen`.

**Deferred to Stage 2:** Two opcode groups are not yet location-agnostic and
will break once `regAllocation` is populated:

1. **`JUMPI`/`CALLI` memory-indirect addressing on 68020+.** The 68020+ path emits
   `jmp ([operand(reg)])` / `jsr ([operand(reg)])`, which requires an
   address-register base. With an empty map `operand(reg)` is a regfile address
   and the mode is valid; with an allocated data register it is not. Fixing this
   belongs to Stage 2 because it requires the allocator to honor the
   `INDIRECT_TARGET` / `PREFER_ADDRESS` class constraint (or a fallback to `a0`
   staging when the constraint cannot be met).
   **Resolved in Stage 2** by the `a0` staging fallback: `InstrControl.kt` uses
   `jmp ([...])` / `jsr ([...])` only when the target vreg is not in `regAllocation`.

2. **Memory-form word shifts/rotates in `InstrBitwise.kt`.** `LSRM`, `ROLM`,
   `ROXLM`, etc. emit `op.w  operand(reg)`, which is only valid when the operand
   is a memory address. With an allocated data register the instruction is
   misinterpreted as a register-count shift. This requires either a spill policy
   that keeps these operands in memory or instruction re-selection to the
   register form when the operand is allocated — both allocator-stage concerns.
   **Resolved in Stage 2** by the second option: `InstrBitwise.kt` uses the memory
   form only when the operand is not in `regAllocation`; otherwise it uses the
   register form through `d0`.

With an empty allocation map, output must still be byte-identical to Stage 1A.
With a populated map, indexed/indirect operands should use the allocated
registers.

## Compiler-stability gate

- Compiler builds (`gradle :compiler:compileKotlin`).
- Full test suite passes (`gradle build`).
- Byte-identical output for the corpus with an empty allocation map.

Do not start Stage 2 until Stage 1A and Stage 1B gates are both green.
