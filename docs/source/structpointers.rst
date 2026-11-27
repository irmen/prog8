.. _pointers:

********************
Structs and Pointers
********************

.. index::
      pair: Structs; Overview
      pair: Pointers; Overview

.. attention::
    The 6502 cpu lacks some features (addressing modes, registers) to make pointers work efficiently.
    Also it requires that pointer variables have to be in zero page, or copied to a temporary zero page variable,
    before they can even be used as a pointer. This means that pointer operations in prog8 compile
    to rather large and inefficient assembly code most of the time, when compared to direct array access or regular variables.
    At least try to place heavily used pointer variables in zero page using ``@requirezp`` on their declaration,
    if zero page space allows.
    Pointer variables that don't have a zeropage tag specified will be treated as having ``@zp`` so they get
    priority over other variables to be placed into zeropage.

.. note::
    Indexed field access through a pointer works for reading and writing,
    with either implicit or explicit pointer dereferencing::

        ^^Node  np
        np[2].field = 9999
        np[2]^^.field = 9999
        ubyte value = np[2].field

    This also works when the struct field itself is a pointer or array that you index::

        struct Node { ^^uword s }
        ^^Node np
        np[2]^^.s[0] = 9999
        uword value = np[2]^^.s[0]

    To save a repeated pointer calculation when assigning multiple fields,
    break up the expression in multiple steps::

        ^^Node thenode = &&np[2]
        thenode.field = 9999

    Writing through a parenthesized casting expression or a function call does work however;
    the compiler evaluates the expression once and stores through the computed address::

        (np[2] as ^^Node).field = 9999
        get_node(i).field = 9999


Untyped pointers and the ``pointer`` type
-----------------------------------------
.. index:: single: Pointers; untyped

Prior to version 12 of the language, the only pointer type available was a plain ``uword`` value (the memory address)
which could be used as a pointer to an ``ubyte`` (the byte value at that memory address).
Array indexing on an ``uword`` simply means to point to the ``ubyte`` at the location of the address + index value.

When the address of a value (explicitly) or a value of a reference type (string, array) was passed as an argument to a subroutine call,
it became one of these plain ``uword`` 'pointers'. The subroutine receiving it always had to interpret the 'pointer'
explicitly for what it actually pointed to, if that wasn't a simple byte.

Some implicit conversions were allowed too (such as putting ``str`` as the type of a subroutine parameter,
which would be changed to ``uword`` by the compiler).

Since version 12.6 there now is a dedicated ``pointer`` type keyword that serves as an untyped address
and automatically takes the :ref:`pointer size <pointer_size>` for the target.
This is useful when writing code that is compiled for different targets,
because you don't need to manually switch between ``uword`` and ``long`` via conditional compilation::

    pointer ptr = $a000
    ; same program works on both 6502 and m68k targets
    ; ptr is 2 bytes on C64, CX16, etc.
    ; ptr is 4 bytes on qemu68k

