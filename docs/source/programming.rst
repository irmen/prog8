.. _programstructure:

====================
Programming in Prog8
====================

This chapter describes a high level overview of the elements that make up a program.
Details about the syntax can be found in the :ref:`syntaxreference` chapter.


Elements of a program
---------------------

Program
	Consists of one or more *modules*.

Module
	A file on disk with the ``.p8`` suffix. It contains *directives* and *code blocks*.
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
	can contain Prog8 *code*, *variable declarations* and *subroutines*.
	More details about this below: :ref:`blocks`.

Variable declarations
	The data that the code works on is stored in variables ('named values that can change').
	The compiler allocates the required memory for them.
	There is *no dynamic memory allocation*. The storage size of all variables
	is fixed and is determined at compile time.
	Variable declarations tend to appear at the top of the code block that uses them.
	They define the name and type of the variable, and its initial value.
	Prog8 supports a small list of data types, including special 'memory mapped' types
	that don't allocate storage but instead point to a fixed location in the address space.

Code
	These are the instructions that make up the program's logic. There are different kinds of instructions
	('statements' is a better name):

	- value assignment
	- looping  (for, while, repeat, unconditional jumps)
	- conditional execution (if - then - else, when, and conditional jumps)
	- subroutine calls
	- label definition

Subroutine
    Defines a piece of code that can be called by its name from different locations in your code.
    It accepts parameters and can return a value (optional).
    It can define its own variables, and it is even possible to define subroutines nested inside other subroutines.
    Their contents is scoped accordingly.

Label
    This is a named position in your code where you can jump to from another place.
    You can jump to it with a jump statement elsewhere. It is also possible to use a
    subroutine call to a label (but without parameters and return value).
    Labels can only be defined in a block or in another subroutine, so you can't define a label
    inside a loop statement block for instance.

Scope
	Also known as 'namespace', this is a named box around the symbols defined in it.
	This prevents name collisions (or 'namespace pollution'), because the name of the scope
	is needed as prefix to be able to access the symbols in it.
	Anything *inside* the scope can refer to symbols in the same scope without using a prefix.
	There are three scopes in Prog8:

	- global (no prefix)
	- code block
	- subroutine

	Modules are *not* a scope! Everything defined in a module is merged into the global scope.


.. _blocks:

Blocks, Scopes, and accessing Symbols
-------------------------------------

**Blocks** are the top level separate pieces of code and data of your program. They are combined
into a single output program.  No code or data can occur outside a block. Here's an example::

	main $c000 {
		; this is code inside the block...
	}


The name of a block must be unique in your entire program.
Also be careful when importing other modules; blocks in your own code cannot have
the same name as a block defined in an imported module or library.

If you omit both the name and address, the entire block is *ignored* by the compiler (and a warning is displayed).
This is a way to quickly "comment out" a piece of code that is unfinshed or may contain errors that you
want to work on later, because the contents of the ignored block are not fully parsed either.

The address can be used to place a block at a specific location in memory.
Usually it is omitted, and the compiler will automatically choose the location (usually immediately after
the previous block in memory).
The address must be >= ``$0200`` (because ``$00``--``$ff`` is the ZP and ``$100``--``$200`` is the cpu stack).


.. _scopes:

**Scopes**

.. sidebar::
    Scoped access to symbols / "dotted names"

    Every symbol is 'public' and can be accessed from elsewhere given its full "dotted name".
    So, accessing a variable ``counter`` defined in subroutine ``worker`` in block ``main``,
    can be done from anywhere by using ``main.worker.counter``.

*Symbols* are names defined in a certain *scope*. Inside the same scope, you can refer
to them by their 'short' name directly.  If the symbol is not found in the same scope,
the enclosing scope is searched for it, and so on, until the symbol is found.

Scopes are created using either of these two statements:

- blocks  (top-level named scope)
- subroutines   (nested named scope)

.. note::
    In contrast to many other programming languages, a new scope is *not* created inside
    for, while and repeat statements, nor for the if statement and branching conditionals.
    This is a bit restrictive because you have to think harder about what variables you
    want to use inside a subroutine. But it is done precisely for this reason; memory in the
    target system is very limited and it would be a waste to allocate a lot of variables.

    Right now the prog8 compiler is not advanced enough to be able to 'share' or 'overlap'
    variables intelligently by itself. So for now, it's something the programmer has to think about.


