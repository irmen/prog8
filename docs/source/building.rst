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

**using the Gradle build system to make it yourself:**

The Gradle build system is used to build the compiler.
The most interesting gradle commands to run are probably:

    ``./gradlew check``
        Builds the compiler code and runs all available checks and unit-tests.
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

For normal use, the ``installDist`` target should suffice and ater succesful completion
of that build task, you can start the compiler with:

    ``./compiler/build/install/p8compile/bin/p8compile <options> <sourcefile>``

(You should probably make an alias...)

.. note::
    Development and testing is done on Linux, but the compiler should run on most
    operating systems. If you do have trouble building or running
    the compiler on another operating system, please let me know!



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


Compiling program code
----------------------

Make sure you have installed the :ref:`requirements`.

Compilation of program code is done by telling the Prog8 compiler to compile a main source code module file.
Other modules that this code needs will be loaded and processed via imports from within that file.
The compiler will link everything together into one output program at the end.

If you start the compiler without arguments, it will print a short usage text.
For normal use the compiler is invoked with the command:

    ``$ java -jar prog8compiler.jar sourcefile.p8``

    Other options are also available, see the introduction page about how
    to build and run the compiler.


By default, assembly code is generated and written to ``sourcefile.asm``.
It is then (automatically) fed to the `64tass <https://sourceforge.net/projects/tass64/>`_ cross assembler tool
that assembles it into the final program.
If you use the option to let the compiler auto-start a C-64 emulator, it will do so after
a successful compilation. This will load your program and the symbol and breakpoint lists
(for the machine code monitor) into the emulator.

Continuous compilation mode
^^^^^^^^^^^^^^^^^^^^^^^^^^^
Almost instant compilation times (less than a second) can be achieved when using the continuous compilation mode.
Start the compiler with the ``-watch`` argument to enable this.
It will compile your program and then instead of exiting, it waits for any changes in the module source files.
As soon as a change happens, the program gets compiled again.

Other options
^^^^^^^^^^^^^
There's an option to specify the output directory if you're not happy with the default (the current working directory).
Also it is possible to specify more than one main module to compile:
this can be useful to quickly recompile multiple separate programs quickly.
(compiling in a batch like this is a lot faster than invoking the compiler again once per main file)


Module source code files
------------------------

A module source file is a text file with the ``.p8`` suffix, containing the program's source code.
It consists of compilation options and other directives, imports of other modules,
and source code for one or more code blocks.

Prog8 has various *LIBRARY* modules that are defined in special internal files provided by the compiler.
You should not overwrite these or reuse their names.
They are embedded into the packaged release version of the compiler so you don't have to worry about
where they are, but their names are still reserved.


User defined library files
^^^^^^^^^^^^^^^^^^^^^^^^^^
You can create library files yourself too that can be shared among programs.
You can tell the compiler where it should look for these files, by setting the java command line property ``prog8.libdir``
or by setting the ``PROG8_LIBDIR`` environment variable to the correct directory.


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

Getting an assembler error about undefined symbols such as ``not defined 'c64flt'``?
This happens when your program uses floating point values, and you forgot to import ``c64flt`` library.
If you use floating points, the compiler needs routines from that library.
Fix it by adding an ``%import c64flt``.


Examples
--------

A couple of example programs can be found in the 'examples' directory of the source tree.
Make sure you have installed the :ref:`requirements`. Then, for instance,
to compile and run the rasterbars example program, use this command::

    $ java -jar prog8compiler.jar -emu examples/rasterbars.p8

or::

    $ ./p8compile.sh -emu examples/rasterbars.p8

