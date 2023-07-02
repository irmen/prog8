.. _syntaxreference:

================
Syntax Reference
================

Module file
-----------

This is a file with the ``.p8`` suffix, containing *directives* and *code blocks*, described below.
The file is a text file which can also contain:

Lines, whitespace, indentation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Line endings are significant because *only one* declaration, statement or other instruction can occur on every line.
Other whitespace and line indentation is arbitrary and ignored by the compiler.
You can use tabs or spaces as you wish.

Source code comments
^^^^^^^^^^^^^^^^^^^^

Everything after a semicolon ``;`` is a comment and is ignored. There is no block-comment so just
comment out each individual line if you want to comment out a bunch of them.
If the whole line is just a comment, it will be copied into the resulting assembly source code.
This makes it easier to understand and relate the generated code. Examples::

	counter = 42    ; set the initial value to 42
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

	- type ``basic`` : add a tiny C64 BASIC program, with a SYS statement calling into the machine code
	- type ``none`` : no launcher logic is added at all

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
      except the few addresses mentioned above that are used by the system's IRQ routine.
      Even though the default IRQ routine is still active, it is impossible to use most BASIC and Kernal ROM routines.
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

.. data:: %zpreserved <fromaddress>,<toaddress>

    Level: module.
    Global setting, can occur multiple times. It allows you to reserve or 'block' a part of the zeropage so
    that it will not be used by the compiler.


.. data:: %address <address>

	Level: module.
	Global setting, set the program's start memory address. It's usually fixed at ``$0801`` because the
	default launcher type is a CBM-BASIC program. But you have to specify this address yourself when
	you don't use a CBM-BASIC launcher.


.. data:: %import <name>

	Level: module.
	This reads and compiles the named module source file as part of your current program.
	Symbols from the imported module become available in your code,
	without a module or filename prefix.
	You can import modules one at a time, and importing a module more than once has no effect.


.. data:: %option <option> [, <option> ...]

	Level: module, block.
	Sets special compiler options.

    - ``enable_floats`` (module level) tells the compiler
      to deal with floating point numbers (by using various subroutines from the Kernal).
      Otherwise, floating point support is not enabled. Normally you don't have to use this yourself as
      importing the ``floats`` library is required anyway and that will enable it for you automatically.
    - ``no_sysinit`` (module level) which cause the resulting program to *not* include
      the system re-initialization logic of clearing the screen, resetting I/O config etc. You'll have to
      take care of that yourself. The program will just start running from whatever state the machine is in when the
      program was launched.
    - ``force_output`` (in a block) will force the block to be outputted in the final program.
      Can be useful to make sure some data is generated that would otherwise be discarded because the compiler thinks it's not referenced (such as sprite data)
    - ``align_word`` (in a block) will make the assembler align the start address of this block on a word boundary in memory (so, an even memory address).
    - ``align_page`` (in a block) will make the assembler align the start address of this block on a page boundary in memory (so, the LSB of the address is 0).
    - ``merge`` (in a block) will merge this block's contents into an already existing block with the same name. Useful in library scenarios.
    - ``splitarrays`` (block or module) makes all word-arrays in this scope lsb/msb split arrays (as if they all have the @split tag). See Arrays.
    - ``no_symbol_prefixing`` (block) makes the compiler *not* use symbol-prefixing when translating prog8 code into assembly.
      Only use this if you know what you're doing because it could result in invalid assembly code being generated.


.. data:: %asmbinary "<filename>" [, <offset>[, <length>]]

    Level: not at module scope.
    This directive can only be used inside a block.
    The assembler will include the file as binary bytes at this point, prog8 will not process this at all.
    The optional offset and length can be used to select a particular piece of the file.
    The file is located relative to the current working directory!
    To reference the contents of the included binary data, you can put a label in your prog8 code
    just before the %asmbinary. An example program for this can be found below at the description of %asminclude.

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

            }
        }


.. data:: %breakpoint

    Level: not at module scope.
    Defines a debugging breakpoint at this location. See :ref:`debugging`

