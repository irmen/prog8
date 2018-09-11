.. _syntaxreference:

================
Syntax Reference
================

Module file
-----------

This is a file with the ``.ill`` suffix, containing *directives* and *code blocks*, described below.
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
    - style ``basicsafe`` -- the most restricted mode; only use the handful 'free' addresses in the ZP, and don't
      touch change anything else. This allows full use of BASIC and KERNAL ROM routines including default IRQs
      during normal system operation.
    - style ``full`` -- claim the whole ZP for variables for the program, overwriting everything,
      except the few addresses mentioned above that are used by the system's IRQ routine.
      Even though the default IRQ routine is still active, it is impossible to use most BASIC and KERNAL ROM routines.
      This includes many floating point operations and several utility routines that do I/O, such as ``print_string``.
      It is also not possible to cleanly exit the program, other than resetting the machine.
      This option makes programs smaller and faster because many more variables can
      be stored in the ZP, which is more efficient.

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


.. data:: %option <option> [, <option> ...]

	Level: module.
	Sets special compiler options.
	For now, only the ``enable_floats`` option is recognised, which will tell the compiler
	to deal with floating point numbers (by using various subroutines from the Commodore-64 kernal).
	Otherwise, floating point support is not enabled.


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

.. data:: %asm {{ ... }}

	Level: block, subroutine.
	Declares that there is *inline assembly code* in the lines enclosed by the curly braces.
	This code will be written as-is into the generated output file.
	The assembler syntax used should be for the 3rd party cross assembler tool that IL65 uses.
	Note that the start and end markers are both *double curly braces* to minimize the chance
	that the inline assembly itself contains either of those. If it does contain a ``}}``,
 	the parsing of the inline assembler block will end prematurely and cause compilation errors.


Identifiers
-----------

Naming things in IL65 is done via valid *identifiers*. They start with a letter or underscore,
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
can contain IL65 *code*, *directives*, *variable declarations* and *subroutines*::

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
or a (constant) expression. The syntax is::

	<datatype>   <variable name>   [ = <initial value> ]

Various examples::

    word        thing   = 0
    byte        counter = len([1, 2, 3]) * 20
    byte        age     = 2018 - 1974
    float       wallet  = 55.25
    str         name    = "my name is Irmen"
    word        address = #counter
    byte[5]     values  = [11, 22, 33, 44, 55]
    byte[5]     values  = 255           ; initialize with five 255 bytes
    byte[5][6]  empty_matrix = 0        ; initialize with 30 zero bytes
    byte[2][3]  other_matrix = [1,2,3,4,5,6]   ; 2*3 matrix with value | (1,2)  (3,4)  (5,6) |



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
``byte[x]``      unsigned byte array      x bytes            ``byte[4] myvar = [1, 2, 3, 4]``
``word[x]``      unsigned word array      2*x bytes          ``word[4] myvar = [1, 2, 3, 4]``
``byte[x,y]``    unsigned byte matrix     x*y bytes          ``byte[40,25] myvar = @todo``
                                                             word-matrix not supported
``str``          string (petscii)         varies             ``str myvar = "hello."``
                                                             implicitly terminated by a 0-byte
``str_p``        pascal-string (petscii)  varies             ``str_p myvar = "hello."``
                                                             implicit first byte = length, no 0-byte
``str_s``        string (screencodes)     varies             ``str_s myvar = "hello."``
                                                             implicitly terminated by a 0-byte
``str_ps``       pascal-string            varies             ``str_ps myvar = "hello."``
                 (screencodes)                               implicit first byte = length, no 0-byte
===============  =======================  =================  =========================================


**@todo pointers/addresses?  (as opposed to normal WORDs)**

**@todo signed integers (byte and word)?**


Memory mapped variables
^^^^^^^^^^^^^^^^^^^^^^^

The ``memory`` keyword is used in front of a data type keyword, to say that no storage
should be allocated by the compiler. Instead, the (mandatory) value assigned to the variable
should be the *memory address* where the value is located::

	memory  byte  BORDER = $d020


Constants
^^^^^^^^^

All variables can be assigned new values unless you use the ``const`` keyword.
The initial value will now be evaluated at compile time (it must be a compile time constant expression)
and no storage is allocated for the constant::

	const  byte  max_age = 99


Reserved names
^^^^^^^^^^^^^^

The following names are reserved, they have a special meaning::

	A    X    Y              ; 6502 hardware registers
	AX   AY   XY             ; 16-bit pseudo register pairs


Range expression
^^^^^^^^^^^^^^^^

