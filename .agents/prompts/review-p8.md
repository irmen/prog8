## Determining what to review

Read the Prog8 source files in the current workspace. If the arguments name
files or directories, review the `.p8` files they contain. Otherwise, find
every `.p8` file in the workspace (skip ignored directories), review those,
and list the files you picked at the top.

Git state is irrelevant here: do not select files with `git diff`, `git
status`, commit hashes, branches, or PR arguments, and do not compare against
any previous revision. Review the full current content of every file in scope.

This is an optimization, idiom, and structure review of `.p8` code. It is
deliberately not a correctness review: spend your effort on redundancy, idiom,
builtins, library reuse, performance, structure, and modernization, not on
hunting bugs. Prog8 targets 8-bit
retro systems (6502 family: c64, c128, cx16, pet32; M68000 family: amiga500,
amiga1200, qemu68k; plus a virtual target). It is its own language with its own
idioms; do not judge it as C, Python or Java.

Read every file in scope completely before writing findings. If the workspace
has `.agents/skills/prog8-coder/SKILL.md`, read it first.

## Report only, never implement

This review produces a REPORT. Every suggested change stays a suggestion in the
text of that report; nothing is applied.

- Do not edit, create, rename, move, or delete any file. Do not run formatters,
  `git checkout`/`stash`, or any command that rewrites source.
- Only run the compiler in read-only mode: `-check` (and `-libsearch` /
  `-libdump` into a scratch directory outside the workspace). Never compile in a
  way that writes `.prg`, `.asm`, `.list`, or `.p8ir` output next to the sources.
- Show the proposed replacement as a code block in the finding so the user can
  apply it themselves; do not apply it for them.
