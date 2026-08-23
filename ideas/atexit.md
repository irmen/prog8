# Static `atexit` Handlers for Prog8

Design proposal for a static, compile-time-known program exit handler facility,
complementing the existing subroutine-scoped `defer` statement.

## 1. Concept

A new `atexit` modifier keyword that marks no-arg void routines to run
automatically at program termination, before the system cleanup routine
(`p8_sys_startup.cleanup_at_exit`) restores machine state:

    atexit sub gfx_restore() { ... }

The compiler collects all subroutines carrying the modifier across all
compiled modules.
This is static registration: like everything else in prog8, the set of handlers
is known at compile time. No function-pointer table, no runtime registry, zero
RAM cost.

It complements `defer` rather than replacing it:

|                    | defer            | atexit    |
|--------------------|------------------|-----------|
| scope              | single subroutine| whole program |
| runs on deep sys.exit() from call stack | no | yes |
| library-module friendly (auto-cleanup)  | no | yes |

Use cases: libraries that install IRQ handlers or change video/charset state
can self-register restore routines; programs get guaranteed cleanup even on
early-exit error paths.

## 2. Current Program Termination Model

Findings from the current implementation (branch new-codegens):

- Normal end: after `jsr p8b_main.p8s_start`, the program assembly emits an
  unconditional `jmp p8_sys_startup.cleanup_at_exit`
  (codeGenCpu6502/src/prog8/codegen/cpu6502/ProgramAndVarsGen.kt:188-207).
- `cleanup_at_exit` is a per-target library asmsub (e.g.
  compiler/res/prog8lib/c64/syslib.p8) that restores hardware/BASIC state and
  returns registers holding the exit status to the calling BASIC prompt.
- Explicit exits: `sys.exit()` / `sys.exit2()` / `sys.exit3()` are asmsubs in
  each target's syslib that store the status, restore the saved stack pointer
  (`prog8_lib.orig_stackpointer`) and jump directly to `cleanup_at_exit`,
  i.e. immediate termination from anywhere in the call stack.
- `sys.poweroff_system()` (cx16, virtual, m68k targets) and
  `sys.reset_system()` bypass everything entirely.
- Virtual target: VM terminates when RETURN executes with an empty callstack,
  or via syscall EXIT (`sys.exit` in virtual/syslib.p8 is implemented as
  syscall 1).
- `defer` is subroutine-scoped only: DeferProcessor lowers it to a bitmask +
  generated handler sub called before every exit point *of that sub*. It does
  not unwind the call stack, so defers in `start()` do NOT run when
  `sys.exit()` fires deep in the call stack.
- There is no user-registerable exit-hook facility anywhere today; nothing in
  docs/source/todo.rst about it either.

## 3. Syntax and Semantics

### Chosen form: `atexit` modifier keyword

Handlers are marked at their definition site with a modifier keyword, in the
same position as the existing subroutine modifiers (`private`, `public`,
`inline`):

    atexit sub restore_gfx() { if gfx_active { ... } }
    private atexit asmsub irq_restore() { ... }

The compiler collects all subroutines carrying the modifier across all
compiled modules; there is no separate registration list anywhere. "Is an
exit handler" is a property of the sub itself, which gives libraries the
cleanest self-registration story and lets private subs participate naturally
in their own module.

Subroutine declarations already use keyword modifiers rather than tags, so
this follows the local convention exactly (`inline` is precedent for a
modifier that changes compiler behavior for one sub). Implementation cost is
a small grammar change:

- Grammar: add an `ATEXIT` lexer token and `ATEXIT?` to the `sub` and
  `asmsub` rules in Prog8ANTLR.g4, next to `INLINE?`.
  Note: this reserves `atexit` as a keyword program-wide - accepted, matching
  existing reservations like `swap`, `defer`, `alias`. It does not conflict
  with a future dynamic `sys.atexit()` builtin (namespaced under sys).
- AST: boolean field on AstSubroutine (like INLINE handling); no tag-string
  whitelist machinery needed.
- Checker: void, no parameters; reject combination with `inline`; not
  applicable to `extsub` (external address as handler is odd); allowed on
  block-level subs in any module.
- CallGraph: treat handler subs as used roots so UnusedCodeRemover keeps them.
- Collection: scan all subs for the flag after semantic processing; stable
  order per compilation.

### Alternative considered: `@atexit` subroutine tag

Same mechanism, spelled as a declaration tag following the vardecl convention
(`@zp`, `@shared`, ...):

    @atexit sub restore_gfx() { ... }

Requires adding `TAG*` to the sub/asmsub rules plus a whitelist check in the
parser visitor. Tradeoffs versus the modifier keyword:

|                        | `@atexit` tag            | `atexit` keyword         |
|------------------------|--------------------------|--------------------------|
| grammar change         | TAG* + visitor whitelist | single token + optional  |
| AST representation     | tag string list          | plain boolean            |
| identifier impact      | none                     | reserves `atexit`        |
| precedent for subs     | none (tags are vardecl)  | `private/public/inline`  |

