.. _programstructure:

===================
Programming in IL65
===================

This chapter describes a high level overview of the elements that make up a program.
Details about the syntax can be found in the :ref:`syntaxreference` chapter.


Elements of a program
---------------------

Program
	Consists of one or more *modules*.

Module
	A file on disk with the ``.ill`` suffix. It contains *directives* and *code blocks*.
	Whitespace and indentation in the source code are arbitrary and can be tabs or spaces or both.
	You can also add *comments* to the source code.
	One moudule file can *import* others, and also import *library modules*.

Comments
	Everything after a semicolon ``;`` is a comment and is ignored by the compiler.
	If the whole line is just a comment, it will be copied into the resulting assembly source code.
	This makes it easier to understand and relate the generated code. Examples::

		A = 42    ; set the initial value to 42
		; next is the code that...

Directive
	These are special instructions for the compiler, to change how it processes the code
	and what kind of program it creates. A directive is on its own line in the file, and
	starts with ``%``, optionally followed by some arguments.

Code block
	A block of actual program code. It defines a *scope* (also known as 'namespace') and
	can contain IL65 *code*, *variable declarations* and *subroutines*.
	More details about this below: :ref:`blocks`.

Variable declarations
	The data that the code works on is stored in variables ('named values that can change').
	The compiler allocates the required memory for them.
	There is *no dynamic memory allocation*. The storage size of all variables
	is fixed and is determined at compile time.
	Variable declarations tend to appear at the top of the code block that uses them.
	They define the name and type of the variable, and its initial value.
	IL65 supports a small list of data types, including special 'memory mapped' types
	that don't allocate storage but instead point to a fixed location in the address space.

Code
	These are the instructions that make up the program's logic. There are different kinds of instructions
	('statements' is a better name):

	- value assignment
	- looping  (for, while, repeat, unconditional jumps)
	- conditional execution (if - then - else, and conditional jumps)
	- subroutine calls
	- label definition

Subroutine
	Defines a piece of code that can be called by its name from different locations in your code.
	It accepts parameters and can return result values.
	It can define its own variables but it's not possible to define subroutines nested in other subroutines.
	To keep things simple, you can only define subroutines inside code blocks from a module.

Label
	This is a named position in your code where you can jump to from another place.
	You can jump to it with a jump statement elsewhere. It is also possible to use a
	subroutine call to a label (but without parameters and return value).


Scope
	Also known as 'namespace', this is a named box around the symbols defined in it.
	This prevents name collisions (or 'namespace pollution'), because the name of the scope
	is needed as prefix to be able to access the symbols in it.
	Anything *inside* the scope can refer to symbols in the same scope without using a prefix.
	There are three scopes in IL65:

	- global (no prefix)
	- code block
	- subroutine

	Modules are *not* a scope! Everything defined in a module is merged into the global scope.


.. _blocks:

Blocks, Scopes, and accessing Symbols
-------------------------------------

Blocks are the separate pieces of code and data of your program. They are combined
into a single output program.  No code or data can occur outside a block. Here's an example::

	~ main $c000 {
		; this is code inside the block...
	}


The name of a block must be unique in your entire program.
Also be careful when importing other modules; blocks in your own code cannot have
the same name as a block defined in an imported module or library.

It's possible to omit this name, but then you can only refer to the contents of the block via its absolute address,
which is required in this case. If you omit *both* name and address, the block is *ignored* by the compiler (and a warning is displayed).
This is a way to quickly "comment out" a piece of code that is unfinshed or may contain errors that you
want to work on later, because the contents of the ignored block are not fully parsed either.

The address can be used to place a block at a specific location in memory.
Usually it is omitted, and the compiler will automatically choose the location (usually immediately after
the previous block in memory).
The address must be >= ``$0200`` (because ``$00``--``$ff`` is the ZP and ``$100``--``$200`` is the cpu stack).

A block is also a *scope* in your program so the symbols in the block don't clash with
symbols of the same name defined elsewhere in the same file or in another file.
You can refer to the symbols in a particular block by using a *dotted name*: ``blockname.symbolname``.
Labels inside a subroutine are appended again to that; ``blockname.subroutinename.label``.

Every symbol is 'public' and can be accessed from elsewhere given its dotted name.


**The special "ZP" ZeroPage block**

