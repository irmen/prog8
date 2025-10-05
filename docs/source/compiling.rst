*******************
Compiling a program
*******************

.. _building_compiler:

First, getting a working compiler
---------------------------------

Before you can compile Prog8 programs, you'll have to download or build the compiler itself.
Then make sure you have installed the :ref:`requirements`.
Then you can choose a few ways to get a compiler:

**Download an official release version from Github:**

#. download a recent "fat-jar" (called something like "prog8c-all.jar") from `the releases on Github <https://github.com/irmen/prog8/releases>`_
#. run the compiler with "java -jar prog8c.jar" to see how you can use it (use the correct name and version of the jar file you've downloaded).

**Or, install via a Package Manager:**

Arch Linux:
    Currently, it's available on `AUR <https://wiki.archlinux.org/title/Arch_User_Repository>`_ for Arch Linux and compatible systems.
    The package is called `"prog8" <https://aur.archlinux.org/packages/prog8>`_.

    This package, alongside the compiler itself, also globally installs syntax highlighting for ``vim`` and ``nano``.
    In order to run compiler, you can type ``prog8c``. The usage of those commands is exactly the same as with the ``java -jar`` method.

    In case you prefer to install AUR packages in a traditional manner, make sure to install `"tass64" package <https://aur.archlinux.org/packages/tass64>`_
    before installing prog8, as `makepkg <https://wiki.archlinux.org/title/Makepkg>`_ itself doesn't fetch AUR dependencies.

Mac OS (and Linux, and WSL2 on Windows):
    Prog8 can be installed via `Homebrew <https://formulae.brew.sh/formula/prog8>`_ using the command ``brew install prog8``.
    It will make the ``prog8c`` command available and also installs the other required software tools for you.
    While Homebrew works on Linux, it's probably best to first check your distribution's native package manager.

**Or, download a bleeding edge development version from Github:**

#. find the latest CI build on  `the actions page on Github <https://github.com/irmen/prog8/actions>`_
#. download the zipped jar artifact from that build, and unzip it.
#. run the compiler with "java -jar prog8c.jar"  (use the correct name and version of the jar file you've downloaded).

**Or, use the Gradle build system to build it yourself from source:**

The Gradle build system is used to build the compiler. You will also need at least Java version 17 or higher to build it.
The most interesting gradle commands to run are probably the ones listed below.
(Note: if you have a recent gradle installed on your system already, you can probably replace the ``./gradlew`` wrapper commands with just the regular ``gradle`` command.)

    ``./gradlew build``
        Builds the compiler code and runs all available checks and unit-tests.
        Also automatically runs the installDist and installShadowDist tasks.
        Read below at those tasks for where the resulting compiler jar file gets written.
    ``./gradlew installDist``
        Builds the compiler and installs it with scripts to run it, in the directory
        ``./compiler/build/install/prog8c``
    ``./gradlew installShadowDist``
        Creates a 'fat-jar' that contains the compiler and all dependencies, in a single
        executable .jar file, and includes few start scripts to run it.
        The output can be found in ``.compiler/build/install/prog8c-shadow/``
    ``./gradlew shadowDistZip``
        Creates a zipfile with the above in it, for easy distribution.
        This file can be found in ``./compiler/build/distributions/``

For normal use, the ``installDist`` task should suffice and after succesful completion, you can start the compiler with:

    ``./compiler/build/install/prog8c/bin/prog8c <options> <sourcefile>``

(You should probably make an alias or link...)

.. hint::
    Development and testing is done on Linux using the IntelliJ IDEA IDE,
    but the actual prog8 compiler should run on all operating systems that provide a Java runtime (version 11 or newer).
    If you do have trouble building or running the compiler on your operating system, please let me know!

    To successfully build and debug in IDEA, you have to do two things manually first:

    1. you have to generate the buildversion file, do this with the shell command:   ``gradle createVersionFile``
    2. manually generate the Antlr-parser classes first.

    The easiest way to build the parser classes this is the following:

    1. make sure you have the Antlr4 plugin installed in IDEA
    2. right click the grammar file Prog8ANTLR.g4 in the parser project, and choose "Generate Antlr Recognizer" from the menu.
    3. rebuild the full project.

    Alternatively you can also use the Makefile in the antlr directory to generate the parser, but for development the
    Antlr4 plugin provides several extremely handy features so you'll probably want to have it installed anyway.

    .. image:: _static/antlrparser.png
       :alt: Generating the Antlr4 parser files

