.. _history:

.. index:: History

Prog8 Compiler Release History
==============================

A condensed timeline of major releases and significant changes in the Prog8 programming language compiler.

.. note::
    **Python to Kotlin Transition:** The original Prog8 compiler (pre-1.0) was written in **Python**. In early 2022, the compiler was completely rewritten in **Kotlin** starting with v1.0 Beta. The Kotlin rewrite brought significant improvements in performance, IDE support, type safety, and maintainability. This document covers the Kotlin-era releases.


Summary of Major Language Milestones
------------------------------------

+-----------+-------------------------------------------------------------+
| Version   | Milestone Feature                                           |
+===========+=============================================================+
| 1.6       | Address-of operator ``&``                                   |
+-----------+-------------------------------------------------------------+
| 1.10      | ``when`` statement (multi-way branch)                       |
+-----------+-------------------------------------------------------------+
| 1.11      | Structs (first introduction)                                |
+-----------+-------------------------------------------------------------+
| 3.0       | CPU registers removed, new loop syntax                      |
+-----------+-------------------------------------------------------------+
| 4.0       | CommanderX16 target support                                 |
+-----------+-------------------------------------------------------------+
| 5.0       | Calling convention overhaul (registers for returns)         |
+-----------+-------------------------------------------------------------+
| 6.0       | Virtual registers ``cx16.r0-r15``                           |
+-----------+-------------------------------------------------------------+
| 7.0       | Structs removed                                             |
+-----------+-------------------------------------------------------------+
| 8.3       | ``bool`` datatype                                           |
+-----------+-------------------------------------------------------------+
| 9.0       | ``min``/``max``/``clamp`` builtins, ``@split`` arrays       |
+-----------+-------------------------------------------------------------+
| 9.3       | Software evaluation stack removed                           |
+-----------+-------------------------------------------------------------+
| 9.7       | Unicode identifiers, ``continue`` statement                 |
+-----------+-------------------------------------------------------------+
| 10.0      | Short-circuit boolean logic                                 |
+-----------+-------------------------------------------------------------+
| 10.3      | ``void`` keyword for multi-return                           |
+-----------+-------------------------------------------------------------+
| 11.0      | ``const long``, split arrays by default                     |
+-----------+-------------------------------------------------------------+
| 11.1      | Multi-value returns from subroutines                        |
+-----------+-------------------------------------------------------------+
| 11.4      | ``on .. goto`` jump tables                                  |
+-----------+-------------------------------------------------------------+
| 12.0      | **``long`` datatype**, **structs reintroduced**,            |
|           | **typed pointers**                                          |
+-----------+-------------------------------------------------------------+
| 12.1      | ``swap()`` builtin, ``psg2`` module, PET32 floats/gfx/snd,  |
|           | ``strings.split()``/``next_token()``, C128 2MHz mode        |
+-----------+-------------------------------------------------------------+
| 12.2      | Long loop support, ``private``, enum syntax, 2D arrays,     |
|           | new modules (serial, wavfile, adpcm, lineclip)              |
+-----------+-------------------------------------------------------------+
| 12.3      | Deterministic module search order, -srcdirs priority        |
+-----------+-------------------------------------------------------------+


Breaking Changes Summary
------------------------

Major breaking changes that require code modifications when upgrading:

- **v3.0**: CPU registers (A,X,Y) removed, loop syntax changed
- **v4.0**: Module renames, ``mkword()`` parameter order flipped
- **v5.0**: Calling convention overhaul
- **v5.3**: Explicit ``&`` required for pointer assignments
- **v7.0**: Structs removed, ``%target`` directive removed
- **v9.0**: ``-target`` now required
- **v9.3**: Eval stack removed (affects low-level code)
- **v10.0**: ``push``/``pop`` moved to ``sys`` module
- **v10.2**: Stricter boolean types
- **v10.4**: Namespace reorganization (``cx16`` to ``cbm``)
- **v12.0**: Structs reintroduced (different from v1.11), typed pointers
- **v12.1**: Combined virtual register renames (``R0R1_32`` → ``R0R1``, etc.)
- **v12.2**: ``swap()`` is now a statement (not a function), ``math.crc16()`` and ``math.crc16_start()`` require new parameters, ``private`` is now a reserved keyword
- **v12.3**: Deterministic module search order, filesystem priority over internal resources


2019–2022 — Early Development (Python to Kotlin Transition)
-----------------------------------------------------------

**v1.0–v1.2 Beta** — January–February 2022
    - **First public Kotlin release** (v1.0, 26 Jan)
    - **MAJOR: Compiler rewritten from Python to Kotlin** — complete implementation language change
    - Zeropage variable allocation and block-level variable initialization
    - Gradle build system, math optimizations, ``floatsafe`` zeropage mode

