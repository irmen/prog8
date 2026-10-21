# M68k Register Allocation - Stage 4: Default Allocator and Cleanup

Part of the m68k register allocation design in `m68k-register-allocation.md` (§7).
Stage 4 runs after Stage 3 (teardown of the old mitigation layer) and finishes the
transition: the allocator becomes the only code path, and the temporary tooling
used to bring it up is removed.

**Status:** In progress (Oct 2026, uncommitted on `m68k-register-alloc`). Done: steps 1
(flag removed), 2 (flag-guarded regfile branches removed; spills and noframe
subroutines keep the regfile), 3 (golden harness deleted), and 4 (`technical.rst`
and `todo.rst` updated). Step 5 is partly done: stage/step labels, design-section
references, and stale "legacy" and "yet" wording are removed from `codeGenM68k`;
the comment-length rules in `AGENTS.md` are not yet audited across the module.
The example programs run correctly on both Amiga targets (amiga500, amiga1200).

## Precondition

Stage 3 is complete, and the allocator path passes the Stage 3 property tests on
the full m68k example set.

## Inherited from Stage 3

- The default-path growth measured in Stage 3 (+237 lines over 20 amiga500 programs,
  +92 over 7 qemu68k programs) no longer applies: the allocator is now the default
  path. Re-measure against HEAD before the Stage 4 commit.
- Under the allocator, `%option noframe` subroutines (the amiga startup
  `init_system`) are never allocated. They keep regfile round-trips, which is 5
  bounce pairs per amiga program. Decision (Oct 2026): accepted as-is. Allocating
  noframe code is not planned.

## Work

1. **Remove the `-m68kregalloc` flag.** The allocator path is the only path.
   Remove the flag from the compiler options, its help text, and any docs or
   build scripts that mention it.

2. **Delete the old memory-everywhere code path** that the flag guarded: the
   branches that lower every vreg to `p8_regfile` regardless of allocation.
   The regfile remains for spills (§5), so keep the spill addressing path and
   the BSS-regfile last-resort policy from §7.1 #3.

3. **Delete the golden harness.** This covers:
   - the Stage 0 step 2 byte-identical corpus runner,
   - its test class,
   - all expected-output data files it compared against.

   Regression coverage is the Stage 3 property tests and the §8 unit tests.

4. **Update the docs** that describe the m68k backend:
   - `docs/source/technical.rst`: describe the allocator as the implementation.
   - `docs/source/todo.rst`: remove the "Deferred" entry for register allocation.

5. **Review and trim comments in the m68k code generator** (`codeGenM68k`).
   Stages 1-3 leave behind comments about the regfile, the peepholes, and the
   golden harness that no longer apply, so delete those. Then apply the project
   comment rules from `AGENTS.md` to the rest:
   - Inline comments inside a function body: one line at most.
   - KDoc on a declaration: three lines at most, unless a non-obvious contract
     cannot be stated shorter.
   - Remove comments that restate the identifier, the type, or the following line.
   - Keep only comments that record a non-obvious reason or a constraint a future
     edit would otherwise violate (for example, the A5/A6/D7 reservations and the
     byte `A7` step-by-2 rule).
   - Trim, do not rewrite: keep the wording of comments that already comply.
   This is comment-only work, so it is verified by the same `gradle build` as the
   rest of Stage 4.

## Open investigation: code size on tiny programs (from Stage 2 validation)

On programs made of many tiny subroutines, the allocator path emits slightly
more instruction lines than the memory-everywhere path (primes: +1%, +5 lines
on 890). The current hypothesis is `movem` prologue/epilogue pairs (7 added
lines there): every subroutine that uses allocated registers pays two lines
of callee-saved save/restore, which outweighs the eliminated d0 load/store
bounces at that size. The remaining `move.b d2,d0`-style lines look like
required call/return marshalling, not fat, but that has not been verified
line by line. ELF size still falls (-4% on primes) because the emitted
instructions are shorter, and every other corpus program shrinks on all
metrics. This is not a Stage 4 blocker and nothing here should change its
plan: it is recorded so the size effect is investigated (confirm the movem
hypothesis, check whether shrink-wrapping the saves/restores or revisiting
the caller-saved/callee-saved pool split would pay off) before anyone tries
to "fix" it.

## Exit criteria

- `gradle build` passes with no reference to the flag or the golden harness left
  in source, build config, or docs.
- The m68k example programs run correctly on `amiga500`, `amiga1200` (via
  `-emu2`/vamos where applicable) and `qemu68k`.
- The repository contains no golden or expected-output data files for the m68k
  backend.
