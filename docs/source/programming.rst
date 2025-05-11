====================
Programming in Prog8
====================

This chapter describes a high level overview of the elements that make up a program.


Elements of a program
---------------------

Program
    Consists of one or more *modules*.

Module
    A file on disk with the ``.p8`` suffix. It can contain *directives* and *code blocks*.
    Whitespace and indentation in the source code are arbitrary and can be mixed tabs or spaces.
    A module file can *import* other modules, including *library modules*.
    It should be saved in UTF-8 encoding.
    Line endings are significant because *only one* declaration, statement or other instruction can occur on every line.
    Other whitespace and line indentation is arbitrary and ignored by the compiler.
    You can use tabs or spaces as you wish.

Comments
    Everything on the line after a semicolon ``;`` is a comment and is ignored by the compiler.
    If the whole line is just a comment, this line will be copied into the resulting assembly source code for reference.
    There's also a block-comment: everything surrounded with ``/*`` and ``*/`` is ignored and this can span multiple lines.
    This block comment is experimental for now: it may change or even be removed again in a future compiler version.
    The recommended way to comment out a bunch of lines remains to just bulk comment them individually with ``;``.

Directive
    These are special instructions for the compiler, to change how it processes the code
    and what kind of program it creates. A directive is on its own line in the file, and
    starts with ``%``, optionally followed by some arguments. See the syntax reference for all directives.
    The list of directives is given below at :ref:`directives`.

Code block
    A block of actual program code. It has a starting address in memory,
    and defines a *scope* (also known as 'namespace').
    It contains variables and subroutines.
    More details about this below: :ref:`blocks`.

Variable declarations
    The data that the code works on is stored in variables ('named values that can change').
    They are described in the chapter :ref:`variables`.

Code
    These are the instructions that make up the program's logic.
    Code can only occur inside a subroutine.
    There are different kinds of instructions ('statements' is a better name) such as:

    - value assignment
    - looping  (for, while, do-until, repeat, unconditional jumps)
    - conditional execution (if - then - else, when, and conditional jumps)
    - subroutine calls
    - label definition

Subroutine
    Defines a piece of code that can be called by its name from different locations in your code.
    It accepts parameters and can return a value (optional).
    It can define its own variables, and it is also possible to define subroutines within other subroutines.
    Nested subroutines can access the variables from outer scopes easily, which removes the need and overhead to pass everything via parameters all the time.
    Subroutines do not have to be declared in the source code before they can be called.

Label
    This is a named position in your code where you can jump to from another place.
    A label is an identifier followed by a colon ``:``. It's ok to put the next statement on
    the same line, immediately after the label.
    You can jump to it with a goto statement. It is also possible to use a
    subroutine call to a label (but without parameters and return value), however 🦶🔫 footgun warning:
    doing that is tricky because it makes for weird control flow and interferes with defers.

Scope
    Also known as 'namespace', this is a named box around the symbols defined in it.
    This prevents name collisions (or 'namespace pollution'), because the name of the scope
    is needed as prefix to be able to access the symbols in it.
    Anything *inside* the scope can refer to symbols in the same scope without using a prefix.
    There are three scope levels in Prog8:

    - global (no prefix), everything in a module file goes in here;
    - block;
    - subroutine, can be nested in another subroutine.

    Even though modules are separate files, they are *not* separate scopes!
    Everything defined in a module is merged into the global scope.
    This is different from most other languages that have modules.
    The global scope can only contain blocks and some directives, while the others can contain variables and subroutines too.
    Some more details about how to deal with scopes and names is discussed below.


Identifiers
-----------

Naming things in Prog8 is done via valid *identifiers*. They start with a letter,
and after that, a combination of letters, numbers, or underscores.
Note that any Unicode Letter symbol is accepted as a letter!
Examples of valid identifiers::

	a
	A
	monkey
	COUNTER
	Better_Name_2
	something_strange__
	knäckebröd
	приблизительно
	π

**Scoped names**

Sometimes called "qualified names" or "dotted names", a scoped name is a sequence of identifiers separated by a dot.
They are used to reference symbols in other scopes. Note that unlike many other programming languages,
scoped names always need to be fully scoped (because they always start in the global scope). Also see :ref:`blocks`::

    main.start              ; the entrypoint subroutine
    main.start.variable     ; a variable in the entrypoint subroutine

**Aliases**

The ``alias`` statement makes it easier to refer to symbols from other places, and they can save
you from having to type the fully scoped name everytime you need to access that symbol.
Aliases can be created in any scope except at the module level.
An alias is created with ``alias <name> = <target>`` and then you can use ``<name>`` as if it were ``<target>``.
It is possible to alias variables, labels and subroutines, but not whole blocks.
The name has to be an unscoped identifier name, the target can be any symbol.


.. _blocks:

Blocks, Scopes, and accessing Symbols
-------------------------------------

**Blocks** are the top level separate pieces of code and data of your program. They have a
starting address in memory and will be combined together into a single output program.
They can only contain *directives*, *variable declarations*, *subroutines* and *inline assembly code*::

    <blockname> [<address>] {
        <directives>
        <variables>
        <subroutines>
        <inline asm>
    }

The <blockname> must be a valid identifier, and must be unique in the entire program (there's
a directive to merge multiple occurences).
The <address> is optional. If specified it must be a valid memory address such as ``$c000``.
It's used to tell the compiler to put the block at a certain position in memory.

.. sidebar::
    Using qualified names ("dotted names") to reference symbols defined elsewhere

    Every symbol is 'public' and can be accessed from anywhere else, when given its *full* "dotted name".
    So, accessing a variable ``counter`` defined in subroutine ``worker`` in block ``main``,
    can be done from anywhere by using ``main.worker.counter``.
    Unlike most other programming langues, as soon as a name is scoped,
    Prog8 treats it as a name starting in the *global* namespace.
    Relative name lookup is only performed for *non-scoped* names.

The address can be used to place a block at a specific location in memory.
Usually it is omitted, and the compiler will automatically choose the location (usually immediately after
the previous block in memory).
It must be >= ``$0200`` (because ``$00``--``$ff`` is the ZP and ``$100``--``$1ff`` is the cpu stack).

*Symbols* are names defined in a certain *scope*. Inside the same scope, you can refer
to them by their 'short' name directly.  If the symbol is not found in the same scope,
the enclosing scope is searched for it, and so on, up to the top level block, until the symbol is found.
If the symbol was not found the compiler will issue an error message.