The keyword form was preferred because it matches how subs are already
modified and needs less validation machinery. The tag avoids reserving an
identifier and scales better IF many per-sub attributes accumulate later;
it remains the fallback if keyword pollution is a concern.

### Alternative considered: `%atexit` listing directive

    %atexit restore_gfx, irq_restore

Zero grammar change (`directive` rule already accepts it), validated like
`%jmptable`. Tradeoffs versus marking at the definition:

|                        | `%atexit` directive      | mark at sub definition   |
|------------------------|--------------------------|--------------------------|
| grammar change         | none                     | small                    |
| registration site      | list line in block       | at the sub definition    |
| validation             | resolve args             | signature checks only    |
| ordering control       | explicit within line     | source order only        |
| duplicate registration | possible                 | impossible by design     |
| precedent              | `%jmptable`              | sub modifiers            |

Marking at the definition was preferred for its direct self-registration
semantics; the directive remains a viable fallback if grammar changes are
unwanted.

### Why per-sub marking rather than `%option`

The core design decision here - restrict the marker to subroutine scope and
have the compiler collect all subroutines carrying it - comes from the
`%option at_exit` idea, and was adopted. The remaining choice is purely the
spelling of the per-sub marker, where a declaration modifier fits prog8's
existing constructs better:

- `%option` is defined as a scope-level directive: module/block placement with
  distinct valid sets and inheritance rules per level (AstChecker.kt
  ~1457-1476). Subs are not option scopes, so attaching an option to a single
  sub would require either a statement inside the body that marks its
  enclosing sub (overloading a scope-level construct with member-level
  meaning) or a third placement class invented for this one feature.
- Per-declaration behavior in prog8 is already spelled on the declaration
  itself: keywords for subs (`private`, `public`, `inline`), tags for vars
  (`@zp`, `@shared`). The `atexit` modifier extends the sub pattern instead
  of introducing a novel mechanism.
- Diagnostics and tooling: a modifier sits on the declaration line, so
  signature errors point directly at the offending sub; a body-position
  directive affecting its parent is indirection for users and the language
  server.

The block-level `%option at_exit` spelling (a boolean toggle without payload)
would additionally need a magic naming convention or a companion listing
mechanism, which is what the per-sub marking avoids in the first place.

Where `%option` may still become relevant later: knobs for a dynamic
registration facility (e.g., table sizing), should one ever be added.

### Statement keyword and builtin rejected

A *statement-style* keyword like `defer` was considered and rejected:

- `defer` is a runtime construct that registers code at its statement
  position; an atexit marking is a link-time property of a declared sub.
  A statement form would suggest execution-order semantics the design does
  not have.
- An inline-body variant (`atexit { ... }`) would be actively harmful: the
  syntax promises local capture that cannot exist without closures.
- A builtin function (`sys.atexit(f)`) implies runtime evaluation: dynamic
  ordering, conditional calls in loops - all things the static design
  deliberately does not do. It also requires function-pointer arguments and
  the compiler constant-folding "calls" for their compile-time side effects.
  Leave room for a possible future dynamic-registration facility under the
  sys namespace (see scoping section). One caveat: making `atexit` a lexer
  keyword reserves it everywhere (like `swap`, `defer`), which would also
  block the literal spelling `sys.atexit(...)` for such a future builtin -
  acceptable, since an alternative name (`sys.exit_handler`, ...) costs
  nothing and no dynamic facility is planned now.

- Number of registrations: unlimited. Unlike `defer` (which is capped at 8 per
  subroutine by its UBYTE bitmask), the modifier form has no mask or table; the
  compiler emits a straight-line jsr sequence, so 32, 100+ handlers are all
  fine (~4 bytes of code each).
- Ordering: LIFO (reverse collection order), consistent with `defer`.
  Collection order = source order within a module; modules processed imports
  before main module. Documented as stable for a given compilation.
- Duplicate registration impossible by construction (one definition = one
  registration).
- Handlers run before `cleanup_at_exit`, so console output still works.
- When the trampoline variant is used (see section 5), a re-entrancy flag in
  the trampoline prevents infinite recursion if a handler itself calls
  `sys.exit()`.
- Not covered (documented): `sys.poweroff_system()`, `sys.reset_system()`,
  runtime fatal errors (BRK), inline asm that jumps out.

### Scoping model (no closures)

Handlers are plain named subroutines and capture nothing, so prog8's
lack of closures is not a problem - this matches C's atexit(), which also has
no closures.

- Handler signature: `sub name()` - no params, no return.
- Context must live in block/module-level state (`@shared` or plain block
  vars), which prog8's static allocation model makes natural.
- Typical library pattern: library block keeps state (`gfx_active`,
  saved registers) and marks a private restore sub with a guard:
  `if gfx_active { ...restore... }` - safe even when init never ran.
- Visibility: private marked subs work naturally in their own module;
  there is no cross-block reference question at all since nothing is
  registered by name anywhere else.
