TODO
====

For next minor release
^^^^^^^^^^^^^^^^^^^^^^
- can we optimize expressions as function call args? for instance vpoke(1, addr+1, 0)  is inefficient

...


For 9.0 major changes
^^^^^^^^^^^^^^^^^^^^^
- get rid of the disknumber parameter everywhere in diskio, make it a configurable variable that defaults to 8.
  the large majority of users will only deal with a single disk drive so why not make it easier for them.
- duplicate diskio for cx16 (get rid of cx16diskio, just copy diskio and tweak everything) + documentation
- get f_seek_w working like in the BASIC program  - this needs the changes to diskio.f_open to use suffixes ,p,m
- Some more support for (64tass) SEGMENTS ?
    - Add a mechanism to allocate variables into golden ram (or segments really) (see GoldenRam class)
    - maybe treat block "golden" in a special way: can only contain vars, every var will be allocated in the Golden ram area?
    - the variables can NOT have initialization values, they will all be set to zero on startup (simple memset)
      just initialize them yourself in start() if you need a non-zero value
    - OR.... do all this automatically if 'golden' is enabled as a compiler option? So compiler allocates in ZP first, then Golden Ram, then regular ram
    - OR.... make all this more generic and use some %segment option to create real segments for 64tass?
    - (need separate step in codegen and IR to write the "golden" variables)


Need help with
^^^^^^^^^^^^^^
- c128 target: various machine specific things (free zp locations, how banking works, getting the floating point routines working, ...)
- atari target: more details details about the machine, fixing library routines. I have no clue whatsoever.
- see the :ref:`portingguide` for details on what information is needed.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:

- ir: mechanism to determine for chunks which registers are getting input values from "outside"
- ir: mechanism to determine for chunks which registers are passing values out? (i.e. are used again in another chunk)
- ir: peephole opt: renumber registers in chunks to start with 1 again every time (but keep entry values in mind!)
- ir: peephole opt: reuse registers in chunks (but keep result registers in mind that pass values out! and don't renumber registers above SyscallRegisterBase!)
- ir: add more optimizations in IRPeepholeOptimizer
- ir: for expressions with array indexes that occur multiple times, can we avoid loading them into new virtualregs everytime and just reuse a single virtualreg as indexer?
- vm: somehow be able to load a label address as value? (VmProgramLoader) this may require storing the program in actual memory bytes though...
- 6502 codegen: see if we can let for loops skip the loop if startvar>endvar, without adding a lot of code size/duplicating the loop condition.
  It is documented behavior to now loop 'around' $00 but it's too easy to forget about!
  Lot of work because of so many special cases in ForLoopsAsmgen.....  (vm codegen already behaves like this)
- ir: can we determine for the loop variable in forloops if it could be kept in a (virtual) register instead of a real variable? Need to be able to check if the variable is used by another statement beside just the for loop.
- generate WASM to eventually run prog8 on a browser canvas?
- [problematic due to using 64tass:] add a compiler option to not remove unused subroutines. this allows for building library programs. But this won't work with 64tass's .proc ...
  Perhaps replace all uses of .proc/.pend by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is moot anyway as that will have its own dead code removal.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that)
- add special (u)word array type (or modifier?) that puts the array into memory as 2 separate byte-arrays 1 for LSB 1 for MSB -> allows for word arrays of length 256 and faster indexing


Libraries:

- fix the problems in c128 target, and flesh out its libraries.
- fix the problems in atari target, and flesh out its libraries.
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- optimize several inner loops in gfx2 even further?
- add modes 3 and perhaps even 2 to gfx2 (lores 16 color and 4 color)?
- add a flood fill (span fill/scanline fill) routine to gfx2?


Expressions:

- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code
  that, for instance, uses a fixed number of predetermined value 'variables'?
  The VM IL solves this already (by using unlimited registers) but that still lacks a translation to 6502.
- this removes the need for the BinExprSplitter? (which is problematic and very limited now)
  and perhaps the assignment splitting in  BeforeAsmAstChanger  too

Optimizations:

- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then the rest
- when a loopvariable of a forloop isn't referenced in the body, and the number of iterations is known, replace the loop by a repeatloop
  but we have no efficient way right now to see if the body references a variable.
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