**Subroutines** create a new scope. All variables inside a subroutine are hoisted up to the
scope of the subroutine they are declared in. Note that you can define **nested subroutines** in Prog8,
and such a nested subroutine has its own scope!  This also means that you have to use a fully qualified name
to access a variable from a nested subroutine::

    main {
        sub start() {
            sub nested() {
                ubyte counter
                ...
            }
            ...
            txt.print_ub(counter)                       ; Error: undefined symbol
            txt.print_ub(main.start.nested.counter)     ; OK
        }
    }

**Aliases** make it easier to refer to symbols from other places. They save
you from having to type the fully scoped name everytime you need to access that symbol.
Aliases can be created in any scope except at the module level.
You can create and use an alias with the ``alias`` statement like so::

    alias  score   = cx16.r7L        ; 'name' the virtual register
    alias  prn     = txt.print_ub    ; shorter name for a subroutine elsewhere
    ...
    prn(score)


.. important::
    Emphasizing this once more: unlike most other programming languages, a new scope is *not* created inside
    for, while, repeat, and do-until statements, the if statement, and the branching conditionals.
    These all share the same scope from the subroutine they're defined in.
    You can define variables in these blocks, but these will be treated as if they
    were defined in the subroutine instead.


Program Start and Entry Point
-----------------------------

Your program must have a single entry point where code execution begins.
The compiler expects a ``start`` subroutine in the ``main`` block for this,
taking no parameters and having no return value.

As any subroutine, it has to end with a ``return`` statement (or a ``goto`` call)::

    main {
        sub start ()  {
            ; program entrypoint code here
            return
        }
    }


The ``main`` module is always relocated to the start of your programs
address space, and the ``start`` subroutine (the entrypoint) will be on the
first address. This will also be the address that the BASIC loader program (if generated)
calls with the SYS statement.


.. _directives:

Directives
-----------

.. data:: %address <address>

	Level: module.
	Global setting, set the program's start memory address. It's usually fixed at ``$0801`` because the
	default launcher type is a CBM-BASIC program. But you have to specify this address yourself when
	you don't use a CBM-BASIC launcher.


.. data:: %align <interval>

    Level: not at module scope.
    Tells the assembler to continue assembling on the given alignment interval. For example, ``%align $100``
    will insert an assembler command to align on the next page boundary.
    Note that this has no impact on variables following this directive! Prog8 reallocates all variables
    using different rules. If you want to align a specific variable (array or string), you should use
    one of the alignment tags for variable declarations instead.
    Valid intervals are from 2 to 65536.
    **Warning:** if you use this directive in between normal statements, it will disrupt the output
    of the machine code instructions by making gaps between them, this will probably crash the program!


.. data:: %asm {{ ... }}

    Level: not at module scope.
    Declares that a piece of *assembly code* is inside the curly braces.
    This code will be copied as-is into the generated output assembly source file.
    Note that the start and end markers are both *double curly braces* to minimize the chance
    that the assembly code itself contains either of those. If it does contain a ``}}``,
    it will confuse the parser.

    If you use the correct scoping rules you can access symbols from the prog8 program from inside
    the assembly code. Sometimes you'll have to declare a variable in prog8 with `@shared` if it
    is only used in such assembly code.

    .. note::
        64tass syntax is required for the assembly code. As such, mnemonics need to be written in lowercase.

    .. caution::
        Avoid using single-letter symbols in included assembly code, as they could be confused with CPU registers.
        Also, note that all prog8 symbols are prefixed in assembly code, see :ref:`symbol-prefixing`.


