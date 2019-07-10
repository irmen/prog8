.. _syntaxreference:

================
Syntax Reference
================

Module file
-----------

This is a file with the ``.p8`` suffix, containing *directives* and *code blocks*, described below.
The file is a text file wich can also contain:

Lines, whitespace, indentation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Line endings are significant because *only one* declaration, statement or other instruction can occur on every line.
Other whitespace and line indentation is arbitrary and ignored by the compiler.
You can use tabs or spaces as you wish.

Source code comments
^^^^^^^^^^^^^^^^^^^^

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


.. data:: %zeropage <style>

    Level: module.
    Global setting, select ZeroPage handling style. Defaults to ``kernalsafe``.

    - style ``kernalsafe`` -- use the part of the ZP that is 'free' or only used by BASIC routines,
      and don't change anything else.  This allows full use of KERNAL ROM routines (but not BASIC routines),
      including default IRQs during normal system operation.
      When the program exits, a system reset is performed (because BASIC will be in a corrupt state).
    - style ``floatsafe`` -- like the previous one but also reserves the addresses that
      are required to perform floating point operations (from the BASIC kernel). No clean exit is possible.
    - style ``basicsafe`` -- the most restricted mode; only use the handful 'free' addresses in the ZP, and don't
      touch change anything else. This allows full use of BASIC and KERNAL ROM routines including default IRQs
      during normal system operation.
      When the program exits, it simply returns to the BASIC ready prompt.
    - style ``full`` -- claim the whole ZP for variables for the program, overwriting everything,
      except the few addresses mentioned above that are used by the system's IRQ routine.
      Even though the default IRQ routine is still active, it is impossible to use most BASIC and KERNAL ROM routines.
      This includes many floating point operations and several utility routines that do I/O, such as ``print_string``.
      As with ``kernalsafe``, it is not possible to cleanly exit the program, other than to reset the machine.
      This option makes programs smaller and faster because even more variables can
      be stored in the ZP (which allows for more efficient assembly code).

    Also read :ref:`zeropage`.

.. data:: %zpreserved <fromaddress>,<toaddress>

    Level: module.
    Global setting, can occur multiple times. It allows you to reserve or 'block' a part of the zeropage so
    that it will not be used by the compiler.


.. data:: %address <address>

	Level: module.
	Global setting, set the program's start memory address

	- default for ``raw`` output is ``$c000``
	- default for ``prg`` output is ``$0801``
	- cannot be changed if you select ``prg`` with a ``basic`` launcher;
	  then it is always ``$081e`` (immediately after the BASIC program), and the BASIC program itself is always at ``$0801``.
	  This is because the C64 expects BASIC programs to start at this address.


.. data:: %import <name>

	Level: module, block.
	This reads and compiles the named module source file as part of your current program.
	Symbols from the imported module become available in your code,
	without a module or filename prefix.
	You can import modules one at a time, and importing a module more than once has no effect.


.. data:: %option <option> [, <option> ...]

	Level: module, block.
	Sets special compiler options.
	For a module option, only the ``enable_floats`` option is recognised, which will tell the compiler
	to deal with floating point numbers (by using various subroutines from the Commodore-64 kernal).
	Otherwise, floating point support is not enabled.
	When used in a block with the ``force_output`` option, it will force the block to be outputted
	in the final program. Can be useful to make sure some
	data is generated that would otherwise be discarded because it's not referenced (such as sprite data).


.. data:: %asmbinary "<filename>" [, <offset>[, <length>]]

	Level: block.
        This directive can only be used inside a block.
        The assembler will include the file as binary bytes at this point, prog8 will not process this at all.
        The optional offset and length can be used to select a particular piece of the file.
        The file is located relative to the current working directory!

.. data:: %asminclude "<filename>", "scopelabel"

	Level: block.
        This directive can only be used inside a block.
        The assembler will include the file as raw assembly source text at this point,
        prog8 will not process this at all, with one exception: the labels.
        The scopelabel argument will be used as a prefix to access the labels from the included source code,
        otherwise you would risk symbol redefinitions or duplications.
        If you know what you are doing you can leave it as an empty string to not have a scope prefix.
        The compiler first looks for the file relative to the same directory as the module containing this statement is in,
        if the file can't be found there it is searched relative to the current directory.

.. data:: %breakpoint

	Level: block, subroutine.
	Defines a debugging breakpoint at this location. See :ref:`debugging`

