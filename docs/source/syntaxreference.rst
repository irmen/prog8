.. _syntaxreference:

================
Syntax Reference
================

Module file
-----------

This is a file with the ``.ill`` suffix, containing *directives* and *code blocks*, described below.
The file is a text file wich can also contain:

.. data:: Lines, whitespace, indentation

	Line endings are significant because *only one* declaration, statement or other instruction can occur on every line.
	Other whitespace and line indentation is arbitrary and ignored by the compiler.
	You can use tabs or spaces as you wish.

.. data:: Source code comments

	Everything after a semicolon ``;`` is a comment and is ignored.
	If the whole line is just a comment, it will be copied into the resulting assembly source code.
	This makes it easier to understand and relate the generated code. Examples::

		A = 42    ; set the initial value to 42
		; next is the code that...


.. _directives:

Directives
-----------

.. data:: %output <type>

	Level: module.
	Global setting, selects program output type. Default is ``prg``.

	- type ``raw`` : no header at all, just the raw machine code data
	- type ``prg`` : C64 program (with load address header)


.. data:: %launcher <type>

	Level: module.
	Global setting, selects the program launcher stub to use.
	Only relevant when using the ``prg`` output type. Defaults to ``basic``.

	- type ``basic`` : add a tiny C64 BASIC program, whith a SYS statement calling into the machine code
	- type ``none`` : no launcher logic is added at all


.. data:: %zp <style>

	Level: module.
	Global setting, select ZeroPage handling style. Defaults to ``compatible``.

	- style ``compatible`` -- only use the few 'free' addresses in the ZP, and don't change anything else.
	  This allows full use of BASIC and KERNAL ROM routines including default IRQs during normal system operation.
	- style ``full`` -- claim the whole ZP for variables for the program, overwriting everything,
  	  except the few addresses mentioned above that are used by the system's IRQ routine.
	  Even though the default IRQ routine is still active, it is impossible to use most BASIC and KERNAL ROM routines.
	  This includes many floating point operations and several utility routines that do I/O, such as ``print_string``.
	  It is also not possible to cleanly exit the program, other than resetting the machine.
	  This option makes programs smaller and faster because many more variables can
	  be stored in the ZP, which is more efficient.
	- style ``full-restore`` -- like ``full``, but makes a backup copy of the original values at program start.
	  These are restored (except for the software jiffy clock in ``$a0``--``$a2``)
	  when the program exits, and allows it to exit back to the BASIC prompt.

	Also read :ref:`zeropage`.


.. data:: %address <address>

	Level: module.
	Global setting, set the program's start memory address

	- default for ``raw`` output is ``$c000``
	- default for ``prg`` output is ``$0801``
	- cannot be changed if you select ``prg`` with a ``basic`` launcher;
	  then it is always ``$081d`` (immediately after the BASIC program), and the BASIC program itself is always at ``$0801``.
	  This is because the C64 expects BASIC programs to start at this address.


.. data:: %import <name>

	Level: module, block.
	This reads and compiles the named module source file as part of your current program.
	Symbols from the imported module become available in your code,
	without a module or filename prefix.
	You can import modules one at a time, and importing a module more than once has no effect.


.. data:: %saveregisters

	Level: block.
	@todo

.. data:: %noreturn

	Level: block, subroutine.
	@todo

.. data:: %asmbinary "<filename>" [, <offset>[, <length>]]

	Level: block.
        This directive can only be used inside a block.
        The assembler will include the file as binary bytes at this point, il65 will not process this at all.
        The optional offset and length can be used to select a particular piece of the file.

.. data:: %asminclude "<filename>", scopelabel

	Level: block.
        This directive can only be used inside a block.
        The assembler will include the file as raw assembly source text at this point, il65 will not process this at all.
        The scopelabel will be used as a prefix to access the labels from the included source code,
        otherwise you would risk symbol redefinitions or duplications.

.. data:: %breakpoint

	Level: block, subroutine.
	Defines a debugging breakpoint at this location. See :ref:`debugging`

.. data:: %asm { ... }

	Level: block, subroutine.
	Declares that there is *inline assembly code* in the lines enclosed by the curly braces.
	This code will be written as-is into the generated output file.
	The assembler syntax used should be for the 3rd party cross assembler tool that IL65 uses.
        The ``%asm {`` and ``}`` start and end markers each have to be on their own unique line.


Identifiers
-----------

Naming things in IL65 is done via valid *identifiers*. They start with a letter, and after that,
must consist of letters, numbers, or underscores. Examples of valid identifiers::

	a
	A
	monkey
	COUNTER
	Better_Name_2


Code blocks
-----------

A named block of actual program code. Itefines a *scope* (also known as 'namespace') and
can contain IL65 *code*, *directives*, *variable declarations* and *subroutines*::

    ~ <blockname> [<address>] {
        <directives>
        <variables>
        <statements>
        <subroutines>
    }

