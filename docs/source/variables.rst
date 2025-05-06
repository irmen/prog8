.. _variables:

====================
Variables and Values
====================

Because this is such a big subject, variables and values have their own chapter.


Variables
---------

Variables are named values that can be modified during the execution of the program.
The compiler allocates the required memory for them.
There is *no dynamic memory allocation*. The storage size of all variables
is fixed and is determined at compile time.
Variable declarations tend to appear at the top of the code block that uses them, but this is not mandatory.
They define the name and type of the variable, and its initial value.
Prog8 supports a small list of data types, including special memory-mapped types
that don't allocate storage but instead point to a fixed location in the address space.


Declaring a variable
^^^^^^^^^^^^^^^^^^^^

Variables should be declared with their exact type and size so the compiler can allocate storage
for them. You can give them an initial value as well. That value can be a simple literal value,
or an expression. If you don't provide an initial value yourself, zero will be used.
The syntax for variable declarations is::

	<datatype>  [ @tag ]  <variable name>   [ = <initial value> ]

For boolean and numeric variables, you can actually declare them in one go by listing the names in a comma separated list.
Type tags, and the optional initialization value, are applied equally to all variables in such a list.
Various examples::

    word        thing   = 0
    byte        counter = len([1, 2, 3]) * 20
    byte        age     = 2018 - 1974
    float       wallet  = 55.25
    ubyte       x,y,z                   ; declare three ubyte variables x y and z
    str         name    = "my name is Alice"
    uword       address = &counter
    bool        flag    = true
    byte[]      values  = [11, 22, 33, 44, 55]
    byte[5]     values                  ; array of 5 bytes, initially set to zero
    byte[5]     values  = [255]*5       ; initialize with five 255 bytes

    word  @zp         zpword = 9999     ; prioritize this when selecting vars for zeropage storage
    uword @requirezp  zpaddr = $3000    ; we require this variable in zeropage
    word  @shared asmvar                ; variable is used in assembly code but not elsewhere
    byte  @nozp memvar                  ; variable that is never in zeropage


Here are the tags you can add to a variable:

==========  ======
Tag         Effect
==========  ======
@zp         prioritize the variable for putting it into Zero page. No guarantees; if ZP is full the variable will be placed in another memory location.
@requirezp  force the variable into Zero page. If ZP is full, compilation will fail.
@nozp       force the variable to normal system ram, never place it into zeropage.
@shared     means the variable is shared with some assembly code and that it cannot be optimized away if not used elsewhere.
@nosplit    (only valid on (u)word arrays) Store the array as a single inear array instead of a separate array for lsb and msb values
@alignword  aligns string or array variable on an even memory address
@align64    aligns string or array variable on a 64 byte address interval (example: for C64 sprite data)
@alignpage  aligns string or array variable on a 256 byte address interval (example: to avoid page boundaries)
@dirty      the variable won't be set to zero when entering the subroutine (note: it will still be set to zero once on program startup, like all other uninitialized variables). You'll usually have to make sure to assign a value yourself before using the variable! This is used to reduce overhead in certain scenarios. 🦶🔫 Footgun warning.
==========  ======


Variables can be defined inside any scope (blocks, subroutines etc.) See :ref:`blocks`.
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

In any case, you can use the ``@dirty`` tag on the variable declaration to make the variable *not* being reinitialized
when entering the subroutine (it will still be set to 0 once at program startup).
This means you usually have to make sure to assign a value yourself, before using the variable. 🦶🔫 Footgun warning.


**memory alignment:**
A string or array variable can be aligned to a couple of possible interval sizes in memory.
The use for this is very situational, but two examples are: sprite data for the C64 that needs
to be on a 64 byte aligned memory address, or an array aligned on a full page boundary to avoid
any possible extra page boundary clock cycles on certain instructions when accessing the array.
You can align on word, 64 bytes, and page boundaries::

    ubyte[] @alignword array = [1, 2, 3, 4, ...]
    ubyte[] @align64 spritedata = [ %00000000, %11111111, ...]
    ubyte[] @alignpage lookup = [11, 22, 33, 44, ...]