.. data:: %asm {{ ... }}

	Level: block, subroutine.
	Declares that there is *inline assembly code* in the lines enclosed by the curly braces.
	This code will be written as-is into the generated output file.
	The assembler syntax used should be for the 3rd party cross assembler tool that Prog8 uses.
	Note that the start and end markers are both *double curly braces* to minimize the chance
	that the inline assembly itself contains either of those. If it does contain a ``}}``,
 	the parsing of the inline assembler block will end prematurely and cause compilation errors.


Identifiers
-----------

Naming things in Prog8 is done via valid *identifiers*. They start with a letter or underscore,
and after that, a combination of letters, numbers, or underscores. Examples of valid identifiers::

	a
	A
	monkey
	COUNTER
	Better_Name_2
	_something_strange_


Code blocks
-----------

A named block of actual program code. Itefines a *scope* (also known as 'namespace') and
can contain Prog8 *code*, *directives*, *variable declarations* and *subroutines*::

    ~ <blockname> [<address>] {
        <directives>
        <variables>
        <statements>
        <subroutines>
    }

The <blockname> must be a valid identifier.
The <address> is optional. If specified it must be a valid memory address such as ``$c000``.
It's used to tell the compiler to put the block at a certain position in memory.
Also read :ref:`blocks`.  Here is an example of a code block, to be loaded at ``$c000``::

	~ main $c000 {
		; this is code inside the block...
	}



Labels
------

To label a position in your code where you can jump to from another place, you use a label::

	nice_place:
			; code ...

It's just an identifier followed by a colon ``:``. It's allowed to put the next statement on
the same line, after the label.


Variables and value literals
----------------------------

The data that the code works on is stored in variables. Variable names have to be valid identifiers.
Values in the source code are written using *value literals*. In the table of the supported
data types below you can see how they should be written.


Variable declarations
^^^^^^^^^^^^^^^^^^^^^

Variables should be declared with their exact type and size so the compiler can allocate storage
for them. You must give them an initial value as well. That value can be a simple literal value,
or an expression. You can add a ``@zp`` zeropage-tag, to tell the compiler to prioritize it
when selecting variables to be put into zeropage.
The syntax is::

	<datatype>  [ @zp ]  <variable name>   [ = <initial value> ]

Various examples::

    word        thing   = 0
    byte        counter = len([1, 2, 3]) * 20
    byte        age     = 2018 - 1974
    float       wallet  = 55.25
    str         name    = "my name is Irmen"
    uword       address = &counter
    byte[]      values  = [11, 22, 33, 44, 55]
    byte[5]     values  = 255           ; initialize with five 255 bytes

    word  @zp   zpword = 9999           ; prioritize this when selecting vars for zeropage storage



Data types
^^^^^^^^^^

Prog8 supports the following data types:

===============  =======================  =================  =========================================
type identifier  type                     storage size       example var declaration and literal value
===============  =======================  =================  =========================================
``byte``         signed byte              1 byte = 8 bits    ``byte myvar = -22``
``ubyte``        unsigned byte            1 byte = 8 bits    ``ubyte myvar = $8f``
--               boolean                  1 byte = 8 bits    ``byte myvar = true`` or ``byte myvar == false``
                                                             The true and false are actually just aliases
                                                             for the byte values 1 and 0.
``word``         signed word              2 bytes = 16 bits  ``word myvar = -12345``
``uword``        unsigned word            2 bytes = 16 bits  ``uword myvar = $8fee``
``float``        floating-point           5 bytes = 40 bits  ``float myvar = 1.2345``
                                                             stored in 5-byte cbm MFLPT format
``byte[x]``      signed byte array        x bytes            ``byte[4] myvar``
``ubyte[x]``     unsigned byte array      x bytes            ``ubyte[4] myvar``
``word[x]``      signed word array        2*x bytes          ``word[4] myvar``
``uword[x]``     unsigned word array      2*x bytes          ``uword[4] myvar``
``float[x]``     floating-point array     5*x bytes          ``float[4] myvar``
``byte[]``       signed byte array        depends on value   ``byte[] myvar = [1, 2, 3, 4]``
``ubyte[]``      unsigned byte array      depends on value   ``ubyte[] myvar = [1, 2, 3, 4]``
``word[]``       signed word array        depends on value   ``word[] myvar = [1, 2, 3, 4]``
``uword[]``      unsigned word array      depends on value   ``uword[] myvar = [1, 2, 3, 4]``
``float[]``      floating-point array     depends on value   ``float[] myvar = [1.1, 2.2, 3.3, 4.4]``
``str``          string (petscii)         varies             ``str myvar = "hello."``
                                                             implicitly terminated by a 0-byte
