.. _loadable_library:

=========================
Binary Loadable Libraries
=========================

**also called 'Library Blobs'.**

Prog8 allows you to create binary library files that contain routines callable by other programs.
Those programs can be written in Prog8, BASIC, or something else. They just LOAD the binary library
file into memory, and call the routines.

An example of a library file loaded in BASIC on the Commander X16:

.. image:: _static/x16library.png
    :align: center

Requirements
^^^^^^^^^^^^

Such a loadable library has to adhere to a few rules:

It can't use zero page variables
    Otherwise it might overwrite variables being used by the calling program.
    For systems that have the 16 'virtual registers' cx16.r0-r15 in zero page:
    these 16 words are free to use. For other systems, only the internal prog8
    zeropage scratch variables can be used.
    *note: this may be improved upon in a future version*

No system initialization and startup code
    The library cannot perform any regular "system initialization" that normal
    Prog8 programs usually perform (such as resetting the IO registers, clearing the screen,
    changing the colors, and other initialization logic). This would disturb the
    state of the calling program!  The library can (must) assume that that the calling
    program has already done all required initialization.

Variable initialization
    The library still has to initialize any variables it might use and clear
    uninitialized "BSS" variables! Otherwise the code will not run predictably as prog8 code.
    So, the library must still have a "start" entrypoint subroutine like any outher prog8 program,
    that must be called before any other library routine can be called.

Binary output and loaded into a fixed memory address
    The library must not have a launcher such as a BASIC SYS command, because
    it is not ran like a normal program.
    Also, because it is not possible to create position independent code with prog8,
    a fixed load address has to be decided on and the library must be compiled
    with that address as the load address. For convenience (and compatibility with older CBM
    target machines such as the C64 and C128) it's easiest if the resulting library
    program includes a PRG load header: 2 bytes at the start of the library that contain
    the load address. This allows BASIC to load the library via a simple ``LOAD "LIB.BIN",8,1`` for example.


``%output library``
^^^^^^^^^^^^^^^^^^^
Most (but not all) of the above requirements can be fulfilled by setting various directives in your
source code such as %launcher, %zeropage and so on. But there is a single directive that does it correctly for you in one go
(and makes sure there won't be any initialization code left at all): ``%output library``

Together with ``%address`` and possibly ``%memtop`` -to tell the compiler what the load address of the library should be-
it will create a "library.bin" file that fulfills the requirements of a loadable binary library program as listed above.

The entrypoint (= the start subroutine) that must be called to initialize the variables,
will be the very first thing at the beginning of the library.


Jump table
^^^^^^^^^^

For ease of use, libraries should probably have a fixed "jump table" where the offsets of the
library routines stay the same across different versions of the library. Without needing new syntax,
there's a trick in Prog8 that you can use to build such a jumptable:
add a non-splitted word array at the top of the library main block that contains JMP instructions
and the addresses of the individual library subroutines. Do NOT change the order of the subroutines
in this table!
Also note that the Prog8 compiler will insert a single JMP instruction at the very start of the library,
that jumps to the start subroutine (= the entrypoint of the library program).
Users of the library need to call this to initialize the variables, so it is a required part of the
external interface of the library.
Because the compiler will place the global word array jumptable immediately after this JMP instruction,
it seems as if the very first entry in the jump table is the jump to the start routine.

Look at the generated assembly code to see exactly what is going on.
But the users of the library are none the wiser and it just seems as if it is part of the jump table in a natural way :-)


Here is the small example library that was used in the example at the beginning of this chapter::

    %address  $A000
    %memtop   $C000
    %output   library

    %import textio


    main {
        ; Create a jump table as first thing in the library.
        uword[] @shared @nosplit jumptable = [
            ; NOTE: the compiler has inserted a single JMP instruction at the start
            ; of the 'main' block, that jumps to the start() routine.
            ; This is convenient because the rest of the jump table simply follows it,
            ; making the first jump neatly be the required initialization routine
            ; for the library (initializing variables and BSS region).
            ; Btw, $4c = opcode for JMP.
            $4c00, &library.func1,
            $4c00, &library.func2,
        ]

        sub start() {
            ; has to be here for initialization
            txt.print("lib initialized\n")
        }
    }


    library {
        sub func1() {
            txt.print("lib func 1\n")
        }

        sub func2() {
            txt.print("lib func 2\n")
        }
    }

