# Amiga bare metal helper libraries

### CPU scope

Only **classic M680x0 CPUs** (68000, 68010, 68020, 68030, 68040, 68060) are considered.
**Dragonball and ColdFire variants are fully ignored** — their instruction sets,
exception frames, and supervisor/user privilege rules are not part of this design.
This matches the `m68k-coder` skill scope: write/understand classic M680x0 assembly
in vasm mot syntax only; do not target or reference Dragonball/ColdFire.

- **Never use `CALLM` or `RTM`.** Both exist **only** on the MC68020; they are
  removed on every later classic CPU (68030/68040/68060) and on ColdFire, where
  executing them takes an unimplemented-instruction exception. They are the
  module call/return pair tied to the MC68851 access-control model Motorola
  dropped once the on-chip MMU replaced it. Use a normal `jsr`/`rts` subroutine
  call instead.

## IRQ handler support (umbrella)

The highest-value missing piece: cx16-style IRQ helpers reshaped for the
68000/Amiga model. Both entries below converge on it.

These helpers are for programs that have taken over the machine with
`custom.grab_system()`, not for OS-friendly interrupt installation. Programs
that leave AmigaOS running should use `AddIntServer()`/`RemIntServer()` for
shared Exec interrupt chains, `AddICRVector()`/`RemICRVector()` for CIA sources,
and `timer.device` for ordinary timing. Exec calls these routines as
subroutines, so they return with `rts`; the bare-metal vector trampoline returns
with `rte`.

- `custom.set_handler(level, sub)` / `custom.restore_handler(level)` plus
  Paula source-bit enable/disable (`INTENA`/`INTREQ` discipline). A straight
  copy of `sys.set_rasterirq` doesn't map: seven autovector levels (1-7) carry
  multiplexed Amiga sources (VERTB, COPER and BLIT share level 3, CIA-A/B sit
  on 2/6, and level 7 is NMI and not maskable through `INTENA`). A level-3
  handler must check `INTREQR` to tell the sources apart and acknowledge only
  the one it services.
- **VBR-aware**: the handler mechanism must use `custom.cpuVBR` as the vector
  base address (not hardcode $000000). On 68000, VBR is always 0 (no VBR
  register). On 68010+ (68020/68030/68040/68060), VBR can be relocated.
  `custom.cpuVBR` is already populated by `grab_system()` (or equivalent)
  via supervisor-mode `MOVEC VBR,d0` on capable CPUs, defaulting to 0 on
  68000. Autovector level N lives at `custom.cpuVBR + (24+N)*4`, i.e.
  `custom.cpuVBR + $60 + N*4` ($64..$7C for levels 1-7). `cpuVBR + N*4` would
  land on the reset/exception vectors instead. The `IRQ1`..`IRQ7` equates in
  `grab_system` are assembler-local, not Prog8 constants, so either add public
  constants or compute the offset in the trampoline. Call `set_handler` only after
  `grab_system()`, so that `cpuVBR` is populated and the vectors it saves are
  the OS ones.
- **No codegen work needed.** An `asmsub` trampoline supplies the frame: save
  `D0-D7/A0-A6`, derive the level with `MOVE.W SR,dN` inside the supervisor
  trampoline (valid because exception entry runs in supervisor mode with
  `IPL = level`), `jsr` the handler, restore the registers and return with
  `rte`. Do not parse the stacked exception frame: its format differs between
  68000 and 68010+. The handler itself is an ordinary Prog8
  `sub` with no parameters and no return value, returning with a normal `rts`.
  `rte` is accepted as an asmsub terminator (alongside `rts`/`rti`), so the
  trampoline needs no `; !notreached!` marker.