**v1.3–v1.6** — March–April 2022
    - ``asmsub`` routines can return values in expressions
    - **Address-of operator ``&`` implemented** — replaces ``memory`` keyword
    - For loops can iterate over literal collections
    - New builtins: ``strlen()``, ``sqrt16()``, ``pow()``, ``powf()``

**v1.7–v1.11** — July 2022
    - **Array size optional** with initializer
    - **``%asmbinary`` directive** for assembly inclusion
    - **AST-based Virtual Machine** with optimization passes
    - **``when`` statement** — new multi-way control flow
    - **Structs added** — composite datatype feature

**v1.20** — July 2023
    - **Struct literals** — inline struct initialization


2023 — Language Maturation
--------------------------

**v2.0–v2.3** — May–July 2023
    - Major compiler speedup (scope lookups, code generation)
    - Stricter type checking, optimized ``swap()``
    - **String value reassignment**, new string functions (``leftstr``, ``rightstr``, ``substr``)
    - Subroutine inlining optimization

**v3.0–v3.2** — July–August 2023
    - **CPU register variables (A, X, Y) removed** — use inline assembly
    - **Loop syntax changes**: ``repeat-until`` to ``do-until``, new ``repeat X {}``, ``forever {}`` removed
    - **``continue`` statement removed**
    - **``sizeof()`` function added**
    - Optimized in-place/augmented assignments — major performance boost

**v4.0–v4.1** — August–September 2023
    - **CommanderX16 target added** — major platform expansion
    - **Floating point support for CX16**, VERA registers, 65c02 features
    - **``lsl()``/``lsr()`` removed** — use ``<<=1``/``>>=1``
    - Module renames (``c64scr`` to ``txt``), ``mkword()`` parameter order flipped

**v4.2–v4.6** — September–October 2023
    - **Cross-platform C64/CX16 compatibility**
    - **``.w`` postfix removed** — breaking syntax change
    - **``%option no_sysinit`` directive** — skip system initialization
    - **String escape characters**: ``\\xHH``, ``\\'``
    - **``diskio`` module introduced** — file I/O routines
    - **Automatic string comparisons** by value

**v5.0–v5.4** — November 2023–January 2024
    - **Calling convention overhaul**: args via variables, returns via registers
    - **Explicit ``&`` required** for string/array pointer assignments
    - **``in`` containment operator**: ``if xx in [1,2,3]``
    - **Experimental C128 target**
    - **Pipe operator ``|>``** and **string encoding syntax** (``iso:"hello"``)
    - **``@requirezp`` flag** — force zeropage allocation
    - **Rewritten variable allocation** — large program size savings


2024 — Advanced Features
------------------------

**v6.0–v6.4** — January–March 2024
    - **Virtual registers ``cx16.r0..r15``** for CX16 and C64
    - **New builtins**: ``target()``, ``offsetof()``, ``memory()``, ``cmp()``
    - **``gfx2`` module** — enhanced graphics with highres modes
    - **``peekw()``, ``pokew()``** — word memory access
    - **IRQ handling routines** for CX16
    - **Improved RNG** with seeded variants (``rndseed``, ``rndseedf``)
    - **Assembly codegen completed** — all expression types supported

**v7.0–v7.8** — June 2024–February 2025
    - **Struct feature removed** — rewrite as separate variables
    - **``%target`` directive removed** — use CLI options only
    - **Software evaluation stack removed** — frees memory page and X register
    - **PET32 target** — Commodore PET 4032 support
    - **2x faster multiplication** and square root operations
    - **New modules**: ``verafx``, ``emudbg``, ``monogfx``, ``sprites``
    - **Builtins**: ``setlsb()``, ``setmsb()``, ``math.diff()``, ``math.diffw()``

**v8.0–v8.13** — April 2024–May 2025
    - **R39 CX16 ROM support**
    - **``**`` operator removed** — use ``floats.pow()``
    - **Experimental VM target** with ``syscall`` builtin
    - **API reorganization**: trig/float functions moved to ``math``/``floats`` modules
    - **``bool`` datatype introduced** — optimized true/false (0/1)
    - **BSS section** — uninitialized variables, reduced PRG size
    - **``divmod()``, ``divmodw()`` builtins**
    - **Major codegen optimizations** — significantly smaller and faster code

**v9.0–v9.7** — June–December 2024
    - **``-target`` now required** (c64 no longer default)
    - **New builtins**: ``min()``, ``max()``, ``clamp()``
    - **``@split`` storage class** — efficient LSB/MSB array storage
    - **``cbm`` module** — all CBM kernal routines
    - **Boolean ``when`` conditions**
    - **Underscores in numbers**: ``320_000``
    - **Multiple declarations/assignments**: ``ubyte x,y,z`` and ``x=y=z=calculate()``
    - **``continue`` statement** for loops
    - **Unicode identifiers**: ``knäckebröd``, ``π``
    - **Negative array indexing** (Python-style)
    - **Range containment**: ``if x in 10 to 100``


2024–2026 — Modern Prog8
------------------------