Initializing a variable
^^^^^^^^^^^^^^^^^^^^^^^

You can specify an initialization value in the variable declaration.
This will then be used to initialize the variable with at the start of the subroutine, instead of the default value 0.
The provided value doesn't have to be a constant; it can be any expression.
It is a shorter notation for declaring the variables and then assigning the values to them in separate assignment statment(s).

There are a few special situations:

initializing an array: ``ubyte[3] array = [11,22,33]``
    The initiazation value has to be a range value or an array literal (remember you can use '[4] * 3' and such).
    Ofcourse the size of the range or the number of values in the array has to match the declared array size.

initializing a multi variable declaration: ``ubyte a,b,c = multi()``
    The initialization value can be a single constant value which will then be assigned to each of the variables.
    It can also be a subroutine call to a subroutine returning multiple result values, which will then be put
    into the declared variables in order.  Ofcourse the number of values has to match the number of variables.


Data Types
----------

Prog8 supports the following data types:

===============  =======================  =================  =========================================
type identifier  type                     storage size       example var declaration and literal value
===============  =======================  =================  =========================================
``byte``         signed byte              1 byte = 8 bits    ``byte myvar = -22``
``ubyte``        unsigned byte            1 byte = 8 bits    ``ubyte myvar = $8f``,   ``ubyte c = 'a'``
``bool``         boolean                  1 byte = 8 bits    ``bool myvar = true`` or ``bool myvar == false``
``word``         signed word              2 bytes = 16 bits  ``word myvar = -12345``
``uword``        unsigned word            2 bytes = 16 bits  ``uword myvar = $8fee``
``long``         signed 32 bits integer   n/a                ``const long LARGE = $12345678``
                                          (only for consts)
``float``        floating-point           5 bytes = 40 bits  ``float myvar = 1.2345``
                                                             stored in 5-byte cbm MFLPT format
``byte[x]``      signed byte array        x bytes            ``byte[4] myvar``
``ubyte[x]``     unsigned byte array      x bytes            ``ubyte[4] myvar``
``word[x]``      signed word array        2*x bytes          ``word[4] myvar``
``uword[x]``     unsigned word array      2*x bytes          ``uword[4] myvar``
``float[x]``     floating-point array     5*x bytes          ``float[4] myvar``.   The 5 bytes per float is on CBM targets.
``bool[x]``      boolean array            x bytes            ``bool[4] myvar``  note: consider using bit flags in a byte or word instead to save space
``byte[]``       signed byte array        depends on value   ``byte[] myvar = [1, 2, 3, 4]``
``ubyte[]``      unsigned byte array      depends on value   ``ubyte[] myvar = [1, 2, 3, 4]``
``word[]``       signed word array        depends on value   ``word[] myvar = [1, 2, 3, 4]``
``uword[]``      unsigned word array      depends on value   ``uword[] myvar = [1, 2, 3, 4]``
``float[]``      floating-point array     depends on value   ``float[] myvar = [1.1, 2.2, 3.3, 4.4]``
``bool[]``       boolean array            depends on value   ``bool[] myvar = [true, false, true]``  note: consider using bit flags in a byte or word instead to save space
``str[]``        array with string ptrs   2*x bytes + strs   ``str[] names = ["ally", "pete"]``  note: equivalent to a uword array.
``str``          string (PETSCII)         varies             ``str myvar = "hello."``
                                                             implicitly terminated by a 0-byte
===============  =======================  =================  =========================================

Integers (bytes, words)
^^^^^^^^^^^^^^^^^^^^^^^

Integers are 8 or 16 bit numbers and can be written in normal decimal notation,
in hexadecimal and in binary notation. There is no octal notation. Hexadecimal has the '$' prefix,
binary has the '%' prefix. Note that ``%`` is also the remainder operator so be careful: if you want to take the remainder
of something with an operand starting with 1 or 0, you'll have to add a space in between, otherwise
the parser thinks you've typed an invalid binary number.