.. data:: %asm {{ ... }}

    Level: not at module scope.
    Declares that a piece of *assembly code* is inside the curly braces.
    This code will be copied as-is into the generated output assembly source file.
    The assembler syntax used should be for the 3rd party cross assembler tool that Prog8 uses (64tass).
    Note that the start and end markers are both *double curly braces* to minimize the chance
    that the assembly code itself contains either of those. If it does contain a ``}}``,
    it will confuse the parser.

    If you use the correct scoping rules you can access symbols from the prog8 program from inside
    the assembly code. Sometimes you'll have to declare a variable in prog8 with `@shared` if it
    is only used in such assembly code. For symbols just consisting of 3 letters, prog8 will
    add a special prefix to them, read more about this in :ref:`symbol-prefixing`.


Identifiers
-----------

Naming things in Prog8 is done via valid *identifiers*. They start with a letter,
and after that, a combination of letters, numbers, or underscores. Examples of valid identifiers::

	a
	A
	monkey
	COUNTER
	Better_Name_2
	something_strange__


Code blocks
-----------

A named block of actual program code. It defines a *scope* (also known as 'namespace') and
can only contain *directives*, *variable declarations*, *subroutines* or *inline assembly*::

    <blockname> [<address>] {
        <directives>
        <variables>
        <subroutines>
        <inline asm>
    }

