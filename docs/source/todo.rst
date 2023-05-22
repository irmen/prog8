TODO
====

For 9.0 major changes
^^^^^^^^^^^^^^^^^^^^^
- DONE: added 'cbm' block in the syslib module that now contains all CBM compatible kernal routines and variables
- DONE: added min(), max() builtin functions. For floats, use floats.minf() and floats.maxf().
- DONE: added clamp(value, minimum, maximum)  to restrict a value x to a minimum and maximum value. For floats, use floats.clampf(f, minv, maxv).
- DONE: rename sqrt16() to just sqrt(), make it accept multiple numeric types including float. Removed floats.sqrt().
- DONE: abs() now supports multiple datatypes including float. Removed floats.fabs().
- DONE: divmod() now supports multiple datatypes.  divmodw() has been removed.
- DONE: cx16diskio module merged into diskio (which got specialized for commander x16 target). load() and load_raw() with extra ram bank parameter are gone.
- DONE: drivenumber parameter removed from all routines in diskio module. The drive to work on is now simply stored as a diskio.drivenumber variable, which defaults to 8.
- DONE: for loops now skip the whole loop if from value already outside the loop range (this is what all other programming languages also do)
- DONE: asmsub params or return values passed in cpu flags (like carry) now must be declared as booleans (previously ubyte was still accepted).
- DONE: (on cx16) added diskio.save_raw() to save without the 2 byte prg header
- DONE: added sys.irqsafe_xxx irqd routines
- DONE: added gfx2.fill() flood fill routine

- [much work:] add special (u)word array type (or modifier such as @fast or @split? ) that puts the array into memory as 2 separate byte-arrays 1 for LSB 1 for MSB -> allows for word arrays of length 256 and faster indexing
  this is an enormous amout of work, if this type is to be treated equally as existing (u)word , because all expression / lookup / assignment routines need to know about the distinction....
  So maybe only allow the bare essentials? (store, get, ++/--/+/-, bitwise operations?)
- [much work:] more support for (64tass) SEGMENTS ?
    - (What, how, isn't current BSS support enough?)
    - Add a mechanism to allocate variables into golden ram (or segments really) (see GoldenRam class)
    - maybe treat block "golden" in a special way: can only contain vars, every var will be allocated in the Golden ram area?
    - maybe or may not needed: the variables can NOT have initialization values, they will all be set to zero on startup (simple memset)
      just initialize them yourself in start() if you need a non-zero value .
    - OR.... do all this automatically if 'golden' is enabled as a compiler option? So compiler allocates in ZP first, then Golden Ram, then regular ram
    - OR.... make all this more generic and use some %segment option to create real segments for 64tass?
    - (need separate step in codegen and IR to write the "golden" variables)


Need help with
^^^^^^^^^^^^^^
- atari target: more details details about the machine, fixing library routines. I have no clue whatsoever.
- see the :ref:`portingguide` for details on what information is needed.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:

- ir: idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- ir: add more optimizations in IRPeepholeOptimizer
- ir: for expressions with array indexes that occur multiple times, can we avoid loading them into new virtualregs everytime and just reuse a single virtualreg as indexer? (simple form of common subexpression elimination)
- PtAst/IR: more complex common subexpression eliminations
- generate WASM to eventually run prog8 on a browser canvas? Use binaryen toolkit or my binaryen kotlin library?
- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
- [problematic due to using 64tass:] better support for building library programs, where unused .proc shouldn't be deleted from the assembly?
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions
- For c128 target; put floating point variables in bank 1 to make the FP routines work (is this even worth it? very few people will use fp)

Libraries:

- fix the problems in atari target, and flesh out its libraries.
- c128 target: make syslib more complete (missing kernal routines)?
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- optimize several inner loops in gfx2 even further?
- add modes 3 and perhaps even 2 to gfx2 (lores 16 color and 4 color)?


Expressions:

- Once the evalstack-free expression codegen is in place, the Eval Stack can be removed from the compiler.
    Machinedefinition, .p8 and .asm library files, all routines operationg on estack, and everything saving/restoring the X register related to this stack.
- Or rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code
  that, for instance, uses a fixed number of predetermined value 'variables'?
  The VM IL solves this already (by using unlimited registers) but that still lacks a translation to 6502.
- this removes the need for the BinExprSplitter? (which is problematic and very limited now)
  and perhaps the assignment splitting in  BeforeAsmAstChanger  too

Optimizations:

- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers, then the rest
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code,
  those checks should probably be removed, or be made permanent


STRUCTS again?
--------------

What if we were to re-introduce Structs in prog8? Some thoughts:

- can contain only numeric types (byte,word,float) - no nested structs, no reference types (strings, arrays) inside structs
- is just some syntactic sugar for a scoped set of variables -> ast transform to do exactly this before codegen. Codegen doesn't know about struct.
- no arrays of struct -- because too slow on 6502 to access those, rather use struct of arrays instead.
  can we make this a compiler/codegen only issue? i.e. syntax is just as if it was an array of structs?
  or make it explicit in the syntax so that it is clear what the memory layout of it is.
- ability to assign struct variable to another?   this is slow but can be quite handy sometimes.
  however how to handle this in a function that gets the struct passed as reference? Don't allow it there? (there's no pointer dereferencing concept in prog8)
- ability to be passed as argument to a function (by reference)?
  however there is no typed pointer in prog8 at the moment so this can't be implemented in a meaningful way yet,
  because there is no way to reference it as the struct type again. (current ast gets the by-reference parameter
  type replaced by uword)
  So-- maybe don't replace the parameter type in the ast?  Should fix that for str and array types as well then