.. _requirements:

Required additional tools
-------------------------

`64tass <https://sourceforge.net/projects/tass64/>`_ - cross assembler. Install this program somewhere on your shell's search path.
It's easy to compile yourself, but a recent precompiled .exe (only for Windows) can be obtained from
`the files section <https://sourceforge.net/projects/tass64/files/binaries/>`_ in the official project on sourceforge.
*You need at least version 1.58.0 of this assembler.*
If you are on Linux, there's probably a "64tass" package in the repositories, but check if it is a recent enough version.

A **Java runtime (jre or jdk), version 11 or newer**  is required to run the prog8 compiler itself. Version 17 or higher if you want to
build the compiler from source.
If you're scared of Oracle's licensing terms, get one of the versions of another vendor. Even Microsoft provides their own version.
Other OpenJDK builds can be found at `Adoptium <https://adoptium.net/temurin/releases/?version=11>`_ .
For MacOS you can also use the Homebrew system to install a recent version of OpenJDK.



Running the compiler
--------------------

You run the Prog8 compiler on a main source code module file.
Other modules that this code needs will be loaded and processed via imports from within that file.
The compiler will link everything together into one output program at the end.

If you start the compiler without arguments, it will print a short usage text.
For normal use the compiler can be invoked with the command:

    ``$ java -jar prog8c.jar -target cx16 sourcefile.p8``

    (Use the appropriate name and version of the jar file downloaded from one of the Git releases.
    Other ways to invoke the compiler are also available: see the introduction page about how
    to build and run the compiler yourself. The ``-target`` option is always required, in this case we
    tell it to compile a program for the Commander X16)


By default, assembly code is generated and written to ``sourcefile.asm``.
It is then (automatically) fed to the `64tass <https://sourceforge.net/projects/tass64/>`_ assembler tool
that creates the final runnable program.


Command line options
^^^^^^^^^^^^^^^^^^^^

One or more .p8 module files
    Specify the main module file(s) to compile.
    Every file specified is a separate program.

``-help``, ``-h``
    Prints short command line usage information.

``-asmlist``
    Also generate an assembler listing file  <program>.list

``-breakinstr <instruction>``
    Also output the specified CPU instruction for a ``%breakpoint``, as well as the entry in the vice monitor list file.
    This can be useful on emulators/systems that don't parse the breakpoint information in the list file,
    such as the X16Emu emulator for the Commander X16.
    Useful instructions to consider are ``brk`` and ``stp``.
    For example for the Commander X16 emulator, ``stp`` is useful because it can actually tyrigger
    a breakpoint halt in the debugger when this is enabled by running the emulator with -debug.

``-bytes2float <bytes>``
    convert a comma separated list of bytes from the specified target system to a float value.
    Also see -float2bytes

``-check``
    Quickly check the program for errors. No actual compilation will be performed.

``-D SYMBOLNAME=VALUE``
    Add this user-defined symbol directly to the beginning of the generated assembly file.
    Can be repeated to define multiple symbols.

``-dumpsymbols``
    print a dump of the variable declarations and subroutine signatures

``-dumpvars``
    print a dump of the variables in the program

``-emu``, ``-emu2``
    Auto-starts target system emulator after successful compilation.
    emu2 starts the alternative emulator if available.
    The compiled program and the symbol and breakpoint lists
    (for the machine code monitor) are immediately loaded into the emulator (if it supports them)

``-expericodegen``
    Use experimental code generation backend (*incomplete*).

``-float2bytes <number>``
    convert floating point number to a list of bytes for the specified target system.
    Also see -bytes2float

``-ignorefootguns``
    Don't print warnings for 'footgun' issues.
    Footgun issues are certain things you can do in Prog8 that may make your program blow up unexpectedly,
    for instance uncareful use of dirty variables, or reusing the R0-R15 registers for subroutine parameters.
    With this option you're basically saying: "Yes, I know I am treading on mighty thin ice here, but I don't want to be reminded about that".

``-noasm``
    Do not create assembly code and output program.
    Useful for debugging or doing quick syntax checks.

``-noopt``
    Don't perform any code optimizations.
    Useful for debugging or faster compilation cycles.

``-out <directory>``
    sets directory location for output files instead of current directory

