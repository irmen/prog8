Remaining M68k Codegen Issues
==============================

1. 32-bit Pointers
-------------------
Prog8 currently treats pointers as `uword` (16-bit) throughout the compiler.
The M68k target needs full 32-bit addresses.

What this affects:
- **String pointers**: `str` variables store only the lower 16 bits of the
  string address. The upper 16 bits are lost, so strings beyond the first 64KB
  of the load address (0x10000-0x1FFFF) will be accessed at the wrong address.
- **Pointer arithmetic**: Adding to a pointer wraps at 16 bits (65535) instead
  of 32 bits.
- **`sizeof(&var)`** returns 2 instead of 4.
- **SIZEOF_POINTER** in syslib.p8 is 2 instead of 4.
- **The register file slot** for pointer variables is 2 bytes instead of 4.

This is the single most impactful limitation. Until this is fixed, programs using pointers or addresses will malfunction.

How to fix:
- Change `ICompilationTarget` / `IMemSizer` to report pointer size as 4 for
  M68k (affects `DataType.isPassByRef`, `memorySize(DataType.UWORD, ...)`).
- Update the register file and variable allocation to use LONG slots for pointers.
- Update all `move.w` to `move.l` when loading/storing pointer values.  BUT ONLY pointers.  For normal word values, it should not change, keep `move.w` intact
- Update `sys.SIZEOF_POINTER` in syslib.p8 from 2 to 4.

2. Calling Convention Caveats
-------------------------------
The current calling convention passes all arguments on the stack with no
standardized frame layout. The caller pushes arguments, calls `jsr`, then
cleans up with `addq.l`/`add.l`.

However, this is mitigated by Prog8's design:
- Prog8 has **no local stack frame** — all variables are static/BSS, so
  `link`/`unlk` frame management is not needed.
- The convention is caller-pops (C declaration style), which is simple and
  functional.

Still missing:
- **Floating-point arguments** (passed on stack via `fmove.d -(sp)`).

3. No Support for `asmsub` / `extsub`
--------------------------------------
Inline assembly subroutines (`%asmsub`) and external subroutines (`%extsub`)
are not implemented. The codegen simply doesn't handle them.

`extsub` currently targets ROM routines at fixed addresses (C64/CX16 model).
For QEMU `-M virt` there are no ROM routines, so `extsub` is not needed.

For a future Amiga target, `extsub` could be repurposed to call OS library
functions via offset-based calling convention:
- Classic AmigaOS libraries use negative-offset vector tables from a base
  pointer returned by `OpenLibrary()`.  The NDK `.fd` files define every
  function's offset with `##bias` directives (e.g., `##bias 30` for
  dos.library means first function is at offset -30, each entry = 6 bytes).
  Library/device includes and `fd2pragma` files are available in the NDK.
- The OS loader relocates libraries at load time, so addresses never need to
  be known ahead of time.
- `extsub` could map to `OpenLibrary()` + `jsr -offset(a6)` calls, with the
  library name and function offset specified in the directive.

4. No Support for Banked Calls / Far Calls
------------------------------------------
`CALLFAR` and `CALLFARVB` are not implemented (not applicable to M68k,
but should produce a clear error instead of `TODO`).

5. Zeropage / CPU Registers
----------------------------
The `asmsub` parameter register assignments (A, X, Y, AX, AY, XY, FAC1, FAC2)
are based on the 6502 convention and have no M68k equivalent. The slot
mapping in `m68kSlotRegister()` maps these to D0-D2 and FP0-FP1, but this
mapping is arbitrary and not documented or standardized.

6. Float Support
-----------------
Floating-point operations are partially implemented (FPU instructions are
emitted) but:
- Float constant loading uses `fmovecr` for 0.0 and 1.0 only.
- Other float constants are stored as 8-byte DC.B sequences but the
  `convertFloatToBytes` and `getFloatAsmBytes` functions are TODO.
- Float comparison uses `fcmp` + conditional branches, edge cases untested.

7. QEMU-Specific Assumptions
-----------------------------
Several aspects are hardcoded for QEMU `-M virt`:
- MMIO addresses (Goldfish TTY at 0xFF008000, virt controller at 0xFF009000).
- RAM size assumed to be 1MB (matches `-m 1M` flag).
- CPU is assumed to be M68020.
- ELF format with NMAGIC linking (`vlink -n`).
- No bootinfo parsing (hardcoded addresses instead).

Running on real hardware or a different emulator would require significant
changes.

8. Linker Script Hardcoded
---------------------------
The linker script (compiler/res/prog8lib/qemu68k/link.ld) hardcodes:
- Load address: 0x10000
- Entry point: prog8_program_start
- BSS via COMMON section (may conflict with other section placement)

9. Testing Gaps
----------------
- No automated test suite exercises the M68k codegen.
- No CI runs the M68k tests.
- Manual QEMU testing is required for any change.
