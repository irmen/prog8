.. _comparingprog8:

============================
Prog8 versus other languages
============================

This chapter is meant for new Prog8 users coming with existing knowledge in another programming language such as C or Python.
It discusses some key design aspects of Prog8 and how it differs from what you may know from those languages.


The language
------------
- Prog8 is a structured imperative programming language. It looks like a mix of Python and C.
- It is meant to sit well above low level assembly code, but still allows that low level access to the system it runs on.
  Via language features, or even simply by using inline hand-written assembly code.
- Prog8 is targeting very CPU and memory constrained 8-bit systems, this reflects many design choices to work within those limitations
  (single digit Megaherz cpu clock speeds, and memory capacity counted in Kilobytes)
- Identifiers and string literals can contain non-ASCII characters so for example ``knäckebröd`` and ``見せしめ`` are valid identifiers.
- There's usually a single statement per line. There is no statement separator.
- Semicolon ``;`` is used to start a line comment.  Multi-line comments are also possible by enclosing it all in ``/*`` and ``*/``.
- Ternary operator ``x ? value1 : value2`` is available in the form of an *if-expression*: ``if x  value1 else value2``
- There's a Swift/Zig/Go style ``defer`` statement for delayed cleanup is available in the subroutine scope.
- Qualified names are searched from within the top level namespace (so you have to provide the full qualified name). Unqualified names are locally scoped.
- A trailing comma is allowed optionally in array literals:  [1,2,3,]  is a valid array of values 1, 2 and 3.


No linker
---------
- Even though your programs can consist of many separate module files, the compiler always outputs a single program file. There is no separate linker step.
  Currently, it's not easily possible to integrate object files created elsewhere. If the object file has a fixed load location and fixed entrypoints,
  it can be loaded explicitly and accessed easily using extsub definitions though.
- The prog8 compiler is self-contained in a single jar file. You do need 1 external tool namely 64tass, which performs the assembler step.


Data types
----------
- There are byte, word (16 bits) and float datatypes for numbers. There are no bigger integer types natively available.
- There is no automatic type enlargement: calculations remain within the data type of the operands. Any overflow silently wraps or truncates.
  You'll have to add explicit casts to increase the size of the value if required.
  For example when adding two byte variables having values 100 and 200, the result won't be 300, because that doesn't fit in a byte. It will be 44.
  You'll have to cast one or both of the *operands* to a word type first if you want to accomodate the actual result value of 300.
- strings and arrays are allocated once, statically, and never resized.
- strings and arrays are mutable: you can change their contents, but always keep the original storage size in mind to avoid overwriting memory outside of the buffer.
- maximum string length is 255 characters + a trailing 0 byte.
- word arrays are split into 2 separate arrays by default (this is configurable): one for the LSBs and one for the MSBs of the words. This enables efficient 6502 instructions to access the words.
- maximum storage size for arrays is 256 bytes (512 for split word arrays) , the maximum number of elements in the array depends on the size of a single element value.
  you can use larger "arrays" via pointer indexing, see below at Pointers.  One way of obtaining a piece of memory to store
  such an "array" is by using  ``memory()`` builtin function.


Variables
---------
- There is no dynamic memory management in the language; all variables are statically allocated.
  (but user written libraries are possible that provide that indirectly).
- Variables can be declared everywhere inside the code but all variable declarations in a subroutine
  are moved to the top of the subroutine. A for loop, or if/else blocks do not introduce a new scope.
  A subroutine (also nested ones) *do* introduce a new scope.
- All variables are initialized at the start of the program. There is no random garbage in them: they are zero or any other initialization value you provide.
- This als means you can run a Prog8 program multiple times without having to reload it from disk, unlike programs produced by most other compilers targeting these 8 bit platforms.


Subroutines
-----------
- Subroutines can be nested. Inner subroutines can directly access variables from their parent.
- Subroutine parameters are just local variables in the subroutine. (you can access them directly as such via their scoped name, if you want)
- There is no call stack for subroutine arguments: subroutine parameters are overwritten when called again. Thus recursion is not easily possible, but you can do it with manual stack manipulations.
  There are a couple of example programs that show how to solve this in different ways, among which are fractal-tree.p8, maze.p8 and queens.p8
- There is no function overloading (except for a couple of builtin functions).
- Some subroutine types can return multiple return values, and you can multi-assign those in a single statement.
- Because every declared variable allocates some memory, it might be beneficial to share the same variables over different subroutines
  instead of defining the same sort of variables in every subroutine.
  This reduces the memory needed for variables. A convenient way to do this is by using nested subroutines - these can easily access the
  variables declared in their parent subroutine(s).
- Everything in prog8 is publicly accessible from everywhere else (via fully scoped names) - there is no notion of private or public symbol accessibility.
- Because there is no callstack for subroutine arguments, it becomes very easy to manipulate the return address that *does* get pushed on the stack by the cpu.
  With only a little bit of code it is possible to implement a simple cooperative multitasking system that runs multiple tasks simultaneously. See the "coroutines" example.
  Each task is a subroutine and it simply has its state stored in its statically allocated variables so it can resume after a yield, without doing anything special.

Pointers
--------
- There is no specific pointer datatype.
  However, variables of the ``uword`` datatype can be used as a pointer to one of the possible 65536 memory locations,
  so the value it points to is always a single byte. This is similar to ``uint8_t*`` from C.
  You have to deal with the uword manually if the object it points to is something different.
- Note that there is the ``peekw`` builtin function that *does* allow you to directy obtain the *word* value at the given memory location.
  So if you use this, you can use uword pointers as pointers to word values without much hassle.
- "dereferencing" a uword pointer is done via array indexing ``ptr[index]`` (where index value can be 0-65535!) or via the memory read operator ``@(ptr)``, or ``peek/peekw(ptr)``.
- Pointers don't have to be a variable, you can immediately access the value of a given memory location using ``@($d020)`` for instance.
  Reading is done by assigning it to a variable, writing is done by just assigning the new value to it.


Foreign function interface (external/ROM calls)
-----------------------------------------------
- You can use the ``extsub`` keyword to define the call signature of foreign functions (ROM routines or external routines elsewhere in RAM) in a natural way.
  Calling those generates code that is as efficient or even more efficient as calling regular subroutines.
  No additional stubs are needed.  Y
- High level support of memory banking: an ``extsub`` can be defined with the memory bank number (constant or variable) where the routine is located in,
  and then when you call it as usual, the compiler takes care of the required bank switching.

Optimizations
-------------
- Prog8 contains many compiler optimizations to generate efficient code, but also lacks many optimizations that modern compilers do have.
  While empirical evidence shows that Prog8 generates more efficent code than some C compilers that also target the same 8 bit systems,
  the optimizations it makes on your code aren't super sophisticated.
- For time critical code, it may be worth it to inspect the generated assembly code to see if you can write things differently
  to help the compiler generate more efficient code (or even replace it with hand written inline assembly altogether).
  For example, if you repeat an expression multiple times it will be evaluated every time, so maybe you should store it
  in a variable instead and reuse that variable::

    if board[i+1]==col or board[i+1]-j==col-row or board[i+1]+j==col+row {
        ...do something...
    }

    ; more efficiently written as:

    ubyte boardvalue = board[i+1]
    if boardvalue==col or boardvalue-j==col-row or boardvalue+j==col+row {
        ...do something...
    }