The <blockname> must be a valid identifier or can be completely omitted.
In that case the <address> is required to tell the compiler to put the block at
a certain position in memory. Otherwise it would be impossible to access its contents.
The <address> is optional. It must be a valid memory address such as ``$c000``.
Also read :ref:`blocks`.  Here is an example of a code block, to be loaded at ``$c000``::

	~ main $c000 {
		; this is code inside the block...
	}


Variables and value literals
----------------------------

The data that the code works on is stored in variables. Variable names have to be valid identifiers.
Values in the source code are written using *value literals*. In the table of the supported
data types below you can see how they should be written.

Variable declarations
^^^^^^^^^^^^^^^^^^^^^

@todo


Data types
^^^^^^^^^^

IL65 supports the following data types:

===============  =======================  =================  =========================================
type identifier  type                     storage size       example var declaration and literal value
===============  =======================  =================  =========================================
``byte``         unsigned byte            1 byte = 8 bits    ``byte myvar = $8f``
--               boolean                  1 byte = 8 bits    ``byte myvar = true`` or ``byte myvar == false``
                                                             The true and false are actually just aliases
                                                             for the byte values 1 and 0.
``word``         unsigned word            2 bytes = 16 bits  ``word myvar = $8fee``
``float``        floating-point           5 bytes = 40 bits  ``float myvar = 1.2345``
                                                             stored in 5-byte cbm MFLPT format
``byte[x]``      byte array               x bytes            ``byte[4] myvar = [1, 2, 3, 4]``
``word[x]``      word array               2*x bytes          ``word[4] myvar = [1, 2, 3, 4]``
``byte[x,y]``    byte matrix              x*y bytes          ``byte[40,25] myvar = @todo``
                                                             Note: word-matrix not supported
``str``          string (petscii)         varies             ``str myvar = "hello."``
                                                             implicitly terminated by a 0-byte
``str_p``        pascal-string (petscii)  varies             ``str_p myvar = "hello."``
                                                             implicit first byte = length, no 0-byte
``str_s``        string (screencodes)     varies             ``str_s myvar = "hello."``
                                                             implicitly terminated by a 0-byte
``str_ps``       pascal-string            varies             ``str_ps myvar = "hello."``
                 (screencodes)                               implicit first byte = length, no 0-byte
===============  =======================  =================  =========================================


**String encoding:**
Strings in your code will be encoded (translated from ASCII/UTF-8) into either CBM PETSCII or C-64 screencodes.
PETSCII is the default, so if you need screencodes (also called 'poke' codes)
you have to use the ``_s`` variants of the string type identifier.
A string literal of length 1 is considered to be a *byte* instead with
that single character's PETSCII value.  If you really need a *string* of length 1 you
can only do so by assigning it to a variable with one of the string types.

**Floating point numbers:**
Floats are stored in the 5-byte 'MFLPT' format that is used on CBM machines,
and also most float operations are specific to the Commodore-64.
This is because routines in the C-64 BASIC and KERNAL ROMs are used for that.
So floating point operations will only work if the C-64 BASIC ROM (and KERNAL ROM)
are banked in (and your code imports the ``c64lib.ill``)

The largest 5-byte MFLPT float that can be stored is: **1.7014118345e+38**   (negative: **-1.7014118345e+38**)

**Initial values over multiple runs:**
The initial values of your variables will be restored automatically when the program is (re)started,
*except for string variables*. It is assumed these are left unchanged by the program.
If you do modify them in-place, you should take care yourself that they work as
expected when the program is restarted.


**@todo pointers/addresses?  (as opposed to normal WORDs)**
**@todo signed integers (byte and word)?**



Indirect addressing and address-of
----------------------------------

**Address-of:**
The ``#`` prefix is used to take the address of something.
It can be used for example to work with the *address* of a memory mapped variable rather than
the value it holds.  You could take the address of a string as well, but that is redundant:
the compiler already treats those as a value that you manipulate via its address.
For most other types this prefix is not supported and will result in a compilation error.
The resulting value is simply a 16 bit word.

**Indirect addressing:**
@todo

**Indirect addressing in jumps:**
@todo
For an indirect ``goto`` statement, the compiler will issue the 6502 CPU's special instruction
(``jmp`` indirect).  A subroutine call (``jsr`` indirect) is emitted
using a couple of instructions.


Conditional Execution
---------------------

Conditional execution means that the flow of execution changes based on certiain conditions,
rather than having fixed gotos or subroutine calls. IL65 has a *conditional goto* statement for this,
that is translated into a comparison (if needed) and then a conditional branch instruction::

	if[_XX] [<expression>] goto <label>


The if-status XX is one of: [cc, cs, vc, vs, eq, ne, true, not, zero, pos, neg, lt, gt, le, ge]
It defaults to 'true' (=='ne', not-zero) if omitted. ('pos' will translate into 'pl', 'neg' into 'mi') 
@todo signed: lts==neg?, gts==eq+pos?, les==neg+eq?, ges==pos?