A special value is the *range expression* ( ``<startvalue>  to  <endvalue>`` )
which represents a range of numbers or characters,
from the starting value to (and including) the ending value.
If used in the place of a literal value, it expands into the actual array of values::

	byte[100] array = 100 to 199     ; initialize array with [100, 101, ..., 198, 199]


.. todo::
	this may be used later in the for-loop as well.  Add 'step' to range expression as well?


Operators
---------

address-of: ``#``
	Takes the address of the symbol following it:   ``word  address =  #somevar``


arithmetic: ``+``  ``-``  ``*``  ``/``  ``//``  ``**``  ``%``
	``+``, ``-``, ``*``, ``/`` are the familiar arithmetic operations.
	``//`` means *integer division* even when the operands are floating point values:  ``9.5 // 2.5`` is 3 (and not 3.8)
	``**`` is the power operator: ``3 ** 5`` is equal to 3*3*3*3*3 and is 243.
	``%`` is the remainder operator: ``25 % 7`` is 4.


bitwise arithmetic: ``&``  ``|``  ``^``  ``~``
	``&`` is bitwise and, ``|`` is bitwise or, ``^`` is bitwise xor, ``~`` is bitwise invert (this one is an unary operator)

assignment: ``=``
	Sets the target on the LHS (left hand side) of the operator to the value of the expression on the RHS (right hand side).

augmented assignment: ``+=``  ``-=``  ``*=``  ``/=``  ``//=``  ``**=``  ``&=``  ``|=``  ``^=``
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

		byte[4] array = 10 to 13   ; sets the array to [1, 2, 3, 4]

		for  i  in  0 to 127  {
			; i loops 0, 1, 2, ... 127
		}


.. todo::
    array indexing:  ``[`` *index* ``]``
        When put after a sequence type (array, string or matrix) it means to point to the given element in that sequence::

            array[2]		; the third byte in the array (index is 0-based)
            matrix[4,2]		; the byte at the 5th column and 3rd row in the matrix


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
        outputvar1, outputvar2  =  subroutine ( arg1, arg2, arg3 )

Arguments are separated by commas. The argument list can also be empty if the subroutine
takes no parameters.
If the subroutine returns one or more result values, you must use an assignment statement
to store those values somewhere. If the subroutine has no result values, you must
omit the assignment.



Subroutine definitions
----------------------

The syntax is::

        sub   <identifier>  ([proc_parameters]) -> ([proc_results])  {
                ... statements ...
        }

        ; example:
        sub  triple_something (amount: X) -> A  {
        	return  X * 3
        }

The open curly brace must immediately follow the subroutine result specification on the same line,
and can have nothing following it. The close curly brace must be on its own line as well.

Pre-defined subroutines that are available on specific memory addresses
(in system ROM for instance) can be defined by assigning the routine's memory address to the sub,
and not specifying a code block::

	sub  <identifier>  ([proc_parameters]) -> ([proc_results])  = <address>

	; example:
	sub  CLOSE  (logical: A) -> (A?, X?, Y?)  = $FFC3


.. data:: proc_parameters

        comma separated list of "<parametername>:<register>" pairs specifying the input parameters.
        You can omit the parameter names as long as the arguments "line up".

.. data:: proc_results

        comma separated list of <register> names specifying in which register(s) the output is returned.
        If the register name ends with a '?', that means the register doesn't contain a real return value but
        is clobbered in the process so the original value it had before calling the sub is no longer valid.
        This is not immediately useful for your own code, but the compiler needs this information to
        emit the correct assembly code to preserve the cpu registers if needed when the call is made.
        For convenience: a single '?' als the result spec is shorthand for ``A?, X?, Y?`` ("I don't know
        what the changed registers are, assume the worst")


Loops
-----

for loop
^^^^^^^^
.. todo:: not implemented yet, for now you can use the if statement with gotos to implement a for-loop.

::

	for  <loopvar>  in  <range>  [ step <amount> ]   {
		; do something...
		break		; break out of the loop
		continue	; immediately enter next iteration
	}


while loop
^^^^^^^^^^
.. todo:: not implemented yet, for now you can use the if statement with gotos to implement a while-loop.

::

	while  <condition>  {
		; do something...
		break		; break out of the loop
		continue	; immediately enter next iteration
	}



repeat--until loop
^^^^^^^^^^^^^^^^^^
.. todo:: not implemented yet, for now you can use the if statement with gotos to implement a repeat-loop.

::

	repeat  {
		; do something...
		break		; break out of the loop
		continue	; immediately enter next iteration
	} until  <condition>


Conditional Execution and Jumps
-------------------------------

unconditional jump
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

	if  ( <expression> )  <statements>  [else  <statements> ]

where <statements> can be just a single statement for instance just a ``goto``, or it can be a block such as this::

	if  ( <expression> ) {
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