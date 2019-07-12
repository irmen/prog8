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

This code calculates prime numbers using the Sieve of Eratosthenes algorithm::

    %import c64utils
    %zeropage basicsafe

    ~ main {

        ubyte[256] sieve
        ubyte candidate_prime = 2

        sub start() {
            memset(sieve, 256, false)

            c64scr.print("prime numbers up to 255:\n\n")
            ubyte amount=0
            while true {
                ubyte prime = find_next_prime()
                if prime==0
                    break
                c64scr.print_ub(prime)
                c64scr.print(", ")
                amount++
            }
            c64.CHROUT('\n')
            c64scr.print("number of primes (expected 54): ")
            c64scr.print_ub(amount)
            c64.CHROUT('\n')
        }


        sub find_next_prime() -> ubyte {

            while sieve[candidate_prime] {
                candidate_prime++
                if candidate_prime==0
                    return 0
            }

            sieve[candidate_prime] = true
            uword multiple = candidate_prime
            while multiple < len(sieve) {
                sieve[lsb(multiple)] = true
                multiple += candidate_prime
            }
            return candidate_prime
        }
    }


when compiled an ran on a C-64 you'll get:

.. image:: _static/primes_example.png
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
and for Windows it's possible to get that as well. Check out `AdoptOpenJDK <https://adoptopenjdk.net/>`_ for
downloads.

Finally: a **C-64 emulator** (or a real C-64 ofcourse) to run the programs on. The compiler assumes the presence
of the `Vice emulator <http://vice-emu.sourceforge.net/>`_.

.. important::
    **Building the compiler itself:** (*Only needed if you have not downloaded a pre-built 'fat-jar'*)

    (re)building the compiler itself requires a recent Kotlin SDK.
    The compiler is developed using the `IntelliJ IDEA <https://www.jetbrains.com/idea/>`_
    IDE from Jetbrains, with the Kotlin plugin (free community edition of this IDE is available).
    But a bare Kotlin SDK installation should work just as well.

    Instructions on how to obtain a working compiler are in :ref:`building_compiler`.


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
