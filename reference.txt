------------------------------------------------------------
il65 - "Intermediate Language for 6502/6510 microprocessors"
------------------------------------------------------------
    Written by Irmen de Jong (irmen@razorvine.net)
    License: GNU GPL 3.0, see LICENSE
------------------------------------------------------------


The python program parses it and generates 6502 assembler code.
It uses the 64tass macro cross assembler to assemble it into binary files.



Memory Model
------------

Zero page:      $00 - $ff
Hardware stack: $100 - $1ff
Free RAM/ROM:   $0200 - $ffff

Reserved:

data direction  $00
bank select     $01
NMI VECTOR      $fffa
RESET VECTOR    $fffc
IRQ VECTOR      $fffe

A particular 6502/6510 machine such as the Commodore-64 will have many other 
special addresses due to:
    - ROMs installed in the machine (basic, kernel and character generator roms)
    - memory-mapped I/O registers (for the video and sound chip for example)
    - RAM areas used for screen graphics and sprite data.


Usable Hardware registers:
    A, X, Y,
    AX, AY, XY (16-bit combined register pairs)
    SC  (status register Carry flag)
    These cannot occur as variable names - they will always refer to the hardware registers.


The zero page locations $02-$ff can be regarded as 254 other registers.
Free zero page addresses on the C-64:
    $02,$03   # reserved as scratch addresses
    $04,$05
    $06
    $0a
    $2a
    $52
    $93
    $f7,$f8
    $f9,$fa
    $fb,$fc
    $fd,$fe



IL program parsing structure:
-----------------------------


OUTPUT MODES:
-------------
output raw     ; no load address bytes
output prg     ; include the first two load address bytes, (default is $0801), no basic program
output prg,sys ; include the first two load address bytes, basic start program with sys call to code, default code start
                ;   immediately after the basic program at $081d, or beyond.

address $0801   ; override program start address (default is set to $c000 for raw mode and $0801 for c-64 prg mode)
                ; cannot be used if output mode is prg,sys because basic programs always have to start at $0801


data types:
    byte    8 bits      $8f    (unsigned, @todo signed bytes)
    int     16 bits     $8fee  (unsigned, @todo signed ints)
    bool    true/false  (aliases for the integer values 1 and 0, not a true datatype by itself)
    char    '@' (converted to a byte)
    float   40 bits     1.2345     (stored in 5-byte cbm MFLPT format)
    @todo 24 and 32 bits integers, unsigned and signed?
    string  0-terminated sequence of bytes  "hello."  (implicit 0-termination byte)
    pstring sequence of bytes where first byte is the length. (no 0-termination byte)
    For strings, both petscii and screencode variants can be written in source, they will be translated at compile/assembler time.


    Note: for many floating point operations, the compiler uses routines in the C64 BASIC and KERNAL ROMs.
    So they will only work if the BASIC ROM (and KERNAL ROM) are banked in.
    largest 5-byte MFLPT float: 1.7014118345e+38   (negative: -1.7014118345e+38)


    Note: with the # prefix you can take the address of something. This is sometimes useful,
    for instance when you want to manipulate the ADDRESS of a memory mapped variable rather than
    the value it represents.  You can take the address of a string as well, but the compiler already
    treats those as a value that you manipulate via its address, so the # is ignored here.



BLOCKS
------

~ blockname [address] {
        statements
}

The blockname "ZP" is reserved and always means the ZeroPage. Its start address is always set to $04,
because $00/$01 are used by the hardware and $02/$03 are reserved as general purpose scratch registers.

Block names cannot occur more than once, EXCEPT 'ZP' where the contents of every occurrence of it are merged.
Block address must be >= $0200 (because $00-$fff is the ZP and $100-$200 is the cpu stack)

You can omit the blockname but then you can only refer to the contents of the block via its absolute address,
which is required in this case. If you omit both, the block is ignored altogether (and a warning is displayed).


IMPORTING, INCLUDING and BINARY-INCLUDING files
-----------------------------------------------

import "filename[.ill]"
        Can only be used outside of a block (usually at the top of your file).
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



MACROS
------

@todo macros are meta-code (written in Python syntax) that actually runs in a preprecessing step
during the compilation, and produces output value that is then replaced on that point in the input source.
Allows us to create pre calculated sine tables and such. Something like:

        var .array sinetable ``[sin(x) * 10 for x in range(100)]``



EXPRESSIONS
-----------

