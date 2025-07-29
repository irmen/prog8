.. _pointers:

====================
Structs and Pointers
====================


Legacy untyped pointers (uword)
-------------------------------

Prior to version 12 of the language, the only pointer type available was a plain ``uword`` value (the memory address)
which could be used as a pointer to an ``ubyte`` (the byte value at that memory address).
Array indexing on an ``uword`` simply means to point to the ``ubyte`` at the location of the address + index value.

When the address of a value (explicitly) or a value of a reference type (string, array) was passed as an argument to a subroutine call,
it became one of these plain ``uword`` 'pointers'. The subroutine receiving it always had to interpret the 'pointer'
explicitly for what it actually pointed to, if that wasn't a simple byte.

Some implicit conversions were allowed too (such as putting ``str`` as the type of a subroutine parameter,
which would be changed to ``uword`` by the compiler).

Since Prog8 version 12 there now are *typed pointers* that better express the intent and tell the compiler how to use the pointer;
these are explained below.

*For backward compatibility reasons, this untyped uword pointer still exists in the language.*
You can assign any other pointer type to an untyped pointer variable (uword) without the need for an explicit cast.
You can assign an untyped pointer (uword) to a typed pointer variable without the need for an explicit cast.



Typed pointer to simple datatype
--------------------------------

Prog8 syntax has the 'double hat' token ``^^`` that appears either in front of a type ("pointer to this type") or
after a pointer variable ("get the value it points to" - a pointer dereference).

So the syntax for declaring typed pointers looks like this:

``^^type``: pointer to a type
    You can declare a pointer to any numeric datatype (bytes, words, longs, floats), and booleans, and struct types.
    Structs are explained in the next section.
    So for example; ``^^float fptr`` declares fptr as a pointer to a float value.

``^^type[size]``: array with size size containing pointers to a type.
    So for example; ``^^word[100] values`` declares values to be an array of 100 pointers to words.
    Note that an array of pointers (regardless of the type they point to) is always a @split word array at this time.
    (this is the most efficient way to access the pointers, and they need to be copied to zeropage first to
    be able to use them anyway. It also allows for arrays of up to 256 pointers instead of 128.)

It is not possible to define pointers to *arrays*; ``^^(type[])`` is invalid syntax.

Pointers of different types cannot be assigned to one another, unless you use an explicit cast.
This rule is not enforced for untyped pointers/regular uword, as described earlier.

The ``str`` type in subroutine parameters and return values has always been a bit weird in the sense that in these cases,
the string is actually passed by reference (it's address pointer is passed) instead of a ``str`` variable that is accessed by value.
In previous Prog8 versions these were untyped uword pointers, but since version 12, these are now translated as ``^^ubyte``.
Resulting assembly code should be equivalent still.

.. note::
    **Pointers to subroutines:**
    While Prog8 allows you to take the address of a subroutine, it has no support yet for typed function pointers.
    Calling a routine through a pointer with ``goto``, ``call()`` and such, only works with the raw uword address for now.


Dereferencing a pointer, pointer arithmetic
-------------------------------------------

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
(that is what Prog8 still does for untyped uword pointers, or pointers to a type that just takes up a single byte of memory).

That special pointer arithmetic is also performed for pointers to struct types:
the compiler knows the memory storage size of the whole struct type and advances or rewinds
the pointer value (memory address) by the appropriate number of bytes (X times the size of the struct). More info about structs can be found below.


Structs
-------

A struct is a grouping of multiple variables. Say your game is going to track several enemy sprites on the screen,
in which case it may be useful to describe the various properties of an enemy together in a struct type, rather than
dealing with all of them separately.  You first define the struct type like so::

    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

You can use boolean fields, numeric fields (byte, word, float), and pointer fields (including str, which is translated into ^^ubyte).
You cannot nest struct types nor put arrays in them as a field.
Fields in a struct are 'packed' (meaning the values are placed back-to-back in memory), and placed in memory in order of declaration. This guarantees exact size and place of the fields.
``sizeof()`` knows how to calculate the size of a struct.
The size of a struct cannot exceed 1 memory page (256 bytes).

You can copy the whole contents of a struct to another one by assigning the dereferenced pointers::

    ^^Enemy e1,e2
    e1^^ = e2^^     ; copies all fields of e2 into e1


The struct type creates a new name scape, so accessing the fields of a struct is done as usual with the dotted notation::

    if e1.ypos > 300
        e1.health -= 10

    ; notice that that implicitly dereferences the pointer variable, actually it is doing this:

    if e1^^.ypos > 300
        e1^^.health -= 10


.. note::
    Structs are only supported as a *reference type* (via a pointer). It is currently not possible to use them as a value type.
    This means you cannot create an array of structs either - only arrays of pointers to structs.

.. note::
    Using structs instead of plain arrays may result in less efficent code being generated.
    This is because the 6502 CPU is not particularly well equipped to dealing with pointers and accessing struct fields via offsets,
    as compared to direct variable access or array indexing. The prog8 program code may be easier to work with though!


Static initialization of structs
================================

You can 'allocate' and statically initialize a struct. This behave much like initializing arrays does,
and it won't reset to the original value when the program is restarted, so beware.
*Remember that the struct is statically allocated, and appears just once:* this means that, for instance, if you do this in a subroutine that gets
called multiple times, the struct will be the same instance every time. Read below if you need *dynamic* struct allocation.
There are two ways to initialize a struct like this:

``^^Node ptr = Node(1,2,3,4)``
    statically allocates a Node with its fields set to 1,2,3,4 and puts the address of this struct in ptr.
    The values between the parenthesis must correspond exactly with the first to last declared fields in the struct type.
``Node()``
    (without arguments) Allocates a node in BSS variable space instead, which gets zeroed out at program startup.


Dynamic allocation of structs
=============================

There is no real 'dynamic' memory allocation in Prog8. Everything is statically allocated. This doesn't change with struct types.
However, it is possible to write a dynamic memory handling library yourself (it has to track memory blocks manually).
If you ask such a library to give you a pointer to a piece of memory with size ``sizeof(Enemy)`` you can use that as
a dynamic pointer to an Enemy struct.


Address-Of: untyped vs typed
----------------------------

``&`` still returns an untyped (uword) pointer, as it did in older Prog8 versions. This is for backward compatibility reasons so existing programs don't break.
The new *double ampersand* operator ``&&`` returns a *typed* pointer to the value. The semantics are slightly different from the old untyped address-of operator, because adding or subtracting
a number from a typed pointer uses *pointer arithmetic* that takes the size of the value that it points to into account.
