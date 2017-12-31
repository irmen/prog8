IL65 / 'Sick' - Experimental Programming Language for 8-bit 6502/6510 microprocessors
=====================================================================================

*Written by Irmen de Jong (irmen@razorvine.net)*

*Software license: GNU GPL 3.0, see LICENSE*


This is an experimental programming language for the 8-bit 6502/6510 microprocessor from the late 1970's and 1980's
as used in many home computers from that era. IL65 is a medium to low level programming language,
which aims to provide many conveniences over raw assembly code (even when using a macro assembler):

- reduction of source code length
- easier program understanding (because it's higher level, and more terse)
- option to automatically run the compiled program in the Vice emulator  
- modularity, symbol scoping, subroutines
- subroutines have enforced input- and output parameter definitions
- various data types other than just bytes (16-bit words, floats, strings, 16-bit register pairs)
- automatic variable allocations, automatic string variables and string sharing
- automatic type conversions
- floating point operations
- automatically preserving and restoring CPU registers state, when calling routines that otherwise would clobber these 
- abstracting away low level aspects such as zero page handling, program startup, explicit memory addresses
- breakpoints, that let the Vice emulator drop into the monitor if execution hits them
- source code labels automatically loaded in Vice emulator so it can show them in disassembly
- conditional gotos
- @todo: loops
- @todo: memory block operations


It still allows for low level programming however and inline assembly blocks
to write performance critical pieces of code, but otherwise compiles fairly straightforwardly
into 6502 assembly code. This resulting code is assembled into a binary program by using
an external macro assembler, [64tass](https://sourceforge.net/projects/tass64/).
It can be compiled pretty easily for various platforms (Linux, Mac OS, Windows) or just ask me
to provide a small precompiled executable if you need that.
You need [Python 3.5](https://www.python.org/downloads/) or newer to run IL65 itself.

IL65 is mainly targeted at the Commodore-64 machine, but should be mostly system independent.


MEMORY MODEL
------------

Most of the 64 kilobyte address space can be accessed by your program.

| type            | memory area             | note                                                            |
|-----------------|-------------------------|-----------------------------------------------------------------|
| Zero page       | ``$00`` - ``$ff``       | contains many sensitive system variables                        |
| Hardware stack  | ``$100`` - ``$1ff``     | is used by the CPU and should normally not be accessed directly |
| Free RAM or ROM | ``$0200`` - ``$ffff``   | free to use memory area, often a mix of RAM and ROM             |


A few memory addresses are reserved and cannot be used freely by your own code,
because they have a special hardware function, or are reserved for internal use by the compiler:

| reserved       | address    |
|----------------|------------|
| data direction | ``$00``    |
| bank select    | ``$01``    |
| scratch var #1 | ``$02``    |
| scratch var #2 | ``$03``    |
| NMI vector     | ``$fffa``  |
| RESET vector   | ``$fffc``  |
| IRQ vector     | ``$fffe``  |

A particular 6502/6510 machine such as the Commodore-64 will have many other special addresses due to:

- ROMs installed in the machine (BASIC, kernel and character generator roms)
- memory-mapped I/O registers (for the video and sound chip for example)
- RAM areas used for screen graphics and sprite data.


### Usable Hardware Registers

The following 6502 hardware registers are directly accessible in your code (and are reserved symbols):

- ``A``, ``X``, ``Y``
- ``AX``, ``AY``, ``XY`` (surrogate registers: 16-bit combined register pairs in LSB byte order lo/hi)
- ``SC``  (status register's Carry flag)
- ``SI``  (status register's Interrupt Disable flag)


### Zero Page ("ZP")

The zero page locations ``$02`` - ``$ff`` can be regarded as 254 other registers because
they take less clock cycles to access and need fewer instruction bytes than access to other memory locations.
Theoretically you can use all of them in your program but there are a few limitations:
- several locations (``$02``, ``$03``, ``$fb - $fc``, ``$fd - $fe``) are reserved for internal use as scratch registers by IL65
- most other addresses often are in use by the machine's operating system or kernal,
  and overwriting them can crash the machine. Your program must take over the entire
  system to be able to safely use all zero page locations.
- it's often more convenient to let IL65 allocate the particular locations for you and just
  use symbolic names in your code.

For the Commodore-64 here is a list of free-to-use zero page locations even when its BASIC and KERNAL are active:

``$02``; ``$03``; ``$04``; ``$05``;  ``$06``; ``$2a``;  ``$52``; 
``$f7`` - ``$f8``;  ``$f9`` - ``$fa``;  ``$fb`` - ``$fc``;  ``$fd`` - ``$fe``

The four reserved locations mentioned above are subtracted from this set, leaving you with
five 1-byte and two 2-byte usable zero page registers.
IL65 knows about all this: it will use the above zero page locations to place its ZP variables in,
until they're all used up. You can instruct it to treat your program as taking over the entire
machine, in which case (almost) all of the zero page locations are suddenly available for variables.
IL65 can generate a special routine that saves and restores the zero page to let your program run
and return safely back to the system afterwards - you don't have to take care of that yourself.

**IRQ and the Zero page:**

The normal IRQ routine in the C-64's kernal will read and write several locations in the zero page:

``$a0 - $a2``; ``$91``; ``$c0``; ``$c5``; ``$cb``; ``$f5 - $f6``

These locations will not be used by the compiler for zero page variables, so your variables will
not interfere with the IRQ routine and vice versa. This is true for the normal zp mode but also 
for the mode where the whole zp has been taken over.


@todo: some global way (in ZP block) to promote certian other blocks/variables from that block or even
subroutine to the zeropage.  Don't do this in the block itself because it's a global optimization
and if blocks require it themselves you can't combine various modules anymore once ZP runs out.
  


DATA TYPES
----------

IL65 supports the following data types:

| type               | size       | type identifier | example                                           |
|--------------------|------------|-----------------|---------------------------------------------------|
| (unsigned) byte    | 8 bits     | ``.byte``       | ``$8f``    |
| (unsigned) integer | 16 bits    | ``.word``       | ``$8fee``  |
| boolean            | 1 byte     |                 | ``true``, ``false`` (aliases for the numeric values 1 and 0) |
| character          | 1 byte     |                 | ``'@'`` (converted to a numeric byte)             |
| floating-point     | 40 bits    | ``.float``      | ``1.2345``   (stored in 5-byte cbm MFLPT format)    |
| string             | variable   | ``.text``, ``.stext``   | ``"hello."``  (implicitly terminated by a 0-byte) |
| pascal-string      | variable   | ``.ptext``, ``.pstext`` | ``"hello."``  (implicit first byte = length, no 0-byte |
| address-of         | 16 bits    |                 | ``#variable``    |
| indirect           | variable   |                 | ``[ address ]``  |

Strings can be writen in your code as CBM PETSCII or as C-64 screencode variants,
these will be translated by the compiler. PETSCII is the default, if you need screencodes you
have to use the ``s`` variants of the type identifier.
If you write a string with just one character in it, it is *always* considered to be a BYTE instead with
that character's PETSCII value.  So if you really need a string of length 1 you must declare it 
explicitly as a variable of type ``.text``, you cannot put ``"x"`` as a subroutine argument where
the subroutine expects (the address of) a string.  IL65's type system is unfortunately not strict enough to 
avoid this mistake, but it does print a warning if the situation is detected.

For many floating point operations, the compiler has to use routines in the C-64 BASIC and KERNAL ROMs.
So they will only work if the BASIC ROM (and KERNAL ROM) are banked in, and your code imports the ``c654lib.ill``.

The largest 5-byte MFLPT float that can be stored is: 1.7014118345e+38   (negative: -1.7014118345e+38)

The ``#`` prefix is used to take the address of something. This is sometimes useful,
for instance when you want to manipulate the *address* of a memory mapped variable rather than
the value it represents.  You can take the address of a string as well, but the compiler already
treats those as a value that you manipulate via its address, so the ``#`` is ignored here.
For most other types this prefix is not supported.

**Indirect addressing:** The ``[address]`` syntax means: the contents of the memory at address, or "indirect addressing".
By default, if not otherwise known, a single byte is assumed. You can add the ``.byte`` or ``.word`` or ``.float``
type identifier suffix to make it clear what data type the address points to.
This addressing mode is only supported for constant (integer) addresses and not for variable types,
unless it is part of a subroutine call statement. For an indirect goto call, the 6502 CPU has a special instruction
(``jmp`` indirect) and an indirect subroutine call (``jsr`` indirect) is synthesized using a couple of instructions.


PROGRAM STRUCTURE
-----------------

In IL65 every line in the source file can only contain *one* statement or declaration.
Compilation is done on *one* main source code file, but other files can be imported.

### Comments

Everything after a semicolon '``;``' is a comment and is ignored.
If the comment is the only thing on the line, it is copied into the resulting assembly source code.
This makes it easier to understand and relate the generated code.


### Output Modes

The default format of the generated program is a "raw" binary where code starts at ``$c000``.
You can generate other types of programs as well, by telling IL65 via an output mode statement
at the beginning of your program:

| mode declaration     | meaning                                                                            |
|----------------------|------------------------------------------------------------------------------------|
| ``output raw``       | no load address bytes                                                              |
| ``output prg``       | include the first two load address bytes, (default is ``$0801``), no BASIC program |
| ``output prg,basic`` | as 'prg', but include a BASIC start program with SYS call, default code start is immediately after the BASIC program at ``$081d``, or after that. |
|                      |   |
| ``address $0801``    | override program start address (default is set to ``$c000`` for raw mode and ``$0801`` for C-64 prg mode). Cannot be used if output mode is ``prg,basic`` because BASIC programs always have to start at ``$0801``. |


### ZeroPage Options

You can tell the compiler how to treat the *zero page*. Normally it is considered a 'no-go' area
except for the frew free locations mentioned under "Memory Model". 
However you can specify some options globally in your program to change this behavior:

- ``zp clobber``      
        Use the whole zeropage for variables. It is not possible to exit your program
        correctly back to BASIC, other than resetting the machine.
        It does make your program smaller and faster because many more variables can
        be stored in the ZP, which is more efficient. 
- ``zp clobber, restore``
        Use the whole zeropage, but make a backup copy of the original values at program start. 
        When your program exits, the original ZP is restored (except for the software jiffy clock
        in ``$a0 - $a2``) and you drop back to the BASIC prompt.
        Not that the default IRQ routine is *still enabled* when your program is entered!
        See the paragraph on the zero page for more info about this.

If you use ``zp clobber``, you can no longer use most BASIC or KERNAL routines,
because these depend on most of the locations in the ZP. This includes the floating-point
logic and several utility routines that do I/O, such as  ``print_string``.
        

### Program Entry Point

Every program has to have one entry point where code execution begins.
The compiler looks for the ``start`` label in the ``main`` block for this.
For proper program termination, this block has to end with a ``return`` statement (or a ``goto`` call).
Blocks and other details are described below.


### Blocks

~ blockname [address] {
        statements
}

The blockname "ZP" is reserved and always means the ZeroPage. Its start address is always set to $04,
because $00/$01 are used by the hardware and $02/$03 are reserved as general purpose scratch registers.

Block names cannot occur more than once, EXCEPT 'ZP' where the contents of every occurrence of it are merged.
Block address must be >= $0200 (because $00-$fff is the ZP and $100-$200 is the cpu stack)

You can omit the blockname but then you can only refer to the contents of the block via its absolute address,
which is required in this case. If you omit both, the block is ignored altogether (and a warning is displayed).
This is a way to quickly "comment out" a piece of code that is unfinshed or may contain errors that you
want to work on later, because the contents of the ignored block are not syntactically parsed.


### Importing, Including and Binary-Including Files

import "filename[.ill]"
        Must be used *after* any global option statements such as ``output``,
        and can only be used outside of a block. But can otherwise occur between any blocks.
        Reads everything from the named IL65 file at this point and compile it as a normal part of the program.

asminclude "filename.txt", scopelabel
        Can only be used in a block.
        The assembler will include the file as asm source text at this point, il65 will not process this at all.
        The scopelabel will be used as a prefix to access the labels from the included source code,
        otherwise you would risk symbol redefinitions or duplications.

asmbinary "filename.bin" [, <offset>[, <length>]]
        Can only be used in a block.
        The assembler will include the file as binary bytes at this point, il65 will not process this at all.
        The optional offset and length can be used to select a particular piece of the file.



### Assignments

Assignment statements assign a single value to one or more variables or memory locations.
If you know that you have to assign the same value to more than one thing at once, it is more
efficient to write it as a multi-assign instead of several separate assignments. The compiler
tries to detect this situation however and optimize it itself if it finds the case.

        target = value-expression
        target1 = target2 = target3 [,...] = value-expression


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

        proc_parameters = comma separated list of "<parametername>:<register>" pairs specifying the input parameters.
                          You can omit the parameter names as long as the arguments "line up".
                          (actually, the Python parameter passing rules apply, so you can also mix positional
                          and keyword arguments, as long as the keyword arguments come last)

        proc_results = comma separated list of <register> names specifying in which register(s) the output is returned.
                       If the register name ends with a '?', that means the register doesn't contain a real return value but
                       is clobbered in the process so the original value it had before calling the sub is no longer valid.
                       This is not immediately useful for your own code, but the compiler needs this information to
                       emit the correct assembly code to preserve the cpu registers if needed when the call is made.
                       For convenience: a single '?' als the result spec is shorthand for ``A?, X?, Y?`` ("I don't know
                       what the changed registers are, assume the worst")


Subroutines that are pre-defined on a specific memory location (usually routines from ROM),
can also be defined using the 'sub' statement. But in this case you don't supply a block with statements,
but instead assign a memory address to it:

        sub  <identifier>  ([proc_parameters]) -> ([proc_results])  = <address>

        example:  "sub  CLOSE  (logical: A) -> (A?, X?, Y?)  = $FFC3"


### Subroutine Calling

You call a subroutine like this:
        subroutinename_or_address [!]  ( [arguments...] )

If the subroutine returns one or more values as results, you must use an assignment statement
to store those values somewhere:
        outputvar1, outputvar2  =  subroutine ( arg1, arg2, arg3 )

The output variables must occur in the correct sequence of return registers as specified
in the subroutine's definiton. It is possible to not specify any of them but the compiler
will issue a warning then if the result values of a subroutine call are discarded.
Even if the subroutine returns something in a register that already is the correct one
you want to keep, you'll have to explicitly assign the return value to that register.
If you omit it, no return value is stored at all (well, unless you call the subroutine without
register preserving, see the next paragraph.)


Normally, the registers are preserved when calling the subroutine and restored on return (except
the ones that explictly take result values from the call).
If you add a '!' after the name, no register preserving is done and the call essentially
is just a single JSR instruction.
Arguments should match the subroutine definition. You are allowed to omit the parameter names.
If no definition is available (because you're directly calling memory or a label or something else),
you can freely add arguments (but in this case they all have to be named).

To jump to a subroutine (without returning), prefix the subroutine call with the word 'goto'.
Unlike gotos in other languages, here it take arguments as well, because it
essentially is the same as calling a subroutine and only doing something different when it's finished.


@todo support call non-register args (variable parameter passing)


### Conditional Execution Flow

Conditional execution flow means that the flow of execution changes based on certiain conditions,
rather than having fixed gotos or subroutine calls. IL65 has a *conditional goto* statement for this,
that is translated into a comparison (if needed) and then a conditional branch instruction:
 
        if[_XX] [<expression>] goto <label>

The if-status XX is one of: [cc, cs, vc, vs, eq, ne, true, not, zero, pos, neg, lt, gt, le, ge]
It defaults to 'true' (=='ne', not-zero) if omitted. ('pos' will translate into 'pl', 'neg' into 'mi') 
@todo signed: lts==neg?, gts==eq+pos?, les==neg+eq?, ges==pos?

The <expression> is optional. If it is provided, it will be evaluated first. Only the [true] and [not] and [zero] 
if-statuses can be used when such a *comparison expression* is used. An example is:

        ``if_not  A > 55  goto  more_iterations``  


Conditional jumps are compiled into 6502's branching instructions (such as ``bne`` and ``bcc``) so 
the rather strict limit on how *far* it can jump applies. The compiler itself can't figure this
out unfortunately, so it is entirely possible to create code that cannot be assembled successfully.
You'll have to restructure your gotos in the code (place target labels closer to the branch)
if you run into this type of assembler error.


Debugging (with Vice)
---------------------

The ``breakpoint`` statement is a special statement that instructs the compiler to put
a *breakpoint* at that position in the code. It's a logical breakpoint instead of a physical
BRK instruction because that will usually halt the machine altogether instead of breaking execution.
Instead, a NOP instruction is generated and in a special output file the list of breakpoints is written.

This file is called "yourprogramname.vice-mon-list" and is meant to be used by the Vice C-64 emulator.
It contains a series of commands for Vice's monitor, this includes source labels and the breakpoint settings.
If you use the vice autostart feature of the compiler, it will be automatically processed by Vice.
If you launch Vice manually, you can use a command line option to load the file: ``x64 -moncommands yourprogramname.vice-mon-list`` 

Vice will use the label names in memory disassembly, and will activate the breakpoints as well
so if your program runs and it hits a breakpoint, Vice will halt execution and drop into the monitor.



TODOS
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

these should call (or emit inline) optimized pieces of assembly code, so they run as fast as possible



### Register Preservation Block

preserve [regs] { .... }     adds register preservation around the containing code default = all 3 regs, or specify which.
nopreserve [regs] { .... }     removes register preservation on all statements in the block that would otherwise have it.


### Bitmap Definition (for Sprites and Characters)

to define CHARACTERS (8x8 monochrome or 4x8 multicolor = 8 bytes)
--> PLACE in memory on correct address (???k aligned)
and SPRITES (24x21 monochrome or 12x21 multicolor = 63 bytes)
--> PLACE in memory on correct address (base+sprite pointer, 64-byte aligned)


### More Datatypes

@todo 24 and 32 bits integers, unsigned and signed?

### Some support for simple arithmetic

        A *= Y
        A = X * Y
        A /= Y
        A = Y / Y

@todo multiplication routines (8*8 -> 16, 8*16 -> 16, 16*16->16 (or 32?))
@todo division routines