``str_s``        string (screencodes)     varies             ``str_s myvar = "hello."``
                                                             implicitly terminated by a 0-byte
===============  =======================  =================  =========================================

**arrays:** you can split an array initializer list over several lines if you want. When an initialization
value is given, the array size in the declaration can be omitted.

**hexadecimal numbers:** you can use a dollar prefix to write hexadecimal numbers: ``$20ac``

**binary numbers:** you can use a percent prefix to write binary numbers: ``%10010011``
Note that ``%`` is also the remainder operator so be careful: if you want to take the remainder
of something with an operand starting with 1 or 0, you'll have to add a space in between.

**character values:** you can use a single character in quotes like this ``'a'`` for the Petscii byte value of that character.


**``byte`` versus ``word`` values:**

- When an integer value ranges from 0..255 the compiler sees it as a ``ubyte``.  For -128..127 it's a ``byte``.
- When an integer value ranges from 256..65535 the compiler sees it as a ``uword``.  For -32768..32767 it's a ``word``.
- When a hex number has 3 or 4 digits, for example ``$0004``, it is seen as a ``word`` otherwise as a ``byte``.
- When a binary number has 9 to 16 digits, for example ``%1100110011``, it is seen as a ``word`` otherwise as a ``byte``.
- You can force a byte value into a word value by adding the ``.w`` datatype suffix to the number: ``$2a.w`` is equivalent to ``$002a``.


Data type conversion
^^^^^^^^^^^^^^^^^^^^
Many type conversions are possible by just writing ``as <type>`` at the end of an expression,
for example ``ubyte ub = floatvalue as ubyte`` will convert the floating point value to an unsigned byte.


Memory mapped variables
^^^^^^^^^^^^^^^^^^^^^^^

The ``&`` (address-of operator) used in front of a data type keyword, indicates that no storage
should be allocated by the compiler. Instead, the (mandatory) value assigned to the variable
should be the *memory address* where the value is located::

	&byte BORDERCOLOR = $d020


Direct access to memory locations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Instead of defining a memory mapped name for a specific memory location, you can also
directly access the memory. Enclose a numeric expression or literal with ``@(...)`` to do that::

    A = @($d020)      ; set the A register to the current c64 screen border color ("peek(53280)")
    @($d020) = 0      ; set the c64 screen border to black ("poke 53280,0")
    @(vic+$20) = 6    ; a dynamic expression to 'calculate' the address


Constants
^^^^^^^^^

All variables can be assigned new values unless you use the ``const`` keyword.
The initial value will now be evaluated at compile time (it must be a compile time constant expression).
This is only valid for the simple numeric types (byte, word, float)::

	const  byte  max_age = 99


Reserved names
^^^^^^^^^^^^^^

The following names are reserved, they have a special meaning::

	A     X    Y              ; 6502 hardware registers
	Pc    Pz   Pn  Pv         ; 6502 status register flags
	true  false              ; boolean values 1 and 0


Range expression
^^^^^^^^^^^^^^^^

A special value is the *range expression* ( ``<startvalue>  to  <endvalue>`` )
which represents a range of numbers or characters,
from the starting value to (and including) the ending value.
If used in the place of a literal value, it expands into the actual array of values::

	byte[] array = 100 to 199     ; initialize array with [100, 101, ..., 198, 199]


Array indexing
^^^^^^^^^^^^^^

Strings and arrays are a sequence of values. You can access the individual values by indexing.
Syntax is familiar with brackets:  ``arrayvar[x]`` ::

    array[2]        ; the third byte in the array (index is 0-based)
    string[4]       ; the fifth character (=byte) in the string



Operators
---------

arithmetic: ``+``  ``-``  ``*``  ``/``  ``**``  ``%``
    ``+``, ``-``, ``*``, ``/`` are the familiar arithmetic operations.
    ``/`` is division (will result in integer division when using on integer operands, and a floating point division when at least one of the operands is a float)
    ``**`` is the power operator: ``3 ** 5`` is equal to 3*3*3*3*3 and is 243. (it only works on floating point variables)
    ``%`` is the remainder operator: ``25 % 7`` is 4.  Be careful: without a space, %10 will be parsed as the binary number 2
    Remainder is only supported on integer operands (not floats).