Program Start and Entry Point
-----------------------------

Your program must have a single entry point where code execution begins.
The compiler expects a ``start`` subroutine in the ``main`` block for this,
taking no parameters and having no return value.

.. sidebar::
    60hz IRQ entry point

    When running the generated code on the StackVm virtual machine,
    it will use the ``irq`` subroutine in the ``irq`` block for the
    60hz irq routine. This is optional.

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





Variables and values
--------------------

Variables are named values that can change during the execution of the program.
They can be defined inside any scope (blocks, subroutines, for loops, etc.) See :ref:`Scopes <scopes>`.
When declaring a numeric variable it is possible to specify the initial value, if you don't want it to be zero.
For other data types it is required to specify that initial value it should get.
Values will usually be part of an expression or assignment statement::

    12345                 ; integer number
    $aa43                 ; hex integer number
    %100101               ; binary integer number (% is also remainder operator so be careful)
    "Hi, I am a string"   ; text string
    'a'                   ; petscii value (byte) for the letter a
    -33.456e52            ; floating point number

    byte  counter  = 42   ; variable of size 8 bits, with initial value 42


*zeropage tag:*
If you add the ``@zp`` tag to the variable declaration, the compiler will prioritize this variable
when selecting variables to put into zero page. If there are enough free locations in the zeropage,
it will then try to fill it with as much other variables as possible (before they will be put in regular memory pages).
Example::

    byte  @zp  zeropageCounter = 42


Variables that represent CPU hardware registers
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following variables are reserved
and map directly (read/write) to a CPU hardware register: ``A``, ``X``, ``Y``.


Integers
^^^^^^^^

Integers are 8 or 16 bit numbers and can be written in normal decimal notation,
in hexadecimal and in binary notation.
A single character in single quotes such as ``'a'`` is translated into a byte integer,
which is the Petscii value for that character.

Unsigned integers are in the range 0-255 for unsigned byte types, and 0-65535 for unsigned word types.
The signed integers integers are in the range -128..127 for bytes,
and -32768..32767 for words.


Floating point numbers
^^^^^^^^^^^^^^^^^^^^^^

Floats are stored in the 5-byte 'MFLPT' format that is used on CBM machines,
and currently all floating point operations are specific to the Commodore-64.
This is because routines in the C-64 BASIC and KERNAL ROMs are used for that.
So floating point operations will only work if the C-64 BASIC ROM (and KERNAL ROM)
are banked in.

Also your code needs to import the ``c64flt`` library to enable floating point support
in the compiler, and to gain access to the floating point routines.
(this library contains the directive to enable floating points, you don't have
to worry about this yourself)

The largest 5-byte MFLPT float that can be stored is: **1.7014118345e+38**   (negative: **-1.7014118345e+38**)


Arrays
^^^^^^
Array types are also supported. They can be made of bytes, words or floats::

    byte[10]  array                   ; array of 10 bytes, initially set to 0
    byte[]  array = [1, 2, 3, 4]      ; initialize the array, size taken from value
    byte[99] array = 255              ; initialize array with 99 times 255 [255, 255, 255, 255, ...]
    byte[] array = 100 to 199         ; initialize array with [100, 101, ..., 198, 199]

    value = array[3]            ; the fourth value in the array (index is 0-based)
    char = string[4]            ; the fifth character (=byte) in the string

.. note::
    Right now, the array should be small enough to be indexable by a single byte index.
    This means byte arrays should be <= 256 elements, word arrays <= 128 elements, and float
    arrays <= 51 elements.

You can split an array initializer list over several lines if you want.

Note that the various keywords for the data type and variable type (``byte``, ``word``, ``const``, etc.)
can't be used as *identifiers* elsewhere. You can't make a variable, block or subroutine with the name ``byte``
for instance.


Strings
^^^^^^^

Strings are a sequence of characters enclosed in ``"`` quotes. The length is limited to 255 characters.
They're stored and treated much the same as a byte array,
but they have some special properties because they are considered to be *text*.
Strings in your source code files will be encoded (translated from ASCII/UTF-8) into either CBM PETSCII or C-64 screencodes.
PETSCII is the default choice. If you need screencodes (also called 'poke' codes) instead,
you have to use the ``str_s`` variants of the string type identifier.