``-plaintext``
    Prints output messages in plain text: no colors or fancy symbols.

``-printast1``
    Prints the "compiler AST" (the internal representation of the program) after all processing steps.

``-printast2``
    Prints the "simplified AST" which is the reduced representation of the program.
    This is what is used in the code generators, to generate the executable code from.

``-quiet``
    Don't print compiler and assembler messages.

``-quietasm``
    Don't print assembler messages

``-slabsgolden``
    put memory() slabs in 'golden ram' memory area instead of at the end of the program.
    On the cx16 target this is $0400-07ff. This is unavailable on other systems.

``-slabshigh``
    put memory() slabs in high memory area instead of at the end of the program.
    On the cx16 target the value specifies the HiRAM bank to use, on other systems this value is ignored.

``-nosourcelines``
    Do not include the original prog8 source code lines as comments in the generated assembly code file,
    mixed in between the actual generated assembly code. The default behavior is to include the sourcel lines.

``-srcdirs <pathlist>``
    Specify a list of extra paths (separated with ':'), to search in for imported modules.
    Useful if you have library modules somewhere that you want to re-use,
    or to switch implementations of certain routines via a command line switch.

``-target <compilation target>``
    Sets the target output of the compiler. This option is required.
    ``c64`` = Commodore 64, ``c128`` = Commodore 128, ``cx16`` = Commander X16, ``pet32`` - Commodore PET model 4032,
    ``virtual`` = builtin virtual machine.
    You can also specify a file name as target, prog8 will when try to read the target
    machine's configuration and properties from that configuration file instead of using one of the built-in targets.
    See :ref:`customizable_target` for details about this.

``-timings``
    Show a more detailed breakdown of the time taken in various compiler phases, for performance analysis of the compiler itself.

``-varsgolden``
    Like ``-varshigh``, but places the variables in the $0400-$07FF "golden ram" area instead.
    Because this is in normal system memory, there are no bank switching issues.
    This mode is only available on the Commander X16, and possibly on custom configured targets.