- **No FPU in handlers (decision):** handlers and the subroutines they call must be integer-only. The trampoline preserves `D0-D7/A0-A6` only and does not save any FPU state (`FP0-FP7`, `FPCR`, `FPSR`, `FPIAR`, or implementation-specific internal state). Use of FPU instructions inside a handler (or anything it calls) is unsupported and must be avoided - there is no FPU-saving wrapper provided. This keeps the trampoline small and predictable; if an FPU-using handler is ever needed, it will require a dedicated wrapper as a separate, opt-in feature.
- **Leave `grab_system` unchanged; add an additive teardown hook to
  `restore_system`.** `grab_system` must not manage handlers: it runs at
  startup, before any handler exists, and it only saves the IRQ1/IRQ3/IRQ4
  vectors anyway. Keep a separate registry (one slot per autovector level
  1-7; slot 0 is unused) holding the handler address plus the vector in place when
  `set_handler` ran. `restore_handler(level)` puts that vector back.
  `restore_system()` gains one trailing loop that calls `restore_handler` for
  every active slot **after** restoring its own saved IRQ1/3/4 vectors. That
  ordering is what makes teardown safe: it also repairs the case where a
  handler was installed before `grab_system` (which would otherwise have
  saved the trampoline as the "original"). The loop is a no-op for programs
  that never call `set_handler`. Levels 2, 5, 6 and 7 have no saved vector in
  `grab_system`, so the registry's captured original is the only way to
  restore those.

  **Why a separate registry is simpler than modifying `grab_system`:**
  - `grab_system` runs at startup before user code — it saves original vectors
    but cannot know which handlers will be installed later.
  - The 68000 has 7 autovector levels (1-7), and the Amiga uses 1-6 (7 is the
    nonmaskable level from the expansion connector). A fixed 7-slot table is
    tiny and covers all cases.
  - Modifying `grab_system` to allocate and manage a handler table would
    couple IRQ tracking to system takeover, making it harder to test and
    increasing the risk of breaking existing `grab_system` behavior.
  - The separate registry is a pure addition — programs that never call
    `set_handler` are completely unaffected.
- `restore_handler(level)` can also be called manually for early teardown.
- VERTB and CIA timers below become thin consumers of this API.

## VERTB IRQ handler

No bare-metal vertical-blank example exists (`lowlevel-timer.p8` is OS-based;
m68k targets have no `sys.set_irq` helpers, see `targetsystem.rst`).

- Install an autovector level-3 handler via `custom.cpuVBR` after
  `custom.grab_system()`, clear any pending VERTB request first, then enable
  `VERTB` in `INTENA` (including the master `INTEN` bit, which `grab_system`
  cleared). Acknowledge each vblank with a `INTREQ` clear write, i.e. bit 15
  (SET/CLR) = 0 and bit 5 = 1 (`#$0020`); writing `$8020` would set the request
  again. Return with `rte`. Restoration is handled by the separate handler
  registry plus the trailing loop in `restore_system`; `grab_system` stays
  unchanged.
- Keep the handler minimal (set a frame flag, like the timer example);
  the main loop does the work.
- `playfield.waitvsync` stays the polling fallback; document when the IRQ
  version pays off (frame time too tight to spin on the beam).

## CIA timer interrupt helpers

Interrupt-driven CIA timer support, the part that needs design decisions
rather than register pokes:

- Route to CPU levels 2 (CIA-A) / 6 (CIA-B) through `INTENA` PORTS/EXTER,
  install the handler via `custom.cpuVBR`, acknowledge by *reading* ICR,
  return with `rte`, restore on exit. Do not assume the CIAs are in a known
  state even after `grab_system()`: mask all ICR sources and clear pending
  requests first, then enable only the timer bit. A read of ICR clears every
  pending bit, so the handler must preserve the whole returned value and
  service all bits it contained.
- Keep the handler minimal (tick counter / frame flag), like the VERTB one.
- Decide the callback shape: a Prog8 `sub` pointer called from the stub (needs
  a register contract the stub upholds), a bare address of hand-written asm, or
  an `exec.Interrupt`-like node the user fills in.
- Decide the OS story: `cia.resource/AddICRVector` and `RemICRVector` are not
  wrapped anywhere in the library (only `ptplayer` calls them from inline asm),
  while `timer.device` claims CIA-A Timer A and Timer B for its microhertz unit
  and the 50/60 Hz tick. Direct ICR writes while the OS runs are explicitly
  discouraged, so this helper can only be bare-metal until someone wraps
  `cia.resource`.
- Name it once it exists: something like `cia.timer_irq`, kept separate from
  the polling helpers.