Blocks named "ZP" are treated a bit differently: they refer to the ZeroPage.
The contents of every block with that name (this one may occur multiple times) are merged into one.
Its start address is always set to ``$04``, because ``$00 - $01`` are used by the hardware
and ``$02 - $03`` are reserved as general purpose scratch registers.


Program Start and Entry Point
-----------------------------

Your program must have a single entry point where code execution begins.
The compiler expects a ``start`` subroutine in the ``main`` block for this,
taking no parameters and having no return value.
As any subroutine, it has to end with a ``return`` statement (or a ``goto`` call)::

	~ main {
	    sub start () -> ()  {
	        ; program entrypoint code here
	        return
	    }
	}

The ``main`` module is always relocated to the start of your programs
address space, and the ``start`` subroutine (the entrypoint) will be on the
first address. This will also be the address that the BASIC loader program (if generated)
calls with the SYS statement.


Variables and values
--------------------

Variables are named values that can change during the execution of the program.
When declaring a variable it is possible to specify the initial value it should get.
Values will usually be part of an expression or assignment statement::

	12345			; integer number
	$aa43			; hex integer number
	%100101			; binary integer number
	"Hi, I am a string"	; text string
	-33.456e52		; floating point number

	byte  counter  = 42	; variable of size 8 bits, with initial value 42


Note that the various keywords for the data type and variable type (``byte``, ``word``, ``const``, etc.)
cannot be used as *identifiers* elsewhere. You can't make a variable, block or subroutine with the name ``byte``
for instance.


Special types: const and memory-mapped
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When using ``const``, the value of the 'variable' can no longer be changed.
You'll have to specify the initial value expression. This value is then used
by the compiler everywhere you refer to the constant (and no storage is allocated
for the constant itself).

When using ``memory``, the variable will point to specific location in memory,
rather than being newly allocated. The initial value (mandatory) must be a valid
memory address.  Reading the variable will read the given data type from the
address you specified, and setting the varible will directly modify that memory location(s)::

	const  byte  max_age = 2000 - 1974      ; max_age will be the constant value 26
	memory word  SCREENCOLORS = $d020       ; a 16-bit word at the addres $d020-$d021


Integers
^^^^^^^^

Integers are 8 or 16 bit numbers and can be written in normal decimal notation,
in hexadecimal and in binary notation.

@todo right now only unsinged integers are supported (0-255 for byte types, 0-65535 for word types)


Strings
^^^^^^^

Strings are a sequence of characters enclosed in ``"`` quotes.
They're stored and treated much the same as a byte array,
but they have some special properties because they are considered to be *text*.
Strings in your source code files will be encoded (translated from ASCII/UTF-8) into either CBM PETSCII or C-64 screencodes.
PETSCII is the default choice. If you need screencodes (also called 'poke' codes) instead,
you have to use the ``str_s`` variants of the string type identifier.
If you assign a string literal of length 1 to a non-string variable, it is treated as a *byte* value instead
with has the PETSCII value of that single character,


Floating point numbers
^^^^^^^^^^^^^^^^^^^^^^

Floats are stored in the 5-byte 'MFLPT' format that is used on CBM machines,
and also most float operations are specific to the Commodore-64.
This is because routines in the C-64 BASIC and KERNAL ROMs are used for that.
So floating point operations will only work if the C-64 BASIC ROM (and KERNAL ROM)
are banked in (and your code imports the ``c64lib.ill``)

The largest 5-byte MFLPT float that can be stored is: **1.7014118345e+38**   (negative: **-1.7014118345e+38**)


Initial values across multiple runs of the program
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The initial values of your variables will be restored automatically when the program is (re)started,
*except for string variables*. It is assumed these are left unchanged by the program.
If you do modify them in-place, you should take care yourself that they work as
expected when the program is restarted.



Indirect addressing and address-of
----------------------------------

The ``#`` operator is used to take the address of the symbol following it.
It can be used for example to work with the *address* of a memory mapped variable rather than
the value it holds.  You could take the address of a string as well, but that is redundant:
the compiler already treats those as a value that you manipulate via its address.
For most other types this prefix is not supported and will result in a compilation error.
The resulting value is simply a 16 bit word. Example::

	AX = #somevar


**Indirect addressing:**
@todo ???

