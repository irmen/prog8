==============================
Writing and building a program
==============================

.. _building_compiler:

First, getting a working compiler
---------------------------------

Before you can compile Prog8 programs, you'll have to download or build the compiler itself.
First make sure you have installed the :ref:`requirements`.
Then you can choose a few ways to get a compiler:

**Download a precompiled version from github:**

#. download a recent "fat-jar" (called something like "prog8compiler-all.jar") from `the releases on Github <https://github.com/irmen/prog8/releases>`_
#. run the compiler with "java -jar prog8compiler-all.jar" to see how you can use it.

**Using the Gradle build system to build it yourself:**

The Gradle build system is used to build the compiler.
The most interesting gradle commands to run are probably:

    ``./gradlew build``
        Builds the compiler code and runs all available checks and unit-tests.
        Also automatically runs the installDist and installShadowDist tasks.
    ``./gradlew installDist``
        Builds the compiler and installs it with scripts to run it, in the directory
        ``./compiler/build/install/p8compile``
    ``./gradlew installShadowDist``
        Creates a 'fat-jar' that contains the compiler and all dependencies, in a single
        executable .jar file, and includes few start scripts to run it.
        The output can be found in ``.compiler/build/install/compiler-shadow/``
    ``./gradlew shadowDistZip``
        Creates a zipfile with the above in it, for easy distribution.
        This file can be found in ``./compiler/build/distributions/``

For normal use, the ``installDist`` target should suffice and after succesful completion, you can start the compiler with:

    ``./compiler/build/install/p8compile/bin/p8compile <options> <sourcefile>``

(You should probably make an alias...)

.. hint::
    Development and testing is done on Linux using the IntelliJ IDEA IDE,
    but the compiler should run on most operating systems that provide a fairly modern
    java runtime (11 or newer). If you do have trouble building or running the compiler on your
    operating system, please let me know!

    To successfully build and debug in IDEA, you have to manually generate the Antlr-parser classes
    first. The easiest way to do this is the following:

    1. make sure you have the Antlr4 plugin installed in IDEA
    2. right click the grammar file Prog8ANTLR.g4 in the parser project, and choose "Generate Antlr Recognizer" from the menu.
    3. rebuild the full project.

    Alternatively you can also use the Makefile in the antlr directory to generate the parser, but for development the
    Antlr4 plugin provides several extremely handy features so you'll probably want to have it installed anyway.

    .. image:: _static/antlrparser.png
       :alt: Generating the Antlr4 parser files


What is a Prog8 "Program" anyway?
---------------------------------

A "complete runnable program" is a compiled, assembled, and linked together single unit.
It contains all of the program's code and data and has a certain file format that
allows it to be loaded directly on the target system.   Prog8 currently has no built-in
support for programs that exceed 64 Kb of memory, nor for multi-part loaders.

For the Commodore-64, most programs will have a tiny BASIC launcher that does a SYS into the generated machine code.
This way the user can load it as any other program and simply RUN it to start. (This is a regular ".prg" program).
Prog8 can create those, but it is also possible to output plain binary programs
that can be loaded into memory anywhere.


Running the compiler
--------------------

Make sure you have installed the :ref:`requirements`.

You run the Prog8 compiler on a main source code module file.
Other modules that this code needs will be loaded and processed via imports from within that file.
The compiler will link everything together into one output program at the end.

If you start the compiler without arguments, it will print a short usage text.
For normal use the compiler can be invoked with the command:

    ``$ java -jar prog8compiler-7.3-all.jar sourcefile.p8``

    (Use the appropriate name and version of the jar file downloaded from one of the Git releases.
    Other ways to invoke the compiler are also available: see the introduction page about how
    to build and run the compiler yourself)


By default, assembly code is generated and written to ``sourcefile.asm``.
It is then (automatically) fed to the `64tass <https://sourceforge.net/projects/tass64/>`_ assembler tool
that creastes the final runnable program.


Command line options
^^^^^^^^^^^^^^^^^^^^

One or more .p8 module files
    Specify the main module file(s) to compile.
    Every file specified is a separate program.

``-help``, ``-h``
    Prints short command line usage information.

``-target <compilation target>``
    Sets the target output of the compiler, currently 'c64' and 'cx16' are valid targets.
    c64 = Commodore 64, c128 = Commodore 128, cx16 = Commander X16, atari = Atari 800 XL
    Default = c64

``-srcdirs <pathlist>``
    Specify a list of extra paths (separated with ':'), to search in for imported modules.
    Useful if you have library modules somewhere that you want to re-use,
    or to switch implementations of certain routines via a command line switch.