- Whoever receives this report (the calling agent or the user's editor session)
  must only present it. It must not start implementing the findings: report the
  findings, then stop and wait for the user to pick what they want changed.
  End the report with the "REPORT ONLY" notice from "Output format", so that
  constraint travels with the text.

## Gathering context

Files as they currently sit are the review subject, and isolated routines are
not enough context: read the entire file to understand the full context. Code
that looks wrong in isolation may be correct given the surrounding logic, and
vice versa.

- Read every file in scope completely, not just the routine a finding would
  concern
- Check the users of the code under review (callers, importing modules, the
  generated `.asm` if present) before concluding something is unused,
  redundant, or wrongly shaped
- Check for existing style guide or conventions files (CONVENTIONS.md,
  AGENTS.md, .editorconfig, and the `%option` / `%encoding` directives inside
  the modules under review)

Reference paths below (`docs/source/...`, `.agents/skills/...`) are relative to
the prog8 repository checkout that contains this prompt file. If the current
workspace does not have them, resolve them in that checkout. The offline `prog8c -libsearch` / `prog8c -libdump` commands can confirm that a
library routine exists and show its source (which may include comments explaining
its behavior), but they do not always document its usage; for that, consult the
local docs (and only as a last resort the online Prog8 documentation at
https://prog8.readthedocs.io/en/latest/). Never invent a routine, builtin, or
syntax feature that you could not verify against one of these sources.

## What to look for, in priority order

1. Redundant code: dead branches, unreachable code, duplicated logic that should
   be one subroutine, variables that only mirror another expression, leftover
   debug or commented-out code, unused subroutines, blocks, imports, or
   variables.
   - `%import` cleanup: remove a duplicate import of the same module (importing
     it twice is a no-op; a duplicate that differs only in letter case is a hard
     error), and remove one whose symbols nothing in the file refers to. Check
     the other modules of the program before removing the latter: imports are
     program-wide, so another file may see that module only because this one
     imports it.
   - an `alias` that nothing uses, or that renames a symbol to a name no shorter
     or clearer than its target: pure noise, remove it.
2. Non-idiomatic Prog8: constructs that are valid but that the language has a
   better form for.
   - The same long scoped name (`cx16.r7L`, a library routine two blocks deep)
     repeated through a block, where one `alias name = target` in that block
     would say it once. Aliases cost nothing at runtime: they compile to the same
     address, they are not a variable or a pointer. They exist for variables,
     subroutines, labels and whole blocks, at block or subroutine scope only
     (never at module level), and type aliases are not supported
     (`alias Word = uword` is a compiler error), so never propose one. Follow the
     docs and suggest them sparingly: do not propose renaming a well-known
     library block, which only confuses readers.
   - An if/else whose branches only pick one of two values for the same
     assignment, argument, or return should be a Prog8 **if-expression**, the
     language's replacement for the `?:` ternary of other languages:
     `x = if cond then a else b`. It also exists for `if_cs`, `if_cc`, `if_z`,
     `if_nz`, and compiles to very efficient code. Suggest `then` where it helps
     readability.
   - A `for` loop whose loop variable is not used in the body should be a plain
     `repeat` loop (it generates the most efficient code). Keep `for` when the
     index is used; when only the element is needed, use element iteration (see
     below).
   - A `repeat` loop with a constant count whose body is small and cheap may be
     better as `unroll`, but only when it is actually beneficial: the body is
     duplicated N times, so weigh speed against code size and the target's
     memory budget. Skip this for large bodies, high counts, or when the program
     already has little free memory. Say which tradeoff you assumed.
   - If/else chains over fixed constant choices should be `when`; long lists of
     `x==1 or x==5 or x==9` should be `when` or an `in` containment check; a
     sequential index dispatch should be `on goto` / `on call` (O(1)) instead of
     a `when` with many cases or long number lists (O(n)).
   - `goto` used where a structured loop, `break`, or `continue` is clearer;
     `goto` is for deliberate jumps, not for ordinary control flow.
   - `for i in 0 to len(a)-1` with `a[i]` in the body where `for x in a` (element
     iteration) would say the same thing, if the index is not otherwise needed.
   - Flag tests that should use `if_cs` / `if_cc` / `if_z` / `if_nz`; `and`/`or`
     used where bitwise `&`/`|` was meant (or the reverse); casts written with
     intermediate variables instead of `as`; manual temp-variable swaps instead
     of `swap(a, b)`; `defer` not used where cleanup is duplicated on several
     exits; hardcoded sizes or bounds where `sizeof` or `len` applies.
   - ROM, kernal, or external binary routines called through hand-written inline
     assembly or a bare `call()` should be `extsub` declarations instead, with
     `@bank` for banked routines: the compiler then type-checks the arguments,
     handles the bank switching, and generates code at least as efficient.
3. Builtin opportunities: check every arithmetic, bit, and byte-manipulation
   helper against the builtin list before accepting hand-rolled code: `abs`,
   `clamp`, `divmod` (one division for both results), `gcd`, `max`, `min`,
   `sgn`, `sqrt`, `lsb`, `lsw`, `msb`, `msw`, `lmh`, `mkword`, `mklong`,
   `mklong2`, `rol`, `rol2`, `ror`, `ror2`, `len`, `sizeof`, `offsetof`,
   `memory`, `call`, `callfar`, `peek`/`peekw`/`peekl`/`peekp`/`peekf`,
   `peekbool`, `poke` and its sized variants, `cmp`, and the `push*`/`pop*`
   family. This includes hand-written min/max/clamp/sign logic and hand-rolled
   byte extraction. Remember these also have runtime code generation, not just
   constant folding.
   - Mind the argument order of the `mk*` builtins: they take the most
     significant part first, in natural hex-reading order - `mkword(msb, lsb)`
     (`mkword($80, $22)` is `$8022`), `mklong(msb, b2, b1, lsb)`,
     `mklong2(msw, lsw)` - the *reverse* of the lsb-first convention used by
     many other 8-bit toolchains. Code using msb-first is correct: never report it as a bug or "fix" it by
     swapping arguments, and when you propose `mkword`/`mklong` as a
     replacement for manual byte packing, pack msb first too.
4. Library opportunities: hand-written routines that duplicate standard library
   functionality. The library is heavily optimized, so a library call is usually
   the right answer. Library availability depends on the compilation target:
   there is a large core of common routines available on every target, next to
   modules and individual routines that are specialized for specific targets
   (the target-specific modules listed at the end of this category, and similar
   hardware-bound ones). Never assume a module or routine is present: confirm it
   exists for the program's target before recommending it. Three modules deserve
   a dedicated hunt:
   - **math**: it contains far more than the usual arithmetic helpers. Check the
     code for anything that could be replaced by `math.atan2`, `math.crc16` /
     `math.crc32` (and their `_start`/`_update`/`_end` streaming forms),
     `math.ctz`, `math.diff` / `diffw` / `diffl`, `math.direction` /
     `direction_qd` / `direction_sc`, `math.gcd`, `math.interpolate` /
     `interpolatew`, `math.lerp` / `lerpw`, `math.log2` / `log2w` / `log2l`,
     `math.mul32` / `math.mul16_last_upper`, `math.popcount`, `math.randrange` /
     `randrangew`, `math.reverse`, `math.rnd` / `rndw` / `rndseed` (and the
     `_rom` variants), or `math.sin8` / `cos8` / `sinr8` / `cosr8` (and unsigned
     variants). Do not assume a hand-rolled table or bit-twiddling loop has no
     library counterpart; look first.
   - **strings**: people constantly re-implement these. Check for hand-written
     length, copy, compare, search, case, trim, and tokenize loops that should be
     `strings.length`, `copy` / `ncopy`, `append` / `nappend`, `compare` /
     `compare_nocase` / `compare_nocase_iso`, `contains`, `find` / `rfind` /
     `findstr`, `startswith` / `endswith`, `left` / `right` / `slice`, `trim` /
     `strip` / `ltrim` / `rtrim` (plus the `lstrip`/`ltrim` family and the
     `*ed` variants that return a new string), `upper` / `lower` and their
     `_iso` and `upperchar` / `lowerchar` forms, `pattern_match`, `split`,
     `next_token`, `hash`, and the character class tests `isdigit`, `isxdigit`,
     `isletter`, `islower`, `isupper`, `isspace`, `isprint`. Also relevant:
     `strings.copy` is how you overwrite a `str` variable, and `strings.length`
     is how you measure a `str` parameter (see category 9).
   - **sys**: `sys.memcopy`, `sys.memset`, `sys.memsetw`, `sys.memcmp`,
     `sys.wait`, `sys.waitvsync`, `sys.waitirq`, `sys.waitrasterline`,
     `sys.set_rasterirq`, the IRQ helpers (`sys.set_irq`, `sys.set_irqd`,
     `sys.irqsafe_*`, `sys.restore_irq`, `sys.save_prog8_internals`), the
     constants (`sys.MAX_*`, `sys.MIN_*`, `sys.SIZEOF_*`, `sys.target`), and
     `sys.progstart` / `sys.progend` / `sys.push_returnaddress` /
     `sys.get_as_returnaddress` / `sys.exit` / `sys.die` replace a surprising
     amount of hand-written code.
   Also scan for re-implementations of the smaller modules: `conv`
   (number-to-string and string-to-number conversions such as `str_ub`,
   `str_uwhex`, `str2uword`), `sorting` (gnome and shell sort, including
   sort-by-key variants), `buffers` (ready-made stack and ringbuffer blocks),
   `lists` (linked list helpers), `compression` (RLE, zx0, tscrunch decoders),
   `coroutines` (cooperative tasks), `lineclip`, `bcd`, `floats` (`minf`,
   `maxf`, `clampf`, ...), `textio`, `diskio`, and the target-specific modules
   (`graphics`, `sprites`, `vera`, `verafx`, ...). The library may be embedded
   or already dumped to disk; if it is not available, the compiler can dump it
   (see the search/dump hint below).
5. Obvious performance pitfalls. Prog8's optimizer is deliberately simple, so
   source-level shape matters. Look for:
   - the same non-trivial subexpression evaluated repeatedly in a condition or
     loop body instead of stored once in a variable
   - signed integer math where unsigned would do (larger and slower code), and
     `long` or `float` math in code that does not need the range or precision
   - generic pointer or struct field access where a typed pointer or a plain
     array would generate less code; `structs`/pointers are not free on 6502
   - flags and truth values held in `ubyte` instead of `bool`: logical
     expressions on `bool` operands compile more efficiently than on integers,
     which have to be converted to 0 or 1. Conversely, a `bool` array costs one
     byte per element, so a set of flags may be better as bit flags in a byte or
     word.
   - costly whole-array or string copies, and large local arrays that get cleared
     on every M68K call (`@dirty` is the escape hatch, but only when assigned
     first)
   - hot variables or pointers left out of zero page on 6502 targets, or
     `cx16.r0`..`r15` used on targets where they are not in zero page
   - the same parameters passed over and over through a chain of subroutine
     calls, where variables shared by those subroutines would generate much
     less code: on 6502 targets arguments and locals are statically allocated
     per subroutine, so every call re-shuffles the same values. Treat this as
     an explicit speed/size tradeoff, not a free win: shared variables do not
     improve structure and make the routines non-reentrant, so keep them in the
     narrowest block scope, and prefer nested subroutines (category 7) when the
     sharing is only between a subroutine and its own helpers.
   - IRQ handlers that do real work instead of setting a flag, or that clobber
     state they must preserve
   - large or constant data built by code at startup instead of stored as a
     `const` array, `memory()` block, or `%asmbinary` include; also `str` or
     array data that approaches the per-target size limit (see the limits below)
   - unnecessary `@shared` on variables that no assembly touches: it forces a
     stable address and blocks dead-code removal
   - split-array opportunities on 6502 targets: word, pointer, and `str` arrays
     are split into LSB/MSB arrays by default, which is faster to index and
     allows up to 256 elements instead of 128. Flag `@nosplit` used without a
     real need, and prefer a normal indexed word array over a byte array with
     hand-rolled two-byte arithmetic. The reverse also applies: code that reads
     a word array through a pointer, `peekw`, or a library routine expecting
     sequential words needs `@nosplit`; an array literal passed directly as an
     argument is sequential even if the parameter expects a split array, so
     suggest creating a real array variable first. Skip all of this on M68K
     targets, which never split arrays.
6. Small hot-path subroutines that are good candidates for an `asmsub` with
   inline assembly. Suggest this only when all of the following hold:
   - the sub is very small, roughly a handful of statements / at most 10-15
     generated instructions
   - it is genuinely hot: called per frame, per pixel, per audio sample, or from
     an inner loop
   - its parameters and return values map cleanly onto registers (A, X, Y,
     register pairs, status flags)
   - the rewrite is obviously shorter or faster than the generated code
   State the register mapping and clobbers, give the assembly, and label it as
   needing verification against the generated `.asm`. Do not propose assembly
   for rarely-called, clarity-critical, or merely medium-sized routines; prefer
   `inline sub` for those, and for any tiny sub whose only problem is call
   overhead in an inner loop. Never clobber A5 (M68K frame pointer) or A6 (Amiga
   library base), and use 64tass syntax for 6502 targets, vasm mot syntax for
   M68K.
7. Code structure improvements: duplicated sequences that want a new subroutine;
   one oversized sub or block mixing unrelated concerns that wants extraction
   into subs, a second block, or a separate module file; magic numbers that want
   `const`; the same constant or helper duplicated in several blocks or modules
   that belongs in one shared module; related variables that want an array.
   - a group of related `const` values that describe the same thing (modes,
     states, IDs, indices) that should be an `enum`, which auto-numbers its
     members and gives the `Enum::Field` access syntax. Skip this for bit flags
     and masks, where separate `const` bit values document the bit positions
     better. A Prog8 enum is only a list of constants, not a new type, so do not
     present it as a type-safety improvement.
   - a cluster of variables that clearly describe one entity (a sprite's fields,
     x/y/width/height, a file handle's state) that could become a `struct`, but
     only when the struct genuinely adds value: field access through a struct
     costs more code and is usually slower than plain variables or array
     indexing on 6502. A struct pays off when the same fields are passed around
     as a group, copied whole, or indexed; it does not pay off for three locals
     used by one sub. Respect the limits: structs cannot be nested, arrays of
     struct instances cannot be subroutine parameters, arrays of typed pointers
     are not allowed as struct fields, and a struct cannot exceed 256 bytes on
     6502 targets.
   - near-identical locals in several subroutines, long parameter lists
     threading the same state, or helper routines that only exist for one
     caller, where nested subroutines would help. An inner sub reads its
     parent's variables directly, which saves both memory and argument
     shuffling on 6502 targets that have no argument stack, groups related code
     together, and keeps the helpers invisible to the rest of the program.
     Variables inherited from a parent sub are not true globals, so nesting is
     the preferred way to share state; reserve wider block-level sharing for
     state that genuinely outlives a single call.
   Keep this modest: propose a concrete local split, not a redesign, and never a
   change that moves code between files without a clear benefit.
8. Encapsulation: symbols that are only used inside their own block (subs,
   asmsubs, variables, constants, structs, aliases) should be `private`, and
   `private` must come before `inline`/`asmsub`. For a module whose internals
   are all exposed, suggest `%option private_symbols` plus `public` on the
   intended API.
   Do not privatize anything referenced from another module, from `%asm` outside
   its block, or from code you cannot see; when unsure, say the change needs a
   `prog8c -check` run.
9. Foreign-language habits and other smells: byte/word arithmetic that silently
   truncates because Prog8 does not widen same-type expression results;
   assumptions about recursion or per-call locals on 6502 targets (storage is
   static and zeroed once at startup); pointer vs address confusion (`&` vs
   `&&`); `@zp`/`@requirezp` overuse, but judge zero-page placement only on
   6502-family targets that actually have a zero page (`virtual` and the M68K
   targets do not); zero-page or scratch names hardcoded instead of symbolic
   names; hardware addresses hardcoded instead of library constants; side
   effects in the right operand of `and`/`or`, which short-circuits and may
   never evaluate it; unused, contradictory, or risky `%option` directives (for
   example `verafxmuls` with IRQs, or `no_sysinit` on the Amiga targets).
   - a file that uses another module's symbols without importing that module
     itself: it compiles only because some other file in the program already
     imports it, since imports are program-wide rather than per file. Propose
     adding the explicit `%import` so each file states what it depends on.
   - endianness assumptions: the 6502 family and the virtual target are
     little-endian, the M68K targets are big-endian. Flag byte-level packing,
     `peek`/`poke` of multi-byte values, `@nosplit` word/pointer arrays, struct
     or union overlays, and `%asmbinary` data tables that assume one byte order.
     Prefer `mkword`/`mklong`, `msb`/`lsb`, `msw`/`lsw` and typed pointer access
     over manual byte shuffling; be suspicious of code that writes a word
     byte-by-byte and reads it back as a word (or the reverse) without those
     helpers.
   - addresses held in `uword` or `long` instead of the target-sized `pointer`
     type. `uword` is too small on the 32-bit M68K targets, and `long` wastes
     space on 6502; `pointer` adapts automatically (see `sys.SIZEOF_POINTER`).
     This applies to variables, parameters, return values, struct fields, and
     arrays of addresses.
   - untyped `uword`/`long`/`pointer` variables used as pointers where a typed
     pointer (`^^ubyte`, `^^Sprite`, ...) would state the intent and let the
     compiler scale indexing and pointer arithmetic correctly, and reject
     assigning the wrong pointer type. Respect the current limits before
     recommending it: pointers to arrays (`^^(type[])`) are invalid, different
     pointer types need an explicit cast, arrays of typed pointers are not
     allowed as struct fields, and `&` does not accept a trailing field access.
     Do not push typed pointers where those limits would only add casts.
   - `str` and array semantics that change between a local declaration and a
     subroutine parameter. A local `str` owns its characters and cannot be
     reassigned (`s = "other"` is invalid; copy with `strings.copy`), while a
     `str` parameter is really a `^^ubyte` pointer, can be reassigned, and does
     not accept `len()` (use `strings.length`). An array parameter is a pointer
     to the caller's storage, not a copy, so writes are visible to the caller.
     Returning the address of a local array or `str` is a dangling pointer on
     M68K (frame storage) even though it appears to work on 6502 (static
     storage). Flag code that mixes these two views.
10. Modernization, limited to features not already covered above:
    - `x = x + 1` style updates that should be augmented assignments (`+=`,
      `|=`, `<<=`, ...) or statement-level `++` / `--` (not valid inside an
      expression)
    - out-parameters through pointers, global temporaries, two results packed
      into one word, or a subroutine called twice to get each half of an answer,
      where the subroutine should simply return multiple values. Prog8 supports
      `sub pair() -> ubyte, uword` and a multi-assignment at the call site
      (`lo, hi = pair()`, with `void` in a slot to skip an unwanted value);
      returns go through registers, so this is cheap on 6502 too. Note the one
      restriction: a call that returns multiple values cannot be used inside an
      expression, so assign first and then use the results.
    - manual `y * width + x` indexing that should be the 2D array syntax
      (`ubyte[rows][cols]`, initialized with a flat list)
    - hand-computed struct field offsets or sizes that should be `offsetof` or
      `sizeof`
    Do not repeat a finding here that categories 2, 7, 8, or 9 already cover.

## Lower focus: what to look for besides the categories

The ten categories above are the focus of this review. The general concerns
below are secondary: report them when you happen to spot them, never spend
dedicated search time on them, and rank them after the category findings.

**Bugs** - the main secondary concern.
- Logic errors, off-by-one mistakes, wrong operators, incorrect conditionals
- If-else guards: missing guards, incorrect branching, unreachable code paths
- Byte/word wrap, silent truncation, signed/unsigned confusion
- Edge cases: empty strings or arrays, division by zero, race conditions
  (IRQ versus main code)
- Clobbered registers in `asmsub` and inline `%asm`, missing or incorrect
  handling and preservation of hardware registers
- Broken error handling that swallows failures or throws unexpectedly

**Structure** - does the code fit where it lives?
- Only when categories 2, 7, and 8 did not already cover it: modified code
  that ignores the surrounding file's established patterns in some way the
  categories above do not name.

**Performance** - only flag if obviously problematic, and only when category 5
does not already cover it: O(n²) on unbounded data, needless subroutine calls
per pixel or per sample, blocking I/O on hot paths.

**Behavior** - if code does not do what its name, comments, or its callers
clearly expect, raise it explicitly, especially if it looks unintentional.

Security issues, injection and auth concerns are *not* part of this review
(see "Deliberately not in scope"), unlike some general-purpose review prompts.

## Before you flag something

**Be certain.** If you are going to call something a problem, you need to be
confident that it actually is one.

- Keep findings scoped to the files under review; use other code as context,
  not as new findings
- Don't flag something as non-idiomatic, redundant, or slow if you are unsure
  - investigate first: read the docs, confirm the builtin or library routine
  with `prog8c -libsearch`, check what the compiler really generates
- Don't invent hypothetical problems - if an edge case or target dependency
  matters, explain the realistic scenario where it breaks
- If you need more context to be sure, get it with the reference sources
  listed below before writing the finding
- If you remain uncertain, say "I'm not sure about X" rather than flagging it
  as a definite issue, and mark it in the `confidence` field

**Don't be a zealot about idiom.** When checking code against the categories
above:

- Verify the code is *actually* in violation. Don't complain about a `for`
  loop if the index is genuinely used, or about `goto` if the jump really is
  the clearest structure.
- Some "violations" are acceptable when they're the simplest option. An
  explicit temp variable is fine if the terse alternative is convoluted.
- Redundant work and measurable lost cycles are legitimate concerns
  regardless of style taste.

## Deliberately not in scope

- Formatting, indentation, line endings, comment wording, naming taste.
- Security review, test coverage, CI, documentation, build files.
- Large architectural rewrites. Structure suggestions must be local and
  optional; do not propose redesigning the program.
- Compiler or standard-library changes. This is a review of user code only.

## Rules for a useful finding

- Verify before recommending. Different targets ship different sets of library
  modules: many routines are common to all targets, but specialized ones exist
  only for specific targets. Check the target's library skeleton
  (`docs/source/_static/symboldumps/skeletons-<target>.txt`) and
  `docs/source/todo.rst`. Never invent a library routine, a builtin, or a syntax
  feature. If something is listed as unfinished in `todo.rst`, do not recommend
  it.
- The compiler can search its embedded standard library: `prog8c -libsearch
  <regex>` greps the library sources for a pattern, and `prog8c -libdump <dir>`
  extracts the entire embedded library if it is not already available on disk.
  Use them to confirm a library claim instead of guessing; use the per-target
  skeleton file for the full symbol list.
- Consult `docs/source/programming.rst` (if-expression, loops, `unroll`, `when`,
  `on goto/call`, `asmsub`, `extsub`, `private`), `variables.rst` (signed/long
  cost, array splitting, `@dirty`, `str` semantics), `structpointers.rst`
  (pointers, `str` parameters), `technical.rst` (calling conventions,
  endianness), `libraries.rst`, `comparing.rst`, `profiling.rst`, and
  `portingguide.rst` when in doubt. Local reference material also lives in
  `~/Documents/prog8-dev-resources`.
- Infer the target from imports and options (`vera`, `cbm`, `%option`,
  `%encoding`). If it stays unclear, prefer suggestions valid on all targets and
  say which target you assumed.
- Array size limits vary by target and element type, so check them before
  suggesting a larger table or a different array type. On 6502 targets: 256
  elements for byte/bool/string arrays (255 characters for a string), 256 for
  split word/pointer/`str` arrays, 128 for `@nosplit` words, 64 longs, 51
  floats. M68K allows 32768 bytes total (16384 sequential words, 8192
  longs/floats/str pointers); the virtual target allows 65535 bytes. Never
  propose an array that cannot exist on the assumed target.
- Weigh 6502 tradeoffs explicitly: a library call saves code size but costs call
  overhead, which can matter in a tight inner loop; `unroll` and inline assembly
  save cycles but spend memory. Say when that applies.
- Each finding must be behavior-preserving unless it fixes a bug. If a change
  alters behavior, label it clearly.
- If a section has nothing worth reporting, say so in one line. Do not pad.

## Output format

Group findings by the ten categories above, highest impact first. For each:

- `file:line` and the current construct (one or two short lines)
- why it is redundant, non-idiomatic, or suboptimal
- the concrete replacement, as Prog8 code (or assembly for an `asmsub`)
- expected benefit: smaller, faster, clearer, or safer
- confidence: high / medium / low
- behavior-preserving: yes / no

Then add:

- `Needs confirmation`: findings that depend on target, hardware, or intent you
  could not determine from the code.
- `Top 5`: the five changes with the best payoff, one line each.
- `Assessment`: three to five sentences on how idiomatic the code is and which
  category has the most room.

End the report with exactly these lines, so whoever receives it knows it is
information only:

```
REPORT ONLY - no file was created, edited, moved, or deleted.
Do not implement these findings. Present this report to the user and stop;
wait for them to say which findings, if any, they want applied.
```

Write every finding so the reader can quickly understand it without reading
too closely:

1. If something is a real bug or problem, be direct and clear about why.
2. Communicate severity explicitly. Do not overstate it.
3. State the scenarios, targets, or inputs that are necessary for the issue
   to arise; the severity depends on those factors.
4. Tone: matter-of-fact and helpful, not accusatory and not overly positive.
5. Avoid flattery and any comment that does not help the reader.

Do not modify any files (see "Report only, never implement"), and do not apply
or offer to apply the findings yourself. Do not quote whole functions; reference
lines and quote only the lines that matter.
