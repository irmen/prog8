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
    A file on disk with the ``.p8`` suffix. It can contain *directives* and *code blocks*.
    Whitespace and indentation in the source code are arbitrary and can be mixed tabs or spaces.
    A module file can *import* other modules, including *library modules*.

Comments
    Everything after a semicolon ``;`` is a comment and is ignored by the compiler.
    If the whole line is just a comment, this line will be copied into the resulting assembly source code for reference.

Directive
    These are special instructions for the compiler, to change how it processes the code
    and what kind of program it creates. A directive is on its own line in the file, and
    starts with ``%``, optionally followed by some arguments.

Code block
    A block of actual program code. It has a starting address in memory,
    and defines a *scope* (also known as 'namespace').
    It contains variables and subroutines.
    More details about this below: :ref:`blocks`.

Variable declarations
    The data that the code works on is stored in variables ('named values that can change').
    The compiler allocates the required memory for them.
    There is *no dynamic memory allocation*. The storage size of all variables
    is fixed and is determined at compile time.
    Variable declarations tend to appear at the top of the code block that uses them, but this is not mandatory.
    They define the name and type of the variable, and its initial value.
    Prog8 supports a small list of data types, including special 'memory mapped' types
    that don't allocate storage but instead point to a fixed location in the address space.

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
    It can define its own variables, and it is even possible to define subroutines nested inside other subroutines.
    Their contents is scoped accordingly.
    Nested subroutines can access the variables from outer scopes.
    This removes the need and overhead to pass everything via parameters.
    Subroutines do not have to be declared before they can be called.

Label
    This is a named position in your code where you can jump to from another place.
    You can jump to it with a jump statement elsewhere. It is also possible to use a
    subroutine call to a label (but without parameters and return value).

Scope
    Also known as 'namespace', this is a named box around the symbols defined in it.
    This prevents name collisions (or 'namespace pollution'), because the name of the scope
    is needed as prefix to be able to access the symbols in it.
    Anything *inside* the scope can refer to symbols in the same scope without using a prefix.
    There are three scope levels in Prog8:

    - global (no prefix)
    - code block
    - subroutine

    While Modules are separate files, they are *not* separate scopes!
    Everything defined in a module is merged into the global scope.
    This is different from most other languages that have modules.
    The global scope can only contain blocks and some directives, while the others can contain variables and subroutines too.


.. _blocks:

Blocks, Scopes, and accessing Symbols
-------------------------------------

**Blocks** are the top level separate pieces of code and data of your program. They have a
starting address in memory and will be combined together into a single output program.
They can only contain *directives*, *variable declarations*, *subroutines* and *inline assembly code*.
Your actual program code can only exist inside these subroutines.
(except the occasional inline assembly)

Here's an example::

    main $c000 {
        ; this is code inside the block...
    }

The name of a block must be unique in your entire program.
Be careful when importing other modules; blocks in your own code cannot have
the same name as a block defined in an imported module or library.

If you omit both the name and address, the entire block is *ignored* by the compiler (and a warning is displayed).
This is a way to quickly "comment out" a piece of code that is unfinshed or may contain errors that you
want to work on later, because the contents of the ignored block are not fully parsed either.

The address can be used to place a block at a specific location in memory.
Usually it is omitted, and the compiler will automatically choose the location (usually immediately after
the previous block in memory).
It must be >= ``$0200`` (because ``$00``--``$ff`` is the ZP and ``$100``--``$1ff`` is the cpu stack).


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

.. important::
    Unlike most other programming languages, a new scope is *not* created inside
    for, while, repeat, and do-until statements, the if statement, and the branching conditionals.
    These all share the same scope from the subroutine they're defined in.
    You can define variables in these blocks, but these will be treated as if they
    were defined in the subroutine instead.
    This can seem a bit restrictive because you have to think harder about what variables you
    want to use inside the subroutine, to avoid clashes.
    But this decision was made for a good reason: memory in prog8's
    target systems is usually very limited and it would be a waste to allocate a lot of variables.
    The prog8 compiler is not yet advanced enough to be able to share or overlap
    variables intelligently. So for now that is something you have to think about yourself.


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




