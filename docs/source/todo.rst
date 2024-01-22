TODO
====

maze:   if cell & UP!=0 and @(celladdr(cx,cy-1)) & (WALKED|BACKTRACKED) ==0
              ^^ adding this !=0 caused a weird beq + / lda #1 / +  to appear in front of the shortcircuit beq...

...


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:

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
- ir: proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- ir: idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- ir: fix call() return value handling
- ir: add more optimizations in IRPeepholeOptimizer
- ir: the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- ir: getting it in shape for code generation...
- [problematic due to using 64tass:] better support for building library programs, where unused .proc are NOT deleted from the assembly.
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions
- generate WASM to eventually run prog8 on a browser canvas? Use binaryen toolkit and/or my binaryen kotlin library?
- add Vic20 target?
- split words arrays all()
- split words arrays sort()


Libraries:

- once kernal rom v47 is released, remove most of the workarounds in cx16 floats.parse_f()  .   Prototype parse routine in examples/cx16/floatparse.p8
- fix the problems in atari target, and flesh out its libraries.
- c128 target: make syslib more complete (missing kernal routines)?
- pet32 target: make syslib more complete (missing kernal routines)?


Optimizations:

- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers, then the rest
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code,
  those checks should probably be removed, or be made permanent
- optimizeCommonSubExpressions: currently only looks in expressions on a single line, could search across multiple expressions

STRUCTS again?
--------------

What if we were to re-introduce Structs in prog8? Some thoughts:

- can contain only numeric types (byte,word,float) - no nested structs, no reference types (strings, arrays) inside structs
- only as a reference type (uword pointer). This removes a lot of the problems related to introducing a variable length value type.
- arrays of struct is just an array of uword pointers. Can even be @split?
- need to introduce typed pointer datatype in prog8
- str is then syntactic sugar for pointer to character/byte?
- arrays are then syntactic sugar for pointer to byte/word/float?


Other language/syntax features to think about
---------------------------------------------

- support for assigning multiple return values from romsub/asmsub to multiple variables.
- add (rom/ram)bank support to romsub.   A call will then automatically switch banks, use callfar and something else when in banked ram.
  challenges: how to not make this too X16 specific? How does the compiler know what bank to switch (ram/rom)?
  How to make it performant when we want to (i.e. NOT have it use callfar/auto bank switching) ?