**v10.0–v10.5** — January–November 2024
    - **Short-circuit boolean evaluation** (McCarthy logic)
    - **Stricter boolean types** — no longer equivalent to bytes, require explicit casting
    - **``void`` keyword** — skip unused return values in multi-return assignments
    - **Namespace reorganization**: non-X16 variables moved from ``cx16`` to ``cbm``
    - **Builtins removed**: ``sort``, ``reverse``, ``any``, ``all`` to ``anyall`` module
    - **Compiler renamed to ``prog8c``**
    - **``defer`` statement** — delayed execution for resource cleanup
    - **If-expression**: ``result = if x>10 "yes" else "no"``
    - **Memory alignment**: ``@alignword``, ``@alignpage``, ``@align64``
    - **Array literals with repetition**: ``[42] * 99``

**v11.0–v11.4** — December 2024–June 2025
    - **``const long`` numbers** — 32-bit integer literals
    - **``goto`` can jump to calculated addresses**
    - **Word arrays split by default** (LSB/MSB in separate arrays)
    - **Subroutines can return multiple values**
    - **Multi-value variable initialization**: ``ubyte a,b,c = multi()``
    - **``%output library`` and ``%jmptable`` directives** — loadable libraries
    - **``%option romable``** — ROM-compatible code warnings
    - **ROMABLE programs** — no inline variables or self-modifying code
    - **Range choices in ``when`` statements**
    - **Foenix256 target**
    - **Boolean virtual registers**: ``cx16.r0bL``, ``cx16.r0bH``
    - **``on .. goto`` / ``on .. call``** — efficient jump tables
    - **Float to int casts** (truncating)
    - **Double buffering** in monogfx module

**v12.0–v12.1** — November 2024–February 2026
    - **``long`` datatype** — native 32-bit signed integers
    - **Structs reintroduced** — grouping multiple fields together
    - **Typed pointers** — can point to structs and specific types (not just ``uword`` addresses)
    - **PET32 modules**: ``petgfx``, ``petsnd``, ``diskio``
    - **New builtin**: ``swap()``
    - **C128 enhancements**: ``fast()``/``slow()`` for 2MHz mode
    - **String operations**: case-insensitive comparison, ``strings.split()``, ``strings.next_token()``
    - **Virtual target improvements**: ``f_seek()``, ``f_tell()``, ``diskname()``, ``loadlib()``

**v12.2** — May 2026
    - **Long loop support** — for-loops with long counters on 6502, long math (division, sqrt, min/max/clamp/abs)
    - **``private`` keyword** — for structs, enums, aliases, variables, and subroutines
    - **Const pointers** — compile-time constant-folding for const pointers
    - **``enum`` syntax** — concise constant list declarations
    - **2D array support** — ``ubyte[3][4] matrix``
    - **``swap()`` is now a statement** — more efficient than the previous builtin function
    - **``math.crc16()`` signature changed** — now requires ``initvalue`` and ``xorout`` parameters for flexibility
    - **New compiler options**: ``-daemon`` (IDE integration), ``-nostdlib``
    - **New modules**: ``serial`` (CX16 UART+ZiModem), ``lineclip``, ``wavfile``, ``adpcm``
    - **``-libsearch`` fuzzy search fallback** — finds libraries even with partial names


**v12.3** — June 2026
    - **Deterministic module search order** — replaced alphabetical search with prioritized list.
    - **``-srcdirs`` priority** — user-specified source directories now have the highest priority.
    - **Neighboring directory priority** — the directory of the importing file is searched before the current directory.
    - **Standard library overrides** — the filesystem is now searched before internal resources.
    - **Improved error messages** — missing module errors now list all searched filesystem paths and the requester.
    - **Fuzzy library search** — ``-libsearch`` now automatically attempts a fuzzy search if no exact matches are found.
    - **Trace imports** — new ``-traceimports`` option to see exactly how modules are being resolved.
    - **Search Path Comparison**:

    +------+-----------------------------------------------+-----------------------------------------------+
    | Step | Old Behavior (Approximate)                    | New Behavior (Strict)                         |
    +======+===============================================+===============================================+
    | 1    | Internal Standard Library                     | **User Source Directories (-srcdirs)**        |
    +------+-----------------------------------------------+-----------------------------------------------+
    | 2    | Target Library Directories                    | **Neighboring Directory**                     |
    +------+-----------------------------------------------+-----------------------------------------------+
    | 3    | Neighboring Directory                         | **Current Working Directory (.)**             |
    +------+-----------------------------------------------+-----------------------------------------------+
    | 4    | User Source Directories (alphabetical)        | **Target Library Directories**                |
    +------+-----------------------------------------------+-----------------------------------------------+
    | 5    | (not applicable)                              | **Internal Standard Library**                 |
    +------+-----------------------------------------------+-----------------------------------------------+


*This document summarizes major and minor releases. Bugfix releases (e.g., v12.0.1, v12.1.1) are omitted for brevity.*