You can use underscores to group digits to make long numbers more readable: any underscores in the number are ignored by the compiler.
For instance ``3_000_000`` is a valid decimal number and so is ``%1001_0001`` a valid binary number.

A single character in single quotes such as ``'a'`` is translated into a byte integer,
which is the PETSCII value for that character. You can prefix it with the desired encoding, like with strings, see :ref:`encodings`.

**bytes versus words:**

Prog8 tries to determine the data type of integer values according to the table below,
and sometimes the context in which they are used.

========================= =================
value                     datatype
========================= =================
-128 .. 127               byte
0 .. 255                  ubyte
-32768 .. 32767           word
0 .. 65535                uword
-2147483647 .. 2147483647 long (only for const)
========================= =================

If the number fits in a byte but you really require it as a word value, you'll have to explicitly cast it: ``60 as uword``
or you can use the full word hexadecimal notation ``$003c``.  This is useful in expressions where you want a calcuation
to be done on word values, and don't want to explicitly have to cast everything all the time. For instance::

    ubyte  column
    uword  offset = column * 64       ; does (column * 64) as uword, wrong result?
    uword  offset = column * $0040    ; does (column as uword) * 64 , a word calculation

Only for ``const`` numbers, you can use larger values (32 bits signed integers). The compiler can handle those
internally in expressions. As soon as you have to actually store it into a variable,
you have to make sure the resulting value fits into the byte or word size of the variable.

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