The <blockname> must be a valid identifier.
The <address> is optional. If specified it must be a valid memory address such as ``$c000``.
It's used to tell the compiler to put the block at a certain position in memory.
Also read :ref:`blocks`.  Here is an example of a code block, to be loaded at ``$c000``::

	main $c000 {
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
for them. You can give them an initial value as well. That value can be a simple literal value,
or an expression. If you don't provide an initial value yourself, zero will be used.
Add a ``@zp`` zeropage-tag, to tell the compiler to prioritize it
when selecting variables to be put into zeropage (but no guarantees). If the ZP is full,
the variable will be allocated in normal memory elsewhere.
Add a ``@requirezp`` tag to force the variable in zeropage, but if the ZP is full,
the compilation will fail.
Add a ``@shared`` shared-tag, to tell the compiler that the variable is shared
with some assembly code and that it should not be optimized away if not used elsewhere.
For (u)word arrays, add ``@split`` to make the array layout in memory as 2 split arrays
one with the LSBs one with the MSBs of the word values.


The syntax is::

	<datatype>  [ @type-tag ]  <variable name>   [ = <initial value> ]

where type-tag is one of the tags mentioned earlier.
Various examples::

    word        thing   = 0
    byte        counter = len([1, 2, 3]) * 20
    byte        age     = 2018 - 1974
    float       wallet  = 55.25
    str         name    = "my name is Alice"
    uword       address = &counter
    byte[]      values  = [11, 22, 33, 44, 55]
    byte[5]     values                  ; array of 5 bytes, initially set to zero
    byte[5]     values  = 255           ; initialize with five 255 bytes

    word  @zp         zpword = 9999     ; prioritize this when selecting vars for zeropage storage
    uword @requirezp  zpaddr = $3000    ; we require this variable in zeropage
    word  @shared asmvar                ; variable is used in assembly code but not elsewhere


Data types
^^^^^^^^^^

Prog8 supports the following data types:

===============  =======================  =================  =========================================
type identifier  type                     storage size       example var declaration and literal value
===============  =======================  =================  =========================================
``byte``         signed byte              1 byte = 8 bits    ``byte myvar = -22``
``ubyte``        unsigned byte            1 byte = 8 bits    ``ubyte myvar = $8f``,   ``ubyte c = 'a'``
``bool``         boolean                  1 byte = 8 bits    ``bool myvar = true`` or ``bool myvar == false``
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
``bool[x]``      boolean array            5*x bytes          ``bool[4] myvar``  note: consider using bit flags in a byte or word instead to save space
``byte[]``       signed byte array        depends on value   ``byte[] myvar = [1, 2, 3, 4]``
``ubyte[]``      unsigned byte array      depends on value   ``ubyte[] myvar = [1, 2, 3, 4]``
``word[]``       signed word array        depends on value   ``word[] myvar = [1, 2, 3, 4]``
``uword[]``      unsigned word array      depends on value   ``uword[] myvar = [1, 2, 3, 4]``
``float[]``      floating-point array     depends on value   ``float[] myvar = [1.1, 2.2, 3.3, 4.4]``
``bool[]``       boolean array            depends on value   ``bool[] myvar = [true, false, true]``  note: consider using bit flags in a byte or word instead to save space
``str[]``        array with string ptrs   2*x bytes + strs   ``str[] names = ["ally", "pete"]``
``str``          string (PETSCII)         varies             ``str myvar = "hello."``
                                                             implicitly terminated by a 0-byte
===============  =======================  =================  =========================================

**arrays:** you can split an array initializer list over several lines if you want. When an initialization
value is given, the array size in the declaration can be omitted.

**hexadecimal numbers:** you can use a dollar prefix to write hexadecimal numbers: ``$20ac``

**binary numbers:** you can use a percent prefix to write binary numbers: ``%10010011``
Note that ``%`` is also the remainder operator so be careful: if you want to take the remainder
of something with an operand starting with 1 or 0, you'll have to add a space in between.

**character values:** you can use a single character in quotes like this ``'a'`` for the PETSCII byte value of that character.


**``byte`` versus ``word`` values:**

- When an integer value ranges from 0..255 the compiler sees it as a ``ubyte``.  For -128..127 it's a ``byte``.
- When an integer value ranges from 256..65535 the compiler sees it as a ``uword``.  For -32768..32767 it's a ``word``.
- When a hex number has 3 or 4 digits, for example ``$0004``, it is seen as a ``word`` otherwise as a ``byte``.
- When a binary number has 9 to 16 digits, for example ``%1100110011``, it is seen as a ``word`` otherwise as a ``byte``.
- If the number fits in a byte but you really require it as a word value, you'll have to explicitly cast it: ``60 as uword``
  or you can use the full word hexadecimal notation ``$003c``.


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
    &ubyte[5*40]  top5screenrows = $0400        ; works for array as well


.. _pointervars:

Direct access to memory locations ('peek' and 'poke')
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Instead of defining a memory mapped name for a specific memory location, you can also
directly access the memory. Enclose a numeric expression or literal with ``@(...)`` to do that::

    color = @($d020)  ; set the variable 'color' to the current c64 screen border color ("peek(53280)")
    @($d020) = 0      ; set the c64 screen border to black ("poke 53280,0")
    @(vic+$20) = 6    ; a dynamic expression to 'calculate' the address

The array indexing notation on a uword 'pointer variable' is syntactic sugar for such a direct memory access expression::

    pointervar[999] = 0     ; equivalent to @(pointervar+999) = 0


Constants
^^^^^^^^^

All variables can be assigned new values unless you use the ``const`` keyword.
The initial value must be known at compile time (it must be a compile time constant expression).
This is only valid for the simple numeric types (byte, word, float)::

	const  byte  max_age = 99


Reserved names
^^^^^^^^^^^^^^

The following names are reserved, they have a special meaning::

	true  false              ; boolean values 1 and 0


Range expression
^^^^^^^^^^^^^^^^

A special value is the *range expression* which represents a range of integer numbers or characters,
from the starting value to (and including) the ending value::

    <start>  to  <end>   [ step  <step> ]
    <start>  downto  <end>   [ step  <step> ]

You an provide a step value if you need something else than the default increment which is one (or,
in case of downto, a decrement of one).   Because a step of minus one is so common you can just use
the downto variant to avoid having to specify the step as well.

If used in the place of a literal value, it expands into the actual array of integer values::

	byte[] array = 100 to 199     ; initialize array with [100, 101, ..., 198, 199]


Array indexing
^^^^^^^^^^^^^^

Strings and arrays are a sequence of values. You can access the individual values by indexing.
Syntax is familiar with brackets:  ``arrayvar[x]`` ::

    array[2]        ; the third byte in the array (index is 0-based)
    string[4]       ; the fifth character (=byte) in the string

Note: you can also use array indexing on a 'pointer variable', which is basically an uword variable
containing a memory address. Currently this is equivalent to directly referencing the bytes in
memory at the given index. See :ref:`pointervars`

String
^^^^^^
A string literal can occur with or without an encoding prefix (encoding followed by ':' followed by the string itself).
When this is omitted, the string is stored in the machine's default character encoding (which is PETSCII on the CBM machines).
You can choose to store the string in other encodings such as ``sc`` (screencodes) or ``iso`` (iso-8859-15).
String length is limited to 255 characters.
Here are several examples:

    - ``"hello"``   a string translated into the default character encoding (PETSCII)
    - ``petscii:"hello"``   same as the above, on CBM machines.
    - ``sc:"my name is Alice"``      string with screencode encoding (new syntax)
    - ``iso:"Ich heiße François"``   string in iso encoding


There are several escape sequences available to put special characters into your string value:

- ``\\`` - the backslash itself, has to be escaped because it is the escape symbol by itself
- ``\n`` - newline character (move cursor down and to beginning of next line)
- ``\r`` - carriage return character (more or less the same as newline if printing to the screen)
- ``\"`` - quote character (otherwise it would terminate the string)
- ``\'`` - apostrophe character (has to be escaped in character literals, is okay inside a string)
- ``\uHHHH`` - a unicode codepoint \u0000 - \uffff (16-bit hexadecimal)
- ``\xHH`` - 8-bit hex value that will be copied verbatim *without encoding*

- String literals can contain many symbols directly if they have a PETSCII equivalent, such as "♠♥♣♦π▚●○╳".
  Characters like ^, _, \\, {, } and | (that have no direct PETSCII counterpart) are still accepted and converted to the closest PETSCII equivalents. (Make sure you save the source file in UTF-8 encoding if you use this.)


Operators
---------

arithmetic: ``+``  ``-``  ``*``  ``/``  ``%``
    ``+``, ``-``, ``*``, ``/`` are the familiar arithmetic operations.
    ``/`` is division (will result in integer division when using on integer operands, and a floating point division when at least one of the operands is a float)
    ``%`` is the remainder operator: ``25 % 7`` is 4.  Be careful: without a space, %10 will be parsed as the binary number 2.
    Remainder is only supported on integer operands (not floats).

bitwise arithmetic: ``&``  ``|``  ``^``  ``~``  ``<<``  ``>>``
    ``&`` is bitwise and, ``|`` is bitwise or, ``^`` is bitwise xor, ``~`` is bitwise invert (this one is an unary operator)
    ``<<`` is bitwise left shift and ``>>`` is bitwise right shift (both will not change the datatype of the value)

assignment: ``=``
    Sets the target on the LHS (left hand side) of the operator to the value of the expression on the RHS (right hand side).
    Note that an assignment sometimes is not possible or supported.

augmented assignment: ``+=``  ``-=``  ``*=``  ``/=``  ``&=``  ``|=``  ``^=``  ``<<=``  ``>>=``
	This is syntactic sugar; ``aa += xx`` is equivalent to ``aa = aa + xx``

postfix increment and decrement: ``++``  ``--``
	Syntactic sugar; ``aa++`` is equivalent to ``aa = aa + 1``, and ``aa--`` is equivalent to ``aa = aa - 1``.
	Because these operations are so common, we have these short forms.

comparison: ``!=``  ``<``  ``>``  ``<=``  ``>=``
	Equality, Inequality, Less-than, Greater-than, Less-or-Equal-than, Greater-or-Equal-than comparisons.
	The result is a 'boolean' value 'true' or 'false' (which in reality is just a byte value of 1 or 0).

logical:  ``not``  ``and``  ``or``  ``xor``
	These operators are the usual logical operations that are part of a logical expression to reason
	about truths (boolean values). The result of such an expression is a 'boolean' value 'true' or 'false'
	(which in reality is just a byte value of 1 or 0).
	Notice that the expression ``not x`` is equivalent to ``x==0``, and the compiler will treat it as such.

	.. note::
		You can use regular integers directly in logical expressions but these have to be converted to
		the boolean value 0 or 1 every time, resulting in larger and slower code. Consider using
		the ``bool`` variable type instead, where this conversion doesn't need to occur.

	.. note::
		Unlike most other programming languages, there is no short-circuit or McCarthy evaluation
		for the logical ``and`` and ``or`` operators. This means that prog8 currently always evaluates
		all operands from these logical expressions, even when one of them already determines the outcome!
		If you don't want this to happen, you have to split and nest the if-statements yourself.

range creation:  ``to``
	Creates a range of values from the LHS value to the RHS value, inclusive.
	These are mainly used in for loops to set the loop range. Example::

		0 to 7		; range of values 0, 1, 2, 3, 4, 5, 6, 7  (constant)

		aa = 5
		aa = 10
	    aa to xx		; range of 5, 6, 7, 8, 9, 10

		byte[] array = 10 to 13   ; sets the array to [1, 2, 3, 4]

		for  i  in  0 to 127  {
			; i loops 0, 1, 2, ... 127
		}

containment check:  ``in``
    Tests if a value is present in a list of values, which can be a string or an array.
    The result is a simple boolean ``true`` or ``false``.
    Consider using this instead of chaining multiple value tests with ``or``, because the
    containment check is more efficient.
    Examples::

        ubyte cc
        if cc in [' ', '@', 0] {
            txt.print("cc is one of the values")
        }

        str  email_address = "?????????"
        if '@' in email_address {
            txt.print("email address seems ok")
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

        [ void / result = ] subroutinename_or_address ( [argument...] )

        ; example:
        resultvariable = subroutine(arg1, arg2, arg3)
        void noresultvaluesub(arg)


Arguments are separated by commas. The argument list can also be empty if the subroutine
takes no parameters.  If the subroutine returns a value, usually you assign it to a variable.
If you're not interested in the return value, prefix the function call with the ``void`` keyword.
Otherwise the compiler will warn you about discarding the result of the call.

Multiple return values
^^^^^^^^^^^^^^^^^^^^^^
Normal subroutines can only return zero or one return values.
However, the special ``asmsub`` routines (implemented in assembly code) or ``romsub`` routines
(referencing a routine in Kernal ROM) can return more than one return value.
For example a status in the carry bit and a number in A, or a 16-bit value in A/Y registers.
It is not possible to process the results of a call to these kind of routines
directly from the language, because only single value assignments are possible.
You can still call the subroutine and not store the results.

**There is an exception:** if there's just one return value in a register, and one or more others that are returned
as bits in the status register (such as the Carry bit), the compiler allows you to call the subroutine.
It will then store the result value in a variable if required, and *try to keep the status register untouched
after the call* so you can often use a conditional branch statement for that. But the latter is tricky,
make sure you check the generated assembly code.

If there really are multiple relevant return values (other than a combined 16 bit return value in 2 registers),
you'll have to write a small block of custom inline assembly that does the call and stores the values
appropriately. Don't forget to save/restore any registers that are modified.


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

The parameters is a (possibly empty) comma separated list of "<datatype> <parametername>" pairs specifying the input parameters.
The return type has to be specified if the subroutine returns a value.


Assembly /  ROM subroutines
^^^^^^^^^^^^^^^^^^^^^^^^^^^

Subroutines implemented in ROM are usually defined by compiler library files, with the following syntax::

    romsub $FFD5 = LOAD(ubyte verify @ A, uword address @ XY) -> clobbers() -> bool @Pc, ubyte @ A, ubyte @ X, ubyte @ Y

This defines the ``LOAD`` subroutine at ROM memory address $FFD5, taking arguments in all three registers A, X and Y,
and returning stuff in several registers as well. The ``clobbers`` clause is used to signify to the compiler
what CPU registers are clobbered by the call instead of being unchanged or returning a meaningful result value.

User subroutines in the program source code that are implemented purely in assembly and which have an assembly calling convention (i.e.
the parameters are strictly passed via cpu registers), are defined with ``asmsub`` like this::

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

the statement body of such a subroutine should consist of just an inline assembly block.

The ``@ <register>`` part is required for rom and assembly-subroutines, as it specifies for the compiler
what cpu registers should take the routine's arguments.  You can use the regular set of registers
(A, X, Y), special 16-bit register pairs to take word values (AX, AY and XY) and even a processor status
flag such as Carry (Pc).

It is not possible to use floating point arguments or return values in an asmsub.

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
    ``cx16.r0`` .. ``cx16.r15``  (these are memory mapped uword values),
    ``cx16.r0s`` .. ``cx16.r15s``  (these are memory mapped word values),
    and ``L`` / ``H`` variants are also available to directly access the low and high bytes of these.


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

The loop variable must be a byte or word variable, and it must be defined separately first.
The expression that you loop over can be anything that supports iteration (such as ranges like ``0 to 100``,
array variables and strings) *except* floating-point arrays (because a floating-point loop variable is not supported).

You can use a single statement, or a statement block like in the example below::

	for <loopvar>  in  <expression>  [ step <amount> ]   {
		; do something...
		break		; break out of the loop
	}

For example, this is a for loop using a byte variable ``i``, defined before, to loop over a certain range of numbers::

    ubyte i

    ...

    for i in 20 to 155 {
        ; do something
    }

And this is a loop over the values of the array ``fibonacci_numbers``::

    uword[] fibonacci_numbers = [0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181]

    uword number
    for number in fibonacci_numbers {
        ; do something with number...
        break       ; break out of the loop early
    }



while loop
^^^^^^^^^^

As long as the condition is true (1), repeat the given statement(s).
You can use a single statement, or a statement block like in the example below::

	while  <condition>  {
		; do something...
		break		; break out of the loop
	}


do-until loop
^^^^^^^^^^^^^

Until the given condition is true (1), repeat the given statement(s).
You can use a single statement, or a statement block like in the example below::

	do  {
		; do something...
		break		; break out of the loop
	} until  <condition>


repeat loop
^^^^^^^^^^^

When you're only interested in repeating something a given number of times.
It's a short hand for a for loop without an explicit loop variable::

    repeat 15 {
        ; do something...
        break		; you can break out of the loop
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

A `break` statement cannot occur in an unroll loop, as there is not really a loop to break out of.


Conditional Execution and Jumps
-------------------------------

Unconditional jump: goto
^^^^^^^^^^^^^^^^^^^^^^^^

To jump to another part of the program, you use a ``goto`` statement with an address or the name
of a label or subroutine::

    goto  $c000     ; address
    goto  name      ; label or subroutine

    uword address = $4000
    goto  address   ; jump via address variable

Notice that this is a valid way to end a subroutine (you can either ``return`` from it, or jump
to another piece of code that eventually returns).

If you jump to an address variable (uword), it is doing an 'indirect' jump: the jump will be done
to the address that's currently in the variable.


if statements
^^^^^^^^^^^^^

With the 'if' / 'else' statement you can execute code depending on the value of a condition::

	if  <expression>  <statements>  [else  <statements> ]

If  <statements> is just a single statement, for instance just a ``goto`` or a single assignment,
it's possible to just write the statement without any curly braces.
However if <statements> is a block of multiple statements, you'll have to enclose it in curly braces::

	if  <expression> {
		<statements>
	} else if <expression> {
		<statements>
	} else {
		<statements>
	}


**Special status register branch form:**

There is a special form of the if-statement that immediately translates into one of the 6502's branching instructions.
It is almost the same as the regular if-statement but it lacks a conditional expression part, because the if-statement
itself defines on what status register bit it should branch on::

	if_XX  <statements>  [else  <statements> ]

where <statements> can be just a single statement or a block again::

	if_XX {
		<statements>
	} else {
	  	<alternative statements>
	}

The XX corresponds to one of the processor's branching instructions, so the possibilities are:
``if_cs``, ``if_cc``, ``if_eq``, ``if_ne``, ``if_pl``, ``if_mi``, ``if_vs`` and ``if_vc``.
It can also be one of the four aliases that are easier to read: ``if_z``, ``if_nz``, ``if_pos`` and ``if_neg``.

.. caution::
    These special ``if_XX`` branching statements are only useful in certain specific situations where you are *certain*
    that the status register (still) contains the correct status bits.
    This is not always the case after a function call or other operations!
    If in doubt, check the generated assembly code!


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
        4 -> txt.print("four")
        5 -> txt.print("five")
        10,20,30 -> {
            txt.print("ten or twenty or thirty")
        }
        else -> txt.print("don't know")
    }

