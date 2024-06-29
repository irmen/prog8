TODO
====

optimizer bug, see "optimizing inlined functions must reference proper scopes" unittest (skipped for now)
causes compiler error for virtual: just calling txt.cls() gives compile error undefined symbol clear_screen

https://github.com/irmen/prog8/issues/136 (string.find register order issue)

if-optimization:
        if row == NUMQUEENS {
            print_solution()
            return
        }
 compiles into this, where the bne+jmp could be a single beq instead:
	cmp  #8
	bne  label_asm_21_afterif
	jmp  p8b_main.p8s_print_solution
label_asm_21_afterif:


optimize signed byte/word division by powers of 2 (and shift right?), it's now using divmod routine.  (also % ?)
    see inplacemodificationByteVariableWithLiteralval() and inplacemodificationSomeWordWithLiteralval()
    and for IR: see divideByConst() in IRCodeGen
    1 shift right of AX signed word:
                 stx	P8ZP_SCRATCH_B1
                 cpx	#$80
                 ror	P8ZP_SCRATCH_B1
                 ror    a
                 ldx	P8ZP_SCRATCH_B1

    multi shift right: (amount in $22)
         sta	$4
         txa
         ldx	$22
         beq    end
    loop  cmp	#$80
         ror
         ror	$4
         dex
         bne	loop
    end:  tax
         lda	$4


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:

- Relax newline / bracket in parser so that you can put open and close brackets on the same line or on the next line if you so wish. For example be able to write a true one liner?
- Can we support signed % (remainder) somehow?
- IR: implement missing operators in AssignmentGen  (array shifts etc)
- instead of copy-pasting inline asmsubs, make them into a 64tass macro and use that instead.
  that will allow them to be reused from custom user written assembly code as well.
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays.
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)

- [much work:] more support for (64tass) SEGMENTS ?
    - (What, how, isn't current BSS support enough?)
    - Add a mechanism to allocate variables into golden ram (or segments really) (see GoldenRam class)
    - maybe treat block "golden" in a special way: can only contain vars, every var will be allocated in the Golden ram area?
    - maybe or may not needed: the variables can NOT have initialization values, they will all be set to zero on startup (simple memset)
      just initialize them yourself in start() if you need a non-zero value .
    - OR.... do all this automatically if 'golden' is enabled as a compiler option? So compiler allocates in ZP first, then Golden Ram, then regular ram
    - OR.... make all this more generic and use some %segment option to create real segments for 64tass?
    - (need separate step in codegen and IR to write the "golden" variables)

- do we need (array)variable alignment tag instead of block alignment tag? You want to align the data, not the code in the block?
- ir: related to the one above: block alignment doesn't translate well to variables in the block (the actual stuff that needs to be aligned in memory)  but: need variable alignment tag instead of block alignment tag, really
- ir: fix call() return value handling
- ir: proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- ir: idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- ir: add more optimizations in IRPeepholeOptimizer
- ir: the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- ir: getting it in shape for code generation...
- [problematic due to using 64tass:] better support for building library programs, where unused .proc are NOT deleted from the assembly.
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions
- Zig-like defer to execute a statement/anonymousscope at subroutine exit?
- generate WASM to eventually run prog8 on a browser canvas? Use binaryen toolkit and/or my binaryen kotlin library?
- implement split words arrays all()
- implement split words arrays sort()


Libraries:

- gfx2: add EOR mode support like in monogfx and see PAINT for inspiration.  Self modifying code to keep it optimized?
- fix the problems in atari target, and flesh out its libraries.
- c128 target: make syslib more complete (missing kernal routines)?
- pet32 target: make syslib more complete (missing kernal routines)?
- VM: implement more diskio support


Optimizations:

- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers, then the rest
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code,
  those checks should probably be removed, or be made permanent
- optimizeCommonSubExpressions: currently only looks in expressions on a single line, could search across multiple expressions

STRUCTS?
--------

- can contain only numeric types (byte,word,float) - no nested structs, no reference types (strings, arrays) inside structs
- only as a reference type (uword pointer). This removes a lot of the problems related to introducing a variable length value type.
- arrays of struct is just an array of uword pointers. Can even be @split?
- need to introduce typed pointer datatype in prog8
- str is then syntactic sugar for pointer to character/byte?
- arrays are then syntactic sugar for pointer to byte/word/float?


Other language/syntax features to think about
---------------------------------------------

- add (rom/ram)bank support to romsub.   A call will then automatically switch banks, use callfar and something else when in banked ram.
  challenges: how to not make this too X16 specific? How does the compiler know what bank to switch (ram/rom)?
  How to make it performant when we want to (i.e. NOT have it use callfar/auto bank switching) ?
