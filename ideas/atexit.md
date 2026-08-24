# Dynamic `sys.atexit()` Exit Handlers for Prog8

Design proposal for runtime exit-handler registration, complementing the
existing subroutine-scoped `defer` statement.

Status: revised design. This supersedes the earlier static-modifier
proposal (`atexit` keyword on subroutine declarations), which was dropped.

## 1. Concept and API

A new subroutine `sys.atexit(handler)` registers a handler routine to be
executed when the program terminates:

    main {
        sub start() {
            sys.atexit(&cleanup_thing)
            ...
        }
        sub cleanup_thing() { ... }
    }

- `handler` has type `pointer` and holds the address of a plain void
  no-parameter routine. Only direct `&subname` references are accepted;
  arbitrary pointer expressions are rejected at compile time (see
  section 3).
- Using type `pointer` (not `uword`) makes the mechanism work uniformly on
  all supported targets: slots are 2 bytes on the 6502 targets and
  4 bytes on the 32-bit m68k targets, exactly matching the existing
  `call(address)` builtin signature (codeCore BuiltinFunctions.kt:181).
- Not available on the virtual target: see section 2 and section 6.
- Registration happens at runtime, so registration order is runtime order.
  Duplicate registrations are allowed (C semantics): the handler runs once
  per registration.
- The table has room for 32 handlers. When it is full, `sys.atexit()`
  aborts the program immediately with a BRK-style fatal error (see
  section 5).
- Execution order is LIFO: most recently registered handler first,
  matching `defer`'s reverse-order semantics.

Handlers run *before* the system cleanup routine
(`p8_sys_startup.cleanup_at_exit`) restores machine state, so console
output still works in handlers.

Intended for application code. Libraries should NOT use this mechanism:
technically a library never "exits" the program, and a library registering
handlers unconditionally steals table capacity and imposes cleanup actions
on the host program. Library-scoped cleanup remains `defer`'s job.

Comparison with `defer`:

|                    | defer             | sys.atexit          |
|--------------------|-------------------|---------------------|
| scope              | single subroutine | whole program       |
| registration       | static (position) | runtime call        |
| ordering           | static, LIFO      | runtime order, LIFO |
| runs on sys.exit() from deeper scopes | only sites in same sub | yes, all paths |
| capacity           | 8 per subroutine  | 32 per program      |

Use cases: guaranteed teardown on early-exit error paths (restore video
mode, kill IRQ tasks, flush/save state) regardless of where the program
decides to exit.

## 2. Current Program Termination Model

Findings from the current implementation (branch new-codegens):

- Normal end: after `jsr p8b_main.p8s_start`, the program assembly emits an
  unconditional `jmp p8_sys_startup.cleanup_at_exit`
  (codeGenCpu6502/src/prog8/codegen/cpu6502/ProgramAndVarsGen.kt:188-207).
- `cleanup_at_exit` is a per-target library asmsub (e.g.
  compiler/res/prog8lib/c64/syslib.p8:1083) that restores hardware/BASIC
  state and returns registers holding the exit status to the calling BASIC
  prompt.
- Explicit exits: `sys.exit()` / `sys.exit2()` / `sys.exit3()` are asmsubs
  in each target's syslib that store the status, restore the saved stack
  pointer (`prog8_lib.orig_stackpointer`) and jump directly to
  `cleanup_at_exit` (e.g. c64/syslib.p8:763-797): immediate termination
  from anywhere in the call stack.
- Virtual target: `sys.exit(code)` stores the code and issues `syscall 1`;
  the VM also terminates when RETURN executes with an empty callstack.
- `sys.poweroff_system()` (cx16, virtual, m68k) and `sys.reset_system()`
  bypass everything entirely.
- `defer` is lowered by DeferProcessor (compiler/src/prog8/compiler/
  astprocessing/DeferProcessor.kt) into a bitmask + generated
  `prog8_invoke_defers` routine called at every exit point *of that sub*
  (returns, jumps out, sys.exit call sites, sub end). It does not unwind
  the call stack: defers in `start()` do NOT run when `sys.exit()` fires
  deeper.
- Indirect calls exist already: the `call(address)` builtin performs an
  indirect JSR; on 6502 it compiles to push-return-address + `jmp (ptr)`
  (BuiltinFunctionsAsmGen.kt:369-409). Known hazard: the `jmp (ptr)`
  page-wrap bug on plain 6502 if the pointer variable lands at `$xxFF`
  (65C02 is fine); the new6502 CALLI lowering shares it.
