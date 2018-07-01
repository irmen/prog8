What is a Program?
------------------

A "complete program" is a compiled, assembled, and linked together single unit.
It contains all of the program's code and data and has a certain file format that
allows it to be loaded directly on the target system.   

Most programs will need a tiny BASIC launcher that does a SYS into the generated machine code,
but it is also possible to output just binary programs that can be loaded into memory elsewhere.

Compiling a program
-------------------

Compilation of a program is done by compiling just the main source code module file.
Other modules that the code needs can be imported from within the file.
The compiler will eventually link them together into one output program. 

Program code structure
----------------------

A program is created by compiling and linking *one or more module source code files*.

### Module file

This is a file with the ``.ill`` suffix, without spaces in its name, containing:
- source code comments
- global program options
- imports of other modules
- one or more *code blocks* 

The filename doesn't really matter as long as it doesn't contain spaces.
The full name of the symbols defined in the file is not impacted by the filename. 

#### Source code comments

    A=5    ; set the initial value to 5
    ; next is the code that...

In any file, everything after a semicolon '``;``' is considered a comment and is ignored by the compiler.
If all of the line is just a comment, it will be copied into the resulting assembly source code.
This makes it easier to understand and relate the generated code.


#### Things global to the program

The global program options that can be put at the top of a module file, 
determine the settings for the entire output program.
They're all optional (defaults will be chosen as mentioned below). 
If specified though, they can only occur once in the entire program:

    %output prg
    %address $0801
    %launcher none
    %zp compatible


##### ``%output`` : select output format of the program
- ``raw`` : no header at all, just the raw machine code data 
- ``prg`` : C64 program (with load address header)

The default is ``prg``.


##### ``%address`` : specify start address of the code

- default for ``raw`` output is $c000
- default for ``prg`` output is $0801
- cannot be changed if you select ``prg`` with a ``basic`` launcher; 
  then it is always $081d (immediately after the BASIC program), and the BASIC program itself is always at $0801.
  This is because the C64 expects BASIC programs to start at this address.


##### ``%launcher`` : specify launcher type 

Only relevant when using the ``prg`` output type. Defaults to ``basic``.
- ``basic`` : add a tiny C64 BASIC program, whith a SYS statement calling into the machine code
- ``none`` : no launcher logic is added at all


##### ``%zp`` : select ZeroPage behavior 

- ``compatible`` : only use a few free locations in the ZP
- ``full`` : use the whole ZP for variables, makes the program faster but can't use BASIC or KERNAL routines anymore, and cannot exit cleanly
- ``full-restore`` : like ``full``, but makes a backup copy of the original values at program start. 
    These are restored when exiting the program back to the BASIC prompt
 
Defaults to ``compatible``. 
The exact meaning of these options can be found in the paragraph
about the ZeroPage in the system documentation.


##### Program Start and Entry Point

Your program must have a single entry point where code execution begins. 
The compiler expects a ``start`` subroutine in the ``main`` block for this,
taking no parameters and having no return value.
As any subroutine, it has to end with a ``return`` statement (or a ``goto`` call).

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

Blocks and subroutines are explained below.


#### Using other modules via import

Immediately following the global program options at the top of the module file,
the imports of other modules are placed:

``%import filename``

This reads and compiles the named module source file as part of your current program.
Symbols from the imported module become available in your code, 
without a module or filename prefix.
You can import modules one at a time, and importing a module more than once has no effect. 


#### Blocks, Scopes, and accessing Symbols

Blocks are the separate pieces of code and data of your program. They are combined 
into a single output program.  No code or data can occur outside a block.

    ~ blockname [address] {
        [directives...]
        [variables...]
        [subroutines...]
    }

Block names must be unique in your entire program.
It's possible to omit the blockname, but then you can only refer to the contents of the block via its absolute address,
which is required in this case. If you omit *both* name and address, the block is *ignored* by the compiler (and a warning is displayed).
This is a way to quickly "comment out" a piece of code that is unfinshed or may contain errors that you
want to work on later, because the contents of the ignored block are not fully parsed either.

The address can be used to place a block at a specific location in memory.
Otherwise the compiler will automatically choose the location (usually immediately after
the previous block in memory).
The address must be >= $0200 (because $00-$fff is the ZP and $100-$200 is the cpu stack).

A block is also a *scope* in your program so the symbols in the block don't clash with
symbols of the same name defined elsewhere in the same file or in another file.
You can refer to the symbols in a particular block by using a *dotted name*: ``blockname.symbolname``.
Labels inside a subroutine are appended again to that; ``blockname.subroutinename.label``.