Variables and values
--------------------

Variables are named values that can change during the execution of the program.
They can be defined inside any scope (blocks, subroutines etc.) See :ref:`Scopes <scopes>`.
When declaring a numeric variable it is possible to specify the initial value, if you don't want it to be zero.
For other data types it is required to specify that initial value it should get.
Values will usually be part of an expression or assignment statement::

    12345                 ; integer number
    $aa43                 ; hex integer number
    %100101               ; binary integer number (% is also remainder operator so be careful)
    -33.456e52            ; floating point number
    "Hi, I am a string"   ; text string, encoded with compiler target default encoding
    'a'                   ; byte value (ubyte) for the letter a
    @"Alternate"          ; text string, encoded with alternate encoding
    @'a'                  ; byte value of the letter a, using alternate encoding

    byte  counter  = 42   ; variable of size 8 bits, with initial value 42


*zeropage tag:*
If you add the ``@zp`` tag to the variable declaration, the compiler will prioritize this variable
when selecting variables to put into zero page. If there are enough free locations in the zeropage,
it will then try to fill it with as much other variables as possible (before they will be put in regular memory pages).
Example::

    byte  @zp  zeropageCounter = 42


*shared tag:*
If you add the ``@shared`` tag to the variable declaration, the compiler will know that this variable
is a prog8 variable shared with some assembly code elsewhere. This means that the assembly code can
refer to the variable even if it's otherwise not used in prog8 code itself.
(usually, these kinds of 'unused' variables are optimized away by the compiler, resulting in an error
when assembling the rest of the code). Example::

    byte  @shared  assemblyVariable = 42


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

Also your code needs to import the ``floats`` library to enable floating point support
in the compiler, and to gain access to the floating point routines.
(this library contains the directive to enable floating points, you don't have
to worry about this yourself)

The largest 5-byte MFLPT float that can be stored is: **1.7014118345e+38**   (negative: **-1.7014118345e+38**)

.. note::
    On the Commander X16, to use floating point operations, ROM bank 4 has to be enabled (BASIC).
    Importing the ``floats`` library will do this for you if needed.


Arrays
^^^^^^
Array types are also supported. They can be made of bytes, words or floats, strings, and other arrays
(although the usefulness of the latter is very limited for now)::

    byte[10]  array                   ; array of 10 bytes, initially set to 0
    byte[]  array = [1, 2, 3, 4]      ; initialize the array, size taken from value
    byte[99] array = 255              ; initialize array with 99 times 255 [255, 255, 255, 255, ...]
    byte[] array = 100 to 199         ; initialize array with [100, 101, ..., 198, 199]
    str[] names = ["ally", "pete"]    ; array of string pointers/addresses (equivalent to uword)
    uword[] others = [names, array]   ; array of pointers/addresses to other arrays

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


It's possible to assign a new array to another array, this will overwrite all elements in the original
array with those in the value array. The number and types of elements have to match.
For large arrays this is a slow operation because every element is copied over. It should probably be avoided.


**Arrays at a specific memory location:**
Using the memory-mapped syntax it is possible to define an array to be located at a specific memory location.
For instance to reference the first 5 rows of the Commodore 64's screen matrix as an array, you can define::

    &ubyte[5*40]  top5screenrows = $0400

This way you can set the second character on the second row from the top like this::

    top5screenrows[41] = '!'


Strings
^^^^^^^

Strings are a sequence of characters enclosed in ``"`` quotes. The length is limited to 255 characters.
They're stored and treated much the same as a byte array,
but they have some special properties because they are considered to be *text*.
Strings in your source code files will be encoded (translated from ASCII/UTF-8) into bytes via the
default encoding that is used on the target platform. For the C-64, this is CBM PETSCII.
Alternate-encoding strings (prefixed with ``@``) will be encoded via the alternate encoding for the
platform (if defined). For the C-64, that is SCREEN CODES (also known as POKE codes).
This @-prefix can also be used for character byte values.


