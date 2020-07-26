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


Code examples
-------------

This code calculates prime numbers using the Sieve of Eratosthenes algorithm::

    %import c64utils
    %zeropage basicsafe

    main {

        ubyte[256] sieve
        ubyte candidate_prime = 2

        sub start() {
            memset(sieve, 256, false)

            c64scr.print("prime numbers up to 255:\n\n")
            ubyte amount=0
            repeat {
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


when compiled an ran on a C-64 you get this:

.. image:: _static/primes_example.png
    :align: center
    :alt: result when run on C-64


The following programs shows a use of the high level ``struct`` type::

    %import c64utils
    %zeropage basicsafe

    main {

        struct Color {
            ubyte red
            ubyte green
            ubyte blue
        }

        sub start() {

            Color purple = {255, 0, 255}
            Color other
            other = purple
            other.red /= 2
            other.green = 10 + other.green / 2
            other.blue = 99

            c64scr.print_ub(other.red)
            c64.CHROUT(',')
            c64scr.print_ub(other.green)
            c64.CHROUT(',')
            c64scr.print_ub(other.blue)
            c64.CHROUT('\n')
        }
    }

when compiled and ran, it prints  ``127,10,99`` on the screen.


Design principles and features
------------------------------

- It is a cross-compiler running on modern machines (Linux, MacOS, Windows, ...)
  The generated output is a machine code program runnable on actual 8-bit 6502 hardware.
- Based on simple and familiar imperative structured programming (it looks like a mix of C and Python)
- 'One statement per line' code, resulting in clear readable programs.
- Modular programming and scoping via modules, code blocks, and subroutines.
- Provide high level programming constructs but at the same time stay close to the metal;
  still able to directly use memory addresses and ROM subroutines,
  and inline assembly to have full control when every register, cycle or byte matters
- Arbitrary number of subroutine parameters
- Complex nested expressions are possible
- Nested subroutines can access variables from outer scopes to avoids the overhead to pass everything via parameters
- Values are typed. Available data types include signed and unsigned bytes and words, arrays, strings and floats.
- No dynamic memory allocation or sizing! All variables stay fixed size as determined at compile time.
- Provide various quality of life language features and library subroutines specifically for the target platform.
- Provide a very convenient edit/compile/run cycle by being able to directly launch
  the compiled program in an emulator and provide debugging information to this emulator.
- Arbitrary control flow jumps and branches are possible,
  and will usually translate directly into the appropriate single 6502 jump/branch instruction.
- There are no complicated built-in error handling or overflow checks, you'll have to take care
  of this yourself if required. This keeps the language and code simple and efficient.
- The compiler tries to optimize the program and generated code a bit, but hand-tuning of the
  performance or space-critical parts will likely still be required. This is supported by
  the ability to easily write embedded assembly code directly in the program source code.
- There are many built-in functions, such as ``sin``, ``cos``, ``rnd``, ``abs``, ``min``, ``max``, ``sqrt``, ``msb``, ``rol``, ``ror``, ``swap``, ``memset``, ``memcopy``, ``substr``, ``sort`` and ``reverse`` (and others)
- Assembling the generated code into a program wil be done by an external cross-assembler tool.


.. _requirements:

Required tools
--------------

`64tass <https://sourceforge.net/projects/tass64/>`_ - cross assembler. Install this on your shell path.
It's very easy to compile yourself.
A recent precompiled .exe for Windows can be obtained from my `clone <https://github.com/irmen/64tass/releases>`_ of this project.

A **Java runtime (jre or jdk), version 8 or newer**  is required to run the prog8 compiler itself.
If you're scared of Oracle's licensing terms, most Linux distributions ship OpenJDK instead.
Fnd for Windows it's possible to get that as well. Check out `AdoptOpenJDK <https://adoptopenjdk.net/>`_ .

Finally: a **C-64 emulator** (or a real C-64 ofcourse) can be nice to test and run your programs on.
The compiler assumes the presence of the `Vice emulator <http://vice-emu.sourceforge.net/>`_.

.. important::
    **Building the compiler itself:** (*Only needed if you have not downloaded a pre-built 'fat-jar'*)

    (Re)building the compiler itself requires a recent Kotlin SDK.
    The compiler is developed using `IntelliJ IDEA <https://www.jetbrains.com/idea/>`_ ,
    but only a Kotlin SDK installation should work as well, because the gradle tool is
    used to compile everything from the commandline.

    Instructions on how to obtain a prebuilt compiler are in :ref:`building_compiler`.


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