The <expression> is optional. If it is provided, it will be evaluated first. Only the [true] and [not] and [zero] 
if-statuses can be used when such a *comparison expression* is used. An example is::

        if_not  A > 55  goto  more_iterations


Conditional jumps are compiled into 6502's branching instructions (such as ``bne`` and ``bcc``) so 
the rather strict limit on how *far* it can jump applies. The compiler itself can't figure this
out unfortunately, so it is entirely possible to create code that cannot be assembled successfully.
You'll have to restructure your gotos in the code (place target labels closer to the branch)
if you run into this type of assembler error.


Assignments
-----------

Assignment statements assign a single value to a target variable or memory location.::

        target = value-expression


Augmented Assignments
---------------------

A special assignment is the *augmented assignment* where the value is modified in-place.
Several assignment operators are available: ``+=``, ``-=``, ``&=``, ``|=``, ``^=``, ``<<=``, ``>>=``


Expressions
-----------

In most places where a number or other value is expected, you can use just the number, or a full constant expression.
The expression is parsed and evaluated by Python itself at compile time, and the (constant) resulting value is used in its place.
Ofcourse the special il65 syntax for hexadecimal numbers (``$xxxx``), binary numbers (``%bbbbbbbb``),
and the address-of (``#xxxx``) is supported. Other than that it must be valid Python syntax.
Expressions can contain function calls to the math library (sin, cos, etc) and you can also use
all builtin functions (max, avg, min, sum etc). They can also reference idendifiers defined elsewhere in your code,
if this makes sense.


Subroutines
-----------

Defining a subroutine
^^^^^^^^^^^^^^^^^^^^^

Subroutines are parts of the code that can be repeatedly invoked using a subroutine call from elsewhere.
Their definition, using the sub statement, includes the specification of the required input- and output parameters.
For now, only register based parameters are supported (A, X, Y and paired registers,
the carry status bit SC and the interrupt disable bit SI as specials).
For subroutine return values, the special SZ register is also available, it means the zero status bit.

The syntax is::

        sub   <identifier>  ([proc_parameters]) -> ([proc_results])  {
                ... statements ...
        }

**proc_parameters =**
        comma separated list of "<parametername>:<register>" pairs specifying the input parameters.
        You can omit the parameter names as long as the arguments "line up".
        (actually, the Python parameter passing rules apply, so you can also mix positional
        and keyword arguments, as long as the keyword arguments come last)

**proc_results =**
        comma separated list of <register> names specifying in which register(s) the output is returned.
        If the register name ends with a '?', that means the register doesn't contain a real return value but
        is clobbered in the process so the original value it had before calling the sub is no longer valid.
        This is not immediately useful for your own code, but the compiler needs this information to
        emit the correct assembly code to preserve the cpu registers if needed when the call is made.
        For convenience: a single '?' als the result spec is shorthand for ``A?, X?, Y?`` ("I don't know
        what the changed registers are, assume the worst")


Pre-defined subroutines that are available on specific memory addresses 
(in system ROM for instance) can also be defined using the 'sub' statement.
To do this you assign the routine's memory address to the sub::

        sub  <identifier>  ([proc_parameters]) -> ([proc_results])  = <address>

example::

        sub  CLOSE  (logical: A) -> (A?, X?, Y?)  = $FFC3"


Calling a subroutine
^^^^^^^^^^^^^^^^^^^^

You call a subroutine like this::

        subroutinename_or_address ( [arguments...] )

or::

        subroutinename_or_address ![register(s)] ( [arguments...] )

If the subroutine returns one or more values as results, you must use an assignment statement
to store those values somewhere::

        outputvar1, outputvar2  =  subroutine ( arg1, arg2, arg3 )

The output variables must occur in the correct sequence of return registers as specified
in the subroutine's definiton. It is possible to not specify any of them but the compiler
will issue a warning then if the result values of a subroutine call are discarded.
If you don't have a variable to store the output register in, it's then required
to list the register itself instead as output variable.

Arguments should match the subroutine definition. You are allowed to omit the parameter names.
If no definition is available (because you're directly calling memory or a label or something else),
you can freely add arguments (but in this case they all have to be named).

To jump to a subroutine (without returning), prefix the subroutine call with the word 'goto'.
Unlike gotos in other languages, here it take arguments as well, because it
essentially is the same as calling a subroutine and only doing something different when it's finished.

**Register preserving calls:** use the ``!`` followed by a combination of A, X and Y (or followed
by nothing, which is the same as AXY) to tell the compiler you want to preserve the origial
value of the given registers after the subroutine call.  Otherwise, the subroutine may just
as well clobber all three registers. Preserving the original values does result in some
stack manipulation code to be inserted for every call like this, which can be quite slow.
