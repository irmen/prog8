IL65 documentation - |version|
==============================

.. image:: _static/logo.jpg
    :align: center
    :alt: IL65 logo

.. index:: what is IL65

What is IL65?
-------------

IL65 is an experimental compiled programming language targeting the 8-bit
`6502 <https://en.wikipedia.org/wiki/MOS_Technology_6502>`_ /
`6510 <https://en.wikipedia.org/wiki/MOS_Technology_6510>`_ microprocessor.
This CPU is from the late 1970's and early 1980's and was used in many home computers from that era,
such as the `Commodore-64 <https://en.wikipedia.org/wiki/Commodore_64>`_.
The language aims to provide many conveniences over raw assembly code (even when using a macro assembler),
while still being low level enough to create high performance programs.


IL65 is copyright © Irmen de Jong (irmen@razorvine.net | http://www.razorvine.net).

This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html


Design principles
-----------------

- It is a cross-compilation toolkit running on a modern machine.
  The resulting output is a machine code program runnable on actual 8-bit 6502 hardware.
- Based on simple and familiar imperative structured programming paradigm.
- Allowing modular programming: modules, code blocks, subroutines.
- Provide high level programming constructs but stay close to the metal;
  still able to directly use memory addresses, CPU registers and ROM subroutines
- No dynamic memory allocation. All variables stay fixed size as determined at compile time.
- Provide various quality of life language features specifically for the target platform.
- Provide a convenient edit/compile/run cycle by being able to directly launch
  the compiled program in an emulator and provide debugging information to the emulator.
- The compiler outputs a regular 6502 assembly code file, it doesn't assemble this itself.
  A third party cross-assembler tool is used to do this final step.
- Goto is considered harmful, but not here; arbitrary control flow jumps are allowed.
- No complicated error handling or overflow checks that would slow things down.


Required tools
--------------

@TODO
- 64tass cross-assembler?
- java?
- kotlin?


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