``-emu``, ``-emu2``
    Auto-starts target system emulator after successful compilation.
    emu2 starts the alternative emulator if available.
    The compiled program and the symbol and breakpoint lists
    (for the machine code monitor) are immediately loaded into the emulator..

``-out <directory>``
    sets directory location for output files instead of current directory

``-noasm``
    Do not create assembly code and output program.
    Useful for debugging or doing quick syntax checks.

``-noopt``
    Don't perform any code optimizations.
    Useful for debugging or faster compilation cycles.

``-noreinit``
    Don't create code to reinitialize the global (block level) variables on every run of the program.
    Also means that all such variables are no longer placed in the zero page.
    Sometimes the program will be a lot shorter when using this, but sometimes the opposite happens.
    When using this option, it is no longer be possible to run the program correctly more than once!
    *Experimental feature*: still has some problems!

``-optfloatx``
    Also optimize float expressions if optimizations are enabled.
    Warning: can increase program size significantly if a lot of floating point expressions are used.

``-watch``
    Enables continuous compilation mode (watches for file changes).
    This greatly increases compilation speed on subsequent runs:
    almost instant compilation times (less than a second) can be achieved in this mode.
    The compiler will compile your program and then instead of exiting, it waits for any changes in the module source files.
    As soon as a change happens, the program gets compiled again.
    Note that it is possible to use the watch mode with multiple modules as well, but it will
    recompile everything in that list even if only one of the files got updated.

``-slowwarn``
    Shows debug warnings about slow or problematic assembly code generation.
    Ideally, the compiler should use as few stack based evaluations as possible.

``-quietasm``
    Don't print assembler output results.

``-asmlist``
    Generate an assembler listing file as well.

``-expericodegen``
    Use experimental code generation backend (*incomplete*).

``-vm``
    load and run a p8-virt listing in the internal VirtualMachine instead of compiling a prog8 program file..

``-D SYMBOLNAME=VALUE``
    Add this user-defined symbol directly to the beginning of the generated assembly file.
    Can be repeated to define multiple symbols.

``-esa <address>``
    Override the base address of the evaluation stack. Has to be page-aligned.
    You can specify an integer or hexadecimal address.
    When not compiling for the CommanderX16 target, the location of the 16 virtual registers cx16.r0..r15
    is changed accordingly (to keep them in the same memory space as the evaluation stack).


Module source code files
------------------------

A module source file is a text file with the ``.p8`` suffix, containing the program's source code.
It consists of compilation options and other directives, imports of other modules,
and source code for one or more code blocks.

Prog8 has various *LIBRARY* modules that are defined in special internal files provided by the compiler.
You should not overwrite these or reuse their names.
They are embedded into the packaged release version of the compiler so you don't have to worry about
where they are, but their names are still reserved.


Importing other source files and specifying search location(s)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
You can create multiple source files yourself to modularize your large programs into
multiple module files. You can also create "library" modules this way with handy routines,
that can be shared among programs. By importing those module files, you can use them in other modules.
It is possible to tell the compiler where it should look for these files, by using
the ``srcdirs`` command line option.


.. _debugging:

Debugging (with Vice)
---------------------

There's support for using the monitor and debugging capabilities of the rather excellent
`Vice emulator <http://vice-emu.sourceforge.net/>`_.

The ``%breakpoint`` directive (see :ref:`directives`) in the source code instructs the compiler to put
a *breakpoint* at that position. Some systems use a BRK instruction for this, but
this will usually halt the machine altogether instead of just suspending execution.
Prog8 issues a NOP instruction instead and creates a 'virtual' breakpoint at this position.
All breakpoints are then written to a file called "programname.vice-mon-list",
which is meant to be used by the Vice emulator.
It contains a series of commands for Vice's monitor, including source labels and the breakpoint settings.
If you use the emulator autostart feature of the compiler, it will take care of this for you.
If you launch Vice manually, you'll have to use a command line option to load this file:

	``$ x64 -moncommands programname.vice-mon-list``

Vice will then use the label names in memory disassembly, and will activate any breakpoints as well.
If your running program hits one of the breakpoints, Vice will halt execution and drop you into the monitor.


Troubleshooting
---------------

Getting an assembler error about undefined symbols such as ``not defined 'floats'``?
This happens when your program uses floating point values, and you forgot to import ``floats`` library.
If you use floating points, the compiler needs routines from that library.
Fix it by adding an ``%import floats``.


Examples
--------

A couple of example programs can be found in the 'examples' directory of the source tree.
Make sure you have installed the :ref:`requirements`. Then, for instance,
to compile and run the rasterbars example program, use this command::

    $ java -jar prog8compiler.jar -emu examples/rasterbars.p8

or::

    $ ./p8compile.sh -emu examples/rasterbars.p8