You can concatenate two string literals using '+' (not very useful though) or repeat
a string literal a given number of times using '*'::

    str string1 = "first part" + "second part"
    str string2 = "hello!" * 10


.. caution::
    It's probably best that you don't change strings after they're created.
    This is because if your program exits and is restarted (without loading it again),
    it will then operate on the changed strings instead of the original ones.
    The same is true for arrays by the way.


Structs
^^^^^^^

A struct is a group of one or more other variables.
This allows you to reuse the definition and manipulate it as a whole.
Individual variables in the struct are accessed as you would expect, just
use a scoped name to refer to them: ``structvariable.membername``.

Structs are a bit limited in Prog8: you can only use numerical variables
as member of a struct, so strings and arrays and other structs can not be part of a struct.
Also, it is not possible to use a struct itself inside an array.
Structs are mainly syntactic sugar for repeated groups of vardecls
and assignments that belong together. However,
*they are layed out in sequence in memory as the members are defined*
which may be usefulif you want to pass pointers around.

To create a variable of a struct type you need to define the struct itself,
and then create a variable with it::

    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

    Color rgb = {255,122,0}     ; note the curly braces here instead of brackets
    Color another               ; the init value is optional, like arrays

    another = rgb           ; assign all of the values of rgb to another
    another.blue = 255      ; set a single member



Special types: const and memory-mapped
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When using ``const``, the value of the 'variable' can no longer be changed.
You'll have to specify the initial value expression. This value is then used
by the compiler everywhere you refer to the constant (and no storage is allocated
for the constant itself). This is only valid for the simple numeric types (byte, word, float).

When using ``&`` (the address-of operator but now applied to a datatype), the variable will point to specific location in memory,
rather than being newly allocated. The initial value (mandatory) must be a valid
memory address.  Reading the variable will read the given data type from the
address you specified, and setting the varible will directly modify that memory location(s)::

	const  byte  max_age = 2000 - 1974      ; max_age will be the constant value 26
	&word  SCREENCOLORS = $d020             ; a 16-bit word at the addres $d020-$d021


.. note::
    Directly accessing random memory locations is not yet supported without the
    intermediate step of declaring a memory-mapped variable for the memory location.
    The advantages of this however, is that it's clearer what the memory location
    stands for, and the compiler also knows the data type.


Converting types into other types
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Sometimes you need an unsigned word where you have an unsigned byte, or you need some other type conversion.
Many type conversions are possible by just writing ``as <type>`` at the end of an expression::

    uword  uw = $ea31
    ubyte  ub = uw as ubyte     ; ub will be $31, identical to lsb(uw)
    float  f = uw as float      ; f will be 59953, but this conversion can be omitted in this case
    word   w = uw as word       ; w will be -5583 (simply reinterpret $ea31 as 2-complement negative number)
    f = 56.777
    ub = f as ubyte             ; ub will be 56

Sometimes it is a straight 'type cast' where the value is simply interpreted as being of the other type,
sometimes an actual value conversion is done to convert it into the targe type.
Try to avoid type conversions as much as possible.


Initial values across multiple runs of the program
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When declaring values with an initial value, this value will be set into the variable each time
the program reaches the declaration again. This can be in loops, multiple subroutine calls,
or even multiple invocations of the entire program.  If you omit an initial value, it will
be set to zero *but only for the first run of the program*. A second run will utilize the last value
where it left off (but your code will be a bit smaller because no initialization instructions
are generated)

This only works for simple types, *and not for string variables and arrays*.
It is assumed these are left unchanged by the program; they are not re-initialized on
a second run.
If you do modify them in-place, you should take care yourself that they work as
expected when the program is restarted.
(This is an optimization choice to avoid having to store two copies of every string and array)

.. caution::
   variables that get allocated in zero-page will *not* have a zero starting value when you omit
   the variable's initialization. They'll be whatever the last value in that zero page
   location was. So it's best to don't depend on the uninitialized starting value!

.. warning::
    this behavior may change in a future version so that subsequent runs always
    use the same initial values


Loops
-----

The *for*-loop is used to let a variable (or register) iterate over a range of values. Iteration is done in steps of 1, but you can change this.
The loop variable can be declared as byte or word earlier so you can reuse it for multiple occasions,
or you can declare one directly in the for statement which will only be visible in the for loop body.
Iterating with a floating point variable is not supported. If you want to loop over a floating-point array, use a loop with an integer index variable instead.