``-varshigh <rambank>``
    Places uninitialized non-zeropage variables in a separate memory area, instead of inside the program itself.
    This increases the amount of system ram available for program code.
    The size of the increase depends on the program but can be several hundreds of bytes or more.
    The location of the memory area for these variables depends on the compilation target machine:

    c64: $C000 - $CFFF   ; 4 kB, and the specified rambank number is ignored

    cx16: $A000 - $BFFF  ; 8 kB in the specified HiRAM bank (note: no auto bank switching is done, you must make sure yourself that this HiRAM bank is active when accessing these variables!)

    If you use this option, you can no longer use the part of the above memory area that is
    alotted to the variables, for your own purposes. The output of the 64tass assembler step at the
    end of compilation shows precise details of where and how much memory is used by the variables
    (it's called 'BSS' section or Gap at the address mentioned above).
    Assembling the program will fail if there are too many variables to fit in a single high ram bank.

``-version``
    Just print the compiler version and copyright message, and exit.

``-vm``
    load and run a 'p8ir' intermediate representation file in the internal VirtualMachine instead of compiling a prog8 program file.

``-warnshadow``
    Tells the assembler to issue warning messages about symbol shadowing.
    These *can* be problematic, but usually aren't because prog8 has different scoping rules
    than the assembler has.
    You may want to watch out for shadowing of builtin names though. Especially 'a', 'x' and 'y'
    as those are the cpu register names and if you shadow those, the assembler might
    interpret certain instructions differently and produce unexpected opcodes (like LDA X getting
    turned into TXA, or not, depending on the symbol 'x' being defined in your own assembly code or not)

``-watch``
    Enables continuous compilation mode (watches for file changes).
    This greatly increases compilation speed on subsequent runs:
    almost instant compilation times (less than a second) can be achieved in this mode.
    The compiler will compile your program and then instead of exiting, it waits for any changes in the module source files.
    As soon as a change happens, the program gets compiled again.
    Note that it is possible to use the watch mode with multiple modules as well, but it will
    recompile everything in that list even if only one of the files got updated.


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
the ``srcdirs`` command line option. This can also be a lo-fi way to use different source files
for different compilation targets if you wish. Which is useful as currently the compiler
doesn't have conditional compilation like #ifdef/#endif in C.


.. _debugging:

Debugging (with VICE or Box16)
------------------------------

There's support for using the monitor and debugging capabilities of the rather excellent
`VICE emulator <http://vice-emu.sourceforge.net/>`_.

The ``%breakpoint`` directive (see :ref:`directives`) in the source code instructs the compiler to put
a *breakpoint* at that position. Some systems use a BRK instruction for this, but
this will usually halt the machine altogether instead of just suspending execution.
Prog8 issues a NOP instruction instead and creates a 'virtual' breakpoint at this position.
All breakpoints are then written to a file called "programname.vice-mon-list",
which is meant to be used by the VICE and Box16 emulators.
It contains a series of commands for VICE's monitor, including source labels and the breakpoint settings.
If you use the emulator autostart feature of the compiler, it will take care of this for you.
If you launch VICE manually, you'll have to use a command line option to load this file:

	``$ x64 -moncommands programname.vice-mon-list``

VICE will then use the label names in memory disassembly, and will activate any breakpoints as well.
If your running program hits one of the breakpoints, VICE will halt execution and drop you into the monitor.

Box16 is the alternative emulator for the Commander X16 and it also includes debugging facilities
that support these symbol and breakpoint lists.


Troubleshooting
---------------

Compiler doesn't run, complains about "UnsupportedClassVersionError"
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
You need to install and use JDK version 11 or newer to run the prog8 compiler. Check this with "java -version".
See :ref:`requirements`.

The computer just resets (at the end of the program)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In the default compiler configuration, it is not safely possible to return back to the BASIC prompt when
your program exits. The only reliable thing to do is to reboot the system.
This is due to the fact that in this mode, prog8 will overwrite important BASIC and Kernal variables in zero page memory.
To avoid the reset from happening, use an empty ``repeat`` loop at the end of your program to keep it from exiting.
Alternatively, if you want your program to exit cleanly back to the BASIC prompt,
you have to use ``%zeropage basicsafe``, see :ref:`directives`.
The reason this is not the default is that it is very beneficial to have more zeropage space available to the program,
and programs that have to return cleanly to the BASIC prompt are considered to be the exception.


Odd text and screen colors at start
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Prog8 will reset the screen mode and colors to a uniform well-known state. If you don't like the
default text and screen colors, you can simply change them yourself to whatever you want at the
start of your program. It depends on the computer system how you do this but there are some
routines in the textio module to help you with this.
Alternatively you can choose to disable this re-initialization altogether
using ``%option no_sysinit``, see :ref:`directives`.

Floats error
^^^^^^^^^^^^
Are you getting an assembler error about undefined symbols such as ``not defined 'floats'``?
This happens when your program uses floating point values, and you forgot to import ``floats`` library.
If you use floating points, the compiler needs routines from that library.
Fix it by adding an ``%import floats``.

Gradle error when building the compiler yourself
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
If you get a gradle build error containing the line "No matching toolchains found for requested specification"
or "Gradle requires JVM 17 or later to run", it means that the Gradle build tool can't locate the correct version of the JDK to use.
You will need a Java JDK version 17 or higher to build the compiler.
(the compiler itself only needs Java 11 or higher to run though.)

Strange assembler errors
^^^^^^^^^^^^^^^^^^^^^^^^
If the compilation of your program fails in the assembly step, please check that you have
the required version of the 64tass assembler installed. See :ref:`requirements`.
Also make sure that inside hand-written inlined assembly,
you don't use symbols named just a single letter (especially 'a', 'x' and 'y').
Sometimes these are interpreted as the CPU register of that name. To avoid such confusions,
always use 2 or more letters for symbols in your assembly code.

'shadowing' warnings form the assembler
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Avoid using 'a', 'x' or 'y' as symbols in your inlined assembly code.
Also avoid using 64tass' built-in function or type names as symbols in your inlined assembly code.
The 64tass manual contains `a list of those <https://tass64.sourceforge.net/#functions>`_.


Examples
--------

A bunch of example programs can be found in the 'examples' directory of the source tree.
There are cross-platform examples that can be compiled for various systems unaltered,
and there are also examples specific to certain computers (C64, X16, etcetera).
So for instance, to compile and run the Commodore 64 rasterbars example program, use this command::

    $ java -jar prog8c.jar -target c64 -emu examples/c64/rasterbars.p8

or::

    $ /path/to/prog8c -target c64 -emu examples/c64/rasterbars.p8

