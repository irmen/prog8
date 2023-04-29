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

.. note::
    For full details on what is available in the libraries, please study their source code here:
    https://github.com/irmen/prog8/tree/master/compiler/res/prog8lib

.. caution::
    The resulting compiled binary program *only works on the target machine it was compiled for*.
    You must recompile the program for every target you want to run it on.



syslib
------
The "system library" for your target machine. It contains many system-specific definitions such
as ROM/Kernal subroutine definitions, memory location constants, and utility subroutines.


Many of these definitions overlap for the C64 and Commander X16 targets so it is still possible
to write programs that work on both targets without modifications.

This module is usually imported automatically and can provide definitions in the ``sys``, ``c64``, ``cx16``, ``c128``, ``atari`` blocks
depending on the chosen compilation target. Read the `syslib source code <https://github.com/irmen/prog8/tree/master/compiler/res/prog8lib>`_ for the correct compilation target to see exactly what is there.


sys (part of syslib)
--------------------
``target``
    A constant ubyte value designating the target machine that the program is compiled for.
    Notice that this is a compile-time constant value and is not determined on the
    system when the program is running.
    The following return values are currently defined:

    - 16 = compiled for Commander X16 with 65C02 CPU
    - 64 = compiled for Commodore 64 with 6502/6510 CPU

``exit(returncode)``
    Immediately stops the program and exits it, with the returncode in the A register.
    Note: custom interrupt handlers remain active unless manually cleared first!

``memcopy(from, to, numbytes)``
    Efficiently copy a number of bytes from a memory location to another.
    *Warning:* can only copy *non-overlapping* memory areas correctly!
    Because this function imposes some overhead to handle the parameters,
    it is only faster if the number of bytes is larger than a certain threshold.
    Compare the generated code to see if it was beneficial or not.
    The most efficient will often be to write a specialized copy routine in assembly yourself!

``memset(address, numbytes, bytevalue)``
    Efficiently set a part of memory to the given (u)byte value.
    But the most efficient will always be to write a specialized fill routine in assembly yourself!
    Note that for clearing the screen, very fast specialized subroutines are
    available in the ``textio`` and ``graphics`` library modules.

``memsetw(address, numwords, wordvalue)``
    Efficiently set a part of memory to the given (u)word value.
    But the most efficient will always be to write a specialized fill routine in assembly yourself!

``read_flags() -> ubyte``
    Returns the current value of the CPU status register.

``set_carry()``
    Sets the CPU status register Carry flag.

``clear_carry()``
    Clears the CPU status register Carry flag.

``set_irqd()``
    Sets the CPU status register Interrupt Disable flag.

``clear_irqd()``
    Clears the CPU status register Interrupt Disable flag.

``progend()``
    Returns the last address of the program in memory + 1.
    Can be used to load dynamic data after the program, instead of hardcoding something.