Floats are stored in the 5-byte 'MFLPT' format that is used on CBM machines.
Floating point support is available on the c64 and cx16 (and virtual) compiler targets.
On the c64 and cx16, the rom routines are used for floating point operations,
so on both systems the correct rom banks have to be banked in to make this work.
Although the C128 shares the same floating point format, Prog8 currently doesn't support
using floating point on that system (because the c128 fp routines require the fp variables
to be in another ram bank than the program, something Prog8 doesn't support yet).

Also your code needs to import the ``floats`` library to enable floating point support
in the compiler, and to gain access to the floating point routines.
(this library contains the directive to enable floating points, you don't have
to worry about this yourself)

The largest 5-byte MFLPT float that can be stored is: **1.7014118345e+38**   (negative: **-1.7014118345e+38**)

You can use underscores to group digits in floating point literals to make long numbers more readable:
any underscores in the number are ignored by the compiler.
For instance ``30_000.999_999`` is a valid floating point number 30000.999999.

.. attention::
    On the X16, make sure rom bank 4 is still active before doing floationg point operations (it's the bank that contains the fp routines).
    On the C64, you have to make sure the Basic ROM is still banked in (same reason).


.. _arrayvars:

Arrays
^^^^^^
Arrays can be created from a list of booleans, bytes, words, floats, or addresses of other variables
(such as explicit address-of expressions, strings, or other array variables) - values in an array literal
always have to be constants. A trailing comma is allowed, sometimes this is easier when copying values
or when adding more stuff to the array later. Here are some examples of arrays::

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
    To allow the 6502 CPU to efficiently access values in an array, the array should be small enough to be
    indexable by a single byte index.
    This means byte arrays should be <= 256 elements, word arrays <= 256 elements as well (if split, which
    is the default. When not split, the maximum length is 128. See below for details about this disctinction).t
    Float arrays should be <= 51 elements.

Arrays can be initialized with a range expression or an array literal value.
You can write out such an initializer value over several lines if you want to improve readability.
When an initialization value is given, you are allowed to omit the array size in the declaration,
because it can be inferred from the initialization value.
You can use '*' to repeat array fragments to build up a larger array.

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
See also :ref:`pointervars`

**LSB/MSB split word and str arrays:**

As an optimization, (u)word arrays and str arrays are split by the compiler in memory as two separate arrays,
one with the LSBs and one with the MSBs of the word values. This is more efficient to access by the 6502 cpu.
It also allows a maximum length of 256 for word arrays, where normally it would have been 128.

For normal prog8 array indexing, the compiler takes care of the distiction for you under water.
*But for assembly code, or code that otherwise accesses the array elements directly, you have to be aware of the distinction from 'normal' arrays.*
In the assembly code, the array is generated as two byte arrays namely ``name_lsb`` and ``name_msb``, immediately following eachother in memory.

The ``@split`` tag can be added to the variable declaration to *always* split the array even when the command line option -dontsplitarrays is set
The ``@nosplit`` tag can be added to the variable declaration to *never* split the array. This is useful for compatibility with
code that expects the words to be sequentially in memory (such as the cx16.FB_set_palette routine).

There is a command line option ``-dontsplitarrays`` that avoids splitting word arrays by default,
so every word array is layed out sequentially in memory (this is what older versions of Prog8 used to do).immediately
It reduces the maximum word array length to 128. You can still override this by adding ``@split`` explicitly.

.. note::
    Most but not all array operations are supported yet on "split word arrays".
    If you get a compiler error message, simply revert to a regular sequential word array using ``@nosplit``,
    and please report the issue.

.. note::
    Array literals are stored as split arrays if they're initializing a split word array, otherwise,
    they are stored as sequential words!  So if you pass one directly to a subroutine (like ``func([1111,2222,3333])``),
    the array values are sequential in memory.  If this is undesiarable (i.e. the subroutine expects a split word array),
    you have to create a normal array variable first and then pass that to the subroutine.

.. caution::
    Be aware that the default is to split word arrays. Normal array access is taken care of by Prog8, so you won't
    notice this optimization. However if you are accessing the array's values using other ways (for example via a pointer,
    and then using ``peekw`` to get the value) you have to be aware of this. In that ``peekw`` example you have
    to make sure to use ``@nosplit`` on the word array so that the words stay sequentially in memory which is what ``peekw`` needs.
    Also be careful when passing arrays to library routines (this is via a pointer!): you have to make sure
    the library routine can deal with the split array otherwise you have to use ``@nosplit`` as well.


.. _encodings:

Strings
^^^^^^^

Strings are a sequence of characters enclosed in double quotes. The length is limited to 255 characters.
They're stored and treated much the same as a byte array,
but they have some special properties because they are considered to be *text*.
Strings (without encoding prefix) will be encoded (translated from ASCII/UTF-8) into bytes via the
*default encoding* for the target platform. On the CBM machines, this is CBM PETSCII.

Strings without an encoding prefix are stored in the machine's default character encoding (which is PETSCII on the CBM machines,
but can be something else on other targets).
There are ways to change the encoding: prefix the string with an encoding name, or use the ``%encoding`` directive to
change it for the whole file at once. Here are examples of the possible encodings:

    - ``"hello"``   a string translated into the default character encoding (PETSCII on the CBM machines)
    - ``petscii:"hello"``               string in CBM PETSCII encoding
    - ``sc:"my name is Alice"``         string in CBM screencode encoding
    - ``iso:"Ich heiße François"``      string in iso-8859-15 encoding (Latin)
    - ``iso5:"Хозяин и Работник"``      string in iso-8859-5 encoding (Cyrillic)
    - ``iso16:"zażółć gęślą jaźń"``     string in iso-8859-16 encoding (Eastern Europe)
    - ``atascii:"I am Atari!"``         string in "atascii" encoding (Atari 8-bit)
    - ``cp437:"≈ IBM Pc ≈ ♂♀♪☺¶"``     string in "cp437" encoding (IBM PC codepage 437)
    - ``kata:"ｱﾉ ﾆﾎﾝｼﾞﾝ ﾜ ｶﾞｲｺｸｼﾞﾝ｡ # が # ガ"``  string in "kata" encoding (Katakana)
    - ``c64os:"^Hello_World! \\ ~{_}~"`` string in "c64os" encoding (C64 OS)

So what follows below is a string literal that will be encoded into memory bytes using the iso encoding.
It can be correctly displayed on the screen only if a iso-8859-15 charset has been activated first
(the Commander X16 has this capability)::

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

