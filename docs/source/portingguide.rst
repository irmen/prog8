
.. _portingguide:

*************
Porting Guide
*************

Here is a guide for porting Prog8 to other compilation targets.
Answers to the questions below are used to configure the new target and supporting libraries.

.. note::
    The assembly code that prog8 generates is not suitable to be put into ROM. (It contains
    embedded variables, and self-modifying code).
    If the target system is designed to run programs from ROM, and has just a little bit of RAM
    intended for variables, prog8 is likely not a feasible language for such a system right now.


CPU
---
#. 6502 or 65C02? (or strictly compatible with one of these)
#. can the **64tass** cross assembler create programs for the system?  (if not, bad luck atm)

Memory Map
----------

Zeropage
========
#. *Absolute requirement:* Provide four times 2 consecutive bytes (i.e. four 16-bit words) in the zeropage that are free to use at all times.
#. Provide list of any additional free zeropage locations for a normal running system (BASIC + Kernal enabled)
#. Provide list of any additional free zeropage locations when BASIC is off, but floating point routines should still work
#. Provide list of any additional free zeropage locations when only the Kernal remains enabled

Only the four 16-bit zero page words are absolutely required to be able to use prog8 on the system.
But more known available zeropage locations mean smaller and faster programs.


RAM, ROM, I/O
=============

#. what part(s) of the address space is RAM?  What parts of the RAM can be used by user programs?
#. what is the usual starting memory address of programs?
#. what part(s) of the address space is ROM?
#. what part(s) of the address space is memory-mapped I/O registers?
#. is there a block of "high ram" available (ram that is not the main ram used to load programs in) that could be used for variables?
#. is there a banking system? How does it work (how do you select Ram/Rom banks)? How is the default bank configuration set?
   Note that prog8 itself has no notion of banking, but this knowledge may be required for proper system initialization.

Character encodings
-------------------
#. if not PETSCII or CBM screencodes: provide the primary character encoding table that the system uses (i.e. how is text represented in memory)
#. provide alternate character encodings (if any)
#. what are the system's standard character screen dimensions?
#. is there a screen character matrix directly accessible in Ram? What's it address? Same for color attributes if any.


ROM routines
------------
#. provide a list of the core ROM routines on the system, with names, addresses, and call signatures.

Ideally there are at least some routines to manipulate the screen and get some user input (clear, print text, print numbers, input strings from the keyboard)
Routines to initialize the system to a sane state and to do a warm reset are useful too.
The more the merrier.

Floating point
==============
Prog8 can support floating point math *if* the target system has floating point math routines in ROM. If that is the case:

#. what is the binary representation format of the floating point numbers? (how many bytes, how the bits are set up)
#. what are the valid minimum negative and maximum positive floating point values?
#. provide a list of the floating point math routines in ROM: name, address, call signature.


Support libraries
-----------------
The most important libraries are ``syslib`` and ``textio``.
``syslib`` *has* to provide several system level functions such as how to initialize the machine to a sane state,
and how to warm reset it, etc.
``textio`` contains the text output and input routines, it's very welcome if they are implemented also for
the new target system. But not required.

There are several other support libraries that you may want to port (``diskio``, ``graphics`` to name a few).

Also of course if there are unique things available on the new target system, don't hesitate to provide
extensions to the ``syslib`` or perhaps a new special custom library altogether.

