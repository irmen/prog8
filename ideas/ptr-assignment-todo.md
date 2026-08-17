# Pointer Assignment TODOs

`codeGenCpu6502/src/prog8/codegen/cpu6502/assignment/PointerAssignmentsGen.kt`
contains many active `TODO()` calls. These are generally compiler crash paths,
not runtime fallbacks: valid source code reaching one of them fails during 6502
code generation with `NotImplementedError`.

## Highest Impact

### Pointer dereference assignment helpers

- Line 47: assign a float from AY to a pointer dereference.
- Lines 813-850: assign float, byte, word, and long values to indexed pointer dereferences.

These helpers are still explicit `TODO()` paths, but representative source
cases such as `pointers[idx]^^ = value` currently compile successfully for the
C64 and CX16 targets. Existing pointer tests also cover byte, word, long, and
float indexed assignments. The actual reachable source construct for these
helpers has not been identified, so their severity is currently unverified
rather than a confirmed compiler crash.

### Pointer-array index scaling

- Lines 503, 520, 592, 610, 709, and 734: multiply an index by the element
  size for pointer-indexed byte, word, long, or struct arrays.

Representative variable-index assignments for byte, word, and long pointees
compile successfully for C64 and CX16 with and without optimization. The
reachable source construct for these helpers has not been identified, so their
severity is currently unverified rather than a confirmed compiler crash.

### Pointer augmented multiplication and division

- Lines 390 and 399: byte `*=` and `/=` through pointers.
- Lines 393 and 402: long `*=` and `/=` through pointers.
- Line 410: byte `%=` through a pointer.

Representative direct pointer dereference operations for byte and long values,
including `*=`, `/=`, and byte `%=`, compile successfully for C64 and CX16 with
and without optimization. The reachable source construct for these helpers has
not been identified, so their severity is currently unverified rather than a
confirmed compiler crash.

### Pointer augmented float operations

- Lines 2005-2007: variable, expression, and register operands for float `+=`
  and `*=`.
- Lines 2258-2260: variable, expression, and register operands for float `-=`
  and `/=`.

Representative direct pointer dereferences using shared variable operands for
`+=`, `*=`, `-=`, and `/=` compile successfully for C64 and CX16 with and
without optimization. The reachable source construct for these helpers has
not been identified, so their severity is currently unverified rather than a
confirmed compiler crash.

## Medium-High Impact

### Pointer augmented operations with register operands

- Line 2056: word subtraction from a register.
- Lines 2348 and 2397: byte addition and subtraction from a register.
- Lines 2440, 2484, 2527, 2571, 2614, and 2658: byte and word XOR, OR, and
  AND from a register.

The corresponding literal, variable, or expression paths are implemented in
many cases. Severity: medium-high, compiler crash for register-source paths.

### Long pointer augmented operations

- Lines 1849 and 1919: unsupported source kinds for long add and subtract.
- Lines 2729, 2800, and 2871: long AND, OR, and XOR from a register.

Severity: medium-high, compiler crash for the affected source forms.

### Struct and large-offset dereferences

- Line 1079: read through an address-of dereference when the final offset is
  greater than 255.
- Line 1100: obtain a struct pointer from a pointer dereference before field
  access.

Small offsets and simpler pointer chains work. Severity: high for large
structures or nested pointer/struct access.

### Pointer comparisons

- Lines 942 and 1065: byte pointer comparisons using `<`, `<=`, `>`, and `>=`.

Equality and inequality are implemented. Severity: medium, compiler crash for
relational comparisons.

### Signed byte pointer shifts

- Line 1543: signed byte right shift through a pointer.

Unsigned shifts and other shift widths have implementations. Severity: medium,
compiler crash for signed `>>=` cases.

## Lower Impact or Non-Functional TODOs

- Lines 2943 and 2978: save and restore combined long register pairs on the
  stack. This is likely an internal register-allocation path and is less likely
  to be reached by ordinary source code, but still crashes if selected.
- Line 472: comment that the zeropage scratch-variable detection is not robust.
  This is a code-quality concern, not an unimplemented operation.
- Line 1122: comment about avoiding an unnecessary zeropage scratch register.
  This is an optimization opportunity, not a correctness issue.
- Lines 412-413: commented-out float and long modulo TODOs. They are inactive
  and have no current effect.

## Overall Assessment

The file contains many explicit `TODO()` calls, so any genuinely reachable
path would fail during compilation rather than silently generating incorrect
machine code. However, representative tests for indexed pointer assignments,
pointer-array index scaling, pointer byte/long multiplication and division, and
pointer float augmented operations all compile successfully for C64 and CX16,
with and without optimization. Those entries are therefore currently latent or
unreachable from the tested source forms, not confirmed user-facing crashes.

The remaining struct, large-offset, comparison, register-source, and stack
paths still need targeted reachability tests before their severity can be
assessed reliably.

Recommended implementation order:

1. Identify reachable source constructs for the remaining TODOs.
2. Add regression tests for confirmed failures.
3. Implement confirmed pointer and struct gaps.
4. Reassess or remove TODOs that remain unreachable.