Every symbol is 'public' and can be accessed from elsewhere given its dotted name.


**The special "ZP" ZeroPage block**

Blocks named "ZP" are treated a bit differently: they refer to the ZeroPage.
The contents of every block with that name (this one may occur multiple times) are merged into one.
Its start address is always set to $04, because $00/$01 are used by the hardware 
and $02/$03 are reserved as general purpose scratch registers.


Code elements
-------------

### Data types for Variables and Values

IL65 supports the following data types:

| type                    | storage size      | type identifier | example                                           |
|-------------------------|-------------------|-----------------|---------------------------------------------------|
| unsigned byte           | 1 byte = 8 bits   | ``.byte``       | ``$8f``    |
| unsigned word           | 2 bytes = 16 bits | ``.word``       | ``$8fee``  |
| floating-point          | 5 bytes = 40 bits | ``.float``      | ``1.2345``   (stored in 5-byte cbm MFLPT format)  |
| byte array              | varies            | ``.array``      | @todo      |
| word array              | varies            | ``.wordarray``  | @todo      |
| matrix (of bytes)       | varies            | ``.matrix``     | @todo      |
| string (petscii)        | varies            | ``.str``        | ``"hello."``  (implicitly terminated by a 0-byte)      |
| pascal-string (petscii) | varies            | ``.strp``       | ``"hello."``  (implicit first byte = length, no 0-byte |
| string (screencodes)    | varies            | ``.strs``       | ``"hello."``  (implicitly terminated by a 0-byte)      |
| pascal-string (scr)     | varies            | ``.strps``      | ``"hello."``  (implicit first byte = length, no 0-byte |


You can use the literals ``true`` and ``false`` as 'boolean' values, they are aliases for the 
byte value 1 and 0 respectively.
 
    
Strings in your code will be encoded in either CBM PETSCII or C-64 screencode variants,
this encoding is done by the compiler. PETSCII is the default, if you need screencodes you
have to use the ``s`` variants of the string type identifier.
A string with just one character in it is considered to be a BYTE instead with
that character's PETSCII value.  So if you really need a string of length 1 you must declare
the variable explicitly of type ``.str``.

Floating point numbers are stored in the 5-byte 'MFLPT' format that is used on CBM machines,
but most float operations are specific to the Commodore-64 even because 
routines in the C-64 BASIC and KERNAL ROMs are used.
So floating point operations will only work if the C-64 BASIC ROM (and KERNAL ROM) are banked in, and your code imports the ``c654lib.ill``.
The largest 5-byte MFLPT float that can be stored is: 1.7014118345e+38   (negative: -1.7014118345e+38)

The initial values of your variables will be restored automatically when the program is (re)started,
*except for string variables*. It is assumed these are left unchanged by the program.
If you do modify them in-place, you should take care yourself that they work as
expected when the program is restarted. 


@todo pointers/addresses?  (as opposed to normal WORDs)
@todo signed integers (byte and word)?


### Indirect addressing and address-of

**Address-of:**
The ``#`` prefix is used to take the address of something. This is sometimes useful,
for instance when you want to manipulate the *address* of a memory mapped variable rather than
the value it represents.  You could take the address of a string as well, but that is redundant:
the compiler already treats those as a value that you manipulate via its address.
For most other types this prefix is not supported and will result in a compile error.
The resulting value is simply a 16 bit word.

**Indirect addressing:** The ``[address]`` syntax means: the contents of the memory at address, or "indirect addressing".
By default, if not otherwise known, a single byte is assumed. You can add the ``.byte`` or ``.word`` or ``.float``
type identifier, inside the bracket, to make it clear what data type the address points to.
For instance: ``[address .word]``  (notice the space, to distinguish this from a dotted symbol name).
For an indirect goto call, the 6502 CPU has a special instruction
(``jmp`` indirect) and an indirect subroutine call (``jsr`` indirect) is emitted
using a couple of instructions.


### Conditional Execution

Conditional execution means that the flow of execution changes based on certiain conditions,
rather than having fixed gotos or subroutine calls. IL65 has a *conditional goto* statement for this,
that is translated into a comparison (if needed) and then a conditional branch instruction:
 
        if[_XX] [<expression>] goto <label>

The if-status XX is one of: [cc, cs, vc, vs, eq, ne, true, not, zero, pos, neg, lt, gt, le, ge]
It defaults to 'true' (=='ne', not-zero) if omitted. ('pos' will translate into 'pl', 'neg' into 'mi') 
@todo signed: lts==neg?, gts==eq+pos?, les==neg+eq?, ges==pos?

The <expression> is optional. If it is provided, it will be evaluated first. Only the [true] and [not] and [zero] 
if-statuses can be used when such a *comparison expression* is used. An example is:

        if_not  A > 55  goto  more_iterations


Conditional jumps are compiled into 6502's branching instructions (such as ``bne`` and ``bcc``) so 
the rather strict limit on how *far* it can jump applies. The compiler itself can't figure this
out unfortunately, so it is entirely possible to create code that cannot be assembled successfully.
You'll have to restructure your gotos in the code (place target labels closer to the branch)
if you run into this type of assembler error.


### Including other files or raw assembly code literally

- ``%asminclude "filename.txt", scopelabel``
        This directive can only be used inside a block.
        The assembler will include the file as raw assembly source text at this point, il65 will not process this at all.
        The scopelabel will be used as a prefix to access the labels from the included source code,
        otherwise you would risk symbol redefinitions or duplications.
- ``%asmbinary "filename.bin" [, <offset>[, <length>]]``
        This directive can only be used inside a block.
        The assembler will include the file as binary bytes at this point, il65 will not process this at all.
        The optional offset and length can be used to select a particular piece of the file.
- ``%asm {`` [raw assembly code lines] ``}`` 
        This directive includes raw unparsed assembly code at that exact spot in the program.
        The ``%asm {`` and ``}`` start and end markers each have to be on their own unique line.



### Assignments

Assignment statements assign a single value to a target variable or memory location.

        target = value-expression


### Augmented Assignments

A special assignment is the *augmented assignment* where the value is modified in-place.
Several assignment operators are available: ``+=``, ``-=``, ``&=``, ``|=``, ``^=``, ``<<=``, ``>>=``


### Expressions

In most places where a number or other value is expected, you can use just the number, or a full constant expression.
The expression is parsed and evaluated by Python itself at compile time, and the (constant) resulting value is used in its place.
Ofcourse the special il65 syntax for hexadecimal numbers ($xxxx), binary numbers (%bbbbbb),
and the address-of (#xxxx) is supported. Other than that it must be valid Python syntax.
Expressions can contain function calls to the math library (sin, cos, etc) and you can also use
all builtin functions (max, avg, min, sum etc). They can also reference idendifiers defined elsewhere in your code,
if this makes sense.


### Subroutine Definition

Subroutines are parts of the code that can be repeatedly invoked using a subroutine call from elsewhere.
Their definition, using the sub statement, includes the specification of the required input- and output parameters.
For now, only register based parameters are supported (A, X, Y and paired registers,
the carry status bit SC and the interrupt disable bit SI as specials).
For subroutine return values, the special SZ register is also available, it means the zero status bit.

The syntax is:

        sub   <identifier>  ([proc_parameters]) -> ([proc_results])  {
                ... statements ...
        }

**proc_parameters =**
        comma separated list of "<parametername>:<register>" pairs specifying the input parameters.
        You can omit the parameter names as long as the arguments "line up".
        (actually, the Python parameter passing rules apply, so you can also mix positional
        and keyword arguments, as long as the keyword arguments come last)

**proc_results =**
        comma separated list of <register> names specifying in which register(s) the output is returned.
        If the register name ends with a '?', that means the register doesn't contain a real return value but
        is clobbered in the process so the original value it had before calling the sub is no longer valid.
        This is not immediately useful for your own code, but the compiler needs this information to
        emit the correct assembly code to preserve the cpu registers if needed when the call is made.
        For convenience: a single '?' als the result spec is shorthand for ``A?, X?, Y?`` ("I don't know
        what the changed registers are, assume the worst")


Pre-defined subroutines that are available on specific memory addresses 
(in system ROM for instance) can also be defined using the 'sub' statement.
To do this you assign the routine's memory address to the sub:

        sub  <identifier>  ([proc_parameters]) -> ([proc_results])  = <address>

example:

        sub  CLOSE  (logical: A) -> (A?, X?, Y?)  = $FFC3"


### Subroutine Calling

You call a subroutine like this:

        subroutinename_or_address ( [arguments...] )

or:

        subroutinename_or_address ![register(s)] ( [arguments...] )

If the subroutine returns one or more values as results, you must use an assignment statement
to store those values somewhere:

        outputvar1, outputvar2  =  subroutine ( arg1, arg2, arg3 )

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



Debugging (with Vice)
---------------------

The ``%breakpoint`` directive instructs the compiler to put
a *breakpoint* at that position in the code. It's a logical breakpoint instead of a physical
BRK instruction because that will usually halt the machine altogether instead of breaking execution.
Instead of this, a NOP instruction is generated and in a special output file the list of breakpoints is written.

This file is called "programname.vice-mon-list" and is meant to be used by the Vice C-64 emulator.
It contains a series of commands for Vice's monitor, this includes source labels and the breakpoint settings.
If you use the vice autostart feature of the compiler, it will be automatically processed by Vice.
If you launch Vice manually, you can use a command line option to load this file: ``x64 -moncommands programname.vice-mon-list`` 

Vice will use the label names in memory disassembly, and will activate the breakpoints as well
so if your program runs and it hits a breakpoint, Vice will halt execution and drop into the monitor.


Troubleshooting
---------------

Getting assembler error about undefined symbols such as ``not defined 'c64flt'``?
This happens when your program uses floating point values, and you forgot to import the ``c64lib``.
If you use floating points, the program will need routines from that library.
Fix it by adding an ``%import c64lib``.



@Todo
-----

### IF_XX:

if[_XX] [<expression>] {
        ...
}
[ else {
        ...     ; evaluated when the condition is not met
} ]


==> DESUGARING ==>

(no else:)

                if[_!XX] [<expression>] goto il65_if_999_end          ; !XX being the conditional inverse of XX
                .... (true part)
il65_if_999_end ; code continues after this


(with else):
                if[_XX] [<expression>] goto il65_if_999
                ... (else part)
                goto il65_if_999_end
il65_if_999     ... (true part)
il65_if_999_end ; code continues after this


### IF  X  <COMPARISON>  Y:

==> DESUGARING ==>
   compare X, Y
   if_XX goto ....
   XX based on <COMPARISON>.




### While


while[_XX] <expression> {
	...
	continue
	break
}

==> DESUGARING ==>

	goto il65_while_999_check    ; jump to the check
il65_while_999
	... (code)
	goto  il65_while_999          ;continue
	goto  il65_while_999_end      ;break
il65_while_999_check
        if[_XX] <expression> goto il65_while_999  ; loop condition
il65_while_999_end      ; code continues after this


### Repeat

repeat {
	...
	continue
	break
} until[_XX] <expressoin>

==> DESUGARING ==>

il65_repeat_999
        ... (code)
        goto il65_repeat_999          ;continue
        goto il65_repeat_999_end      ;break
        if[_!XX] <expression> goto il65_repeat_999        ; loop condition via conditional inverse of XX
il65_repeat_999_end         ; code continues after this


### For

for <loopvar> = <from_expression> to <to_expression> [step <step_expression>] {
	...
	break
	continue
}


@todo how to do signed integer loopvars?


==> DESUGARING ==>

        loopvar = <from_expression>
        compare loopvar, <to_expression>
        if_ge goto il65_for_999_end       ; loop condition
        step = <step_expression>        ; (store only if step < -1 or step > 1)
il65_for_999
        goto il65_for_999_end        ;break
        goto il65_for_999_loop       ;continue
        ....  (code)
il65_for_999_loop
        loopvar += step         ; (if step > 1 or step < -1)
        loopvar++               ; (if step == 1)
        loopvar--               ; (if step == -1)
        goto il65_for_999         ; continue the loop
il65_for_999_end        ; code continues after this



### Macros

@todo macros are meta-code (written in Python syntax) that actually runs in a preprecessing step
during the compilation, and produces output value that is then replaced on that point in the input source.
Allows us to create pre calculated sine tables and such. Something like:

        var .array sinetable ``[sin(x) * 10 for x in range(100)]``


### Memory Block Operations

@todo matrix,list,string memory block operations:
- matrix type operations (whole matrix, per row, per column, individual row/column)
  operations: set, get, copy (from another matrix with the same dimensions, or list with same length),
  shift-N (up, down, left, right, and diagonals, meant for scrolling)
  rotate-N (up, down, left, right, and diagonals, meant for scrolling)
  clear (set whole matrix to the given value, default 0)

- list operations (whole list, individual element)
  operations: set, get, copy (from another list with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole list to the given value, default 0)

- list and matrix operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on lists.

- matrix with row-interleave can only be a memory mapped variable and can be used to directly
  access a rectangular area within another piece of memory - such as a rectangle on the (character) screen

these should call (or emit inline) optimized pieces of assembly code, so they run as fast as possible



### Bitmap Definition (for Sprites and Characters)

to define CHARACTERS (8x8 monochrome or 4x8 multicolor = 8 bytes)
--> PLACE in memory on correct address (???k aligned)
and SPRITES (24x21 monochrome or 12x21 multicolor = 63 bytes)
--> PLACE in memory on correct address (base+sprite pointer, 64-byte aligned)

