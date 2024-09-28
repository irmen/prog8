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


No linker
---------
- Even though your programs can consist of many separate module files, the compiler always outputs a single program file. There is no separate linker step.
  Currently, it's not easily possible to integrate object files created elsewhere.
- The prog8 compiler is self-contained in a single jar file. You do need 1 external tool namely 64tass, which performs the assembler step.


Data types
----------
- There are byte, word (16 bits) and float datatypes for numbers. There are no bigger integer types natively available.
- There is no automatic type enlargement: calculations remain within the data type of the operands. Any overflow silently wraps or truncates.
  You'll have to add explicit casts to increase the size of the value if required.
  For example when adding two byte variables having values 100 and 200, the result won't be 300, because that doesn't fit in a byte. It will be 44.
  You'll have to cast one or both of the *operands* to a word type first if you want to accomodate the actual result value of 300.


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
- There is no function overloading (except for a couple of builtin functions).
- Some subroutine types can return multiple return values, and you can multi-assign those in a single statement.
- Because every declared variable allocates some memory, it might be beneficial to share the same variables over different subroutines
  instead of defining the same sort of variables in every subroutine.
  This reduces the memory needed for variables. A convenient way to do this is by using nested subroutines - these can easily access the
  variables declared in their parent subroutine(s).
- Everything in prog8 is publicly accessible from everywhere else (via fully scoped names) - there is no notion of private or public symbol accessibility.


Pointers
--------
- There is no specific pointer datatype.
  However, variables of the ``uword`` datatype can be used as a pointer to one of the possible 65536 memory locations,
  so the value it points to is always a single byte. This is similar to ``uint8_t*`` from C.
  You have to deal with the uword manually if the object it points to is something different.
- Note that there is the ``peekw`` builtin function that *does* allow you to directy obtain the *word* value at the given memory location.
  So if you use this, you can use uword pointers as pointers to word values without much hassle.
- "dereferencing" a uword pointer is done via array indexing (where index value can be 0-65535!) or via the memory read operator ``@(ptr)``, or ``peek/peekw(ptr)``.
- Pointers don't have to be a variable, you can immediately access the value of a given memory location using ``@($d020)`` for instance.
  Reading is done by assigning it to a variable, writing is done by just assigning the new value to it.


Strings and Arrays
------------------
- these are allocated once, statically, and never resized.
- they are mutable: you can change their contents, but always keep the original storage size in mind to avoid overwriting memory outside of the buffer.
- Maximum size is 256 bytes (512 for split word arrays)

Foreign function interface (external/ROM calls)
-----------------------------------------------
- You can use the ``romsub`` keyword to define the call signature of foreign functions (usually ROM routines, hence the name) in a natural way.
  Calling those generates code that is as efficient or even more efficient as calling regular subroutines.
  No additional stubs are needed.  (unless there is bank switching going on, but this *may* be improved in a future language version)

Optimizations
-------------
- Prog8 contains many compiler optimizations to generate efficent code, but also lacks many optimizations that modern compilers do have.
  While empirical evidence shows that Prog8 generates more efficent code than some C compilers that also target the same 8 bit systems,
  it still is limited in how sophisticated the optimizations are that it performs on your code.
- For time critical code, it may be worth it to inspect the generated assembly code to see if you could write things differently
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