The *while*-loop is used to repeat a piece of code while a certain condition is still true.
The *repeat--until* loop is used to repeat a piece of code until a certain condition is true.

You can also create loops by using the ``goto`` statement, but this should usually be avoided.

.. attention::
    The value of the loop variable or register after executing the loop *is undefined*. Don't use it immediately
    after the loop without first assigning a new value to it!
    (this is an optimization issue to avoid having to deal with mostly useless post-loop logic to adjust the loop variable's value)
    Loop variables that are declared inline are not different to them being
    defined in a separate var declaration in the subroutine, it's just a readability convenience.
    (this may change in the future if the compiler gets more advanced with additional sub-scopes)


Conditional Execution
---------------------

if statements
^^^^^^^^^^^^^

Conditional execution means that the flow of execution changes based on certiain conditions,
rather than having fixed gotos or subroutine calls::

	if A>4 goto overflow

	if X==3  Y = 4
	if X==3  Y = 4 else  A = 2

	if X==5 {
		Y = 99
	} else {
		A = 3
	}


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

.. note::
    For now, the symbols used or declared in the statement block(s) are shared with
    the same scope the if statement itself is in.
    Maybe in the future this will be a separate nested scope, but for now, that is
    only possible when defining a subroutine.

when statement ('jump table')
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Instead of writing a bunch of sequential if-elseif statements, it is more readable to
use a ``when`` statement. (It will also result in greatly improved assembly code generation)
Use a ``when`` statement if you have a set of fixed choices that each should result in a certain
action. It is possible to combine several choices to result in the same action::

    when value {
        4 -> c64scr.print("four")
        5 -> c64scr.print("five")
        10,20,30 -> {
            c64scr.print("ten or twenty or thirty")
        }
        else -> c64scr.print("don't know")
    }

The when-*value* can be any expression but the choice values have to evaluate to
compile-time constant integers (bytes or words). They also have to be the same
datatype as the when-value, otherwise no efficient comparison can be done.


Assignments
-----------

Assignment statements assign a single value to a target variable or memory location.
Augmented assignments (such as ``A += X``) are also available, but these are just shorthands
for normal assignments (``A = A + X``).

Only register variables and variables of type byte, word and float can be assigned a new value.
It's not possible to set a new value to string or array variables etc, because they get allocated
a fixed amount of memory which will not change.

.. attention::
    **Data type conversion (in assignments):**
    When assigning a value with a 'smaller' datatype to a register or variable with a 'larger' datatype,
    the value will be automatically converted to the target datatype:  byte --> word --> float.
    So assigning a byte to a word variable, or a word to a floating point variable, is fine.
    The reverse is *not* true: it is *not* possible to assign a value of a 'larger' datatype to
    a variable of a smaller datatype without an explicit conversion. Otherwise you'll get an error telling you
    that there is a loss of precision. You can use builtin functions such as ``round`` and ``lsb`` to convert
    to a smaller datatype, or revert to integer arithmetic.

Direct access to memory locations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Normally memory locations are accessed by a *memory mapped* name, such as ``c64.BGCOL0`` that is defined
as the memory mapped address $d021.

If you want to access a memory location directly (by using the address itself), without defining
a memory mapped location, you can do so by enclosing the address in ``@(...)``::

    A = @($d020)      ; set the A register to the current c64 screen border color ("peek(53280)")
    @($d020) = 0      ; set the c64 screen border to black ("poke 53280,0")
    @(vic+$20) = 6    ; you can also use expressions to 'calculate' the address


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
There are various built-in functions such as sin(), cos(), min(), max() that can be used in expressions (see :ref:`builtinfunctions`).
You can also reference idendifiers defined elsewhere in your code.

.. attention::
    **Floating points used in expressions:**

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
(which in reality are just a 1 or 0 integer value).

You can use parentheses to group parts of an expresion to change the precedence.
Usually the normal precedence rules apply (``*`` goes before ``+`` etc.) but subexpressions
within parentheses will be evaluated first. So ``(4 + 8) * 2`` is 24 and not 20,
and ``(true or false) and false`` is false instead of true.

.. attention::
    **calculations keep their datatype:**
    When you do calculations on a BYTE type, the result will remain a BYTE.
    When you do calculations on a WORD type, the result will remain a WORD.
    For instance::

        byte b = 44
        word w = b*55   ; the result will be 116! (even though the target variable is a word)
        w *= 999        ; the result will be -15188  (the multiplication stays within a word)

    The compiler will NOT give a warning about this! It's doing this for
    performance reasons - so you won't get sudden 16 bit (or even float)
    calculations where you needed only simple fast byte arithmetic.
    If you do need the extended resulting value, cast at least one of the
    operands of an operator to the larger datatype. For example::

        byte b = 44
        word w = b*55.w     ; the result will be 2420
        w = (b as word)*55  ; same result




Subroutines
-----------

Defining a subroutine
^^^^^^^^^^^^^^^^^^^^^

Subroutines are parts of the code that can be repeatedly invoked using a subroutine call from elsewhere.
Their definition, using the ``sub`` statement, includes the specification of the required parameters and return value.
Subroutines can be defined in a Block, but also nested inside another subroutine. Everything is scoped accordingly.

Calling a subroutine
^^^^^^^^^^^^^^^^^^^^

The arguments in parentheses after the function name, should match the parameters in the subroutine definition.
It is possible to not store the return value but the compiler
will issue a warning then telling you the result values of a subroutine call are discarded.

.. caution::
    Note that due to the way parameters are processed by the compiler,
    subroutines are *non-reentrant*. This means you cannot create recursive calls.
    If you do need a recursive algorithm, you'll have to hand code it in embedded assembly for now,
    or rewrite it into an iterative algorithm.
    Also, subroutines used in the main program should not be used from an IRQ handler. This is because
    the subroutine may be interrupted, and will then call itself from the IRQ handler. Results are
    then undefined because the variables will get overwritten.


.. _builtinfunctions:

Built-in Functions
------------------


There's a set of predefined functions in the language. These are fixed and can't be redefined in user code.
You can use them in expressions and the compiler will evaluate them at compile-time if possible.


sin(x)
	Sine.  (floating point version)

cos(x)
	Cosine.  (floating point version)

sin8u(x)
    Fast 8-bit ubyte sine of angle 0..255, result is in range 0..255

sin8(x)
    Fast 8-bit byte sine of angle 0..255, result is in range -127..127

sin16u(x)
    Fast 16-bit uword sine of angle 0..255, result is in range 0..65535

sin16(x)
    Fast 16-bit word sine of angle 0..255, result is in range -32767..32767

cos8u(x)
    Fast 8-bit ubyte cosine of angle 0..255, result is in range 0..255

cos8(x)
    Fast 8-bit byte cosine of angle 0..255, result is in range -127..127

cos16u(x)
    Fast 16-bit uword cosine of angle 0..255, result is in range 0..65535

cos16(x)
    Fast 16-bit word cosine of angle 0..255, result is in range -32767..32767

abs(x)
	Absolute value.

tan(x)
	Tangent.

atan(x)
	Arctangent.

ln(x)
	Natural logarithm (base e).

log2(x)
    Base 2 logarithm.

sqrt16(w)
	16 bit unsigned integer Square root. Result is unsigned byte.

sqrt(x)
	Floating point Square root.

round(x)
	Rounds the floating point to the closest integer.

floor (x)
	Rounds the floating point down to an integer towards minus infinity.

ceil(x)
	Rounds the floating point up to an integer towards positive infinity.

rad(x)
	Degrees to radians.

deg(x)
	Radians to degrees.

max(x)
	Maximum of the values in the array value x

min(x)
	Minimum of the values in the array value x

avg(x)
	Average of the values in the array value x

sum(x)
	Sum of the values in the array value x

len(x)
    Number of values in the array value x, or the number of characters in a string (excluding the size or 0-byte).
    Note: this can be different from the number of *bytes* in memory if the datatype isn't a byte.
    Note: lengths of strings and arrays are determined at compile-time! If your program modifies the actual
    length of the string during execution, the value of len(string) may no longer be correct!
    (use strlen function if you want to dynamically determine the length)

strlen(str)
    Number of bytes in the string. This value is determined during runtime and counts upto
    the first terminating 0 byte in the string, regardless of the size of the string during compilation time.

lsb(x)
    Get the least significant byte of the word x. Equivalent to the cast "x as ubyte".

msb(x)
    Get the most significant byte of the word x.

mkword(lsb, msb)
    Efficiently create a word value from two bytes (the lsb and the msb). Avoids multiplication and shifting.

any(x)
    1 ('true') if any of the values in the array value x is 'true' (not zero), else 0 ('false')

all(x)
	1 ('true') if all of the values in the array value x are 'true' (not zero), else 0 ('false')

rnd()
    returns a pseudo-random byte from 0..255

rndw()
    returns a pseudo-random word from 0..65535

rndf()
    returns a pseudo-random float between 0.0 and 1.0

lsl(x)
    Shift the bits in x (byte or word) one position to the left.
    Bit 0 is set to 0 (and the highest bit is shifted into the status register's Carry flag)
    Modifies in-place, doesn't return a value (so can't be used in an expression).

lsr(x)
    Shift the bits in x (byte or word) one position to the right.
    The highest bit is set to 0 (and bit 0 is shifted into the status register's Carry flag)
    Modifies in-place, doesn't return a value (so can't be used in an expression).

rol(x)
    Rotate the bits in x (byte or word) one position to the left.
    This uses the CPU's rotate semantics: bit 0 will be set to the current value of the Carry flag,
    while the highest bit will become the new Carry flag value.
    (essentially, it is a 9-bit or 17-bit rotation)
    Modifies in-place, doesn't return a value (so can't be used in an expression).

