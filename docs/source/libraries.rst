************************
Compiler library modules
************************

The compiler provides several "built-in" library modules with useful subroutine and variables.

Some of these may be specific for a certain compilation target, or work slightly different,
but some effort is put into making them available across compilation targets.

This means that as long as your program is only using the subroutines from these
libraries and not using hardware- and/or system dependent code, and isn't hardcoding certain
assumptions like the screen size, the exact same source program can
be compiled for multiple different target platforms. Many of the example programs that come
with Prog8 are written like this.

You can ``%import`` and use these modules explicitly, but the compiler may also import one or more
of these library modules automatically as required.

For full details on what is available in the libraries, look at their source code here:
https://github.com/irmen/prog8/tree/master/compiler/res/prog8lib


.. caution::
    The resulting compiled binary program *only works on the target machine it was compiled for*.
    You must recompile the program for every target you want to run it on.



syslib
------
The "system library" for your target machine. It contains many system-specific definitions such
as ROM/kernal subroutine definitions, memory location constants, and utility subroutines.

Many of these definitions overlap for the C64 and Commander X16 targets so it is still possible
to write programs that work on both targets without modifications.

conv
----
Routines to convert strings to numbers or vice versa.

- numbers to strings, in various formats (binary, hex, decimal)
- strings in decimal, hex and binary format into numbers


textio (txt.*)
--------------
This will probably be the most used library module. It contains a whole lot of routines
dealing with text-based input and output (to the screen). Such as

- printing strings and numbers
- reading text input from the user via the keyboard
- filling or clearing the screen and colors
- scrolling the text on the screen
- placing individual characters on the screen


diskio
------
Provides several routines that deal with disk drive I/O, such as:

- list files on disk, optionally filtering by a simple pattern with ? and *
- show disk directory as-is
- display disk drive status
- load and save data from and to the disk
- delete and rename files on the disk


string
------
Provides string manipulation routines.

length(str) -> ubyte length
    Number of bytes in the string. This value is determined during runtime and counts upto
    the first terminating 0 byte in the string, regardless of the size of the string during compilation time.
    Don't confuse this with ``len`` and ``sizeof``

left(source, length, target)
    Copies the left side of the source string of the given length to target string.
    It is assumed the target string buffer is large enough to contain the result.
    Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

right(source, length, target)
    Copies the right side of the source string of the given length to target string.
    It is assumed the target string buffer is large enough to contain the result.
    Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

slice(source, start, length, target)
    Copies a segment from the source string, starting at the given index,
    and of the given length to target string.
    It is assumed the target string buffer is large enough to contain the result.
    Also, you have to make sure yourself that start and length are within bounds of the strings.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

find(string, char) -> uword address
    Locates the first position of the given character in the string, returns the string starting
    with this character or $0000 if the character is not found.

compare(string1, string2) -> ubyte result
    Returns -1, 0 or 1 depeding on wether string1 sorts before, equal or after string2.
    Note that you can also directly compare strings and string values with eachother
    using ``==``, ``<`` etcetera (it will use string.compare for you under water automatically).

copy(from, to) -> ubyte length
    Copy a string to another, overwriting that one. Returns the length of the string that was copied.
    Often you don't have to call this explicitly and can just write ``string1 = string2``
    but this function is useful if you're dealing with addresses for instance.


floats
------
Provides definitions for the ROM/kernel subroutines and utility routines dealing with floating
point variables.  This includes ``print_f``, the routine used to print floating point numbers.


graphics
--------
Monochrome bitmap graphics routines, fixed 320*200 resolution:

- clearing the screen
- drawing individual pixels
- drawing lines, rectangles, filled rectangles, circles, discs

This library is available both on the C64 and the Cx16.
It uses the ROM based graphics routines on the latter, and it is a very small library because of that.
That also means though that it is constrained to 320*200 resolution on the Cx16 as well.
Use the ``gfx2`` library if you want full-screen graphics or non-monochrome drawing.


gfx2  (cx16 only)
-----------------
Full-screen multicolor bitmap graphics routines, available on the Cx16 machine only.

- multiple full-screen resolutions: 640 * 480 monochrome, and 320 * 240 monochrome and 256 colors
- clearing screen, switching screen mode, also back to text mode is possible.
- drawing individual pixels
- drawing lines, rectangles, filled rectangles, circles, discs
- drawing text inside the bitmap
- in monochrome mode, it's possible to use a stippled drawing pattern to simulate a shade of gray.


palette  (cx16 only)
--------------------
Available for the Cx16 target. Various routines to set the display color palette.
There are also a few better looking Commodore-64 color palettes available here,
because the Commander X16's default colors for this (the first 16 colors) are too saturated
and are quite different than how they looked on a VIC-II chip in a C-64.


math
----
Low level math routines. You should not normally have to bother with this directly.
The compiler needs it to implement most of the math operations in your programs.


cx16logo
--------
A 'fun' module that contains the Commander X16 logo and that allows you
to print it anywhere on the screen.


prog8_lib
---------
Low level language support. You should not normally have to bother with this directly.
The compiler needs it for verious built-in system routines.