Using the ``in`` operator you can easily check if a character is present in a string,
example: ``if '@' in email_address {....}`` (however this gives no clue about the location
in the string where the character is present, if you need that, use the ``strings.find()``
library function instead)
**Caution:**
This checks *all* elements in the string with the length as it was initially declared.
Even when a string was changed and is terminated early with a 0-byte early,
the containment check with ``in`` will still look at all character positions in the initial string.
Consider using ``strings.find`` followed by ``if_cs`` (for instance) to do a "safer" search
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


.. _range-expression:

Ranges
^^^^^^

A special value is the *range expression* which represents a range of integer numbers or characters,
from the starting value to (and including) the ending value::

    <start>  to  <end>   [ step  <step> ]
    <start>  downto  <end>   [ step  <step> ]

You an provide a step value if you need something else than the default increment which is one (or,
in case of downto, a decrement of one).  Unlike the start and end values, the step value must be a constant.
Because a step of minus one is so common you can just use
the downto variant to avoid having to specify the step as well::

    0 to 7                   ; range of values 0, 1, 2, 3, 4, 5, 6, 7
    20 downto 10 step -3     ; range of values 20, 17, 14, 11

    aa = 5
    xx = 10
    aa to xx                 ; range of 5, 6, 7, 8, 9, 10

    for  i  in  0 to 127  {
        ; i loops 0, 1, 2, ... 127
    }


Range expressions are most often used in for loops, but can be also be used to create array initialization values::

	byte[] array = 100 to 199     ; initialize array with [100, 101, ..., 198, 199]


Constants
^^^^^^^^^

When using ``const``, the value of the 'variable' cannot be changed; it has become a compile-time constant value instead.
You'll have to specify the initial value expression. This value is then used
by the compiler everywhere you refer to the constant (and no memory is allocated
for the constant itself). Onlythe simple numeric types (byte, word, float) can be defined as a constant.
If something is defined as a constant, very efficient code can usually be generated from it.
Variables on the other hand can't be optimized as much, need memory, and more code to manipulate them.
Note that a subset of the library routines in the ``math``, ``strings`` and ``floats`` modules are recognised in
compile time expressions. For example, the compiler knows what ``math.sin8u(12)`` is and replaces it with the computed result.


Memory-mapped
^^^^^^^^^^^^^
When using ``&`` (the address-of operator but now applied to the datatype in the variable's declaration),
the variable will be placed at a designated position in memory rather than being newly allocated somewhere.
The initial value in the declaration should be the valid memory address where the variable should be placed.
Reading the variable will then read its value from that address, and setting the variable will directly modify those memory location(s)::

	const  byte  max_age = 2000 - 1974      ; max_age will be the constant value 26
	&word  SCREENCOLORS = $d020             ; a 16-bit word at the address $d020-$d021

If you need to use the variable's memory address instead of the value placed there, you can still use `&variable` as usual.
You can memory map all datatypes except strings.


.. _pointervars:

Direct access to memory locations ('peek' and 'poke')
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Usually specific memory locations are accessed through a memory-mapped variable, such as ``cbm.BGCOL0`` that is defined
as the background color register at the memory address $d021 (on the c64 target).

If you want to access any memory location directly (by using the address itself or via an uword pointer variable),
without defining a memory-mapped location, you can do so by enclosing the address in ``@(...)``::

    color = @($d020)  ; set the variable 'color' to the current c64 screen border color ("peek(53280)")
    @($d020) = 0      ; set the c64 screen border to black ("poke 53280,0")
    @(vic+$20) = 6    ; you can also use expressions to 'calculate' the address

This is the official syntax to 'dereference a pointer' as it is often named in other languages.
You can actually also use the array indexing notation for this. It will be silently converted into
the direct memory access expression as explained above. Note that unlike regular arrays,
the index is not limited to an ubyte value. You can use a full uword to index a pointer variable like this::

    pointervar[999] = 0     ; set memory byte to zero at location pointervar + 999.


Converting/Casting types into other types
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
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