You can concatenate two string literals using '+' (not very useful though) or repeat
a string literal a given number of times using '*'. You can also assign a new string
value to another string. No bounds check is done so be sure the destination string is
large enough to contain the new value (it is overwritten in memory)::

    str string1 = "first part" + "second part"
    str string2 = "hello!" * 10

    string1 = string2
    string1 = "new value"


There are several 'escape sequences' to help you put special characters into strings, such
as newlines, quote characters themselves, and so on. The ones used most often are
``\\``, ``\"``, ``\n``, ``\r``.  For a detailed description of all of them and what they mean,
read the syntax reference on strings.


.. hint::
    Strings/arrays and uwords (=memory address) can often be interchanged.
    An array of strings is actually an array of uwords where every element is the memory
    address of the string. You can pass a memory address to assembly functions
    that require a string as an argument.
    For regular assignments you still need to use an explicit ``&`` (address-of) to take
    the address of the string or array.

.. note:: Strings and their (im)mutability

    *String literals outside of a string variable's initialization value*,
    are considered to be "constant", i.e. the string isn't going to change
    during the execution of the program. The compiler takes advantage of this in certain
    ways. For instance, multiple identical occurrences of a string literal are folded into
    just one string allocation in memory. Examples of such strings are the string literals
    passed to a subroutine as arguments.

    *Strings that aren't such string literals are considered to be unique*, even if they
    are the same as a string defined elsewhere. This includes the strings assigned to
    a string variable in its declaration! These kind of strings are not deduplicated and
    are just copied into the program in their own unique part of memory. This means that
    it is okay to treat those strings as mutable; you can safely change the contents
    of such a string without destroying other occurrences (as long as you stay within
    the size of the allocated string!)


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


Direct access to memory locations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Normally memory locations are accessed by a *memory mapped* name, such as ``c64.BGCOL0`` that is defined
as the memory mapped address $d021.

If you want to access a memory location directly (by using the address itself or via an uword pointer variable),
without defining a memory mapped location, you can do so by enclosing the address in ``@(...)``::

    color = @($d020)  ; set the variable 'color' to the current c64 screen border color ("peek(53280)")
    @($d020) = 0      ; set the c64 screen border to black ("poke 53280,0")
    @(vic+$20) = 6    ; you can also use expressions to 'calculate' the address

This is the official syntax to 'dereference a pointer' as it is often named in other languages.
You can actually also use the array indexing notation for this. It will be silently converted into
the direct memory access expression as explained above. Note that this also means that unlike regular arrays,
the index is not limited to an ubyte value. You can use a full uword to index a pointer variable like this::

    pointervar[999] = 0     ; set memory byte to zero at location pointervar + 999.


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
or even multiple invocations of the entire program.
If you omit the initial value, zero will be used instead.

This only works for simple types, *and not for string variables and arrays*.
It is assumed these are left unchanged by the program; they are not re-initialized on
a second run.
If you do modify them in-place, you should take care yourself that they work as
expected when the program is restarted.
(This is an optimization choice to avoid having to store two copies of every string and array)


Loops
-----

The *for*-loop is used to let a variable iterate over a range of values. Iteration is done in steps of 1, but you can change this.
The loop variable must be declared as byte or word earlier so you can reuse it for multiple occasions.
Iterating with a floating point variable is not supported. If you want to loop over a floating-point array, use a loop with an integer index variable instead.

The *while*-loop is used to repeat a piece of code while a certain condition is still true.
The *do--until* loop is used to repeat a piece of code until a certain condition is true.
The *repeat* loop is used as a short notation of a for loop where the loop variable doesn't matter and you're only interested in the number of iterations.
(without iteration count specified it simply loops forever).

You can also create loops by using the ``goto`` statement, but this should usually be avoided.