``wait(uword jiffies)``
    wait approximately the given number of jiffies (1/60th seconds)
    Note: the regular system irq handler has run for this to work as it depends on the system jiffy clock.
    If this is is not possible (for instance because your program is running its own irq handler logic *and* no longer calls
    the kernal's handler routine), you'll have to write your own wait routine instead.

``waitvsync()``
    busy wait till the next vsync has occurred (approximately), without depending on custom irq handling.
    can be used to avoid screen flicker/tearing when updating screen contents.
    note: a more accurate way to wait for vsync is to set up a vsync irq handler instead.
    note for cx16: the regular system irq handler has to run for this to work (this is not required on C64 and C128)

``waitrastborder()`` (c64/c128 targets only)
    busy wait till the raster position has reached the bottom screen border (approximately)
    can be used to avoid screen flicker/tearing when updating screen contents.
    note: a more accurate way to do this is by using a raster irq handler instead.

``reset_system()``
    Soft-reset the system back to initial power-on BASIC prompt.
    (called automatically by Prog8 when the main subroutine returns and the program is not using basicsafe zeropage option)

``poweroff_system()``  (commander x16 only)
    Powers down the computer.

``set_leds_brightness(ubyte activity, ubyte power)``  (commander x16 only)
    Sets the brightness of the activity and power leds on the computer.


conv
----
Routines to convert strings to numbers or vice versa.

- numbers to strings, in various formats (binary, hex, decimal)
- strings in decimal, hex and binary format into numbers (bytes, words)


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
- send arbitrary CbmDos command to disk drive

On the Commander X16 it tries to use that machine's fast Kernal loading routines if possible.


string
------
Provides string manipulation routines.

``length(str) -> ubyte length``
    Number of bytes in the string. This value is determined during runtime and counts upto
    the first terminating 0 byte in the string, regardless of the size of the string during compilation time.
    Don't confuse this with ``len`` and ``sizeof``!

``left(source, length, target)``
    Copies the left side of the source string of the given length to target string.
    It is assumed the target string buffer is large enough to contain the result.
    Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

``right(source, length, target)``
    Copies the right side of the source string of the given length to target string.
    It is assumed the target string buffer is large enough to contain the result.
    Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

``slice(source, start, length, target)``
    Copies a segment from the source string, starting at the given index,
    and of the given length to target string.
    It is assumed the target string buffer is large enough to contain the result.
    Also, you have to make sure yourself that start and length are within bounds of the strings.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

``find(string, char) -> ubyte index + carry bit``
    Locates the first position of the given character in the string, returns carry bit set if found
    and the index in the string. Or 0+carry bit clear if the character was not found.

``compare(string1, string2) -> ubyte result``
    Returns -1, 0 or 1 depending on whether string1 sorts before, equal or after string2.
    Note that you can also directly compare strings and string values with each other
    using ``==``, ``<`` etcetera (it will use string.compare for you under water automatically).

``copy(from, to) -> ubyte length``
    Copy a string to another, overwriting that one. Returns the length of the string that was copied.
    Often you don't have to call this explicitly and can just write ``string1 = string2``
    but this function is useful if you're dealing with addresses for instance.

``lower(string)``
    Lowercases the PETSCII-string in place.

``upper(string)``
    Uppercases the PETSCII-string in place.

``lowerchar(char)``
    Returns lowercased character.

``upperchar(char)``
    Returns uppercased character.

``startswith(string, prefix) -> bool``
    Returns true if string starts with prefix, otherwise false

``endswith(string, suffix) -> bool``
    Returns true if string ends with suffix, otherwise false

``pattern_match(string, pattern) -> ubyte`` (not on Virtual target)
    Returns 1 (true) if the string matches the pattern, 0 (false) if not.
    '?' in the pattern matches any one character. '*' in the pattern matches any substring.


floats
------

.. note::
    Floating point support is only available on c64, cx16 and virtual targets for now.

Provides definitions for the ROM/Kernal subroutines and utility routines dealing with floating
point variables.  This includes ``print_f``, the routine used to print floating point numbers,
``fabs`` to get the absolute value of a floating point number, and a dozen or so floating point
math routines.

atan(x)
    Arctangent.

ceil(x)
    Rounds the floating point up to an integer towards positive infinity.

cos(x)
    Cosine.
    If you want a fast integer cosine, have a look at examples/cx16/sincos.p8
    that contains various lookup tables generated by the 64tass assembler.

deg(x)
    Radians to degrees.

floor (x)
    Rounds the floating point down to an integer towards minus infinity.

ln(x)
    Natural logarithm (base e).

log2(x)
    Base 2 logarithm.

minf(x, y)
    returns the smallest of x and y.

maxf(x, y)
    returns the largest of x and y.

rad(x)
    Degrees to radians.

round(x)
    Rounds the floating point to the closest integer.

sin(x)
    Sine.
    If you want a fast integer sine, have a look at examples/cx16/sincos.p8
    that contains various lookup tables generated by the 64tass assembler.

sqrt(x)
    Floating point Square root.
    To do the reverse, squaring a floating point number, just write ``x*x``.

tan(x)
    Tangent.

rndf()
    returns the next random float between 0.0 and 1.0 from the Pseudo RNG sequence.

rndseedf(seed)
    Sets a new seed for the float pseudo-RNG sequence. Use a negative non-zero number as seed value.


graphics
--------
Monochrome bitmap graphics routines, fixed 320*200 resolution:

- clearing the screen
- drawing individual pixels
- drawing lines, rectangles, filled rectangles, circles, discs

This library is available both on the C64 and the Cx16.
It uses the ROM based graphics routines on the latter, and it is a very small library because of that.
That also means though that it is constrained to 320*200 resolution on the Cx16 as well.
Use the ``gfx2`` library if you want full-screen graphics or non-monochrome drawing (only on Cx16).


math
----
Low-level integer math routines (which you usually don't have to bother with directly, but they are used by the compiler internally).
Pseudo-Random number generators (byte and word).
Various 8-bit integer trig functions that use lookup tables to quickly calculate sine and cosines.
Usually a custom lookup table is the way to go if your application needs these,
but perhaps the provided ones can be of service too.


rnd()
    Returns next random byte 0-255 from the pseudo-RNG sequence.

rndw()
    Returns next random word 0-65535 from the pseudo-RNG sequence.

rndseed(uword seed1, uword seed2)
    Sets a new seed for the pseudo-RNG sequence (both rnd and rndw). The seed consists of two words.
    Do not use zeros for the seed!

sin8u(x)
    Fast 8-bit ubyte sine of angle 0..255, result is in range 0..255

sin8(x)
    Fast 8-bit byte sine of angle 0..255, result is in range -127..127

sinr8u(x)
    Fast 8-bit ubyte sine of angle 0..179 (each is a 2 degree step), result is in range 0..255
    Angles 180..255 will yield a garbage result!

sinr8(x)
    Fast 8-bit byte sine of angle 0..179 (each is a 2 degree step), result is in range -127..127
    Angles 180..255 will yield a garbage result!

cos8u(x)
    Fast 8-bit ubyte cosine of angle 0..255, result is in range 0..255

cos8(x)
    Fast 8-bit byte cosine of angle 0..255, result is in range -127..127

cosr8u(x)
    Fast 8-bit ubyte cosine of angle 0..179 (each is a 2 degree step), result is in range 0..255
    Angles 180..255 will yield a garbage result!

cosr8(x)
    Fast 8-bit byte cosine of angle 0..179 (each is a 2 degree step), result is in range -127..127
    Angles 180..255 will yield a garbage result!


cx16logo
--------
Just a fun module that contains the Commander X16 logo in PETSCII graphics
and allows you to print it anywhere on the screen.


prog8_lib
---------
Low-level language support. You should not normally have to bother with this directly.
The compiler needs it for various built-in system routines.


gfx2  (cx16 only)
-----------------
Full-screen multicolor bitmap graphics routines, available on the Cx16 machine only.

- multiple full-screen resolutions: 640 * 480 monochrome, and 320 * 240 monochrome and 256 colors
- clearing screen, switching screen mode, also back to text mode is possible.
- drawing and reading individual pixels
- drawing lines, rectangles, filled rectangles, circles, discs
- drawing text inside the bitmap
- in monochrome mode, it's possible to use a stippled drawing pattern to simulate a shade of gray.


palette  (cx16 only)
--------------------
Available for the Cx16 target. Various routines to set the display color palette.
There are also a few better looking Commodore 64 color palettes available here,
because the Commander X16's default colors for this (the first 16 colors) are too saturated
and are quite different than how they looked on a VIC-II chip in a C64.


cx16diskio  (cx16 only)
-----------------------
Available for the Cx16 target. Contains extensions to the load and load_raw routines from the regular
diskio module, to deal with loading of potentially large files in to banked ram (HiRam).
Routines to directly load data into video ram are also present (vload and vload_raw).
Also contains a helper function to calculate the file size of a loaded file (although that is truncated
to 16 bits, 64Kb)
Als contains routines for operating on subdirectories (chdir, mkdir, rmdir) and to relabel the disk.


psg  (cx16 only)
----------------
Available for the Cx16 target.
Contains a simple abstraction for the Vera's PSG (programmable sound generator) to play simple waveforms.
It includes an interrupt routine to handle simple Attack/Release envelopes as well.
See the examples/cx16/bdmusic.p8  program for ideas how to use it.
Read the source of this library module for details about the API.

