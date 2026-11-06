# M68K frame-pointer omission

## Problem

The Prog8 M68K backend currently sets up an `A5` frame pointer in every subroutine, even when it is not needed. vbcc (and most M68K compilers) default to SP-relative locals and only use a frame pointer when asked with `-use-framepointer`.

For example, `p8b_wavfile.p8s_read_le_word` in `wavc` currently emits:

```asm
p8b_wavfile.p8s_read_le_word:
    link  a5,#0
    move.l  8(a5),a2
    move.b  (1,a2),d0
    lsl.w  #8,d0
    move.l  8(a5),a2
    move.b  (a2),d2
    or.w  d2,d0
    unlk  a5
    rts
```

vbcc emits the equivalent function as SP-relative:

```asm
l12:
    move.l  (4,sp),a1
    moveq  #0,d1
    move.b  (1,a1),d1
    lsl.w  #8,d1
    moveq  #0,d0
    move.b  (a1),d0
    or.w  d1,d0
    rts
```

The `link`/`unlk` pair is pure overhead here: the function has no locals and its argument can be addressed relative to `SP` just as easily.

The same pattern appears in many small library and helper routines. In the `wavc` build, leaf functions such as `p8b_txt.p8s_print_ub`, `p8b_txt.p8s_print_uw`, `p8b_txt.p8s_print_l`, `p8b_adpcm.p8s_decode_block_mono`, etc. all use `link a5,#0` / `unlk a5` with no locals.

## Expected benefit

- Two instructions saved per leaf function that can omit the frame (`link` + `unlk`).
- One extra address register (`A5`) becomes available to the register allocator in those functions.
- Smaller and faster function entry/exit.

The benefit is function-count dependent, not loop dependent: it helps any subroutine that does not need a frame. It is especially visible in programs that call many small helpers.

## Constraints / when a frame pointer is still required

A frame pointer must be kept when any of the following are true:

1. The function has stack-allocated locals or spills that are accessed via `A5` frame slots.
2. A local's address is taken (`&local`) and accessed through a pointer, because the pointer needs a stable base.
3. The function uses variable-length stack allocation (not currently supported by Prog8).
4. The function is marked `%option noframe` — these already bypass the allocator and must continue to manage the stack themselves.
5. Hand-written `%asm` inside the function refers to `A5` or frame offsets.
6. The function is so large that SP-relative displacements overflow (unlikely; M68K supports signed 16-bit offsets).

Recursion is not a blocker: SP-relative locals are reentrant by construction as long as each activation has its own stack space.

## Implementation sketch

The decision belongs in `M68kCodeGenerator.kt` / `StackFrameLayout`:

1. After stack-frame layout, decide whether the function truly needs an `A5` frame:
   - If the only stack accesses are arguments passed on the stack and there are no locals/spills, the function can be frameless.
   - If there are locals but none of them needs a stable address, an alternative is to keep using `A5` (simpler) or to allocate them SP-relative and update offsets when arguments are pushed/popped.

2. For a first, safe implementation, restrict frame omission to:
   - Functions with **zero locals/spills**.
   - Functions whose arguments are passed on the stack.
   - No `%option noframe`, no hand-written `%asm` referencing `A5`, no `&arg`.

3. In a frameless function:
   - Omit `link`/`unlk`.
   - Address arguments relative to `SP` at function entry. Because there is no `link`, arguments start at `4(sp)` (return address) or `8(sp)` (if register saves were pushed before accessing them), exactly like vbcc.
   - Be careful with `movem.l` register saves: if they are pushed after the function entry, the SP offset of arguments changes. Either access arguments before pushing callee-saved registers, or track the SP delta.

4. Update `AsmGen` / `operand()` / frame-slot resolution so that:
   - Frameless functions never reference `(-off,a5)`.
   - The assembly output labels the frame decision (e.g. `; stack frame: 0 bytes, frame pointer omitted`).

## Why this is backend-level, not a peephole

A post-assembly peephole cannot safely remove `link`/`unlk` because:

- It would have to rewrite every `(off,a5)` reference in the function to an SP-relative offset.
- It would have to account for `movem` and argument pushes that change `SP`.
- It would have to prove no hand-written `%asm` relies on `A5`.

The right place is the code generator, which already knows the frame layout.

## Relation to other work

- `ideas/m68k-eliminate-double-pointer-reload.md` removes the redundant `move.l 8(a5),a2` inside `read_le_word`. Frame-pointer omission is independent but compounds the win: a frameless `read_le_word` would be even closer to the vbcc version.
- `ideas/m68k-better-codegen.md` Tier 4 discusses broader register-allocation improvements. Frame-pointer omission is a narrower, self-contained change that frees one address register and removes entry/exit overhead without altering the allocator itself.

## Testing

1. Compile `examples/amiga/wavc.p8` and verify leaf helpers no longer emit `link`/`unlk`.
2. Verify `read_le_word` (and similar small routines) are frameless and correct.
3. Run `gradle :codeGenM68k:test` and the m68k instruction-selection tests.
4. Run the compiled `wavc` program (or a smaller m68k example) to confirm no stack corruption.

## Status

Not yet implemented.