Breaking out of a loop prematurely is possible with the ``break`` statement.

.. attention::
    The value of the loop variable after executing the loop *is undefined*. Don't use it immediately
    after the loop without first assigning a new value to it!
    (this is an optimization issue to avoid having to deal with mostly useless post-loop logic to adjust the loop variable's value)

.. warning::
    For efficiency reasons, it is assumed that the ending value of the for loop is actually >= the starting value
    (or <= if the step is negative).  This means that for loops in prog8 behave differently than in other
    languages if this is *not* the case! A for loop from ubyte 10 to ubyte 2, for example, will iterate through
    all values 10, 11, 12, 13, .... 254, 255, 0  (wrapped), 1, 2. In other languages the entire loop will
    be skipped in such cases. But prog8 omits the overhead of an extra loop range check and/or branch for every for loop
    by assuming the normal ranges.


Conditional Execution
---------------------

if statements
^^^^^^^^^^^^^

Conditional execution means that the flow of execution changes based on certiain conditions,
rather than having fixed gotos or subroutine calls::

	if aa>4 goto overflow

	if xx==3  yy = 4
	if xx==3  yy = 4 else  aa = 2

	if xx==5 {
		yy = 99
	} else {
		aa = 3
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

.. caution::
    These special ``if_XX`` branching statements are only useful in certain specific situations where you are *certain*
    that the status register (still) contains the correct status bits.
    This is not always the case after a fuction call or other operations!
    If in doubt, check the generated assembly code!

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
        4 -> txt.print("four")
        5 -> txt.print("five")
        10,20,30 -> {
            txt.print("ten or twenty or thirty")
        }
        else -> txt.print("don't know")
    }

The when-*value* can be any expression but the choice values have to evaluate to
compile-time constant integers (bytes or words). They also have to be the same
datatype as the when-value, otherwise no efficient comparison can be done.


Assignments
-----------

Assignment statements assign a single value to a target variable or memory location.
Augmented assignments (such as ``aa += xx``) are also available, but these are just shorthands
for normal assignments (``aa = aa + xx``).

Only variables of type byte, word and float can be assigned a new value.
It's not possible to set a new value to string or array variables etc, because they get allocated
a fixed amount of memory which will not change.  (You *can* change the value of elements in a string or array though).

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



Subroutines
-----------

Defining a subroutine
^^^^^^^^^^^^^^^^^^^^^

Subroutines are parts of the code that can be repeatedly invoked using a subroutine call from elsewhere.
Their definition, using the ``sub`` statement, includes the specification of the required parameters and return value.
Subroutines can be defined in a Block, but also nested inside another subroutine. Everything is scoped accordingly.
With ``asmsub`` you can define a low-level subroutine that is implemented in inline assembly and takes any parameters
in registers directly.

Trivial ``asmsub`` routines can be tagged as ``inline`` to tell the compiler to copy their code
in-place to the locations where the subroutine is called, rather than inserting an actual call and return to the
subroutine. This may increase code size significantly and can only be used in limited scenarios, so YMMV.
Note that the routine's code is copied verbatim into the place of the subroutine call in this case,
so pay attention to any jumps and rts instructions in the inlined code!

At this time it is not yet possible to inline regular Prog8 subroutines, this may be added in the future.


Calling a subroutine
^^^^^^^^^^^^^^^^^^^^

The arguments in parentheses after the function name, should match the parameters in the subroutine definition.
If you want to ignore a return value of a subroutine, you should prefix the call with the ``void`` keyword.
Otherwise the compiler will issue a warning about discarding a result value.

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


Math
^^^^

abs(x)
    Absolute value.

atan(x)
    Arctangent.

ceil(x)
    Rounds the floating point up to an integer towards positive infinity.

cos(x)
    Cosine.  (floating point version)

cos8u(x)
    Fast 8-bit ubyte cosine of angle 0..255, result is in range 0..255

cos8(x)
    Fast 8-bit byte cosine of angle 0..255, result is in range -127..127

cos16u(x)
    Fast 16-bit uword cosine of angle 0..255, result is in range 0..65535

cos16(x)
   Fast 16-bit word cosine of angle 0..255, result is in range -32767..32767

deg(x)
    Radians to degrees.

floor (x)
    Rounds the floating point down to an integer towards minus infinity.

ln(x)
    Natural logarithm (base e).

log2(x)
    Base 2 logarithm.

rad(x)
    Degrees to radians.

round(x)
    Rounds the floating point to the closest integer.

sin(x)
    Sine.  (floating point version)

sgn(x)
    Get the sign of the value. Result is -1, 0 or 1 (negative, zero, positive).

sin8u(x)
    Fast 8-bit ubyte sine of angle 0..255, result is in range 0..255

sin8(x)
    Fast 8-bit byte sine of angle 0..255, result is in range -127..127

sin16u(x)
    Fast 16-bit uword sine of angle 0..255, result is in range 0..65535

sin16(x)
    Fast 16-bit word sine of angle 0..255, result is in range -32767..32767

sqrt16(w)
    16 bit unsigned integer Square root. Result is unsigned byte.
    To do the reverse, squaring an integer, just write ``x*x``.

sqrt(x)
    Floating point Square root.
    To do the reverse, squaring a floating point number, just write ``x*x`` or ``x**2``.

tan(x)
    Tangent.


Array operations
^^^^^^^^^^^^^^^^

any(x)
    1 ('true') if any of the values in the array value x is 'true' (not zero), else 0 ('false')

all(x)
    1 ('true') if all of the values in the array value x are 'true' (not zero), else 0 ('false')

len(x)
    Number of values in the array value x, or the number of characters in a string (excluding the 0-byte).
    Note: this can be different from the number of *bytes* in memory if the datatype isn't a byte. See sizeof().
    Note: lengths of strings and arrays are determined at compile-time! If your program modifies the actual
    length of the string during execution, the value of len(s) may no longer be correct!
    (use the ``string.length`` routine if you want to dynamically determine the length by counting to the
    first 0-byte)

max(x)
    Maximum of the values in the array value x

min(x)
    Minimum of the values in the array value x

reverse(array)
    Reverse the values in the array (in-place).
    Can be used after sort() to sort an array in descending order.

sum(x)
    Sum of the values in the array value x

sort(array)
    Sort the array in ascending order (in-place)
    Supported are arrays of bytes or word values.
    Sorting a floating-point array is not supported right now, as a general sorting routine for this will
    be extremely slow. Either build one yourself or find another solution that doesn't require sorting.
    Finally, note that sorting an array with strings in it will not do what you might think;
    it considers the array as just an array of integer words and sorts the string *pointers* accordingly.
    Sorting strings alphabetically has to be programmed yourself if you need it.


Miscellaneous
^^^^^^^^^^^^^

cmp(x,y)
    Compare the integer value x to integer value y. Doesn't return a value or boolean result, only sets the processor's status bits!
    You can use a conditional jumps (``if_cc`` etcetera) to act on this.
    Normally you should just use a comparison expression (``x < y``)

lsb(x)
    Get the least significant byte of the word x. Equivalent to the cast "x as ubyte".

msb(x)
    Get the most significant byte of the word x.

mkword(msb, lsb)
    Efficiently create a word value from two bytes (the msb and the lsb). Avoids multiplication and shifting.
    So mkword($80, $22) results in $8022.

    .. note::
        The arguments to the mkword() function are in 'natural' order that is first the msb then the lsb.
        Don't get confused by how the system actually stores this 16-bit word value in memory (which is
        in little-endian format, so lsb first then msb)