- The IR instruction set has CALLI, but the VM cannot execute it
  (VirtualMachine.kt:279 "VM cannot run code from memory bytes"), and the
  VM cannot load a label address as a value either (VmProgramLoader.kt,
  see docs/source/todo.rst). Dynamic dispatch is therefore impossible on
  the virtual target without VM extensions; extending the VM for this was
  considered and deliberately abandoned. The virtual target will simply
  not offer `sys.atexit` (it is absent from its syslib, so usage fails
  symbol resolution there).

## 3. Semantics

- Handler signature contract: `sub name()` - no parameters, no return
  value, ends normally (rts). The argument must be a literal `&subname`
  reference; arbitrary pointer expressions or address variables are
  rejected at compile time. This makes every registration statically
  checkable: the checker validates the target routine is void with no
  parameters (`&subname` on subroutines is already supported,
  AstChecker.kt:1010-1013).
- Context: handlers capture nothing; state must live in block/module-level
  variables. Typical pattern mirrors defer usage: guarded restore
  (`if gfx_active { ... }`) making the handler safe even if init never ran.
- Ordering: LIFO over registration order. Stable for a given program run.
- Duplicates: allowed, run once per registration.
- Overflow: fatal runtime error (see section 5).
- Re-entrancy: if a handler itself calls `sys.exit()`, the dispatcher must
  not restart. A one-byte guard flag is set while dispatching; a nested
  entry with the flag set skips straight to the cleanup jump.
- Handlers run before `cleanup_at_exit`, so console output still works.
- Interaction with `defer`: defers of a subroutine run when that sub
  exits, which is always before the process-wide dispatch happens (either
  after `start()` returned, or inside the exit routines before they reach
  the chain). Relative order: inner defers -> outer defers -> atexit
  handlers -> machine-state cleanup.

Exit-path matrix:

| Exit path                              | handlers run?     |
|----------------------------------------|-------------------|
| return / fall off end of start()       | yes               |
| sys.exit() / exit2 / exit3 anywhere    | yes               |
| sys.die() (virtual)                    | no (abort-like)   |
| runtime fatal error (overflow, BRK)    | no                |
| sys.poweroff_system() / reset          | no (by nature)    |

## 4. Runtime Machinery

All pieces live in the sys module of each target's syslib, plus one
trampoline label in the program assembly.

State (BSS; see "Zero RAM when unused" below for why this costs nothing
when the feature is never used):

    PROG8_ATEXIT_SLOTS = 32
    atexit_table: pointer[PROG8_ATEXIT_SLOTS]   ; handler addresses
    atexit_count: .byte ?                       ; number registered
    atexit_guard: .byte ?                       ; re-entrancy flag

RAM cost when used: 66 bytes on 6502 targets, 130 bytes on 32-bit targets,
plus code. The dispatcher is written in Prog8 source using the existing
`call()` builtin for the indirect call, so it ports across all backends
for free:

    asmsub atexit(pointer handler @AY) {
        if atexit_count == PROG8_ATEXIT_SLOTS -> BRK (fatal, see section 5)
        atexit_table[atexit_count] = handler
        atexit_count++
    }

    sub atexit_dispatch() {
        if atexit_guard == 0 {
            atexit_guard = 1
            i = atexit_count
            repeat {
                i--
                if i is negative -> done
                call(atexit_table[i])           ; LIFO dispatch
            }
            atexit_guard = 0
        }
    }

(The exact loop spelling will be adapted to what prog8 supports; the
semantics are as shown. `atexit_count` is deliberately left intact after
dispatch for post-mortem inspection. The loop walks a snapshot taken at
entry, so handlers registered by a running handler do not execute in the
same dispatch pass.)

Trampoline: the compiler always emits this label in the program assembly:

    prog8_atexit_chain:
        jsr  p8_sys_startup.atexit_dispatch     ; only when sys.atexit is used anywhere
        jmp  p8_sys_startup.cleanup_at_exit

and routes the program tail (`jsr p8b_main.p8s_start`) to
`jmp prog8_atexit_chain` instead of jumping to cleanup directly.

The stdlib exit routines of every builtin target get a one-line change:
their final `jmp p8_sys_startup.cleanup_at_exit` becomes
`jmp prog8_atexit_chain`. This is what guarantees handler execution when
`sys.exit()` fires deep in the call stack (~10 files, 3 sites each).
Because the trampoline label is always defined by the program assembly,
the stdlib links unchanged whether or not the feature is used; unused cost
is the 3-byte `jmp`.