**Indirect addressing in jumps:**
@todo ???
For an indirect ``goto`` statement, the compiler will issue the 6502 CPU's special instruction
(``jmp`` indirect).  A subroutine call (``jsr`` indirect) is emitted
using a couple of instructions.


Loops
-----

The *for*-loop is used to iterate over a range of values. Iteration steps by 1,
but you can set it to something else as well.
The *while*-loop is used to repeat a piece of code while a certain condition is still true.
The *repeat--until* loop is used to repeat a piece of code until a certain condition is true.

You can also create loops by using the ``goto`` statement, but this should be avoided.


Conditional Execution
---------------------

.. todo::
	not sure how to handle direct translation into
	[cc, cs, vc, vs, eq, ne, true, not, zero, pos, neg, lt, gt, le, ge]
	It defaults to 'true' (=='ne', not-zero) if omitted. ('pos' will translate into 'pl', 'neg' into 'mi')
	@todo signed: lts==neg?, gts==eq+pos?, les==neg+eq?, ges==pos?

.. todo::
	eventually allow local variable definitions inside the sub blocks but for now,
	they have to use the same variables as the block the ``if`` statement itself is in.


Conditional execution means that the flow of execution changes based on certiain conditions,
rather than having fixed gotos or subroutine calls::

	if A > 4 goto overflow

	if X == 3 then Y = 4
	if X == 3 then Y = 4 else A = 2

	if X == 5 {
		Y = 99
	} else {
		A = 3
	}

condition = arithmetic expression or  logical expression or  comparison expression or  status_register_flags ( ``SR.cs`` , ``SR.cc``, ``SR.pl`` etc... @todo )




Conditional jumps are compiled into 6502's branching instructions (such as ``bne`` and ``bcc``) so
the rather strict limit on how *far* it can jump applies. The compiler itself can't figure this
out unfortunately, so it is entirely possible to create code that cannot be assembled successfully.
You'll have to restructure your gotos in the code (place target labels closer to the branch)
if you run into this type of assembler error.


Assignments
-----------

Assignment statements assign a single value to a target variable or memory location.
Augmented assignments (such as ``A += X``) are also available, but these are just shorthands
for normal assignments (``A = A + X``).


Expressions
-----------

In most places where a number or other value is expected, you can use just the number, or a full constant expression.
The expression is parsed and evaluated by the compiler itself at compile time, and the (constant) resulting value is used in its place.
Expressions can contain function calls to the math library (sin, cos, etc) and you can also use
all builtin functions (max, avg, min, sum etc). They can also reference idendifiers defined elsewhere in your code,
if this makes sense.


Arithmetic and Logical expressions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Arithmetic expressions are expressions that calculate a numeric result (integer or floating point).
Many common arithmetic operators can be used and follow the regular precedence rules.

Logical expressions are expressions that calculate a boolean result, true or false
(which in IL65 will effectively be a 1 or 0 integer value).

You can use parentheses to group parts of an expresion to change the precedence.
Usually the normal precedence rules apply (``*`` goes before ``+`` etc.) but subexpressions
within parentheses will be evaluated first. So ``(4 + 8) * 2`` is 24 and not 20,
and ``(true or false) and false`` is false instead of true.


Subroutines
-----------

Defining a subroutine
^^^^^^^^^^^^^^^^^^^^^

Subroutines are parts of the code that can be repeatedly invoked using a subroutine call from elsewhere.
Their definition, using the sub statement, includes the specification of the required input- and output parameters.
For now, only register based parameters are supported (A, X, Y and paired registers,
the carry status bit SC and the interrupt disable bit SI as specials).
For subroutine return values, the special SZ register is also available, it means the zero status bit.


Calling a subroutine
^^^^^^^^^^^^^^^^^^^^

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


Built-in Functions
------------------

The compiler has the following built-in functions that you can use in expressions:

sin(value)
	Sine.

cos(value)
	Cosine.

abs(value)
	Absolute value.

acos(value)
	Arccosine.

asin(value)
	Arcsine.

tan(value)
	Tangent.

atan(value)
	Arctangent.

log(value)
	Natural logarithm.

log10(value)
	Base-10 logarithm.

sqrt(value)
	Square root.

max(value [, value, ...])
	Maximum of the values.

min(value [, value, ...])
	Minumum of the values.

round(value)
	Rounds the floating point to an integer.

rad(value)
	Degrees to radians.

deg(value)
	Radians to degrees.
