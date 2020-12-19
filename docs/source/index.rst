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
`65c02 <https://en.wikipedia.org/wiki/MOS_Technology_65C02>`_ /
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


Language features
-----------------

- It is a cross-compiler running on modern machines (Linux, MacOS, Windows, ...)
  The generated output is a machine code program runnable on actual 8-bit 6502 hardware.
- Provide a very convenient edit/compile/run cycle by being able to directly launch
  the compiled program in an emulator and provide debugging information to this emulator.
- Based on simple and familiar imperative structured programming (it looks like a mix of C and Python)
- Modular programming and scoping via modules, code blocks, and subroutines.
- Provide high level programming constructs but at the same time stay close to the metal;
  still able to directly use memory addresses and ROM subroutines,
  and inline assembly to have full control when every register, cycle or byte matters
- Arbitrary number of subroutine parameters, Complex nested expressions are possible
- No stack frame allocations because parameters and local variables are automatically allocated statically
- Nested subroutines can access variables from outer scopes to avoids the overhead to pass everything via parameters
- Variable data types include signed and unsigned bytes and words, arrays, strings and floats.
- High-level code optimizations, such as const-folding, expression and statement simplifications/rewriting.
- Many built-in functions, such as ``sin``, ``cos``, ``rnd``, ``abs``, ``min``, ``max``, ``sqrt``, ``msb``, ``rol``, ``ror``, ``swap``, ``memset``, ``memcopy``, ``substr``, ``sort`` and ``reverse`` (and others)
- If you only use standard kernel and prog8 library routines, it is possible to compile the *exact same program* for both machines (just change the compiler target flag)!


Code example
------------

This code calculates prime numbers using the Sieve of Eratosthenes algorithm::

    %import textio
    %zeropage basicsafe

    main {
        ubyte[256] sieve
        ubyte candidate_prime = 2       ; is increased in the loop

        sub start() {
            ; clear the sieve, to reset starting situation on subsequent runs
            memset(sieve, 256, false)
            ; calculate primes
            txt.print("prime numbers up to 255:\n\n")
            ubyte amount=0
            repeat {
                ubyte prime = find_next_prime()
                if prime==0
                    break
                txt.print_ub(prime)
                txt.print(", ")
                amount++
            }
            txt.chrout('\n')
            txt.print("number of primes (expected 54): ")
            txt.print_ub(amount)
            txt.chrout('\n')
        }

        sub find_next_prime() -> ubyte {
            while sieve[candidate_prime] {
                candidate_prime++
                if candidate_prime==0
                    return 0        ; we wrapped; no more primes available in the sieve
            }

            ; found next one, mark the multiples and return it.
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

when the exact same program is compiled for the Commander X16 target, and run on the emulator, you get this:

.. image:: _static/primes_cx16.png
    :align: center
    :alt: result when run on CX16 emulator



.. _requirements:

Required tools
--------------

`64tass <https://sourceforge.net/projects/tass64/>`_ - cross assembler. Install this on your shell path.
It's very easy to compile yourself.
A recent precompiled .exe for Windows can be obtained from my `clone <https://github.com/irmen/64tass/releases>`_ of this project.
*You need at least version 1.55.2257 of this assembler to correctly use the breakpoints feature.*
It's possible to use older versions, but it is very likely that the automatic Vice breakpoints won't work with them.

A **Java runtime (jre or jdk), version 11 or newer**  is required to run the prog8 compiler itself.
If you're scared of Oracle's licensing terms, most Linux distributions ship OpenJDK in their packages repository instead.
For Windows it's possible to get that as well; check out `AdoptOpenJDK <https://adoptopenjdk.net/>`_ .
For MacOS you can use the Homebrew system to install a recent version of OpenJDK.

Finally: an **emulator** (or a real machine ofcourse) to test and run your programs on.
In C64 mode, thhe compiler assumes the presence of the `Vice emulator <http://vice-emu.sourceforge.net/>`_.
If you're targeting the CommanderX16 instead, there's the `x16emu <https://github.com/commanderx16/x16-emulator>`_.

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
    libraries.rst
    technical.rst
    todo.rst


Index
=====

* :ref:`genindex`
