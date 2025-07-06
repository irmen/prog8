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

``^^type``: pointer to that type
    You can declare a pointer to any numeric datatype (bytes, words, longs, floats), booleans,
    and also strings.  (the latter, ``^^str`` - a pointer to a string - is equivalient to ``^^ubyte`` though because a string is just an array of ubytes.)
    ``^^float fptr`` declares fptr as a pointer to a float value.
    Finally, the type can be a struct type, which then declares a pointer to that struct type. This is explained in the next section.

``^^type[size]``: array with size size containing pointers to type.
    So ``^^word[100] values`` declares values to be an array of 100 pointers to words.
    Note that an array of pointers (regardless of the type they point to) is always a @split word array by default.

It is not possible to define "pointers to arrays"; ``^^(type[])`` is invalid syntax.


Structs
-------

Work in progress.


Typed pointer to Struct type
----------------------------

Work in progress.


Pointer arithmetic and array indexing
-------------------------------------

Work in progress.
