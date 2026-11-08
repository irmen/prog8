
.. _portingguide:

*************
Porting Guide
*************
.. index:: pair: Porting; Overview

Here is a guide for making Prog8 work for other compilation targets.
Answers to the questions below are used to configure the new target and supporting libraries.

New 6502-family machine targets can be added without changing the compiler by using a separate
configuration file. See :ref:`customizable_target` for details. The built-in M68K backend also
supports custom target configurations, but adding support for a new CPU family requires compiler
changes. You still need to provide most of the information asked for in this guide and put it in
the configuration file.

.. note::
    By default, generated code may use embedded variables and self-modifying code, which are not
    suitable for ROM. The experimental ``%option romable`` avoids these techniques, but ROM
    execution is not yet guaranteed for every program or target.


CPU
---
.. index:: pair: Porting; CPU

#. For a 6502-family target, is the CPU a 6502 or 65C02 (or strictly compatible with one of these)?
#. Can **64tass** assemble programs for the system? The M68K backend instead uses **vasm**.

Memory Map
----------
.. index:: pair: Porting; Memory Map

Zeropage
========
.. index:: pair: Porting; Zeropage

#. For 6502-family targets, provide four words (16-bit byte pairs) in zeropage that are free to use at all times.
#. Provide list of any additional free zeropage locations for a normal running system (BASIC + Kernal enabled)
#. Provide list of any additional free zeropage locations when BASIC is off, but floating point routines should still work
#. Provide list of any additional free zeropage locations when only the Kernal remains enabled

For 6502-family targets, four 16-bit zeropage words are the minimum required by Prog8. More known
available zeropage locations mean smaller and faster programs. M68K targets have no zeropage.


RAM, ROM, I/O
=============
.. index:: pair: Porting; RAM, ROM, I/O

#. what part(s) of the address space is RAM?  What parts of the RAM can be used by user programs?
#. what is the usual starting memory address of programs?
#. what part(s) of the address space is ROM?
#. what part(s) of the address space is memory-mapped I/O registers?
#. is there a block of "high ram" available (ram that is not the main ram used to load programs in) that could be used for variables?
#. is there a banking system? How does it work (how do you select Ram/Rom banks)? How is the default bank configuration set?

Character encodings
-------------------
.. index:: pair: Porting; Character encodings

#. provide the primary character encoding table that the system uses (i.e. how is text represented in memory. For example, PETSCII)
#. provide alternate character encodings (if any)
#. what are the system's standard character screen dimensions?
#. is there a screen character matrix directly accessible in RAM? What's it address? Same for color attributes if any.


ROM routines
------------
.. index:: pair: Porting; ROM routines

#. provide a list of the core ROM routines on the system, with names, addresses, and call signatures.

Ideally there are at least some routines to manipulate the screen and get some user input (clear, print text, print numbers, input strings from the keyboard)
Routines to initialize the system to a sane state and to do a warm reset are useful too.
The more the merrier.

Floating point
==============
.. index:: pair: Porting; Floating point

Prog8 can support floating point math *if* the target system has suitable floating point math routines in ROM. If that is the case:

#. what is the binary representation format of the floating point numbers? (how many bytes, how the bits are set up)
#. what are the valid minimum negative and maximum positive floating point values?
#. provide a list of the floating point math routines in ROM: name, address, call signature.


Support libraries
-----------------
.. index:: pair: Porting; Support libraries

The most important libraries are ``syslib`` and ``textio``.
``syslib`` *has* to provide several system level functions such as how to initialize the machine to a sane state,
and how to warm reset it, etc.
``textio`` contains the text output and input routines, it's very welcome if they are implemented also for
the new target system. But not required.

There are several other support libraries that you may want to port (``diskio``, ``graphics`` to name a few).

Also of course if there are unique things available on the new target system, don't hesitate to provide
extensions to the ``syslib`` or perhaps a new special custom library altogether.

