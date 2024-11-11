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
    It should be saved in UTF-8 encoding.

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
    It can define its own variables, and it is also possible to define subroutines within other subroutines.
    Nested subroutines can access the variables from outer scopes easily, which removes the need and overhead to pass everything via parameters all the time.
    Subroutines do not have to be declared in the source code before they can be called.

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

    - global (no prefix), everything in a module file goes in here;
    - block;
    - subroutine, can be nested in another subroutine.

    Even though modules are separate files, they are *not* separate scopes!
    Everything defined in a module is merged into the global scope.
    This is different from most other languages that have modules.
    The global scope can only contain blocks and some directives, while the others can contain variables and subroutines too.
    Some more details about how to deal with scopes and names is discussed below.


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




Variables and values
--------------------

Variables are named values that can change during the execution of the program.
They can be defined inside any scope (blocks, subroutines etc.) See :ref:`blocks`.
When declaring a numeric variable it is possible to specify the initial value, if you don't want it to be zero.
For other data types it is required to specify that initial value it should get.
Values will usually be part of an expression or assignment statement::

    12345                 ; integer number
    $aa43                 ; hex integer number
    %100101               ; binary integer number (% is also remainder operator so be careful)
    false                 ; boolean false
    -33.456e52            ; floating point number
    "Hi, I am a string"   ; text string, encoded with default encoding
    'a'                   ; byte value (ubyte) for the letter a
    sc:"Alternate"        ; text string, encoded with c64 screencode encoding
    sc:'a'                ; byte value of the letter a in c64 screencode encoding

    byte  counter  = 42   ; variable of size 8 bits, with initial value 42


**putting a variable in zeropage:**
If you add the ``@zp`` tag to the variable declaration, the compiler will prioritize this variable
when selecting variables to put into zeropage (but no guarantees). If there are enough free locations in the zeropage,
it will try to fill it with as much other variables as possible (before they will be put in regular memory pages).
Use ``@requirezp`` tag to *force* the variable into zeropage, but if there is no more free space the compilation will fail.
It's possible to put strings, arrays and floats into zeropage too, however because Zp space is really scarce
this is not advised as they will eat up the available space very quickly. It's best to only put byte or word
variables in zeropage.  By the way, there is also ``@nozp`` to keep a variable *out of the zeropage* at all times.

Example::

    byte   @zp  smallcounter = 42
    uword  @requirezp  zppointer = $4000


**shared variables:**
If you add the ``@shared`` tag to the variable declaration, the compiler will know that this variable
is a prog8 variable shared with some assembly code elsewhere. This means that the assembly code can
refer to the variable even if it's otherwise not used in prog8 code itself.
(usually, these kinds of 'unused' variables are optimized away by the compiler, resulting in an error
when assembling the rest of the code). Example::

    byte  @shared  assemblyVariable = 42


**uninitialized variables:**
All variables will be initialized by prog8 at startup, they'll get their assigned initialization value, or be cleared to zero.
This (re)initialization is also done on each subroutine entry for the variables declared in the subroutine.

There may be certain scenarios where this initialization is redundant and/or where you want to avoid the overhead of it.
In some cases, Prog8 itself can detect that a variable doesn't need a separate automatic initialization to zero, if
it's trivial that it is not being read between the variable's declaration and the first assignment. For instance, when
you declare a variable immediately before a for loop where it is the loop variable. However Prog8 is not yet very smart
at detecting these redundant initializations. If you want to be sure, check the generated assembly output.

In any case, you can use the ``@dirty`` tag on the variable declaration to make the variable *not* being (re)initialized by Prog8.
This means its value will be undefined (it can be anything) until you assign a value yourself! Don't use such
a variable before you have done so. 🦶🔫 Footgun warning.


**memory alignment:**
A string or array variable can be aligned to a couple of possible interval sizes in memory.
The use for this is very situational, but two examples are: sprite data for the C64 that needs
to be on a 64 byte aligned memory address, or an array aligned on a full page boundary to avoid
any possible extra page boundary clock cycles on certain instructions when accessing the array.
You can align on word, 64 bytes, and page boundaries::

    ubyte[] @alignword array = [1, 2, 3, 4, ...]
    ubyte[] @align64 spritedata = [ %00000000, %11111111, ...]
    ubyte[] @alignpage lookup = [11, 22, 33, 44, ...]


Integers
^^^^^^^^

Integers are 8 or 16 bit numbers and can be written in normal decimal notation,
in hexadecimal and in binary notation. There is no octal notation.
You can use underscores to group digits to make long numbers more readable.
A single character in single quotes such as ``'a'`` is translated into a byte integer,
which is the PETSCII value for that character.

