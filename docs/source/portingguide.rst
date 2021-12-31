=============
Porting Guide
=============

Here is a guide for porting Prog8 to other compilation targets.
Answers to the questions below are used to configure the new target and supporting libraries.


CPU
---
#. 6502 or 65C02? (or strictly compatible with one of these)
#. can the **64tass** cross assembler create programs for the system?  (if not, bad luck atm)

Memory Map
----------

Zero page
=========
#. *Absolute requirement:* Provide three times 2 consecutive bytes (i.e. three 16-bit pointers) in the Zero page that are free to use at all times.
#. Provide list of any additional free Zero page locations for a normal running system (basic + kernal enabled)
#. Provide list of any additional free Zero page locations when basic is off, but floating point routines should still work
#. Provide list of any additional free Zero page locations when only the kernal remains enabled

Only the three 16-bit pointers are absolutely required to be able to use prog8 on the system.
But more known available Zero page locations mean smaller and faster programs.


RAM, ROM, I/O
=============

#. what part(s) of the address space is RAM?  What parts of the RAM can be used by user programs?
#. what is the load address of Basic programs?
#. what is a good load address of machine code programs?
#. what is the best place to put 2 pages (512 bytes total) of scratch area data in RAM?
#. what part(s) of the address space is ROM?
#. what part(s) of the address space is memory mapped I/O registers?
#. is there a banking system? How does it work (how do you select Ram/Rom banks)? How is the default bank configuration set?

Screen and Character encodings
------------------------------
#. provide the primary character encoding table that the system uses (i.e. how is text represented in memory)
#. provide alternate character encoding table (if any)
#. what are the system's character screen dimensions?
#. is there a screen matrix directly accessible in Ram? Provide addresses of the character matrix and color attributes matrix, if any.


ROM routines
------------
#. provide a list of the core ROM routines on the system, with names, addresses, and call signatures.

Ideally there are at least some routines to manipulate the screen and get some user input(clear, print text, print numbers, input strings from the keyboard)
Routines to initialize the system to a sane state and to do a warm reset are useful too.
The more the merrier.

Floating point
==============
Prog8 supports floating point math *if* the target system has floating point math routines in ROM.
If the machine has this:

#. what is the binary representation format of the floating point numbers? (how many bytes, how the bits are set up)
#. what are the valid minimum negative and maximum positive floating point values?
#. provide a list of the floating point math routines in ROM: name, address, call signature.


Support libraries
-----------------
The most important libraries are ``syslib`` and ``textio``.
``syslib`` *has* to provide several system level functions such as how to initialize the machine to a sane state,
and how to warm reset it, etc.
``textio`` contains the text output and input routines, it's very welcome if they are implemented also for
the new target system.

There are several other support libraries that you may want to port (``diskio``, ``graphics`` to name a few).

Also ofcourse if there are unique things available on the new target system, don't hesitate to provide
extensions to the ``syslib`` or perhaps a new special custom library altogether.