rol2(x)
    Like _rol but now as 8-bit or 16-bit rotation.
    It uses some extra logic to not consider the carry flag as extra rotation bit.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

ror(x)
    Rotate the bits in x (byte or word) one position to the right.
    This uses the CPU's rotate semantics: the highest bit will be set to the current value of the Carry flag,
    while bit 0 will become the new Carry flag value.
    (essentially, it is a 9-bit or 17-bit rotation)
    Modifies in-place, doesn't return a value (so can't be used in an expression).

ror2(x)
    Like _ror but now as 8-bit or 16-bit rotation.
    It uses some extra logic to not consider the carry flag as extra rotation bit.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

memcopy(from, to, numbytes)
    Efficiently copy a number of bytes (1 - 256) from a memory location to another.
    NOTE: 'to' must NOT overlap with 'from', unless it is *before* 'from'.
    Because this function imposes some overhead to handle the parameters,
    it is only faster if the number of bytes is larger than a certain threshold.
    Compare the generated code to see if it was beneficial or not.
    The most efficient will always be to write a specialized copy routine in assembly yourself!

memset(address, numbytes, bytevalue)
    Efficiently set a part of memory to the given (u)byte value.
    But the most efficient will always be to write a specialized fill routine in assembly yourself!
    Note that for clearing the character screen, very fast specialized subroutines are
    available in the ``c64scr`` block (part of the ``c64utils`` module)