peek(address)
    same as @(address) - reads the byte at the given address in memory.

peekw(address)
    reads the word value at the given address in memory. Word is read as usual little-endian lsb/msb byte order.

poke(address, value)
    same as @(address)=value - writes the byte value at the given address in memory.

pokew(address, value)
    writes the word value at the given address in memory, in usual little-endian lsb/msb byte order.

rnd()
    returns a pseudo-random byte from 0..255

rndw()
    returns a pseudo-random word from 0..65535

rndf()
    returns a pseudo-random float between 0.0 and 1.0

rol(x)
    Rotate the bits in x (byte or word) one position to the left.
    This uses the CPU's rotate semantics: bit 0 will be set to the current value of the Carry flag,
    while the highest bit will become the new Carry flag value.
    (essentially, it is a 9-bit or 17-bit rotation)
    Modifies in-place, doesn't return a value (so can't be used in an expression).

rol2(x)
    Like ``rol`` but now as 8-bit or 16-bit rotation.
    It uses some extra logic to not consider the carry flag as extra rotation bit.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

ror(x)
    Rotate the bits in x (byte or word) one position to the right.
    This uses the CPU's rotate semantics: the highest bit will be set to the current value of the Carry flag,
    while bit 0 will become the new Carry flag value.
    (essentially, it is a 9-bit or 17-bit rotation)
    Modifies in-place, doesn't return a value (so can't be used in an expression).