You can assign any other pointer type to an untyped pointer variable (``uword``, ``long``` or ``pointer``) without the need for an explicit cast.
You can assign an untyped pointer to a typed pointer variable without the need for an explicit cast.
Prog8 *typed pointers* better express the intent and better tell the compiler how to use the pointer. 
These are preferred to be used in new code, and are explained below.


Typed pointer to simple datatype
--------------------------------
.. index:: single: Pointers; Simple Typed

Prog8 syntax has the 'double hat' token ``^^`` that appears either in front of a type ("pointer to this type") or
after a pointer variable ("get the value it points to" - a pointer dereference).

So the syntax for declaring typed pointers looks like this:

``^^type``: pointer to a type
    You can declare a pointer to any numeric datatype (bytes, words, longs, floats), and booleans, and struct types.
    Structs are explained in the next section.
    So for example; ``^^float fptr`` declares fptr as a pointer to a float value.

``^^type[size]``: array with size size containing pointers to a type.
    So for example; ``^^word[100] values`` declares values to be an array of 100 pointers to words.
    An array of pointers (regardless of the type they point to) is always a split word array.
    (this is the most efficient way to access the pointers, and they need to be copied to zeropage first to
    be able to use them anyway. It also allows for arrays of up to 256 pointers instead of 128.)

It is not possible to define pointers to *arrays*; ``^^(type[])`` is invalid syntax.

Pointers of different types cannot be assigned to one another, unless you use an explicit cast.
This rule is not enforced for untyped pointers/regular uword, as described earlier.

The ``str`` type in subroutine parameters and return values has always been a bit weird in the sense that in these cases,
the string is actually passed by reference (it's address pointer is passed) instead of a ``str`` variable that is accessed by value.
In previous Prog8 versions these were untyped uword pointers, but since version 12, these are now translated as ``^^ubyte``.
Resulting assembly code should be equivalent still.


Dereferencing a pointer, pointer arithmetic
-------------------------------------------
.. index::
    pair: Pointers; Dereferencing a pointer
    pair: Pointers; Pointer arithmetic

To get the value the pointer points at, you *dereference* the pointer. The syntax for that is: ``pointer^^``.
Say the pointer variable is of type ``^^float``, then ``pointer^^`` will return the float value it points at.

You can also use array indexing syntax to get the n-th value. For example: ``floatpointer[3]`` will return the
fourth floating point value in the sequence that the floatpointer points at. Because the pointer is a typed pointer,
the compiler knows what the size of the value is that it points at and correctly skips forward the required number of bytes in memory.
In this case, say a float takes 5 bytes, then ``floatpointer[3]`` will return the float value stored at memory address floatpointer+15.
Notice that ``floatpointer[0]`` is equivalent to ``floatpointer^^``.

You can add and subtract values from a pointer, this is called **pointer arithmetic**.
For example, to advance a pointer to the next value, you can use ``pointer++``.
To make it point to the preceding value, you can use ``pointer--``.
**Adding or subtracting X to a pointer will change the pointer by X times the size of the value it points at (the same as the C language does it),
instead of simply adding or subtracting the value from the pointer address value.**
That is what Prog8 still does for untyped uword pointers, or pointers to a type that just takes up
a single byte of memory.

That special pointer arithmetic is also performed for pointers to struct types:
the compiler knows the memory storage size of the whole struct type and advances or rewinds
the pointer value (memory address) by the appropriate number of bytes (X times the size of the struct).
More info about structs can be found below.

You can subtract two pointers of the **same** type from each other. The result is the number of elements between them (C-style),
using the same scaling rules as adding or subtracting an integer. The result has the target's address type (``uword`` on 16-bit targets, ``long`` on 32-bit targets).
For example, if ``^^uword p1`` points at address ``$2000`` and ``^^uword p2`` points at address ``$2006``, then ``p2 - p1`` is ``3`` (because each ``uword`` is 2 bytes).
Adding two pointers together is not supported. Other arithmetic operators such as ``*``, ``/``, ``%`` and bitwise operators are also not supported on typed pointers; cast to an unsigned word or long first if you need those operations.

Untyped pointers (plain ``pointer`` or ``uword``/``long`` used as an address) do not use this scaling at all; arithmetic on them is plain numeric address arithmetic.


Structs
-------
.. index:: pair: Structs; Definition

A struct is a grouping of multiple variables. Say your game is going to track several enemy sprites on the screen,
in which case it may be useful to describe the various properties of an enemy together in a struct type, rather than
dealing with all of them separately.  You first define the struct type like so::

    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

Allowed field datatypes: all numeric types (``byte``, ``ubyte``, ``word``, ``uword``, ``long``, ``float``),
``bool``, ``str``, typed pointers to those or to other structs, and inline 1D arrays of these element types.
Not allowed: struct instances themselves (structs cannot be nested, use a pointer to the struct instead),
arrays of struct instances, arrays of typed pointers such as ``^^Enemy[4]`` (use untyped pointers instead), and 2D arrays.
The typed pointer array and 2D array restrictions are limitations of the current compiler; they may or may not be lifted in a future version.

Fields in a struct are 'packed' (meaning the values are placed back-to-back in memory), and placed in memory in order of declaration. This guarantees exact size and place of the fields.
``sizeof()`` knows how to calculate the combined size of a struct, and ``offsetof()`` can be used to get the byte offset of a given field in the struct.
The size of a struct cannot exceed 256 bytes on 6502-based targets (C64, CX16, C128, PET32 - one memory page).
On 32-bit targets the limit is much larger: 32768 bytes on Amiga/Qemu68k and 65535 bytes on the virtual target.

.. caution::
    Because fields are packed, keeping multi-byte fields on a suitable address is your own responsibility.
    The 68000 CPU (Amiga 500 and other 68000-based machines) raises an address-error exception on an
    unaligned ``word``, ``long``, ``float`` or pointer access: such a field must start on an even offset.
    Order the fields accordingly, or insert ``ubyte`` filler fields - for example a
    ``ubyte flags`` followed by a ``uword xpos`` puts ``xpos`` on offset 1, which faults on a 68000.
    The 68020 and later CPUs tolerate unaligned accesses (at a speed penalty), and on 6502-based
    targets alignment doesn't matter for correctness at all.

You can copy the whole contents of a struct to another one by assigning the dereferenced pointers::

    ^^Enemy e1,e2
    e1^^ = e2^^     ; copies all fields of e2 into e1


The struct type creates a new name scope, so accessing the fields of a struct is done as usual with the dotted notation.
Because it implies pointer dereferencing you can usually omit the explicit `^^`, prog8 will know what it means::

    if e1.ypos > 300
        e1.health -= 10

    ; explicit dereferencing notation:

    if e1^^.ypos > 300
        e1^^.health -= 10

You can also assign to a struct field through an arbitrary pointer expression by wrapping it in parentheses.
This works with typecasts and with function calls that return a pointer::

    (enemies[i] as ^^Enemy).health = 42
    (get_enemy(index)).shield -= 10
    get_enemy(index).next.target = 0

The compiler evaluates the pointer expression exactly once and then stores the value through the computed address.

.. note::
    Taking the address of a field inside an array of struct instances with the ``&`` operator is not
    supported directly: ``&commands[i].name`` does not compile. Use the explicit form instead::

        p = &commands[i] + offsetof(command.name)

    The same applies to arrays of typed pointers to structs (``&commandPtrs[i].name``).
    The ``&`` address-of construct only accepts a variable or array element, not a trailing field access.


.. note::
    Structs can be accessed through a typed pointer, as an *array of struct instances*
    (see below), or as a single direct struct variable.
    A direct struct variable is declared as ``Point p`` and its fields are accessed with
    ``p.x``. A direct struct variable can also be memory-mapped at a fixed address
    (see below), just like an array of struct instances.
    The compiler replaces whole-struct assignments with a memory copy. You are allowed to write::

        ptr2^^ = ptr1^^
        ptr2^^ = ptr1[2]
        ptr2[2] = ptr1^^
        arr[i] = arr[j]           ; array of struct instances
        p2 = p1                   ; direct struct variables

    The source and target must be the same kind of struct storage; mixing forms such as
    ``p = ptr^^`` or ``ptr^^ = p`` is not supported. The copy is a raw byte copy, so
    pointer and string fields are copied by address (a shallow copy).

    Direct struct variables are stored in static memory unless they are memory-mapped
    to a fixed address. They cannot be subroutine parameters or return values.
    ``sizeof(p)`` on a direct struct variable returns the storage size of its struct type.

    Because the storage is static, a struct variable declared inside a subroutine
    behaves differently from an ordinary local variable. Its initializer is emitted
    as load-time data and is applied only once, when the program starts, and the
    variable keeps its value between calls of the subroutine. This is exactly the
    same behavior as a local array (or string) with an initializer list: variables
    with a non-numeric initializer are never placed in the stack frame, so they are
    not zeroed or re-initialized on every call. A struct variable that needs a fresh
    value on every call must be initialized explicitly at the start of the
    subroutine body.


.. _structmemmap:

Memory-mapped struct instance
-----------------------------
.. index:: pair: Structs; Memory-mapped instance

A single struct instance can be placed at a fixed address with the ``&`` memory-mapped
declaration form. This is useful to overlay a hardware register block or a memory area
that is not owned by the program::

    struct SpriteRegs {
        ubyte x, y
        ubyte color
        ubyte pad
    }

    ; one SpriteRegs structure at $d000
    &SpriteRegs sprite0 = $d000

    sub start() {
        sprite0.x = 100
        sprite0.color = 7
    }

The instance has no compiler-owned storage, and its fields are laid out exactly as the
struct declaration defines them (there is no alignment padding). The value after ``=`` is
the address, not initialization data, so a memory-mapped instance cannot have field
initializers. Field accesses, ``&sprite0`` (the instance address), and whole-struct copies
such as ``copy = sprite0`` all use the fixed address. A memory-mapped instance can also be
referenced from static initializer data, for example
``^^SpriteRegs[2] ptrs = [&&sprite0, &&sprite0]``.


.. _structarrays:

Arrays of struct instances
--------------------------
.. index:: pair: Structs; Arrays of instances

You can declare an array that contains struct instances directly, rather than pointers to structs.
The syntax places the array size between the struct type and the variable name::

    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

    Enemy[10] enemies          ; 10 Enemy instances in a contiguous block

Indexing works as expected and the fields are accessed with the dotted notation::

    enemies[i].health = 100
    enemies[i].elite = true
    ubyte x = enemies[i].xpos

You can copy one whole element to another with a single assignment; the compiler generates a memory copy::

    enemies[i] = enemies[j]

Array-of-struct variables can also be statically initialized by nesting struct initializers inside an array literal.
The initializer elements are struct values, not pointer allocations, so you must not use ``^^``.
You can write them with an explicit type or let the compiler infer it::

    Enemy[3] squad = [
        Enemy : [10, 20, 100, false],
        Enemy : [30, 40, 200, true],
        Enemy : [50, 60, 150, false]
    ]

    ; inferred form (preferred)
    Enemy[3] squad2 = [
        [10, 20, 100, false],
        [30, 40, 200, true],
        [50, 60, 150, false]
    ]

The struct size counts toward the same target size limits as other arrays.
On 6502-family targets a struct cannot exceed 256 bytes, so the maximum number of elements in an
array of structs is limited by that size (for example, at most 128 elements for a 2-byte struct, and
fewer for larger structs). On 32-bit targets the per-array size limit is much larger.

An array of struct instances can also be memory-mapped at a fixed address, which is useful to overlay
a hardware register block. The element stride is the struct size::

    struct AudioChannel {
        pointer LC       ; sample pointer
        uword LEN        ; length in words
        uword PER        ; period
        uword VOL        ; volume
        word DAT         ; sample data (write only)
        uword pad0       ; filler, so the element stride matches the hardware's 16 bytes
        uword pad1
    }

    ; the four Paula channel register blocks start at $dff0a0 (16 bytes apart)
    &AudioChannel[4] channels = $dff0a0

    sub start() {
        channels[0].VOL = 64
        channels[1].PER = 428
    }

Note that the fields are laid out in memory exactly as the struct declaration defines them
(there is no alignment padding), so add explicit filler fields when the hardware register block
requires gaps.

.. note::
    Arrays of struct instances cannot be used as subroutine parameters (the same restriction applies
    to most array types other than ``ubyte`` arrays and strings). Pass a typed pointer to the first
    element instead, or use ``sys.memcopy`` if you need to pass a copy.

.. note::
    Arrays of struct instances are stored contiguously, and on the classic 6502 code generator
    every field access with a variable index recomputes the element address in full
    (``base + index * sizeof(struct)``), once per field. In a tight loop this adds up quickly:
    reading three fields of ``enemies[i]`` rebuilds the address three times. Take the address of
    the element once into a typed pointer and access the fields through it instead::

        ^^Enemy enemy = &&enemies[i]
        enemy^^.health += 10
        enemy^^.elite = true

    All 6502 backends currently recompute the element address for every field access. In the compact
    classic codegen this repeated rebuild dominates the generated code size in field-heavy loops;
    in the more verbose IR-based codegen, direct field access costs about the same as access through
    a typed pointer, so the choice matters less there. The same applies to float fields: assigning a
    float struct field copies the value through the floating-point accumulator, which costs more than
    a plain word copy.

.. note::
    Exchanging two elements of an array of struct instances copies the whole struct value
    (five bytes per float on 6502 targets), while exchanging two entries of an array of typed
    pointers only swaps two 2-byte pointer values. If you only need to reorder elements, consider
    sorting an array of typed pointers (or an index array) that references the struct instances
    instead.

.. note::
    Using structs instead of plain arrays usually results in more and less efficient code being generated.
    This is because the 6502 CPU is not particularly well equipped to dealing with pointers and accessing struct fields via offsets,
    as compared to direct variable access or array indexing. The prog8 program code may be easier to work with though!

.. note::
    Accessing the first field in a struct is more efficient than subsequent fields, because it
    is at offset 0 so no additional addition has to be computed on a pointer to reach the first field.
    Try to put the most often accessed field as the first field to potentially gain a rather substantial boost in code efficiency.


.. _unions:

Unions
------
.. index:: pair: Unions; Definition

A union is like a struct, but every field starts at byte offset 0.
The fields overlap in memory, so writing to one field overwrites the others.
The storage size of a union is the size of its largest field, not the sum of all fields.

You define a union type like this::

    union Value {
        ubyte byte_val
        uword word_val
        float float_val
    }

Allowed field datatypes are the same as for structs: all numeric types, ``bool``, ``str``, typed pointers to those or to other structs, and inline 1D arrays of these element types.
Not allowed are struct instances themselves, unions themselves, arrays of struct instances, arrays of typed pointers such as ``^^Value[4]``, and 2D arrays.
So neither structs nor unions can be nested inside another struct or union; use a typed pointer to them instead.
``sizeof(Value)`` returns the size of the largest field, and ``offsetof(Value.field)`` is always ``0`` for every field.
A union type can be used wherever a struct type is used: typed pointers, pointer arrays, arrays of union instances, subroutine parameters, and return values.
Whole-union copy semantics are the same as for structs: assigning one dereferenced union pointer to another copies ``sizeof(Value)`` bytes.

When to use a union
^^^^^^^^^^^^^^^^^^^
Unions are for interpreting the same memory bytes in more than one way.
Typical uses:

- **Type punning:** read or write the same bytes through different views, e.g. set the low byte with the 1-byte view and then read the 2-byte view::

      v.byte_val = 42
      chk = v.word_val      ; sees the overlapping bytes

- **Hardware or memory-mapped registers:** one location that the hardware interprets as a byte or a word depending on how it is accessed.
- **Overlays:** reinterpret a buffer, such as the raw bytes of a ``float``, for debugging or serialization, or reuse the same scratch space between different phases of a program.

Unions do not (yet) help save space inside structs, because nesting a union (or struct) as a field is not allowed; see above.

Because all fields overlap, static initialization with values is not allowed.
The only valid initializer is the empty form ``[]``, which places a zero-initialized union instance in BSS::

    ^^Value v = []         ; zero-initialized union instance

Values must be written at runtime through the pointer::

    v.byte_val = 42
    ; v.word_val and v.float_val now see the overlapping bytes

.. note::
    Unions, like structs, cannot yet be declared as a local instance - you always access
    a union through a typed pointer, as shown above. See the note in the structs section
    for the current limitations around using unions as standalone values.


Static initialization of structs
================================
.. index:: pair: Structs; Static initialization

You can 'allocate' and statically initialize a struct. This behave much like initializing arrays does,
and it won't reset to the original value when the program is restarted, so beware.
*Remember that the struct is statically allocated, and appears just once in the memory:*
This means that, for instance, if you do this in a subroutine that gets
called multiple times, or inside a loop, the struct *will be the same instance every time*.
Read below if you need *dynamic* struct allocation!
You write a static struct initialization expression like this:

``^^Node : [1,"one", 1000, true, 1.111]``
    statically places an instance of struct 'Node' in memory, with its fields set to 1, "one", 1000 etcetera and returns the address of this struct.
    The values in the initialization array must correspond exactly with the first to last declared fields in the struct type.

Named field initializers
^^^^^^^^^^^^^^^^^^^^^^^^

For a more readable initializer, you can specify field names instead of relying
on declaration order::

    ^^Node : [name="one", flag=true, id=1, array=1000, perc=1.111]

Named fields may be written in any order. Fields that are not specified are
initialized to zero. This also applies to inline array fields, which are filled
with zeroes. Each field may be specified only once, and every name must refer
to a field in the struct. Named and positional values cannot be mixed in the
same initializer.

If the struct contains inline arrays, you can initialize them by nesting another list inside the initialization list:

``^^Node : [1, [10, 20, 30, 40], 1000]``
    Initializes a struct Node where the second field is an inline array (e.g., ``ubyte[4] data``). The nested array ``[10, 20, 30, 40]`` initializes that array field.
``^^Node : []``
    (without values) Places a 'Node' instance in BSS variable space instead, which gets zeroed out at program startup.
    Returns the address of this empty struct.

Initializing byte-array fields with a string
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
.. index:: pair: Structs; String initializer

For a sized ``ubyte`` (or ``byte``) array field, a string literal may be used in place of an explicit
byte array. The string is encoded using the current ``%encoding`` and then filled into the array using
C-style semantics:

- if the encoded string is shorter than the array, the remaining bytes are filled with zero
  (the first zero acts as the implicit C string terminator)
- if it is exactly the array length, no extra terminator is added
- if it is longer than the array, a compilation error is reported

For example, given ``ubyte[4] name``, the initializers ``"abcd"``, ``"abc"`` and ``"a"`` are all
accepted and produce ``[a,b,c,d]``, ``[a,b,c,0]`` and ``[a,0,0,0]`` respectively, while ``"abcde"``
causes an error. The same applies to inferred-form initializers such as ``^^Node n = [1, "abc", true]``.


The field values in a struct initializer must be constants. You can use numeric literals, ``address-of`` expressions,
or ``memory()`` calls (either directly or via ``const`` variables).
It is also possible to put struct initializer inside arrays to make them all statically initialized and accessible via the array::

    ^^Node[] allnodes = [
        ^^Node: [1,"one", 1000, true, 1.111],
        ^^Node: [2,"two", 2000, false, 2.222],
        ^^Node: [],
        ^^Node: [],
    ]

Short form initializers
^^^^^^^^^^^^^^^^^^^^^^^
.. index:: pair: Structs; Initializers

If the required type can be inferred from the context you can also omit the struct pointer type prefix altogether.
The initializer value then is syntactically the same as an array, but Prog8 internally turns it back into a proper
struct initializer value based on the the type of the array element or pointer variable it is assigned to.
So you can write the above in short form as::

    ^^Node nodepointer = [1,2,3,4]

    ^^Node[] allnodes = [
        [1,"one", 1000, true, 1.111],
        [2,"two", 2000, false, 2.222],
        [],
        []
    ]



Dynamic allocation of structs
=============================
.. index:: pair: Structs; Dynamic allocation

There is no real 'dynamic' memory allocation in Prog8. Everything is statically allocated. This doesn't change with struct types.
However, it is possible to write a dynamic memory handling library yourself (it has to track memory blocks manually).
If you ask such a library to give you a pointer to a piece of memory with size ``sizeof(Enemy)`` you can use that as
a dynamic pointer to an Enemy struct.

For this pattern, use the standard ``arena`` library module. Create a static
memory slab and allocate from it. On 6502 targets the module keeps one global
slab::

    %import arena

    arena.init(memory("arena", 2000, 0), 2000)
    ^^Node newnode = arena.alloc(sizeof(Node))
    ...
    arena.reset()

On m68k and virtual targets the allocator state is caller-owned, so several
independent slabs can be used at once::

    %import arena

    arena.Arena node_arena
    arena.init(&&node_arena, memory("arena", 2000, 0), 2000)
    ^^Node newnode = arena.alloc(&&node_arena, sizeof(Node))
    ...
    arena.reset(&&node_arena)

Use ``&&`` for a typed ``^^Arena`` pointer. An ordinary ``&`` also works on
these 32-bit targets as an untyped ``long`` address, but ``&&`` is preferable.
The 6502 implementation uses ``uword`` sizes without even-address rounding and
tracks one slab at a time. The m68k/virtual implementation uses ``long`` sizes,
rounds odd allocations up to an even address, and keeps each slab's state in
its own ``Arena`` struct. ``arena.alloc()`` returns 0 when the selected slab is
exhausted. Individual allocations cannot be freed; ``arena.reset()`` makes the
selected slab available again.


Address-Of: untyped vs typed
----------------------------
.. index:: single: Structs; Address-Of

``&`` still returns an untyped (uword) pointer, as it did in older Prog8 versions. This is for backward compatibility reasons so existing programs don't break.
The new *double ampersand* operator ``&&`` returns a *typed* pointer to the value. The semantics are slightly different from the old untyped address-of operator, because adding or subtracting
a number from a typed pointer uses *pointer arithmetic* that takes the size of the value that it points to into account.

Address-of expressions can also appear in static initializer data, for example to put pointers
to struct instances in a pointer array or in a pointer-typed struct field::

    struct Point { uword x  uword y }
    struct Holder { ^^Point pt  uword extra }
    Point p1, p2
    ^^Point[2] ptrs = [&&p1, &&p2]
    Holder h = [&&p1, 99]

The typed form works on every target. The untyped ``&`` only works there when its inferred
address type happens to match the declared pointer type: on the 6502 targets that is the case
(16-bit addresses), but on the m68k and virtual targets the 32-bit address is inferred as
``long``, so use ``&&`` to get a properly typed pointer.


Accessing struct definitions in Assembly code
---------------------------------------------
.. index:: single: Structs; Access from assembly

Prog8 lets you query the size of a struct type, and the offsets of a field.
You can do the same in assembly code if needed: the struct definition gets written as `.struct` into the assembly file::

    ; prog8 struct declaration:
    struct Node {
        ubyte type
        uword value
        bool flag
    }

    ; generates this assembly code:
    ; (the symbol prefixes are explained in the 'Technical details' chapter)
    p8b_main.p8t_Node    .struct f0,f1,f2
    p8v_type  .byte  \f0
    p8v_value  .word  \f1
    p8v_flag  .byte  \f2
        .endstruct


64tass then lets you query that information::

    ; prog8 code:
    ubyte size = sizeof(Node)
    ubyte offset1 = offsetof(Node.type)
    ubyte offset2 = offsetof(Node.value)
    ubyte offset3 = offsetof(Node.flag)

    ; assembly equivalents (64tass syntax):
    lda  #size(p8t_Node)
    lda  #p8t_Node.p8v_type
    lda  #p8t_Node.p8v_value
    lda  #p8t_Node.p8v_flag

On the m68k targets, the compiler instead emits a ``vasm`` ``RS`` structure definition block.
``RS`` only defines symbols (it emits no data bytes), and the offsets follow the same packed layout as on the other targets::

            RSRESET
    p8b_main.p8t_Node.p8v_type RS.B 1
    p8b_main.p8t_Node.p8v_value RS.W 1
    p8b_main.p8t_Node.p8v_flag RS.B 1
    p8b_main.p8t_Node_size RS.B 0

``RS.B``, ``RS.W`` and ``RS.L`` advance an internal offset counter by 1, 2 or 4 bytes per element
(and multiply that by the field's array size), a leading ``RSRESET`` restarts the counter at 0,
and the trailing ``RS.B 0`` defines the total size without advancing it.
The symbols can be used anywhere in inline assembly, for example ``move.w p8b_main.p8t_Node.p8v_value(a0),d0``.
