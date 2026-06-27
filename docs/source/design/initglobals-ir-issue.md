# INITGLOBALS Chunk and ZP String/Array Initialization in the IR

## The Problem

The IR codegen pipeline never emitted runtime init instructions for non-BSS variables
(such as zero-page variables) that have string or array initialization values.
The existing mechanisms only handled numeric init values:

- **Non-romable mode**: Numeric init values are pulled into the symbol table
  (`changeGlobalVarInits`) and the corresponding AST assignment is removed.
  String/array init values remain as AST assignments at the block level, BUT
  these are NOT separate `PtAssignment` nodes -- they are embedded in the
  variable declaration itself. So the loop at `IRCodeGen.kt:35-42`, which
  collects block-level `PtAssignment` nodes and translates them into
  `globalInits` IR instructions, finds nothing for string/array init values.

- **Romable mode**: BSS variables with numeric init values get `LOAD`+`STOREM`
  instructions in `globalInits`. BSS variables with string/array init values
  are left as readonly inline data (no init instructions). Non-BSS variables
  (including ZP) are not processed at all.

The Virtual Machine works around this by reading init values directly from the
`<INIT>` section variable declarations during `varsToMemory()`. For a future
6502 backend built on IR, no such mechanism would exist -- zero-page variables
with string/array init values would silently have zeroed/bogus data at runtime.

## Affected Variables

Variables that are:
- NOT in BSS (`inBss == false`)
- Have a ZP wish (`zpwish == PREFER_ZEROPAGE` or `REQUIRE_ZEROPAGE`)
- Have a non-numeric init value (`IRVariableInitializer.Str` or `.Array`)
- Are NOT split word arrays (those are handled as separate `_lsb`/`_msb` vars)

These are typically block-level variables declared with the `@zp` tag and an
initialization value such as:
```prog8
str @zp msg = "hello from zp"
ubyte[5] @zp counts = [10, 20, 30, 40, 50]
uword[3] @zp values = [1000, 2000, 3000]
```

## Current Fix

The `generateStringArrayInits()` method in `IRCodeGen.kt` iterates over all
IR symbol table variables matching the criteria above. For each one it:

1. Creates a non-ZP "shadow" variable named `<original>_init_value` containing
   the raw init bytes as a `ubyte[N]` array in the `<INIT>` section.
2. Clears the original ZP variable's init value so it moves to the
   `<NOINIT>` section and is zeroed at startup.
3. Emits a `SYSCALL MEMCOPY` into the `INITGLOBALS` chunk to copy the shadow
   data into the ZP variable at runtime.

For example, a 14-byte ZP string `"hello from zp"` generates:
```asm
load.w r1,main.msg_init_value
load.w r2,main.msg
load.w r3,#14
syscall $1019(r1.w,r2.w,r3.w)
```

This mirrors the approach used in the 6502 code generator
(`ProgramAndVarsGen.kt:628-682`), where shadow `_init_value` data arrays are
copied into zero-page variables at program startup.

### Instruction Count

For a ZP array of N bytes, this generates 4 instructions (3 `LOAD.w` + 1
`SYSCALL`). Since all ZP variables combined can occupy at most ~200 bytes, the
worst-case overhead is ~8 instructions. Typical programs add 4-20 instructions,
a large reduction from the previous per-byte approach.

## Implementation Notes

- The shadow variable is added to the IR symbol table during code generation,
  before the IR program is returned. This ensures it is present when the VM's
  `VmVariableAllocator` builds its address map.
- The original variable's init value is cleared so a future 6502 backend can
  place it in zero-page RAM rather than treating it as ROM-initialized data.
- `IRUnusedCodeRemover.removeBlockInits()` had to be fixed so that its
  "stray load" cleanup computes register uses from `fcallArgs` as well as
  from `reg1`/`reg2`. Without this fix, the `LOAD.w` instructions feeding the
  `SYSCALL MEMCOPY` were incorrectly identified as dead and removed, taking
  the referenced variables with them.

## Future Work

- Consider adding a `zpAddress` field to `IRStStaticVariable` (see
  `zp-ir-issue.md`) so that ZP allocation is explicit in the IR and the
  backend doesn't need to re-derive it from the variable name.
