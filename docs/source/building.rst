==============================
Writing and building a program
==============================

What is a "Program" anyway?
---------------------------

A "complete runnable program" is a compiled, assembled, and linked together single unit.
It contains all of the program's code and data and has a certain file format that
allows it to be loaded directly on the target system.   IL65 currently has no built-in
support for programs that exceed 64 Kb of memory, nor for multi-part loaders.

For the Commodore-64, most programs will have a tiny BASIC launcher that does a SYS into the generated machine code.
This way the user can load it as any other program and simply RUN it to start. (This is a regular ".prg" program).
Il65 can create those, but it is also possible to output plain binary programs
that can be loaded into memory anywhere.


Compiling program code
----------------------

Compilation of program code is done by telling the IL65 compiler to compile a main source code module file.
Other modules that this code needs will be loaded and processed via imports from within that file.
The compiler will link everything together into one output program at the end.

The compiler is invoked with the command:

	``$ @todo``

It produces an assembly source code file which in turn will (automatically) be passed to
the `64tass <https://sourceforge.net/projects/tass64/>`_ cross assembler tool
that assembles it into the final program.


Module source code files
------------------------

A module source file is a text file with the ``.ill`` suffix, containing the program's source code.
It consists of compilation options and other directives, imports of other modules,
and source code for one or more code blocks.

IL65 has a couple of *LIBRARY* modules that are defined in special internal files provided by the compiler:
``c64lib``, ``il65lib``, ``mathlib``.
You should not overwrite these or reuse their names.


.. _debugging:

Debugging (with Vice)
---------------------

There's support for using the monitor and debugging capabilities of the rather excellent
`Vice emulator <http://vice-emu.sourceforge.net/>`_.

The ``%breakpoint`` directive (see :ref:`directives`) in the source code instructs the compiler to put
a *breakpoint* at that position. Some systems use a BRK instruction for this, but
this will usually halt the machine altogether instead of just suspending execution.
IL65 issues a NOP instruction instead and creates a 'virtual' breakpoint at this position.
All breakpoints are then written to a file called "programname.vice-mon-list",
which is meant to be used by the Vice emulator.
It contains a series of commands for Vice's monitor, including source labels and the breakpoint settings.
If you use the vice autostart feature of the compiler, it will be processed by Vice automatically and immediately.
If you launch Vice manually, you'll have to use a command line option to load this file:

	``$ x64 -moncommands programname.vice-mon-list``

Vice will then use the label names in memory disassembly, and will activate the breakpoints as well.
If your running program hits one of the breakpoints, Vice will halt execution and drop you into the monitor.


Troubleshooting
---------------

Getting an assembler error about undefined symbols such as ``not defined 'c64flt'``?
This happens when your program uses floating point values, and you forgot to import the ``c64lib``.
If you use floating points, the program will need routines from that library.
Fix it by adding an ``%import c64lib``.