bitwise arithmetic: ``&``  ``|``  ``^``  ``~``  ``<<``  ``>>``
    ``&`` is bitwise and, ``|`` is bitwise or, ``^`` is bitwise xor, ``~`` is bitwise invert (this one is an unary operator)
    ``<<`` is bitwise left shift and ``>>`` is bitwise right shift (both will not change the datatype of the value)

assignment: ``=``
    Sets the target on the LHS (left hand side) of the operator to the value of the expression on the RHS (right hand side).
    Note that an assignment sometimes is not possible or supported.

augmented assignment: ``+=``  ``-=``  ``*=``  ``/=``  ``**=``  ``&=``  ``|=``  ``^=``  ``<<=``  ``>>=``
	Syntactic sugar; ``A += X`` is equivalent to ``A = A + X``

postfix increment and decrement: ``++``  ``--``
	Syntactic sugar; ``A++`` is equivalent to ``A = A + 1``, and ``A--`` is equivalent to ``A = A - 1``.
	Because these operations are so common, we have these short forms.

comparison: ``!=``  ``<``  ``>``  ``<=``  ``>=``
	Equality, Inequality, Less-than, Greater-than, Less-or-Equal-than, Greater-or-Equal-than comparisons.
	The result is a 'boolean' value 'true' or 'false' (which in reality is just a byte value of 1 or 0).

logical:  ``not``  ``and``  ``or``  ``xor``
	These operators are the usual logical operations that are part of a logical expression to reason
	about truths (boolean values). The result of such an expression is a 'boolean' value 'true' or 'false'
	(which in reality is just a byte value of 1 or 0).

range creation:  ``to``
	Creates a range of values from the LHS value to the RHS value, inclusive.
	These are mainly used in for loops to set the loop range. Example::

		0 to 7		; range of values 0, 1, 2, 3, 4, 5, 6, 7  (constant)

		A = 5
		X = 10
		A to X		; range of 5, 6, 7, 8, 9, 10

		byte[] array = 10 to 13   ; sets the array to [1, 2, 3, 4]

		for  i  in  0 to 127  {
			; i loops 0, 1, 2, ... 127
		}

address of:  ``&``
    This is a prefix operator that can be applied to a string or array variable or literal value.
    It results in the memory address (UWORD) of that string or array in memory:  ``uword a = &stringvar``
    Sometimes the compiler silently inserts this operator to make it easier for instance
    to pass strings or arrays as subroutine call arguments.
    This operator can also be used as a prefix to a variable's data type keyword to indicate that
    it is a memory mapped variable (for instance: ``&ubyte screencolor = $d021``)

precedence grouping in expressions, or subroutine parameter list:  ``(`` *expression* ``)``
	Parentheses are used to group parts of an expression to change the order of evaluation.
	(the subexpression inside the parentheses will be evaluated first):
	``(4 + 8) * 2`` is 24 instead of 20.

	Parentheses are also used in a subroutine call, they follow the name of the subroutine and contain
	the list of arguments to pass to the subroutine:   ``big_function(1, 99)``


Subroutine / function calls
---------------------------

You call a subroutine like this::

        [ result = ]  subroutinename_or_address ( [argument...] )

        ; example:
        resultvariable = subroutine(arg1, arg2, arg3)

Arguments are separated by commas. The argument list can also be empty if the subroutine
takes no parameters.  If the subroutine returns a value, you can still omit the assignment to
a result variable (but the compiler will warn you about discarding the result of the call).

Normal subroutines can only return zero or one return values.
However, the special ``asmsub`` routines (implemented in assembly code or referencing
a routine in kernel ROM) can return more than one return values, for instance a status
in the carry bit and a number in A, or a 16-bit value in A/Y registers.
It is not possible to process the results of a call to these kind of routines
directly from the language, because only single value assignments are possible.
You can still call the subroutine and not store the results.
But if you want to do something with the values it returns, you'll have to write
a small block of custom inline assembly that does the call and stores the values
appropriately. Don't forget to save/restore the registers if required.


Subroutine definitions
----------------------

The syntax is::

        sub   <identifier>  ( [parameters] )  [ -> returntype ]  {
                ... statements ...
        }

        ; example:
        sub  triple_something (word amount) -> word  {
        	return  X * 3
        }

The open curly brace must immediately follow the subroutine result specification on the same line,
and can have nothing following it. The close curly brace must be on its own line as well.
The parameters is a (possibly empty) comma separated list of "<datatype> <parametername>" pairs specifying the input parameters.
The return type has to be specified if the subroutine returns a value.