.. data:: %asmbinary "<filename>" [, <offset>[, <length>]]

    Level: not at module scope.
    This directive can only be used inside a block.
    The assembler itself will include the file as binary bytes at this point, prog8 will not process this at all.
    This means that the filename must be spelled exactly as it appears on your computer's file system.
    Note that this filename may differ in case compared to when you chose to load the file from disk from within the
    program code itself (for example on the C64 and X16 there's the PETSCII encoding difference).
    The file is located relative to the current working directory!
    The optional offset and length can be used to select a particular piece of the file.
    To reference the contents of the included binary data, you can put a label in your prog8 code
    just before the %asmbinary.  To find out where the included binary data ends, add another label directly after it.
    An example program for this can be found below at the description of %asminclude.


.. data:: %asminclude "<filename>"

    Level: not at module scope.
    This directive can only be used inside a block.
    The assembler will include the file as raw assembly source text at this point,
    prog8 will not process this at all. Symbols defined in the included assembly can not be referenced
    from prog8 code. However they can be referenced from other assembly code if properly prefixed.
    You can of course use a label in your prog8 code just before the %asminclude directive, and reference
    that particular label to get to (the start of) the included assembly.
    Be careful: you risk symbol redefinitions or duplications if you include a piece of
    assembly into a prog8 block that already defines symbols itself.
    The compiler first looks for the file relative to the same directory as the module containing this statement is in,
    if the file can't be found there it is searched relative to the current directory.

    .. caution::
        Avoid using single-letter symbols in included assembly code, as they could be confused with CPU registers.
        Also, note that all prog8 symbols are prefixed in assembly code, see :ref:`symbol-prefixing`.

    Here is a small example program to show how to use labels to reference the included contents from prog8 code::

        %import textio
        %zeropage basicsafe

        main {

            sub start() {
                txt.print("first three bytes of included asm:\n")
                uword included_addr = &included_asm
                txt.print_ub(@(included_addr))
                txt.spc()
                txt.print_ub(@(included_addr+1))
                txt.spc()
                txt.print_ub(@(included_addr+2))

                txt.print("\nfirst three bytes of included binary:\n")
                included_addr = &included_bin
                txt.print_ub(@(included_addr))
                txt.spc()
                txt.print_ub(@(included_addr+1))
                txt.spc()
                txt.print_ub(@(included_addr+2))
                txt.nl()
                return

        included_asm:
                %asminclude "inc.asm"

        included_bin:
                %asmbinary "inc.bin"
        end_of_included_bin:

            }
        }


.. data:: %breakpoint

    Level: not at module scope.
    Defines a debugging breakpoint at this location. See :ref:`debugging`


.. data:: %encoding <encodingname>

    Overrides, in the module file it occurs in,
    the default text encoding to use for strings and characters that have no explicit encoding prefix.
    You can use one of the recognised encoding names, see :ref:`encodings`.


.. data:: %import <name>

	Level: module.
	This reads and compiles the named module source file as part of your current program.
	Symbols from the imported module become available in your code,
	without a module or filename prefix.
	You can import modules one at a time, and importing a module more than once has no effect.


.. data:: %jmptable ( lib.routine1, lib.routine2, ... )

    Level: block.
    This builds a compact "jump table" meant to be used in libraries.
    You can put the elements of the table on different lines if you wish.
    It outputs a sequence of JMP machine code instructions jumping to each
    of the given subroutines in the jmptable list in order. This way the routines in the library
    can be accessed using a neat fixed list of offsets at the beginning of the library code,
    and the actual implementation of those routines can be changed in later versions of the library
    without existing callers noticing anything.

    This is usually put at the top of the main block so that it ends up at the beginning
    of the library file. *Note:* the compiler will still insert the required bootstrapping
    code in front of it, which in the case of a library, is the single JMP to the start routine
    which also does some variable initialization and BSS area clearing. So the first JMP
    in the jumptable list will actually end up at offset 3 in the resulting binary program.
    The ``jmp start`` instruction that prog8 inserts ends up as the implicit first entry of the
    actual jump table instructions list that is put into the resulting library program::

        jmp  start              ; first program instruction always generated by prog8
        jmp  lib.routine1       ; jump table first entry
        jmp  lib.routine2       ; jump table second entry
        ...

.. data:: %launcher <type>

	Level: module.
	Global setting, selects the program launcher stub to use.
	Only relevant when using the ``prg`` output type. Defaults to ``basic``.

	- type ``basic`` : add a tiny C64 BASIC program, with a SYS statement calling into the machine code
	- type ``none`` : no launcher logic is added at all


.. data:: %memtop <address>

	Level: module.
	Global setting, changes the program's top memory address. This is usually specified internally by the compiler target,
	but with this you can change it to another value. This can be useful for example to 'reserve' a piece
	of memory at the end of program space where other data such as external library files can be loaded into.
	This memtop value is used for a check instruction for the assembler to see if the resulting program size
	exceeds the given memtop address. This value is exclusive, so $a000 means that $a000 is the first address
	that program can no longer use. Everything up to and including $9fff is still usable.


.. data:: %option <option> [, <option> ...]

	Level: module, block.
	Sets special compiler options.

    - ``enable_floats`` (module level) tells the compiler
      to deal with floating point numbers (by using various subroutines from the Kernal).
      Otherwise, floating point support is not enabled. Normally you don't have to use this yourself as
      importing the ``floats`` library is required anyway and that will enable it for you automatically.
    - ``no_sysinit`` (module level) which cause the resulting program to *not* include
      the system re-initialization logic of clearing the screen, resetting I/O config, setting memory bank configuration etc.
      You'll have to take care of that yourself. The program will just start running from whatever state the machine is in when the
      program was launched.
    - ``force_output`` (in a block) will force the block to be outputted in the final program.
      Can be useful to make sure some data is generated that would otherwise be discarded because the compiler thinks it's not referenced (such as sprite data)
    - ``merge`` (in a block) will merge this block's contents into an already existing block with the same name.
      Can be used to add or override subroutines to an existing library block, for instance.
      Overriding (monkeypatching) happens only if the signature of the subroutine exactly matches the original subroutine, including the exact names and types of the parameters.
      Where blocks with this option are merged into is intricate: it looks for the first other block with the same name that does not have %option merge,
      if that can't be found, select the first occurrence regardless. If no other blocks are found, no merge is done. Blocks in libraries are considered first to merge into.
    - ``no_symbol_prefixing`` (block or module) makes the compiler *not* use symbol-prefixing when translating prog8 code into assembly.
      Only use this if you know what you're doing because it could result in invalid assembly code being generated.
      This option can be useful when writing library modules that you don't want to be exposing prefixed assembly symbols. Various standard library modules use it for this purpose.
    - ``ignore_unused`` (block or module) suppress warnings about unused variables and subroutines. Instead, these will be silently stripped.
      This option is useful in library modules that contain many more routines beside the ones that you actually use.
    - ``verafxmuls`` (block, cx16 target only) uses Vera FX hardware word multiplication on the CommanderX16 for all word multiplications in this block. Warning: this may interfere with IRQs and other Vera operations, so use this only when you know what you're doing. It's safer to explicitly use ``verafx.muls()``.
    - ``romable`` (module) *WORK-IN-PROGRESS/EXPERIMENTAL* make sure that the generated code is suitable for running in ROM (so no self-modifying code and such, which is normally used to generate smaller/more optimized code)
      See :ref:`romable` for more details.


.. data:: %output <type>

	Level: module.
	Global setting, selects program output type. Default is ``prg``.

	- type ``raw`` : no header at all, just the raw machine code data
	- type ``prg`` : C64 program (with load address header)
	- type ``xex`` : Atari xex program
	- type ``library`` : loadable library file. See :ref:`loadable_library`.


.. data:: %zeropage <style>

    Level: module.
    Global setting, select zeropage handling style. Defaults to ``kernalsafe``.

    - style ``kernalsafe`` -- use the part of the ZP that is 'free' or only used by BASIC routines,
      and don't change anything else.  This allows full use of Kernal ROM routines (but not BASIC routines),
      including default IRQs during normal system operation.
      It's not possible to return cleanly to BASIC when the program exits. The only choice is
      to perform a system reset. (A ``system_reset`` subroutine is available in the syslib to help you do this)
    - style ``floatsafe`` -- like the previous one but also reserves the addresses that
      are required to perform floating point operations (from the BASIC Kernal). No clean exit is possible.
    - style ``basicsafe`` -- the most restricted mode; only use the handful 'free' addresses in the ZP, and don't
      touch change anything else. This allows full use of BASIC and Kernal ROM routines including default IRQs
      during normal system operation.
      When the program exits, it simply returns to the BASIC ready prompt.
    - style ``full`` -- claim the whole ZP for variables for the program, overwriting everything,
      except for a few addresses that are used by the system's IRQ handler.
      Even though that default IRQ handler is still active, it is impossible to use most BASIC and Kernal ROM routines.
      This includes many floating point operations and several utility routines that do I/O, such as ``print``.
      This option makes programs smaller and faster because even more variables can
      be stored in the ZP (which allows for more efficient assembly code).
      It's not possible to return cleanly to BASIC when the program exits. The only choice is
      to perform a system reset. (A ``system_reset`` subroutine is available in the syslib to help you do this)
    - style ``dontuse`` -- don't use *any* location in the zeropage.

.. note::
    ``kernalsafe`` and ``full`` on the C64 leave enough room in the zeropage to reallocate the
    16 virtual registers cx16.r0...cx16.r15 from the Commander X16 into the zeropage as well
    (but not on the same locations). They are relocated automatically by the compiler.
    The other options need those locations for other things so those virtual registers have
    to be put into memory elsewhere (outside of the zeropage). Trying to use them as zeropage
    variables or pointers etc. will be a lot slower in those cases!
    On the Commander X16 the registers are always in zeropage. On other targets, for now, they
    are always outside of the zeropage.


.. data:: %zpallowed <fromaddress>,<toaddress>

    Level: module.
    Global setting, can occur multiple times. It allows you to designate a part of the zeropage that
    the compiler is allowed to use (if other options don't prevent usage).


.. data:: %zpreserved <fromaddress>,<toaddress>

    Level: module.
    Global setting, can occur multiple times. It allows you to reserve or 'block' a part of the zeropage so
    that it will not be used by the compiler.


Loops
-----

The *for*-loop is used to let a variable iterate over a range of values. Iteration is done in steps of 1, but you can change this.

.. sidebar::
    Optimization

    Usually a loop in descending order downto 0 or 1, produces more efficient assembly code than the same loop in ascending order.

The loop variable must be declared separately as byte or word earlier, so that you can reuse it for multiple occasions.
Iterating with a floating point variable is not supported. If you want to loop over a floating-point array, use a loop with an integer index variable instead.
If the from value is already outside of the loop range, the whole for loop is skipped.

The *while*-loop is used to repeat a piece of code while a certain condition is still true.
The *do--until* loop is used to repeat a piece of code until a certain condition is true.
The *repeat* loop is used as a short notation of a for loop where the loop variable doesn't matter and you're only interested in the number of iterations.
(without iteration count specified it simply loops forever). A repeat loop will result in the most efficient code generated so use this if possible.

You can also create loops by using the ``goto`` statement, but this should usually be avoided.

Breaking out of a loop prematurely is possible with the ``break`` statement,
immediately continue into the next cycle of the loop with the ``continue`` statement.
(These are just shorthands for a goto + a label)

The *unroll* loop is not really a loop, but looks like one. It actually duplicates the statements in its block on the spot by
the given number of times. It's meant to "unroll loops" - trade memory for speed by avoiding the actual repeat loop counting code.
Only simple statements are allowed to be inside an unroll loop (assignments, function calls etc.).

.. attention::
    The value of the loop variable after executing the loop *is undefined* - you cannot rely
    on it to be the last value in the range for instance! The value of the variable should only be used inside the for loop body.
    (this is an optimization issue to avoid having to deal with mostly useless post-loop logic to adjust the loop variable's value)


for loop
^^^^^^^^

The loop variable must be a byte or word variable, and it must be defined separately first.
The expression that you loop over can be anything that supports iteration (such as ranges like ``0 to 100``,
array variables and strings) *except* floating-point arrays (because a floating-point loop variable is not supported).
Remember that a step value in a range must be a constant value.

You can use a single statement, or a statement block like in the example below::

    for <loopvar>  in  <expression>  [ step <amount> ]   {
        ; do something...
        break       ; break out of the loop
        continue    ; immediately next iteration
    }

For example, this is a for loop using a byte variable ``i``, defined before, to loop over a certain range of numbers::

    ubyte i

    ...

    for i in 20 to 155 {
        ; do something
    }

To loop over a decreasing or descending range, use the ``downto`` keyword::

    ubyte i

    ...

    for i in 155 downto 20 {        ; 155, 154, 153, ..., 20
        ; do something
    }

Similarly, a descending range may be specified by using ``to`` in combination with a ``step`` that is ``< 0``::

    ubyte i

    ...

    for i in 155 to 20 step -1 {    ; 155, 154, 153, ..., 20
        ; do something
    }

The following example is a loop over the values of the array ``fibonacci_numbers``::

    uword[] fibonacci_numbers = [0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181]

    uword number
    for number in fibonacci_numbers {
        ; do something with number...
        break       ; break out of the loop early
    }

See :ref:`range-expression` for all of the details.

while loop
^^^^^^^^^^

As long as the condition is true (1), repeat the given statement(s).
You can use a single statement, or a statement block like in the example below::

	while  <condition>  {
		; do something...
		break		; break out of the loop
		continue    ; immediately next iteration
	}


do-until loop
^^^^^^^^^^^^^

Until the given condition is true (1), repeat the given statement(s).
You can use a single statement, or a statement block like in the example below::

	do  {
		; do something...
		break		; break out of the loop
		continue    ; immediately next iteration
	} until  <condition>


repeat loop
^^^^^^^^^^^

When you're only interested in repeating something a given number of times.
It's a short hand for a for loop without an explicit loop variable::

    repeat 15 {
        ; do something...
        break		; you can break out of the loop
        continue    ; immediately next iteration
    }

If you omit the iteration count, it simply loops forever.
You can still ``break`` out of such a loop if you want though.


unroll loop
^^^^^^^^^^^

Like a repeat loop, but trades memory for speed by not generating the code
for the counter. Instead it duplicates the code inside the loop on the spot for
the given number of iterations. This means that only a constant number of iterations can be specified.
Also, only simple statements such as assignments and function calls can be inside the loop::

    unroll 80 {
        cx16.VERA_DATA0 = 255
    }

A `break` or `continue` statement cannot occur in an unroll loop, as there is no actual loop to break out of.


Conditional Execution
---------------------

if statement
^^^^^^^^^^^^

Conditional execution means that the flow of execution changes based on certain conditions,
rather than having fixed gotos or subroutine calls::

    if xx==5 {
        yy = 99
        zz = 42
    } else {
        aa = 3
        bb = 9
    }

    if xx==5
        yy = 42
    else if xx==6
        yy = 43
    else
        yy = 44

    if aa>4 goto some_label

    if xx==3  yy = 4

    if xx==3  yy = 4 else  aa = 2


Conditional jumps (``if condition goto label``) are compiled using 6502's branching instructions (such as ``bne`` and ``bcc``) so
the rather strict limit on how *far* it can jump applies. The compiler itself can't figure this
out unfortunately, so it is entirely possible to create code that cannot be assembled successfully.
Thankfully the ``64tass`` assembler that is used has the option to automatically
convert such branches to their opposite + a normal jmp. This is slower and takes up more space
and you will get warning printed if this happens. You may then want to restructure your branches (place target labels closer to the branch,
or reduce code complexity).


There is a special form of the if-statement that immediately translates into one of the 6502's branching instructions.
This allows you to write a conditional jump or block execution directly acting on the current values of the CPU's status register bits.
The eight branching instructions of the CPU each have an if-equivalent (and there are some easier to understand aliases):

====================== =====================
condition              meaning
====================== =====================
``if_cs``              if carry status is set
``if_cc``              if carry status is clear
``if_vs``              if overflow status is set
``if_vc``              if overflow status is clear
``if_eq`` / ``if_z``   if result is equal to zero
``if_ne`` / ``if_nz``  if result is not equal to zero
``if_pl`` / ``if_pos`` if result is 'plus' (>= zero)
``if_mi`` / ``if_neg`` if result is 'minus' (< zero)
====================== =====================

So ``if_cc goto target`` will directly translate into the single CPU instruction ``BCC target``.

.. caution::
    These special ``if_XX`` branching statements are only useful in certain specific situations where you are *certain*
    that the status register (still) contains the correct status bits.
    This is not always the case after a function call or other operations!
    If in doubt, check the generated assembly code!

.. note::
    For now, the symbols used or declared in the statement block(s) are shared with
    the same scope the if statement itself is in.
    Maybe in the future this will be a separate nested scope, but for now, that is
    only possible when defining a subroutine.


if expression
^^^^^^^^^^^^^

Similar to the if statement, but this time selects one of two possible values as the outcome of the expression,
depending on the condition. You write it as ``if <condition>  <value1> else <value2>`` and it can be
used anywhere an expression is used to assign or pass a value.
The first value will be used if the condition is true, otherwise the second value is used.
Sometimes it may be more legible if you surround the condition expression with parentheses so it is better
separated visually from the first value following it.
You must always provide two alternatives to choose from, and they can only be values (expressions).
An example, to select the number of cards to use depending on what game is played::

    ubyte numcards = if game_is_piquet  32 else 52

    ; it's more verbose with an if statement:
    ubyte numcards
    if game_is_piquet
        numcards = 32
    else
        numcards = 52



when statement ('jump table')
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Instead of writing a bunch of sequential if-elseif statements, it is more readable to
use a ``when`` statement. (It will also result in greatly improved assembly code generation)
Use a ``when`` statement if you have a set of fixed choices that each should result in a certain
action. It is possible to combine several choices to result in the same action::

    when value {
        4 -> txt.print("four")
        5 -> txt.print("five")
        10,20,30 -> txt.print("ten or twenty or thirty")
        50 to 60 step 2 -> txt.print("fifty to sixty, even")
        else -> txt.print("don't know")
    }

The when-*value* can be any expression but the choice values have to evaluate to
compile-time constant integers (bytes or words). They also have to be the same
datatype as the when-value, otherwise no efficient comparison can be done.
You can explicitly put a list of numbers that all should result in the same case,
or even use any *range expression* as long as it denotes a constant list of numbers.
Be aware that every number is compared individually so using long lists of numbers and/or
many choice cases will result in poor performance.

Choices can result in a single statement or a block of multiple statements in which
case you have to use { } to enclose them.

The else part is optional.


.. note::
    Instead of chaining several value equality checks together using ``or`` (ex.: ``if x==1 or xx==5 or xx==9``),
    consider using a ``when`` statement or ``in`` containment check instead. These are more efficient.


Unconditional jump: goto
------------------------

To jump to another part of the program, you use a ``goto`` statement with an address or the name
of a label or subroutine. Referencing labels or subroutines outside of their defined scope requires
using qualified "dotted names"::

    goto  $c000           ; address
    goto  name            ; label or subroutine
    goto  main.mysub.name ; qualified dotted name; see, "Blocks, Scopes, and accessing Symbols"

    uword address = $4000
    goto  address         ; jump via address variable
    goto  address + idx   ; jump to an adress that is the result of an expression

Notice that this is a valid way to end a subroutine (you can either ``return`` from it, or jump
to another piece of code that eventually returns).

If you jump to an address variable or expression (uword), it is doing an 'indirect' jump: the jump will be done
to the address that's currently in the variable, or the result of the expression.


Assignments
-----------

Assignment statements assign a single value to a target variable or memory location.
Augmented assignments (such as ``aa += xx``) are also available, but these are just shorthands
for normal assignments (``aa = aa + xx``).

It is possible to "chain" assignments: ``x = y = z = 42``, this is just a shorthand
for the three individual assignments with the same value 42.

For subroutines that return multiple values, you should write a "multi assign" statement
with comma separated assignment targets, to assigns those multiple values.
Details can be found here: :ref:`multiassign`.


.. attention::
    **Data type conversion (in assignments):**
    When assigning a value with a 'smaller' datatype to variable with a 'larger' datatype,
    the value will be automatically converted to the target datatype:  byte --> word --> float.
    So assigning a byte to a word variable, or a word to a floating point variable, is fine.
    The reverse is *not* true: it is *not* possible to assign a value of a 'larger' datatype to
    a variable of a smaller datatype without an explicit conversion. Otherwise you'll get an error telling you
    that there is a loss of precision. You can use builtin functions such as ``round`` and ``lsb`` to convert
    to a smaller datatype, or revert to integer arithmetic.


Expressions
-----------

Expressions tell the program to *calculate* something. They consist of
values, variables, operators such as ``+`` and ``-``, function calls, type casts, or other expressions.
Here is an example that calculates to number of seconds in a certain time period::

    num_hours * 3600 + num_minutes * 60 + num_seconds

Long expressions can be split over multiple lines by inserting a line break before or after an operator::

    num_hours * 3600
     + num_minutes * 60
     + num_seconds

In most places where a number or other value is expected, you can use just the number, or a constant expression.
If possible, the expression is parsed and evaluated by the compiler itself at compile time, and the (constant) resulting value is used in its place.
Expressions that cannot be compile-time evaluated will result in code that calculates them at runtime.
Expressions can contain procedure and function calls.
There are various built-in functions that can be used in expressions (see :ref:`builtinfunctions`).
You can also reference identifiers defined elsewhere in your code.

.. note::
    **Order of evaluation:**

    The order of evaluation of expression operands is *unspecified* and should not be relied upon.
    There is no guarantee of a left-to-right or right-to-left evaluation. But don't confuse this with
    operator precedence order (multiplication comes before addition etcetera).

.. attention::
    **Floating point values used in expressions:**

    When a floating point value is used in a calculation, the result will be a floating point, and byte or word values
    will be automatically converted into floats in this case. The compiler will issue a warning though when this happens, because floating
    point calculations are very slow and possibly unintended!

    Calculations with integer variables will not result in floating point values.
    if you divide two integer variables say 32500 and 99 the result will be the integer floor
    division (328) rather than the floating point result (328.2828282828283). If you need the full precision,
    you'll have to make sure at least the first operand is a floating point. You can do this by
    using a floating point value or variable, or use a type cast.
    When the compiler can calculate the result during compile-time, it will try to avoid loss
    of precision though and gives an error if you may be losing a floating point result.



Arithmetic and Logical expressions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Arithmetic expressions are expressions that calculate a numeric result (integer or floating point).
Many common arithmetic operators can be used and follow the regular precedence rules.
Logical expressions are expressions that calculate a boolean result: true or false
(which in reality are just a 1 or 0 integer value). When using variables of the type ``bool``,
logical expressions will compile more efficiently than when you're using regular integer type operands
(because these have to be converted to 0 or 1 every time)
Prog8 applies short-circuit aka McCarthy evaluation for ``and`` and ``or`` on boolean expressions.

You can use parentheses to group parts of an expression to change the precedence.
Usually the normal precedence rules apply (``*`` goes before ``+`` etc.) but subexpressions
within parentheses will be evaluated first. So ``(4 + 8) * 2`` is 24 and not 20,
and ``(true or false) and false`` is false instead of true.

.. attention::
    **calculations keep their datatype even if the target variable is larger:**
    When you do calculations on a BYTE type, the result will remain a BYTE.
    When you do calculations on a WORD type, the result will remain a WORD.
    For instance::

        byte b = 44
        word w = b*55   ; the result will be 116! (even though the target variable is a word)
        w *= 999        ; the result will be -15188  (the multiplication stays within a word, but overflows)

    *The compiler does NOT warn about this!* It's doing this for
    performance reasons - so you won't get sudden 16 bit (or even float)
    calculations where you needed only simple fast byte arithmetic.
    If you do need the extended resulting value, cast at least one of the
    operands explicitly to the larger datatype. For example::

        byte b = 44
        w = (b as word)*55
        w = b*(55 as word)


Operators
---------

arithmetic: ``+``  ``-``  ``*``  ``/``  ``%``
    ``+``, ``-``, ``*``, ``/`` are the familiar arithmetic operations.
    ``/`` is division (will result in integer division when using on integer operands, and a floating point division when at least one of the operands is a float)
    ``%`` is the remainder operator: ``25 % 7`` is 4.  Be careful: without a space after the %, it will be parsed as a binary number.
    So ``25 %10`` will be parsed as the number 25 followed by the binary number 2, which is a syntax error.
    Note that remainder is only supported on integer operands (not floats).

bitwise arithmetic: ``&``  ``|``  ``^``  ``~``  ``<<``  ``>>``
    ``&`` is bitwise and, ``|`` is bitwise or, ``^`` is bitwise xor, ``~`` is bitwise invert (this one is an unary operator)
    ``<<`` is bitwise left shift and ``>>`` is bitwise right shift (both will not change the datatype of the value)
    While the operands can be signed integers (the expression will just consider the underlying bit patterns),
    the result value of a bitwise expression is always unsigned.

assignment: ``=``
    Sets the target on the LHS (left hand side) of the operator to the value of the expression on the RHS (right hand side).
    Note that an assignment sometimes is not possible or supported.
    It's possible to chain assignments like ``x = y = z = 42`` as a shorthand for the three assignments with the same value.

augmented assignment: ``+=``  ``-=``  ``*=``  ``/=``  ``&=``  ``|=``  ``^=``  ``<<=``  ``>>=``
    This is syntactic sugar; ``aa += xx`` is equivalent to ``aa = aa + xx``

postfix increment and decrement: ``++``  ``--``
    Syntactic sugar: ``aa++`` is equivalent to ``aa += 1``, and ``aa--`` is equivalent to ``aa -= 1``.
    Because these operations are so common, and often used in other languages, we have these short forms.
    *Notes:* unlike some other languages, they are *not* expressions in prog8, but statements. You cannot
    increment or decrement something inside an expression like, for example, ``x = value[aa++]`` is invalid.
    Also because of this, there is no *prefix* increment and decrement.

comparison: ``==``  ``!=``  ``<``  ``>``  ``<=``  ``>=``
    Equality, Inequality, Less-than, Greater-than, Less-or-Equal-than, Greater-or-Equal-than comparisons.
    The result is a boolean, true or false.

logical:  ``not``  ``and``  ``or``  ``xor``
	These operators are the usual logical operations that are part of a logical expression to reason
	about truths (boolean values). The result of such an expression is a boolean, true or false.
	Prog8 applies short-circuit aka McCarthy evaluation for ``and`` and ``or``.

range creation:  ``to``, ``downto``
    Creates a range of values from the LHS value to the RHS value, inclusive.
    These are mainly used in for loops to set the loop range.
    See :ref:`range-expression` for details.

containment check:  ``in``
    Tests if a value is present in a list of values, which can be a string, or an array, or a range expression.
    The result is a simple boolean true or false.
    Consider using this instead of chaining multiple value tests with ``or``, because the
    containment check is more efficient.
    Checking N in a range from x to y, is identical to x<=N and N<=y; the actual range of values is never created.
    Examples::

        ubyte cc
        if cc in [' ', '@', 0] {
            txt.print("cc is one of the values")
        }

        if cc in 10 to 20 {
            txt.print("10 <= cc and cc <=20")
        }

        str email_address = "name@test.com"
        if '@' in email_address {
            txt.print("email address seems ok")
        }


address of:  ``&``,   ``&<``,   ``&>``
    This is a prefix operator that can be applied to a string or array variable or literal value.
    It results in the memory address (UWORD) of that string or array in memory:  ``uword a = &stringvar``
    Sometimes the compiler silently inserts this operator to make it easier for instance
    to pass strings or arrays as subroutine call arguments.
    This operator can also be used as a prefix to a variable's data type keyword to indicate that
    it is a memory-mapped variable (for instance: ``&ubyte screencolor = $d021``). This is explained
    in the :ref:`variables` chapter.

    ``&<`` and ``&>`` are for use on split word arrays, they give you the address of the LSB byte array
    and MSB byte array separately, respectively.   Note that ``&<`` is just the same as ``&`` in this case.
    For more details on split word arrays, see :ref:`arrayvars`.


ternary:
    Prog8 doesn't have a ternary operator to choose one of two values (``x? y : z`` in many other languages)
    instead it provides this feature in the form of an *if expression*.  See below under "Conditional Execution".

precedence grouping in expressions, or subroutine parameter list:  ``(`` *expression* ``)``
	Parentheses are used to group parts of an expression to change the order of evaluation.
	(the subexpression inside the parentheses will be evaluated first):
	``(4 + 8) * 2`` is 24 instead of 20.

	Parentheses are also used in a subroutine call, they follow the name of the subroutine and contain
	the list of arguments to pass to the subroutine:   ``big_function(1, 99)``


Subroutines
-----------

Defining a subroutine
^^^^^^^^^^^^^^^^^^^^^

You define a subroutine like so::

    sub   <identifier>  ( [parameters] )  [ -> returntype ]  {
        ... statements ...
    }

    ; example:
    sub  triple (word amount) -> word  {
        return  amount * 3
    }

The parameters is a (possibly empty) comma separated list of "<datatype> <parametername>" pairs specifying the input parameters.
The return type has to be specified if the subroutine returns a value.

Subroutines can be defined in a Block, but also nested inside another subroutine. Everything is scoped accordingly.
There are three different types of subroutines: regular subroutines (the one above), assembly-only, and
external subroutines. These last two are described in detail below.

Reusing *virtual registers* R0-R15 for parameters
*************************************************
.. sidebar::
    🦶🔫 Footgun warning

    when using this the program can clobber the contents of R0-R15 when doing other operations that also
    use these registers, or when calling other routines because Prog8 doesn't have a callstack.
    Be very aware of what you are doing, the compiler can't guarantee correct values by itself anymore.

Normally, every subroutine parameter will get its own local variable in the subroutine where the argument value
will be stored when the subroutine is called. In certain situations, this may lead to many variables being allocated.
You *can* instruct the compiler to not allocate a new variable, but instead to reuse one of the *virtual registers* R0-R15
(accessible in the code as ``cx16.r0`` - ``cx16.r15``)  for the parameter. This is done by adding a ``@Rx`` tag
to the parameter. This can only be done for booleans, byte, and word types.
Note: the R0-R15 *virtual registers* are described in more detail below for the Assembly subroutines.
Here's an example that reuses the R0 and the R1L (lower byte of R1) virtual registers for the paremeters::

    sub  get_indexed_byte(uword pointer @R0, ubyte index @R1) -> ubyte {
        return @(cx16.r0 + cx16.r1L)
    }


Assembly-Subroutines
^^^^^^^^^^^^^^^^^^^^
These are user-written subroutines in the program source code itself, implemented purely in assembly and
which have an assembly calling convention (i.e. the parameters are strictly passed via cpu registers).
Such subroutines are defined with ``asmsub`` like this::

    asmsub  clear_screenchars (ubyte char @ A) clobbers(Y)  {
        %asm {{
            ldy  #0
    _loop   sta  cbm.Screen,y
            sta  cbm.Screen+$0100,y
            sta  cbm.Screen+$0200,y
            sta  cbm.Screen+$02e8,y
            iny
            bne  _loop
            rts
            }}
    }

the statement body of such a subroutine can only consist of just inline assembly.

The ``@ <register>`` part is required for rom and assembly-subroutines, as it specifies for the compiler
what cpu registers should take the routine's arguments.  You can use the regular set of registers
(A, X, Y), special 16-bit register pairs to take word values (AX, AY and XY) and even a processor status
flag such as Carry (Pc).

It is not possible to use floating point arguments or return values in an asmsub.

**inline:** Trivial ``asmsub`` routines can be tagged as ``inline`` to tell the compiler to copy their code
in-place to the locations where the subroutine is called, rather than inserting an actual call and return to the
subroutine. This may increase code size significantly and can only be used in limited scenarios, so YMMV.
Note that the routine's code is copied verbatim into the place of the subroutine call in this case,
so pay attention to any jumps and rts instructions in the inlined code!

.. note::
    Asmsubs can also be tagged as ``inline asmsub`` to make trivial pieces of assembly inserted
    directly instead of a call to them. Note that it is literal copy-paste of code that is done,
    so make sure the assembly is actually written to behave like such - which probably means you
    don't want a ``rts`` or ``jmp`` or ``bra`` in it!

.. note::
    The 'virtual' 16-bit registers from the Commander X16 can also be specified as ``R0`` .. ``R15`` .
    This means you don't have to set them up manually before calling a subroutine that takes
    one or more parameters in those 'registers'. You can just list the arguments directly.
    *This also works on the Commodore 64!*  (however they are not as efficient there because they're not in zeropage)
    In prog8 and assembly code these 'registers' are directly accessible too via
    ``cx16.r0`` .. ``cx16.r15``  (these are memory-mapped uword values),
    ``cx16.r0s`` .. ``cx16.r15s``  (these are memory-mapped word values),
    and ``L`` / ``H`` variants are also available to directly access the low and high bytes of these.
    You can use them directly but their name isn't very descriptive, so it may be useful to define
    an alias for them when using them regularly.


External subroutines
^^^^^^^^^^^^^^^^^^^^

Thse define an external subroutine that's implemented outside of the program
(for instance, a ROM routine, or a routine in a library loaded elsewhere in RAM).
External subroutines are usually defined by compiler library files, with the following syntax::

    extsub $FFD5 = LOAD(ubyte verify @ A, uword address @ XY) clobbers()
         -> bool @Pc, ubyte @ A, ubyte @ X, ubyte @ Y

This defines the ``LOAD`` subroutine at memory address $FFD5, taking arguments in all three registers A, X and Y,
and returning stuff in several registers as well. The ``clobbers`` clause is used to signify to the compiler
what CPU registers are clobbered by the call instead of being unchanged or returning a meaningful result value.
Note that the address ($ffd5 in the example above) can actually be an expression as long as it is a compile time constant. This can
make it easier to define jump tables for example, like this::

    const uword APIBASE = $8000
    extsub APIBASE+0 = firstroutine()
    extsub APIBASE+10 = secondroutine()
    extsub APIBASE+20 = thirdroutine()

**Banks:** it is possible to declare a non-standard ROM or RAM bank that the routine is living in, with ``@bank`` like this:
``extsub @bank 10  $C09F = audio_init()`` to define a routine at $C09F in bank 10. You can also specify a variable for the bank.
See :ref:`banking` for more information.


Calling a subroutine
^^^^^^^^^^^^^^^^^^^^

You call a subroutine like this::

        [ void / result = ] subroutinename_or_address ( [argument...] )

        ; example:
        resultvariable = subroutine(arg1, arg2, arg3)
        void noresultvaluesub(arg)

Arguments are separated by commas. The argument list can also be empty if the subroutine
takes no parameters.  If the subroutine returns a value, usually you assign it to a variable.
If you're not interested in the return value, prefix the function call with the ``void`` keyword.
Otherwise the compiler will warn you about discarding the result of the call.

.. note::
    **Order of evaluation:**

    The order of evaluation of arguments to a single function call is *unspecified* and should not be relied upon.
    There is no guarantee of a left-to-right or right-to-left evaluation of the call arguments.

.. caution::
    Note that due to the way parameters are processed by the compiler,
    subroutines are *non-reentrant*. This means you cannot create recursive calls.
    If you do need a recursive algorithm, you'll have to hand code it in embedded assembly for now,
    or rewrite it into an iterative algorithm.
    Also, subroutines used in the main program should not be used from an IRQ handler. This is because
    the subroutine may be interrupted, and will then call itself from the IRQ handler. Results are
    then undefined because the variables will get overwritten.


.. _multiassign:

Multiple return values
^^^^^^^^^^^^^^^^^^^^^^
Subroutines can return more than one value.
For example, ``asmsub`` routines (implemented in assembly code) or ``extsub`` routines
(referencing an external routine in ROM or elsewhere in RAM) can return multiple values spread
across different registers, and even the CPU's status register flags for boolean values.
Normal subroutines can also return multiple values.
You have to "multi assign" all return values of the subroutine call to something:
write the assignment targets as a comma separated list, where the element's order corresponds
to the order of the return values declared in the subroutine's signature.
Remember that you can use ``void`` to skip a value. So for instance::

    bool   flag
    ubyte  bytevar
    uword  wordvar

    wordvar, flag, bytevar = multisub()        ; call and assign the three result values

    asmsub multisub() -> uword @AY, bool @Pc, ubyte @X { ... }

.. sidebar:: register usage

    Subroutines with multiple return values use cpu registers A, Y, and the R0-R15 "virtual registers" to return those,
    depending on the number of values returend.  A floating point value is passed via the FAC 'register'
    (only a single floating point value is supported).
    Using these during the calculation of the values in the return statement should be avoided.
    Otherwise you risk overwriting an earlier return value in the sequence.


**Using just one of the values:**
Sometimes it is easier to just have a single return value in a subroutine's signagure (even though it
actually may return multiple values): this avoids having to put ``void`` for all other values if you aren't really interested in those.
It also allows it to be called in expressions such as if-statements again.
Examples of these second 'convenience' definition are library routines such as ``cbm.STOP2`` and ``cbm.GETIN2``,
that only return a single value where the "official" versions ``STOP`` and ``GETIN`` always return multiple values.

**Skipping values:** Instead of using ``void`` to ignore the result of a subroutine call altogether,
you can also use it as a placeholder name in a multi-assignment. This skips assignment of the return value in that place.
One of the cases where this is useful, is with boolean values returned in status flags such as the carry flag.
Storing that flag as a boolean in a variable first, and then possibly adding an ``if flag...`` statement afterwards, is a lot less
efficient than just keeping the flag as-is and using a conditional branch such as ``if_cs`` to do something with it.
So in the case above that could be::

    wordvar, void, bytevar = multisub()
    if_cs
        something()

Notice that a call to a subroutine that returns multiple values cannot be used inside an expression,
because expression terms always need to be a single value. You'll have to use a separate multi-assignment
first and then use the result of that in the expression. However, also read the sidebar about a possible alternative.


Deferred ("cleanup") code
^^^^^^^^^^^^^^^^^^^^^^^^^

Usually when a subroutine exits, it has to clean up things that it worked on. For example, it has to close
a file that it opened before to read data from, or it has to free a piece of memory that it allocated via
a dynamic memory allocation library, etc.
Every spot where the subroutine exits (return statement, jump, or the end of the routine) you have to take care
of doing the cleanups required.  This can get tedious, and the cleanup code is separated from the place where
the resource allocation was done at the start.

The ``defer`` keyword can be used to schedule a statement (or block of statements) to be executed
just before exiting of the current subroutine. That can be via a return statement or a jump to somewhere else,
or just the normal ending of the subroutine. This is often useful to "not forget" to clean up stuff,
and if the subroutine has multiple ways or places where it can exit, it saves you from repeating
the cleanup code at every exit spot. Multiple defers can be scheduled in a single subroutine (up to a maximum of 8).
The defers are handled in reversed (LIFO) order. Return values are evaluated before any deferred code is executed.
You write defers like so::

    sub example() -> bool {
        ubyte file = open_file()
        defer close_file(file)              ; "close it when we exit from here"

        uword memory = allocate(1000)
        if memory==0
            return false
        defer deallocate(memory)            ; "deallocate when we exit from here"

        process(file, memory)
        return true
    }

In this example, the two deferred statements are not immediately executed. Instead, they are executed when the
subroutine exits at any point. So for example the ``return false`` after the memory check will automatically also close
the file that was opened earlier because the close_file() call was scheduled there.
At the bottom when the ``return true`` appears, *both* deferred cleanup calls are executed: first the deallocation of
the memory, and then the file close. As you can see this saves you from duplicating the cleanup logic,
and the logic is declared very close to the spot where the allocation of the resource happens, so it's easier to read and understand.

It's possible to write a defer for a block of statements, but the advice is to keep such cleanup code as simple and short as possible.

.. caution::
    Defers only work for subroutines that are written in regular Prog8 code.
    If a piece of inlined assembly somehow causes the routine to exit, the compiler cannot detect this,
    and defers won't be handled in such cases.


Library routines and builtin functions
--------------------------------------

There are many routines available in the compiler libraries or as builtin functions.
The most important ones can be found in the :doc:`libraries` chapter.