memsetw(address, numwords, wordvalue)
    Efficiently set a part of memory to the given (u)word value.
    But the most efficient will always be to write a specialized fill routine in assembly yourself!

swap(x, y)
    Swap the values of numerical variables (or memory locations) x and y in a fast way.

set_carry()  /  clear_carry()
    Set (or clear) the CPU status register Carry flag. No result value.
    (translated into ``SEC`` or ``CLC`` cpu instruction)

set_irqd()  /  clear_irqd()
    Set (or clear) the CPU status register Interrupt Disable flag. No result value.
    (translated into ``SEI`` or ``CLI`` cpu instruction)

rsave()
    Saves the CPU registers and the status flags.
    You can now more or less 'safely' use the registers directly, until you
    restore them again so the generated code can carry on normally.
    Note: it's not needed to rsave() before an asm subroutine that clobbers the X register
    (which is used as the internal evaluation stack pointer).
    The compiler will take care of this situation automatically.

rrestore()
    Restores the CPU registers and the status flags from previously saved values.

read_flags()
    Returns the current value of the CPU status register.



Library routines
----------------

There are many routines available in the compiler libraries.
Some are used internally by the compiler as well.
There's too many to list here, just have a look through the source code
of the library modules to see what's there.
(They can be found in the compiler/res directory)
The example programs also use a small set of the library routines, you can study
their source code to see how they might be used.