ror2(x)
    Like ``ror`` but now as 8-bit or 16-bit rotation.
    It uses some extra logic to not consider the carry flag as extra rotation bit.
    Modifies in-place, doesn't return a value (so can't be used in an expression).

sizeof(name)
    Number of bytes that the object 'name' occupies in memory. This is a constant determined by the data type of
    the object. For instance, for a variable of type uword, the sizeof is 2.
    For an 10 element array of floats, it is 50 (on the C-64, where a float is 5 bytes).
    Note: usually you will be interested in the number of elements in an array, use len() for that.

swap(x, y)
    Swap the values of numerical variables (or memory locations) x and y in a fast way.

memory(name, size)
    Returns the address of the first location of a statically "reserved" block of memory of the given size in bytes,
    with the given name. Requesting the address of such a named memory block again later with
    the same name, will result in the same address as before.
    When reusing blocks in that way, it is required that the size argument is the same,
    otherwise you'll get a compilation error.
    This routine can be used to "reserve" parts of the memory where a normal byte array variable would
    not suffice; for instance if you need more than 256 consecutive bytes.
    The return value is just a simple uword address so it cannot be used as an array in your program.
    You can only treat it as a pointer or use it in inline assembly.

callfar(bank, address, argumentaddress)      ; NOTE: specific to cx16 compiler target for now
    Calls an assembly routine in another ram-bank on the CommanderX16 (using the ``jsrfar`` routine)
    The banked RAM is located in the address range $A000-$BFFF (8 kilobyte).
    Notice that bank $00 is used by the Kernal and should not be used by user code.
    The third argument can be used to designate the memory address
    of an argument for the routine; it will be loaded into the A register and will
    receive the result value returned by the routine in the A register. If you leave this at zero,
    no argument passing will be done.
    If the routine requires different arguments or return values, ``callfar`` cannot be used
    and you'll have to set up a call to ``jsrfar`` yourself to process this.

callrom(bank, address, argumentaddress)      ; NOTE: specific to cx16 compiler target for now
    Calls an assembly routine in another rom-bank on the CommanderX16
    The banked ROM is located in the address range $C000-$FFFF (16 kilobyte).
    There are 32 banks (0 to 31).
    The third argument can be used to designate the memory address
    of an argument for the routine; it will be loaded into the A register and will
    receive the result value returned by the routine in the A register. If you leave this at zero,
    no argument passing will be done.
    If the routine requires different arguments or return values, ``callrom`` cannot be used
    and you'll have to set up a call in assembly code yourself that handles the banking and
    argument/returnvalues.


Library routines
----------------

There are many routines available in the compiler libraries.
Some are used internally by the compiler as well.
There's too many to list here, just have a look through the source code
of the library modules to see what's there.
(They can be found in the compiler/res directory)
The example programs also use a small set of the library routines, you can study
their source code to see how they might be used.