Unsigned integers are in the range 0-255 for unsigned byte types, and 0-65535 for unsigned word types.
The signed integers integers are in the range -128..127 for bytes,
and -32768..32767 for words.

.. attention::
    Doing math on signed integers can result in code that is a lot larger and slower than
    when using unsigned integers. Make sure you really need the signed numbers, otherwise
    stick to unsigned integers for efficiency.


Booleans
^^^^^^^^

Booleans are a distinct type in Prog8 and can have only the values ``true`` or ``false``.
It can be casted to and from other integer types though
where a nonzero integer is considered to be true, and zero is false.
Logical expressions, comparisons and some other code tends to compile more efficiently if
you explicitly use ``bool`` types instead of 0/1 integers.
The in-memory representation of a boolean value is just a byte containing 0 or 1.

If you find that you need a whole bunch of boolean variables or perhaps even an array of them,
consider using integer bit mask variable + bitwise operators instead.
This saves a lot of memory and may be faster as well.


Floating point numbers
^^^^^^^^^^^^^^^^^^^^^^

You can use underscores to group digits to make long numbers more readable.

Floats are stored in the 5-byte 'MFLPT' format that is used on CBM machines.
Floating point support is available on the c64 and cx16 (and virtual) compiler targets.
On the c64 and cx16, the rom routines are used for floating point operations,
so on both systems the correct rom banks have to be banked in to make this work.
Although the C128 shares the same floating point format, Prog8 currently doesn't support
using floating point on that system (because the c128 fp routines require the fp variables
to be in another ram bank than the program, something Prog8 doesn't do).

Also your code needs to import the ``floats`` library to enable floating point support
in the compiler, and to gain access to the floating point routines.
(this library contains the directive to enable floating points, you don't have
to worry about this yourself)

The largest 5-byte MFLPT float that can be stored is: **1.7014118345e+38**   (negative: **-1.7014118345e+38**)


Arrays
^^^^^^
Array types are also supported. They can be formed from a list of booleans, bytes, words, floats, or addresses of other variables
(such as explicit address-of expressions, strings, or other array variables) - values in an array literal
always have to be constants. Here are some examples of arrays::

    byte[10]  array                   ; array of 10 bytes, initially set to 0
    byte[]  array = [1, 2, 3, 4]      ; initialize the array, size taken from value
    ubyte[99] array = [255]*99        ; initialize array with 99 times 255 [255, 255, 255, 255, ...]
    byte[] array = 100 to 199         ; initialize array with [100, 101, ..., 198, 199]
    str[] names = ["ally", "pete"]    ; array of string pointers/addresses (equivalent to array of uwords)
    uword[] others = [names, array]   ; array of pointers/addresses to other arrays
    bool[2] flags = [true, false]     ; array of two boolean values  (take up 1 byte each, like a byte array)

    value = array[3]            ; the fourth value in the array (index is 0-based)
    char = string[4]            ; the fifth character (=byte) in the string
    char = string[-2]           ; the second-to-last character in the string (Python-style indexing from the end)

.. note::
    Right now, the array should be small enough to be indexable by a single byte index.
    This means byte arrays should be <= 256 elements, word arrays <= 128 elements (256 if
    it's a split array - see below), and float arrays <= 51 elements.

Arrays can be initialized with a range expression or an array literal value.
You can write out such an initializer value over several lines if you want to improve readability.

You can assign a new value to an element in the array, but you can't assign a whole
new array to another array at once. This is usually a costly operation. If you really
need this you have to write it out depending on the use case: you can copy the memory using
``sys.memcopy(sourcearray, targetarray, sizeof(targetarray))``. Or perhaps use ``sys.memset`` instead to
set it all to the same value, or maybe even simply assign the individual elements.

Note that the various keywords for the data type and variable type (``byte``, ``word``, ``const``, etc.)
can't be used as *identifiers* elsewhere. You can't make a variable, block or subroutine with the name ``byte``
for instance.

Using the ``in`` operator you can easily check if a value is present in an array,
example: ``if choice in [1,2,3,4] {....}``

**Arrays at a specific memory location:**
Using the memory-mapped syntax it is possible to define an array to be located at a specific memory location.
For instance to reference the first 5 rows of the Commodore 64's screen matrix as an array, you can define::

    &ubyte[5*40]  top5screenrows = $0400

This way you can set the second character on the second row from the top like this::

    top5screenrows[41] = '!'

**Array indexing on a pointer variable:**
An uword variable can be used in limited scenarios as a 'pointer' to a byte in memory at a specific,
dynamic, location. You can use array indexing on a pointer variable to use it as a byte array at
a dynamic location in memory: currently this is equivalent to directly referencing the bytes in
memory at the given index. In contrast to a real array variable, the index value can be the size of a word.
Unlike array variables, negative indexing for pointer variables does *not* mean it will be counting from the end, because the size of the buffer is unknown.
Instead, it simply addresses memory that lies *before* the pointer variable.
See also :ref:`pointervars_programming`

**LSB/MSB split word arrays:**
For (u)word arrays, you can make the compiler layout the array in memory as two separate arrays,
one with the LSBs and one with the MSBs of the word values. This makes it more efficient to access
values from the array (smaller and faster code). It also doubles the maximum size of the array from 128 words to 256 words!
The ``@split`` tag should be added to the variable declaration to do this.
In the assembly code, the array will then be generated as two byte arrays namely ``name_lsb`` and ``name_msb``.

.. caution::
    Not all array operations are supported yet on "split word arrays".
    If you get an error message, simply revert to a regular word array and please report the issue,
    so that more support can be added in the future where it is needed.


Strings
^^^^^^^

Strings are a sequence of characters enclosed in double quotes. The length is limited to 255 characters.
They're stored and treated much the same as a byte array,
but they have some special properties because they are considered to be *text*.
Strings (without encoding prefix) will be encoded (translated from ASCII/UTF-8) into bytes via the
*default encoding* for the target platform. On the CBM machines, this is CBM PETSCII.

Alternative encodings can be specified with a ``encodingname:`` prefix to the string or character literal.
The following encodings are currently recognised:

    - ``petscii``  PETSCII, the default encoding on CBM machines (c64, c128, cx16)
    - ``sc``  CBM-screencodes aka 'poke' codes (c64, c128, cx16)
    - ``iso``  iso-8859-15 text (supported on cx16)

So the following is a string literal that will be encoded into memory bytes using the iso encoding.
It can be correctly displayed on the screen only if a iso-8859-15 charset has been activated first
(the Commander X16 has this feature built in)::

    iso:"Käse, Straße"

You can concatenate two string literals using '+', which can be useful to
split long strings over separate lines. But remember that the length
of the total string still cannot exceed 255 characters.
A string literal can also be repeated a given number of times using '*', where the repeat number must be a constant value.
And a new string value can be assigned to another string, but no bounds check is done!
So be sure the destination string is large enough to contain the new value (it is overwritten in memory)::

    str string1 = "first part" + "second part"
    str string2 = "hello!" * 10

    string1 = string2
    string1 = "new value"


There are several 'escape sequences' to help you put special characters into strings, such
as newlines, quote characters themselves, and so on. The ones used most often are
``\\``, ``\"``, ``\n``, ``\r``.  For a detailed description of all of them and what they mean,
read the syntax reference on strings.

Using the ``in`` operator you can easily check if a character is present in a string,
example: ``if '@' in email_address {....}`` (however this gives no clue about the location
in the string where the character is present, if you need that, use the ``string.find()``
library function instead)
**Caution:**
This checks *all* elements in the string with the length as it was initially declared.
Even when a string was changed and is terminated early with a 0-byte early,
the containment check with ``in`` will still look at all character positions in the initial string.
Consider using ``string.find`` followed by ``if_cs`` (for instance) to do a "safer" search
for a character in such strings (one that stops at the first 0 byte)


.. hint::
    Strings/arrays and uwords (=memory address) can often be interchanged.
    An array of strings is actually an array of uwords where every element is the memory
    address of the string. You can pass a memory address to assembly functions
    that require a string as an argument.
    For regular assignments you still need to use an explicit ``&`` (address-of) to take
    the address of the string or array.

.. hint::
    You can declare parameters and return values of subroutines as ``str``,
    but in this case that is equivalent to declaring them as ``uword`` (because
    in this case, the address of the string is passed as argument or returned as value).

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

When using ``const``, the value of the 'variable' cannot be changed; it has become a compile-time constant value instead.
You'll have to specify the initial value expression. This value is then used
by the compiler everywhere you refer to the constant (and no memory is allocated
for the constant itself). Onlythe simple numeric types (byte, word, float) can be defined as a constant.
If something is defined as a constant, very efficient code can usually be generated from it.
Variables on the other hand can't be optimized as much, need memory, and more code to manipulate them.
Note that a subset of the library routines in the ``math``, ``string`` and ``floats`` modules are recognised in
compile time expressions. For example, the compiler knows what ``math.sin8u(12)`` is and replaces it with the computed result.

When using ``&`` (the address-of operator but now applied to a datatype), the variable will point to specific location in memory,
rather than being newly allocated. The initial value (mandatory) must be a valid
memory address.  Reading the variable will read the given data type from the
address you specified, and setting the variable will directly modify that memory location(s)::

	const  byte  max_age = 2000 - 1974      ; max_age will be the constant value 26
	&word  SCREENCOLORS = $d020             ; a 16-bit word at the address $d020-$d021

.. _pointervars_programming:

Direct access to memory locations ('peek' and 'poke')
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Normally memory locations are accessed by a *memory mapped* name, such as ``cbm.BGCOL0`` that is defined
as the memory mapped address $d021 (on the c64 target).

If you want to access a memory location directly (by using the address itself or via an uword pointer variable),
without defining a memory mapped location, you can do so by enclosing the address in ``@(...)``::

    color = @($d020)  ; set the variable 'color' to the current c64 screen border color ("peek(53280)")
    @($d020) = 0      ; set the c64 screen border to black ("poke 53280,0")
    @(vic+$20) = 6    ; you can also use expressions to 'calculate' the address

This is the official syntax to 'dereference a pointer' as it is often named in other languages.
You can actually also use the array indexing notation for this. It will be silently converted into
the direct memory access expression as explained above. Note that unlike regular arrays,
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

Sometimes it is a straight reinterpretation of the given value as being of the other type,
sometimes an actual value conversion is done to convert it into the other type.
Try to avoid those type conversions as much as possible.


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

You can also use if..else as an *expression* instead of a statement. This expression selects one of two
different values depending of the condition. Sometimes it may be more legible if you surround the condition expression with parentheses.
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
        10,20,30 -> {
            txt.print("ten or twenty or thirty")
        }
        else -> txt.print("don't know")
    }

The when-*value* can be any expression but the choice values have to evaluate to
compile-time constant integers (bytes or words). They also have to be the same
datatype as the when-value, otherwise no efficient comparison can be done.

.. note::
    Instead of chaining several value equality checks together using ``or`` (ex.: ``if x==1 or xx==5 or xx==9``),
    consider using a ``when`` statement or ``in`` containment check instead. These are more efficient.

Assignments
-----------

Assignment statements assign a single value to a target variable or memory location.
Augmented assignments (such as ``aa += xx``) are also available, but these are just shorthands
for normal assignments (``aa = aa + xx``).

It is possible to "chain" assignments: ``x = y = z = 42``, this is just a shorthand
for the three individual assignments with the same value 42.

Only for certain subroutines that return multiple values it is possible to write a "multi assign" statement
with comma separated assignment targets, that assigns those multiple values to different targets in one statement.
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

Read the :ref:`syntaxreference` chapter for all details on the available operators and kinds of expressions you can write.

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



Subroutines
-----------

Defining a subroutine
^^^^^^^^^^^^^^^^^^^^^

Subroutines are parts of the code that can be repeatedly invoked using a subroutine call from elsewhere.
Their definition, using the ``sub`` statement, includes the specification of the required parameters and return value.
Subroutines can be defined in a Block, but also nested inside another subroutine. Everything is scoped accordingly.
With ``asmsub`` you can define a low-level subroutine that is implemented directly in assembly and takes parameters
directly in registers. Finally with ``extsub`` you can define an external subroutine that's implemented outside
of the program (for instance, a ROM routine, or a routine in a library loaded elsewhere in RAM).

Trivial ``asmsub`` routines can be tagged as ``inline`` to tell the compiler to copy their code
in-place to the locations where the subroutine is called, rather than inserting an actual call and return to the
subroutine. This may increase code size significantly and can only be used in limited scenarios, so YMMV.
Note that the routine's code is copied verbatim into the place of the subroutine call in this case,
so pay attention to any jumps and rts instructions in the inlined code!
Inlining regular Prog8 subroutines is at the discretion of the compiler.


Calling a subroutine
^^^^^^^^^^^^^^^^^^^^

The arguments in parentheses after the function name, should match the parameters in the subroutine definition.
If you want to ignore a return value of a subroutine, you should prefix the call with the ``void`` keyword.
Otherwise the compiler will issue a warning about discarding a result value.

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


Deferred ("cleanup") code
^^^^^^^^^^^^^^^^^^^^^^^^^

Usually when a subroutine exits, it has to clean up things that it worked on. For example, it has to close
a file that it opened before to read data from, or it has to free a piece of memory that it allocated via
a dynamic memory allocation library, etc.
Every spot where the subroutine exits (return statement, jump, or the end of the routine) you have to take care
of doing the cleanups required.  This can get tedious, and the cleanup code is separated from the place where
the resource allocation was done at the start.

To help make this easier and less error prone, you can ``defer`` code to be executed automatically,
immediately before any moment the subroutine exits. So for example to make sure a file is closed
regardless of what happens later in the routine, you can write something along these lines::

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
