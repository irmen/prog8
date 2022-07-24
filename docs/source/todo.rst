TODO
====

For next release
^^^^^^^^^^^^^^^^
- Add optimized signed word division for factors of 2 (bit shifting but this time with correct rounding)
    CodeGen divideByConst() and divideByConstInplace()
    ExpressionsAsmGen translateExpression()
    ExpressionSimplifier optimizeDivision() ?

- add item to XyzZeropage that enables an option that if zeropage=FULL or KERNALSAFE, moves the cx16 virtual registers to ZP, same location as on x16
  (can be done on C64 only for now)   Remove those addresses from the ZP free pool = allocate them in ZP like Cx16Zeropage does
  Adapt the code in AstPreprocessor that relocates the registers as well.
- for uword pointer variables: allow pointer[uword] array indexing >255 , rewrite it to @(pointer+index)
  DO NOT allow this for regular array indexing because normal arrays can never exceed size 256
...


Need help with
^^^^^^^^^^^^^^
- c128 target: various machine specific things (free zp locations, how banking works, getting the floating point routines working, ...)
- atari target: more details details about the machine, fixing library routines. I have no clue whatsoever.
- see the :ref:`portingguide` for details on what information is needed.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:
- vm Instruction needs to know what the read-registers/memory are, and what the write-register/memory is.
  this info is needed for more advanced optimizations and later code generation steps.
- vm: implement remaining sin/cos functions in math.p8
- vm: find a solution for the cx16.r0..r15 that "overlap" (r0, r0L, r0H etc) but in the vm each get their own separate variable location now
- vm: somehow deal with asmsubs otherwise the vm IR can't fully encode all of prog8
- vm: how to remove all unused subroutines? (the 6502 assembly codegen relies on 64tass solve this for us)
- vm: rather than being able to jump to any 'address' (IPTR), use 'blocks' that have entry and exit points -> even better dead code elimination possible too
- vm: add ore optimizations in VmPeepholeOptimizer
- see if we can let for loops skip the loop if end<start, like other programming languages. Without adding a lot of code size/duplicating the loop condition.
  this is documented behavior to now loop around but it's too easy to forget about!
    Lot of work because of so many special cases in ForLoopsAsmgen.....
    How is it for the vm target? -> just 2 special cases in CodeGen.
- when the vm is stable and *if* its language can get promoted to prog8 IL, the variable allocation should be changed.
  It's now done before the vm code generation, but the IL should probably not depend on the allocations already performed.
  So the CodeGen doesn't do VariableAlloc *before* the codegen, but as a last step.
- generate WASM from the new ast (or from vm code?) to run prog8 on a browser canvas?
- createAssemblyAndAssemble(): make it possible to actually get rid of the VarDecl nodes by fixing the rest of the code mentioned there.
  but probably better to rewrite the 6502 codegen on top of the new Ast.
- simplifyConditionalExpression() sometimes introduces needless assignment to r9 tempvar, can we detect & prevent this?
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``p8v_``? Or not worth it (most 3 letter opcodes as variables are nonsensical anyway)
  then we can get rid of the instruction lists in the machinedefinitions as well?
- [problematic due to using 64tass:] add a compiler option to not remove unused subroutines. this allows for building library programs. But this won't work with 64tass's .proc ...
  Perhaps replace all uses of .proc/.pend by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already.....
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that)
- add special (u)word array type (or modifier?) that puts the array into memory as 2 separate byte-arrays 1 for LSB 1 for MSB -> allows for word arrays of length 256 and faster indexing
- ast: don't rewrite by-reference parameter type to uword, but keep the original type (str, array)
  BUT that makes the handling of these types different between the scope they are defined in, and the
  scope they get passed in by reference...  unless we make str and array types by-reference ALWAYS? BUT that
  makes simple code accessing them in the declared scope very slow because that then has to always go through
  the pointer rather than directly referencing the variable symbol in the generated asm....


Libraries:

- fix the problems in c128 target, and flesh out its libraries.
- fix the problems in atari target, and flesh out its libraries.
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- optimize several inner loops in gfx2 even further?
- add modes 3 and perhaps even 2 to gfx2 (16 color and 4 color)?
- add a flood fill routine to gfx2?


Expressions:

- rethink the whole "isAugmentable" business.  Because the way this is determined, should always also be exactly mirrorred in the AugmentableAssignmentAsmGen or you'll get a crash at code gen time.
  note: the new Ast doesn't need this any more so maybe we can get rid of it altogether in the old AST - but it's still used for something in the UnusedCodeRemover.
- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code that uses a fixed number of predetermined value 'variables'?
  "Three address code" was mentioned.  https://en.wikipedia.org/wiki/Three-address_code
  these variables have to be unique for each subroutine because they could otherwise be interfered with from irq routines etc.
  The VM IL solves this already (by using unlimited registers) but still lacks a translation to 6502.
- this removes the need for the BinExprSplitter? (which is problematic and very limited now)
  and perhaps the assignment splitting in  BeforeAsmAstChanger  too

Optimizations:

- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  When 6502-codegen is no longer done from
  the old CompilerAst, those checks should probably be removed, or be made permanent
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served
- when a loopvariable of a forloop isn't referenced in the body, and the iterations are known, replace the loop by a repeatloop
  but we have no efficient way right now to see if the body references a variable.


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