.. todo::
    asmsub with assigning memory address to refer to predefined ROM subroutines
    asmsub with a regular body to precisely control what registers are used to call the subroutine


Expressions
-----------

Expressions calculate a value and can be used almost everywhere a value is expected.
They consist of values, variables, operators, function calls, type casts, direct memory reads,
and can be combined into other expressions.
Long expressions can be split over multiple lines by inserting a line break before or after an operator::

    num_hours * 3600
     + num_minutes * 60
     + num_seconds


Loops
-----

for loop
^^^^^^^^

The loop variable must be a register or a byte/word variable. It must be defined in the local scope (to reuse
an existing variable), or you can declare it in the for loop directly to make a new one that is only visible
in the body of the for loop.
The expression that you loop over can be anything that supports iteration (such as ranges like ``0 to 100``,
array variables and strings) *except* floating-point arrays (because a floating-point loop variable is not supported).

You can use a single statement, or a statement block like in the example below::

	for [byte | word]  <loopvar>  in  <expression>  [ step <amount> ]   {
		; do something...
		break		; break out of the loop
		continue	; immediately enter next iteration
	}

For example, this is a for loop using the existing byte variable ``i`` to loop over a certain range of numbers::

    for i in 20 to 155 {
        ; do something
    }

And this is a loop over the values of the array ``fibonacci_numbers`` where the loop variable is declared in the loop itself::

    word[] fibonacci_numbers = [0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181]

    for word fibnr in fibonacci_numbers {
        ; do something
    }


You can inline the loop variable declaration in the for statement, including optional zp-tag. In this case
the variable is not visible outside of the for loop::

    for ubyte @zp fastindex in 10 to 20 {
        ; do something
    }


while loop
^^^^^^^^^^

As long as the condition is true (1), repeat the given statement(s).
You can use a single statement, or a statement block like in the example below::

	while  <condition>  {
		; do something...
		break		; break out of the loop
		continue	; immediately enter next iteration
	}


repeat--until loop
^^^^^^^^^^^^^^^^^^

Until the given condition is true (1), repeat the given statement(s).
You can use a single statement, or a statement block like in the example below::

	repeat  {
		; do something...
		break		; break out of the loop
		continue	; immediately enter next iteration
	} until  <condition>


Conditional Execution and Jumps
-------------------------------

Unconditional jump
^^^^^^^^^^^^^^^^^^

To jump to another part of the program, you use a ``goto`` statement with an addres or the name
of a label or subroutine::

	goto  $c000		; address
	goto  name		; label or subroutine


Notice that this is a valid way to end a subroutine (you can either ``return`` from it, or jump
to another piece of code that eventually returns).


Conditional execution
^^^^^^^^^^^^^^^^^^^^^

With the 'if' / 'else' statement you can execute code depending on the value of a condition::

	if  <expression>  <statements>  [else  <statements> ]

where <statements> can be just a single statement for instance just a ``goto``, or it can be a block such as this::

	if  <expression> {
		<statements>
	} else {
	  	<alternative statements>
	}


**Special status register branch form:**

There is a special form of the if-statement that immediately translates into one of the 6502's branching instructions.
It is almost the same as the regular if-statement but it lacks a contional expression part, because the if-statement
itself defines on what status register bit it should branch on::

	if_XX  <statements>  [else  <statements> ]

where <statements> can be just a single statement for instance just a ``goto``, or it can be a block such as this::

	if_XX {
		<statements>
	} else {
	  	<alternative statements>
	}

The XX corresponds to one of the eigth branching instructions so the possibilities are:
``if_cs``, ``if_cc``, ``if_eq``, ``if_ne``, ``if_pl``, ``if_mi``, ``if_vs`` and ``if_vc``.
It can also be one of the four aliases that are easier to read: ``if_z``, ``if_nz``, ``if_pos`` and ``if_neg``.

when statement ('jump table')
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The structure of a when statement is like this::

    when <expression> {
        <value(s)> -> <statement(s)>
        <value(s)> -> <statement(s)>
        ...
        [ else -> <statement(s)> ]
    }

The when-*value* can be any expression but the choice values have to evaluate to
compile-time constant integers (bytes or words).
The else part is optional.
Choices can result in a single statement or a block of  multiple statements in which
case you have to use { } to enclose them::

    when value {
        4 -> c64scr.print("four")
        5 -> c64scr.print("five")
        10,20,30 -> {
            c64scr.print("ten or twenty or thirty")
        }
        else -> c64scr.print("don't know")
    }