In most places where a number or other value is expected, you can use just the number, or a full constant expression.
The expression is parsed and evaluated by Python itself at compile time, and the (constant) resulting value is used in its place.
Ofcourse the special il65 syntax for hexadecimal numbers ($xxxx), binary numbers (%bbbbbb),
and the address-of (#xxxx) is supported. Other than that it must be valid Python syntax.
Expressions can contain function calls to the math library (sin, cos, etc) and you can also use
all builtin functions (max, avg, min, sum etc). They can also reference idendifiers defined elsewhere in your code,
if this makes sense.

The syntax "[address]" means: the contents of the memory at address.
By default, if not otherwise known, a single byte is assumed. You can add the ".byte" or ".word" or ".float" suffix
to make it clear what data type the address points to.

Everything after a semicolon ';' is a comment and is ignored.
# @todo Everything after a double semicolon ';;' is a comment and is ignored, but is copied into the resulting assembly source code.


    
FLOW CONTROL
------------

Required building blocks: additional forms of 'go' statement: including an if clause, comparison statement.

- a primitive conditional branch instruction (special case of 'go'): directly translates to a branch instruction:
        if[_XX] go <label>
  XX is one of: (cc, cs, vc, vs, eq, ne, pos, min,
  lt==cc, lts==min,  gt==eq+cs, gts==eq+pos,  le==cc+eq, les==neg+eq,  ge==cs, ges==pos)
  and when left out, defaults to ne (not-zero, i.e. true)
  NOTE: some combination branches such as cc+eq an be peephole optimized see http://www.6502.org/tutorials/compare_beyond.html#2.2

- conditional go with expression: where the if[_XX] is followed by a <expression>
  in that case, evaluate the <expression> first (whatever it is) and then emit the primitive if[_XX] go
        if[_XX] <expression> go <label>
  eventually translates to:
        <expression-code>
        bXX <label>

- comparison statement: compares left with right:  compare <first_value>, <second_value>
  (and keeps the comparison result in the status register.)
  this translates into a lda first_value, cmp second_value sequence after which a conditional branch is possible.



IF_XX:
------
if[_XX] [<expression>] {
        ...
}
[ else {
        ...     ; evaluated when the condition is not met
} ]


==> DESUGARING ==>

(no else:)

                if[_!XX] [<expression>] go il65_if_999_end          ; !XX being the conditional inverse of XX
                .... (true part)
il65_if_999_end ; code continues after this


(with else):
                if[_XX] [<expression>] go il65_if_999
                ... (else part)
                go il65_if_999_end
il65_if_999     ... (true part)
il65_if_999_end ; code continues after this


IF  X  <COMPARISON>  Y:
-----------------------

==> DESUGARING ==>
   compare X, Y
   if_XX go ....
   XX based on <COMPARISON>.





WHILE:
------
while[_XX] <expression> {
	...
	continue
	break
}

==> DESUGARING ==>

	go il65_while_999_check    ; jump to the check
il65_while_999
	... (code)
	go  il65_while_999          ;continue
	go  il65_while_999_end      ;break
il65_while_999_check
        if[_XX] <expression> go il65_while_999  ; loop condition
il65_while_999_end      ; code continues after this



REPEAT:
------

repeat {
	...
	continue
	break
} until[_XX] <expressoin>

==> DESUGARING ==>

il65_repeat_999
        ... (code)
        go il65_repeat_999          ;continue
        go il65_repeat_999_end      ;break
        if[_!XX] <expression> go il65_repeat_999        ; loop condition via conditional inverse of XX
il65_repeat_999_end         ; code continues after this



FOR:
----

for <loopvar> = <from_expression> to <to_expression> [step <step_expression>] {
	...
	break
	continue
}


@todo how to do signed integer loopvars?


==> DESUGARING ==>

        loopvar = <from_expression>
        compare loopvar, <to_expression>
        if_ge go il65_for_999_end       ; loop condition
        step = <step_expression>        ; (store only if step < -1 or step > 1)
il65_for_999
        go il65_for_999_end        ;break
        go il65_for_999_loop       ;continue
        ....  (code)
il65_for_999_loop
        loopvar += step         ; (if step > 1 or step < -1)
        loopvar++               ; (if step == 1)
        loopvar--               ; (if step == -1)
        go il65_for_999         ; continue the loop
il65_for_999_end        ; code continues after this



MEMORY BLOCK OPERATIONS:

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


these call (or emit inline) optimized pieces of assembly code, so they run as fast as possible



SUBROUTINES DEFINITIONS
-----------------------

External subroutines for instance defined in ROM, can be defined using the 'subx' statement.

subx   <identifier>    ([proc_parameters]) -> ([proc_results])    <address>

proc_parameters = sequence of "<parametername>:<register>" pairs that specify what the input parameters are
proc_results = sequence of <register> names that specify in which register(s) the output is returned
               if the name ends with a '?', that means the register doesn't contain a real return value but
               is clobbered in the process so the original value it had before calling the sub is no longer valid.

example:  "subx   CLOSE    (logical: A) -> (A?, X?, Y?)       $FFC3"


ISOLATION (register preservation when calling subroutines):  @todo isolation

 isolate [regs] { .... }       that adds register preservation around the containing code default = all 3 regs, or specify which.
 fcall          ->      fastcall, doesn't do register preservations
 call           ->      as before, alsways does it, even when already in isolate block



@todo user defined subroutines


SUBROUTINE CALLS
----------------

CALL and FCALL:
        They are just inserting a call to the specified location or subroutine.
        [F]CALL: calls subroutine and continue afterwards ('gosub'):
                [f]call <subroutine> / <label> / <address> / `[`indirect-pointer`]`  [arguments...]

        A 'call' preserves all registers when doing the procedure call and restores them afterwards.
        'fcall' (fast call) doesn't preserve registers, so generates code that is a lot faster.
        It's basically one jmp or jsr instruction. It can clobber register values because of this.
        If you provide arguments (not required) these will be matched to the subroutine's parameters.
        If you don't provide arguments, it is assumed you have prepared the correct registers etc yourself.


The following contemporary syntax to call a subroutine is also available:
        subroutine `(` [arguments...] `)`
        subroutine! `(` [arguments...] `)`
        These are understood as:  "call subroutine arguments" and "fcall subroutine arguments" respectively.
        You can only call a subroutine or label this way. This syntax cannot be used
        to call a memory address or variable, you have to use the call statement for that.

GO:
        'go' continues execution with the specified routine or address and doesn't retuurn (it is a 'goto'):
                go <subroutine> / <label> / <address> / [indirect-pointer]


@todo support call non-register args (variable parameter passing)
@todo support call return values (so that you can assign these to other variables, and allows the line to be a full expression)


@todo BITMAP DEFINITIONS:
to define CHARACTERS (8x8 monochrome or 4x8 multicolor = 8 bytes)
--> PLACE in memory on correct address (???k aligned)
and SPRITES (24x21 monochrome or 12x21 multicolor = 63 bytes)
--> PLACE in memory on correct address (base+sprite pointer, 64-byte aligned)
