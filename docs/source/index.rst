Prog8 documentation - |version|
===============================

.. image:: _static/logo.jpg
    :align: center
    :alt: Prog8 logo

.. index:: what is Prog8

What is Prog8?
--------------

This is an experimental compiled programming language targeting the 8-bit
`6502 <https://en.wikipedia.org/wiki/MOS_Technology_6502>`_ /
`6510 <https://en.wikipedia.org/wiki/MOS_Technology_6510>`_ microprocessor.
This CPU is from the late 1970's and early 1980's and was used in many home computers from that era,
such as the `Commodore-64 <https://en.wikipedia.org/wiki/Commodore_64>`_.
The language aims to provide many conveniences over raw assembly code (even when using a macro assembler),
while still being low level enough to create high performance programs.


Prog8 is copyright © Irmen de Jong (irmen@razorvine.net | http://www.razorvine.net).
The project is on github: https://github.com/irmen/prog8.git


This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html


.. image:: _static/cube3d.png
    :width: 33%
    :alt: 3d rotating sprites
.. image:: _static/wizzine.png
    :width: 33%
    :alt: Simple wizzine sprite effect
.. image:: _static/tehtriz.png
    :width: 33%
    :alt: Fully playable tetris clone


Code example
------------

When this code is compiled::

    %import c64lib
    %import c64utils
    %import c64flt

    ~ main {
        sub start() {
            ; set text color and activate lowercase charset
            c64.COLOR = 13
            c64.VMCSB |= 2

            ; use optimized routine to write text
            c64scr.print("Hello!\n")

            ; use iteration to write text
            str question = "How are you?\n"
            for ubyte char in question
                c64.CHROUT(char)

            ; use indexed loop to write characters
            str bye = "Goodbye!\n"
            for ubyte c in 0 to len(bye)
                c64.CHROUT(bye[c])


            float clock_seconds = ((mkword(c64.TIME_LO, c64.TIME_MID) as float)
                                    + (c64.TIME_HI as float)*65536.0)
                                     / 60
            float hours = floor(clock_seconds / 3600)
            clock_seconds -= hours*3600
            float minutes = floor(clock_seconds / 60)
            clock_seconds = floor(clock_seconds - minutes * 60.0)

            c64scr.print("system time in ti$ is ")
            c64flt.print_f(hours)
            c64.CHROUT(':')
            c64flt.print_f(minutes)
            c64.CHROUT(':')
            c64flt.print_f(clock_seconds)
            c64.CHROUT('\n')
        }
    }



you get a program that outputs this when loaded on a C-64:

.. image:: _static/hello_screen.png
    :align: center
    :alt: result when run on C-64


Design principles and features
------------------------------

- It is a cross-compiler running on modern machines (Linux, MacOS, Windows, ...)
  The generated output is a machine code program runnable on actual 8-bit 6502 hardware.
- Usable on most operating systems.
- Based on simple and familiar imperative structured programming paradigm.
- 'One statement per line' code style, resulting in clear readable programs.
- Modular programming and scoping via modules, code blocks, and subroutines.
- Provide high level programming constructs but stay close to the metal;
  still able to directly use memory addresses, CPU registers and ROM subroutines
- Arbitrary number of subroutine parameters (constrained only by available memory)
- Complex nested expressions are possible
- Values are typed. Types supported include signed and unsigned bytes and words, arrays, strings and floats.
- No dynamic memory allocation or sizing! All variables stay fixed size as determined at compile time.
- Provide various quality of life language features and library subroutines specifically for the target platform.
- Provide a very convenient edit/compile/run cycle by being able to directly launch
  the compiled program in an emulator and provide debugging information to the emulator.
- The compiler outputs a regular 6502 assembly source code file, but doesn't assemble this itself.
  The (separate) '64tass' cross-assembler tool is used for that.
- Goto is usually considered harmful, but not here: arbitrary control flow jumps and branches are possible,
  and will usually translate directly into the appropriate single 6502 jump/branch instruction.
- There are no complicated built-in error handling or overflow checks, you'll have to take care
  of this yourself if required. This keeps the language and code simple and efficient.
- The compiler tries to optimize the program and generated code, but hand-tuning of the
  performance or space-critical parts will likely still be required. This is supported by
  the ability to easily write embedded assembly code directly in the program source code.


.. _requirements:

Required tools
--------------

`64tass <https://sourceforge.net/projects/tass64/>`_ - cross assembler. Install this on your shell path.
A recent .exe version of this tool for Windows can be obtained from my `clone <https://github.com/irmen/64tass/releases>`_ of this project.
For other platforms it is very easy to compile it yourself (make ; make install).

A **Java runtime (jre or jdk), version 8 or newer**  is required to run the packaged compiler.
If you're scared of Oracle's licensing terms, most Linux distributions ship OpenJDK instead
and for Windows it's possible to get that as well: for instance,
`Azul's Zulu <https://www.azul.com/downloads/zulu/>`_ is a certified OpenJDK
implementation available for various platforms.

Finally: a **C-64 emulator** (or a real C-64 ofcourse) to run the programs on. The compiler assumes the presence
of the `Vice emulator <http://vice-emu.sourceforge.net/>`_.

.. hint::
    The compiler is almost completely written in Kotlin, but the packaged release version
    only requires a Java runtime. All other needed libraries and files are embedded in the
    packaged jar file.

.. note::
    Building the compiler itself:

    (re)building the compiler itself requires a Kotlin SDK version 1.3.
    The compiler is developed using the `IntelliJ IDEA <https://www.jetbrains.com/idea/>`_
    IDE from Jetbrains, with the Kotlin plugin (free community edition of this IDE is available).
    But a bare Kotlin SDK installation should work just as well.
    A shell script (``build_the_compiler.sh``) is provided to build and package the compiler from the command line.
    You can also use the Gradle build system to build the compiler (it will take care of
    downloading all required libraries for you) by typing ``gradle installDist`` for instance.
    The output of this gradle build will appear in the "./compiler/build/install/p8compile/" directory.

.. note::
    Development and testing is done on Linux, but the compiler should run on most
    operating systems. If you do have trouble building or running
    the compiler on another operating system, please let me know!


.. toctree::
    :maxdepth: 2
    :caption: Contents of this manual:

    targetsystem.rst
    building.rst
    programming.rst
    syntaxreference.rst
    todo.rst


Index
=====

* :ref:`genindex`
