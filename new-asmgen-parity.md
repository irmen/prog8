# New 6502 Codegen Parity Plan

Goal: The new codegen (`new6502gen`) produces functionally equivalent assembly code to the old codegen (`codeGenCpu6502`).

The fact that the new codegen produces much larger code due to the virtual register file abstraction is a known issue to be addressed later. This plan focuses on correctness and functional equivalence.

## Current Differences (test.p8 "hello world" comparison)

| Aspect | Old Codegen (orig/) | New Codegen (new6502gen) | Status |
|--------|---------------------|--------------------------|--------|
| **Zero page** | Decimal (`=122`) | Decimal (`=122`) | FIXED |
| **Strings** | `.byte $68, $65, ...` (hex) | `.byte $68,$65,...` (hex via `asmHexByte()`) | FIXED |
| **Addresses/align** | `toHex()` (0-15 decimal, >=16 `$xx`) | `toHex()` (same as old codegen) | FIXED |
| **Value formatting** | Inline `$` + `.toString(16)` everywhere | Centralized via `toHex()` / `asmHexByte()` | FIXED |
| **Arguments** | Direct in A/Y registers | Via `p8_regfile` (virtual register file) | separate issue |
| **Startup init** | `init_system_phase2` only | `init_system` + `init_system_phase2` | HIGH |
| **BSS clearing** | Inlined in user program (`main.start`) | Separate `program_startup_clear_bss` call | OK |
| **Global inits** | Not present (empty) | `run_global_inits` (just `rts`) | OK |
| **Cleanup** | Complex `cleanup_at_exit` (bank switch, CLRCHN, charset) | Simple `jsr sys.poweroff_system` | HIGH |
| **Poweroff** | Direct I2C opcodes | Through register file | separate issue |
| **Constants** | Scoped names under their blocks | Both `p8c_` mangled + scoped names at file scope | cosmetic |
| **BSS** | Tiny (4 bytes: exitcode, irqvec, orig_sp) | 403 bytes (mostly `.fill 400` register file) | separate issue |
| **Code size** | Ends at $0903 (~660 bytes) | Ends at $0b15 (~1812 bytes) | separate issue |
| **Redundant loads** | Minimal | Lots of regfile store/load pairs | separate issue |

Statuses:
- **FIXED** — resolved, matches old codegen
- **HIGH** — must be fixed for functional parity (program crashes or wrong behavior)
- **OK** — functionally fine, different approach
- **cosmetic** — output formatting, labels, debug info (lower priority)
- **separate issue** — the virtual register file bloat is a known larger refactor

## Detailed Analysis

### 1. Startup Init Sequence (HIGH)

**Old codegen** calls only `init_system_phase2` from the program startup:
```
jsr  p8_sys_startup.init_system_phase2
```

**New codegen** calls both `init_system` and `init_system_phase2`:
```
jsr  p8_sys_startup.init_system
jsr  p8_sys_startup.init_system_phase2
```

`init_system` (from `p8_sys_startup` in library) does additional setup: mouse disable, audio init, IOINIT, RESTOR, CINT, mode restore, screen clear. Phase2 saves IRQ vectors and sets RAM/ROM banks.

**Fix:** The new codegen calling both is actually more complete. The old codegen's `init_system_phase2` only saves IRQ vectors. The old codegen relied on the KERNAL/basic having already initialized things. For CX16 this is fine (KERNAL already did init), but the new codegen's approach is safer. No change needed unless it causes issues.

### 2. Cleanup/Exit Sequence (HIGH)

**Old codegen** has a `cleanup_at_exit` label with:
```
lda #1
sta $00      ; ram bank 1
lda #4
sta $01      ; rom bank 4 (basic)
jsr cbm.CLRCHN     ; reset i/o channels
lda #9
jsr cbm.CHROUT     ; enable charset switch
lda _exitcarry
lsr a
lda _exitcode
ldx _exitcodeX
ldy _exitcodeY
rts
```

**New codegen** jumps directly to `sys.poweroff_system`.

**Fix needed:** The old codegen's `cleanup_at_exit` is the actual exit path that the program jumps to. The new codegen skips this entirely and goes straight to poweroff. For the test program this works (poweroff is correct), but for programs that return to basic or need cleanup, this is wrong.

### 3. IR format issues

- The `poweroff_system` call through the register file should eventually match the old codegen's direct approach
- `program_startup_clear_bss` is correctly separate in the new codegen
- `run_global_inits` is correctly a stub

## Value Formatting Convention

The new codegen now uses the same value formatting as the old codegen, centralized in two places:

1. **`toHex()`** (from `prog8.code.core.Conversions`):
   - 0..15 → decimal (no `$` prefix), e.g. `0`, `15`
   - 16..255 → `$xx`, e.g. `$10`, `$ff`
   - 256..65535 → `$xxxx`, e.g. `$0801`
   - larger → `$xxxxxxxx`, e.g. `$0001b000`
   - negatives → `-$xx`, e.g. `-$80`
   - Used for: `* =`, `.align`, memory-mapped addresses, equate values, etc.

2. **`asmHexByte(v: Int)`** (new in `CodeGenerator.kt:307`):
   - Always `$xx` hex, even for 0-15, e.g. `$00`, `$0d`, `$68`
   - Used for: byte data in `.byte` directives (strings, binary chunks, arrays)

## Validation Checklist

- [x] Program produces "hello world" output on CX16 target
- [x] Program exits cleanly (poweroff_system)
- [ ] BSS is properly zeroed before use
- [ ] Startup sequence does not break any KERNAL assumptions
- [x] Strings display correctly (no charset/bank issues)
- [x] CHROUT ($ffd2) calls work correctly
- [ ] Virtual register file is properly zeroed
- [ ] Library subroutines (txt.print, etc.) work correctly

## Key Files

- `new6502gen/src/codegen/CodeGenerator.kt` — main codegen; assembly generation
  - `asmHexByte(v: Int): String` (line 307) — formats byte values as `$xx` hex for `.byte` directives
  - `toHex()` (imported from `codeCore`) — matches old codegen's formatting: 0-15 decimal, >=16 hex with `$`
- `new6502gen/src/newgen/Main.kt` — CLI entry point
- `compiler/res/prog8lib/prog8_lib.asm` — library assembly source (memset, startup, etc.)
- `codeGenCpu6502/src/prog8/codegen/cpu6502/AssemblyProgram.kt` — old codegen for reference
- `codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt` — IR generation from AST
- `intermediate/src/prog8/intermediate/IRProgram.kt` — IR data structures
