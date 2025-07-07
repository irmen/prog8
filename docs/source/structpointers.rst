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

*For backward compatibility reasons, this untyped ``uword`` pointer still exists in the language.*

Since version 12 there now are *typed pointers* that better express the intent and tell the compiler how to use the pointer,
these are explained below.


Typed pointer to simple datatype
--------------------------------

The syntax for declaring typed pointers is as follows:

``^^type``: pointer to a type
    You can declare a pointer to any numeric datatype (bytes, words, longs, floats), booleans, and also strings.
    (The latter: ``^^str`` - a pointer to a string - is equivalient to ``^^ubyte`` though because a string is just an array of ubytes.)
    Finally, the type can be a struct type, which then declares a pointer to that struct type. This is explained in the next section.
    So, for example; ``^^float fptr`` declares fptr as a pointer to a float value.

``^^type[size]``: array with size size containing pointers to a type.
    So for example; ``^^word[100] values`` declares values to be an array of 100 pointers to words.
    Note that an array of pointers (regardless of the type they point to) is always a @split word array.

It is not possible to define pointers to *arrays*; ``^^(type[])`` is invalid syntax.

Pointers of different types cannot be assigned to one another, unless you use an explicit cast.

Typed pointers and an 'untyped' uword pointer/value can be assigned to each other without an explicit cast.

Because it is pretty common to check if a pointer value is zero or not (because zero usually means that the pointer doesn't exist/has no value),
pointers can be implicitly cast to a boolean. This allows you to easily write conditionals such as ``while ptr { ... }``


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
Adding or subtracting X to a pointer will change the pointer by X times the size of the value it points at (the same as the C language does it).

This is true even for pointers to struct types: the compiler knows the storage size of the whole struct type and advances or rewinds
the pointer value (memory address) by the appropriate number of bytes (X times the size of the struct). More info below.


Structs
-------

Work in progress.


Typed pointer to Struct type
----------------------------

Work in progress.


Address-Of: untyped vs typed
----------------------------

``&`` still returns untyped (uword) pointer, as it did in older Prog8 versions. This is for backward compatibility reasons so existing programs don't break.
The *double ampersand* operator ``&&`` returns a *typed* pointer to the value. The semantics are slightly different from the old untyped address-of operator, because adding or subtracting
a number from a typed pointer uses *pointer arithmetic* that takes the size of the value that it points to into account.