Zero RAM when unused (requirement): a program that never references
`sys.atexit` must not allocate the table, count or guard. This falls out
of a removal cascade that must be preserved by the implementation:

1. The compiler omits the `jsr p8_sys_startup.atexit_dispatch` from the
   trampoline when its reachability check finds no reference to
   `sys.atexit` anywhere in the program. The check is conservative: any
   reference at all keeps the dispatch jsr (a false positive only costs
   RAM, never correctness).
2. Without callers, `sys.atexit` and `atexit_dispatch` are unreferenced
   subs and are removed by UnusedCodeRemover like any unused library
   routine.
3. With both gone, `atexit_table`, `atexit_count` and `atexit_guard` have
   no remaining references and are dropped by the unused-variable
   elimination pass.

Conditions: the three state items must be ordinary Prog8 variable
declarations in the syslib (not raw asm labels inside `%asm` blocks) so
the AST-level passes can see and remove them; do not rely on 64tass dead
code elimination for them (unpredictable, see the nested-subroutine DCE
bug in docs/source/todo.rst). Note also that handler subs stay alive
through their `&handler` references: dynamic `call()` creates no static
call-graph edges (UnusedCodeRemover.kt deliberately excludes it), so this
is the only thing keeping them in.

## 5. Fatal Error Mechanism

On overflow, registration aborts the program immediately: BRK on the 6502
targets and an illegal opcode/trap on the m68k targets. No attempt is made
to print a message; the goal is that a monitor or debugger trace shows the
exact location of the failed registration.

## 6. Implementation Phases

Ordered: shared/frontend pieces first, then the 6502 codegen backends,
then m68k. The virtual target is explicitly out of scope (no `sys.atexit`
in its syslib; programs calling it there fail with an unresolved symbol).

### Phase 1 - frontend + shared library pieces

1. Put `sys.atexit`, the table, guard and dispatcher (Prog8 source) into
   the shared library module `shared_atexit.p8`, which every non-virtual
   target's syslib imports. Namespace consequence: the symbols live under
   that module's scope (`shared_atexit.atexit_table`,
   `shared_atexit.atexit_dispatch`, ...) instead of `p8_sys_startup.*`;
   the trampoline jsr and all internal references must use these names.
2. Checker: require literal `&subname` arguments for sys.atexit and
   validate them statically (target routine must be void with no
   parameters); reject all other argument forms.
3. CallGraph/UnusedCodeRemover: make sure `&handler` references keep the
   handler subs alive (they are never called explicitly), and detect
   whether `sys.atexit` is reachable so the trampoline can include or omit
   the dispatch jsr.

### Phase 2 - 6502 codegen backends

4. ProgramAndVarsGen.kt tail emission (old backend) and the equivalent spot
   in new6502 AsmGen.kt: emit the trampoline and jump to it instead of
   cleanup_at_exit.
5. Mechanical stdlib edit: retarget the final jmp of sys.exit/exit2/exit3
   in c64/cx16/c128/pet32 syslibs to `prog8_atexit_chain`.
6. Verify the `call()` lowering used by the dispatcher in both backends
   (including the `$xxFF` page-wrap hazard note; consider placing the
   dispatch pointer variable explicitly away from `$xxFF`, e.g. in the
   safe zeropage scratch area, or use 65C02 `jmp (abs)` semantics only on
   cx16).

### Phase 3 - m68k targets (amiga500, qemu68k)

7. Same trampoline emission in codeGenM68k's program tail; retarget the
   m68k prog8_lib exit routines. The dispatcher needs no changes:
   `call()` lowers to an indirect `jsr (an)` and pointers are 4 bytes.

## 7. ROMable Considerations

The mechanism is ROM-safe as designed: the table lives in BSS (RAM), the
dispatcher is straight-line code with no self-modification, and the 6502
`call()` trampoline writes only to zeropage temporaries at runtime.

## 8. Testing Plan

- Unit tests (compiler): static validation errors for bad `&sub` arguments;
  CallGraph survival of handler subs; trampoline omission/inclusion.
- TestExecution6502 ksim65 tests: LIFO ordering, duplicate registrations,
  handler runs after sys.exit() mid-call-stack, overflow fatal error,
  re-entrancy (handler calls sys.exit()), interaction order with defer.
- Regression guard: generated assembly byte-identical when the program
  never calls sys.atexit (except the fixed 3-byte trampoline jmp).
- Virtual target: test that `sys.atexit` fails to resolve there.