- Not possible: tear-down that needs per-call-site locals ("close the file I
  opened on this branch") - that remains `defer`'s job. `atexit` is for
  process-wide, state-backed cleanup.

Dynamic C-style registration (`sys.atexit(funcptr)`) was considered and
rejected for now: it needs a permanently linked pointer table + counter +
dispatcher, has cx16 banked-call complications for far pointers, and still
offers no closures (C solves context via a void* userdata parameter; prog8
would need a `handler(uword ctx)` convention). Can be added later as an
orthogonal extension if ever needed.

Exit-path matrix (coverage option B; with option A the sys.exit/die rows
become "no"):

| Exit path                              | handlers run?     |
|----------------------------------------|-------------------|
| return / fall off end of start()       | yes               |
| sys.exit() anywhere in call stack      | yes               |
| sys.die() (virtual)                    | yes               |
| sys.poweroff_system() / reset          | no (by nature)    |

## 4. Zero Footprint When Unused

Requirement: no array, no dispatch code, nothing linked when `%atexit` is not
used.

The base design satisfies this completely: with no directives, nothing is
collected and nothing is emitted - not even a label. Even when used there is
no table or dispatch loop, only the inline jsr sequence. The remaining design
choice is only about deep-exit (`sys.exit()`) coverage:

| Option | cost when unused | sys.exit covered? | notes |
|--------|------------------|-------------------|-------|
| A: inline jsrs at normal-exit site only | zero | no | pure frontend, no stdlib changes |
| B: always emit 3-byte trampoline `prog8_atexit_chain` (`jmp cleanup_at_exit` when empty); syslib exit routines retarget to it | ~3 bytes + 1 label | yes | recommended follow-up |
| C: assembler-define conditional in syslibs (`-D PROG8_ATEXIT`) | zero | yes | new compiler-to-assembler define pathway + conditional asm in every syslib |

B and C require the same stdlib edits (~10 files, 3 sites each). C saves only
3 bytes; B is much simpler. Recommendation: implement A first, B as follow-up,
skip C unless code size ever matters that much.

## 5. Implementation Sketch

Phase 1 - frontend (modifier keyword form):

1. Grammar (Prog8ANTLR.g4): add `ATEXIT` lexer token and `ATEXIT?` to the
   `sub` and `asmsub` rules, next to `INLINE?`.
2. AST: add a boolean field to AstSubroutine, set from the modifier.
3. AstChecker: validate marked subs are void with no parameters; reject
   combination with `inline`; not applicable to `extsub`.
4. CallGraph: mark handler subs as used so UnusedCodeRemover keeps them (they
   are never called explicitly).
5. Collect all marked handlers into a list available to backends (scan via
   SymbolTable after semantic processing, computed once).

Phase 2 = option A - 6502 backends, normal-exit coverage:

6. ProgramAndVarsGen.kt:193-206: when handlers exist, emit the LIFO jsr
   sequence between `jsr p8b_main.p8s_start` and
   `jmp p8_sys_startup.cleanup_at_exit`. When none exist, output is
   byte-for-byte identical to today.

Phase 3 = option B - deep-exit coverage:

7. Compiler emits trampoline label `prog8_atexit_chain` in the program
   assembly (handlers LIFO + `jmp p8_sys_startup.cleanup_at_exit`; empty =
   just the jmp, ~3 bytes) plus a one-byte re-entrancy guard checked in its
   prologue.
8. ProgramAndVarsGen.kt tail becomes `jmp prog8_atexit_chain`.
9. Stdlib update (mechanical): each builtin target's syslib.p8 exit routines
   (sys.exit, sys.exit2, sys.exit3) retarget their final
   `jmp p8_sys_startup.cleanup_at_exit` to the trampoline (~10 files, 3 sites
   each; custom-target example libs under examples/customtarget/libraries
   optional).

Phase 4 - virtual/IR target:

10. IRCodeGen appends CALLs to the handler chunks before the final RETURN of
    the main chunk (option A equivalent).
11. VM syscall-EXIT coverage: either give the VM knowledge of a conventional
    atexit chunk name, or accept partial coverage initially (open question).

M68k targets (amiga500, qemu68k): same approach as 6502; their exit paths also
end in `cleanup_at_exit` (prog8_lib.p8 per target).

## 6. Testing Plan

- Unit tests modeled on TestDefers.kt: AST validation errors, ordering,
  unused-code survival.
- Assembly-output test: with no `atexit` modifiers, generated assembly is
  identical to current output (regression guard for the zero-footprint
  requirement).
- TestExecution6502 ksim65 test: handler ordering + runs after sys.exit()
  mid-call-stack.
- VM test: same program on `-target virtual`.

Estimated effort: moderate; phases 1-2 mostly mechanical, low risk.

## 7. Open Questions

1. Cover the VM syscall-EXIT path via VM-level support, or accept "normal end
   + library sys.exit" coverage on the virtual target?
2. Phase it (option A first, option B second) or do full coverage in one go?
