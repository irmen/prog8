TODO
====

For next release
^^^^^^^^^^^^^^^^
- programGen: don't generate variables from the VarDecl nodes, use allocator/zeropage tables

after that is done:

- (newvaralloc) UnusedCodeRemover after(decl: VarDecl): fix that vars defined in a library can also safely be removed if unused. Currently this breaks programs such as textelite (due to diskio.save().end_address ?)
- check that retval_interm_* are not in  the varallocation if they're not used
- make it so that subroutine parameters as variables can again be allocated in ZP, if there's still space
- wormfood became a lot larger??? why???  (and chess a little bit larger, but usually program size is down)


Need help with
^^^^^^^^^^^^^^
- c128 target: various machine specific things (free zp locations, how banking works, getting the floating point routines working, ...)
- other targets such as Atari 800XL: all required details about the machine, I have no clue whatsoever
- see the :ref:`portingguide` for details on what information is needed.


Blocked by an official Commander-x16 r39 release
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- simplify cx16.joystick_get2() once this cx16 rom issue is resolved: https://github.com/commanderx16/x16-rom/issues/203
  (I hope this will be included into the r39 roms when they get released)


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Ast modifications done in AsmGen that perhaps should not be necessary:
 - block2asm: after vardecls2asm it clears the vardecl.value of all variables
   --> Don't rely on vardecls at all any longer but use the new IVariablesAndConsts object passed to the AsmGen, this will solve this item.
 - block2asm: removes init-assignments to no longer output the initialization assignments as regular statements (is done separately in block initialization routine)

- remove support for old @"screencodes" string encoding syntax (parser+code+docs)
- allow "xxx" * constexpr  (where constexpr is not a number literal), now gives expression error not same type
- unify FunctioncallExpression + FunctioncallStatement and PipeExpression + Pipe statement classes, may require moving Expression/Statement into interfaces instead of abstract base classes
- for the pipe operator: recognise a placeholder (``?`` or ``%`` or ``_``) in a non-unary function call to allow non-unary functions in the chain; ``4 |> mkword(?, $44) |> print_uw``
- for the pipe operator: make it 100% syntactic sugar so there's no need for asm codegen like translatePipeExpression
- make it possible to inline non-asmsub routines that just contain a single statement (return, functioncall, assignment)
  but this requires all identifiers in the inlined expression to be changed to fully scoped names
- simplifyConditionalExpression() should not split expression if it still results in stack-based evaluation
- simplifyConditionalExpression() sometimes introduces needless assignment to r9 tempvar
- consider adding McCarthy evaluation to shortcircuit and and or expressions. First do ifs by splitting them up? Then do expressions that compute a value?
- use more of Result<> and Either<> to handle errors/ nulls better?
- rethink the whole "isAugmentable" business.  Because the way this is determined, should always also be exactly mirrorred in the AugmentableAssignmentAsmGen or you'll get a crash at code gen time.
- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``p8v_``? Or not worth it (most 3 letter opcodes as variables are nonsensical anyway)
  then we can get rid of the instruction lists in the machinedefinitions as well?
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- optimize several inner loops in gfx2 even further?
- add modes 2 and 3 to gfx2 (lowres 4 color and 16 color)?
- add a flood fill routine to gfx2?
- add a diskio.f_seek() routine for the Cx16 that uses its seek dos api?
- make it possible for diskio to read and write from more than one file at the same time (= use multiple io channels)?
- fix problems in c128 target
- add (u)word array type (or modifier?) that puts the array into memory as 2 separate byte-arrays 1 for LSB 1 for MSB -> allows for word arrays of length 256
- [problematic due to 64tass:] add a compiler option to not remove unused subroutines. this allows for building library programs. But this won't work with 64tass's .proc ...
  Perhaps replace all uses of .proc/.pend by .block/.bend will fix that?
  (but we lose the optimizing aspect of the assembler where it strips out unused code.
  There's not really a dynamic switch possible as all assembly lib code is static and uses one or the other)
- zig try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for this)
- get rid of all TODO's in the code ;)


More optimization ideas
^^^^^^^^^^^^^^^^^^^^^^^
- translateFunctioncall() in BuiltinFunctionsAsmGen: should be able to assign parameters to a builtin function directly from register(s), this will make the use of a builtin function in a pipe expression more efficient without using a temporary variable
- translateNormalAssignment() -> better code gen for assigning boolean comparison expressions
- when a for loop's loopvariable isn't referenced in the body, and the iterations are known, replace the loop by a repeatloop
- automatically convert if statements that test for multiple values (if X==1 or X==2..) to if X in [1,2,..] statements, instead of just a warning.
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code that uses a fixed number of predetermined value 'variables'?
  "Three address code" was mentioned.  https://en.wikipedia.org/wiki/Three-address_code
  these variables have to be unique for each subroutine because they could otherwise be interfered with from irq routines etc.
- this removes the need for the BinExprSplitter? (which is problematic and very limited now)
  and perhaps as well the assignment splitting in  BeforeAsmAstChanger too
- introduce byte-index operator to avoid index multiplications in loops over arrays? see github issue #4
