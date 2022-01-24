TODO
====

For next release
^^^^^^^^^^^^^^^^
- str s1, ubyte ff,  txt.print_uwhex(s1+ff, true)        ; TODO fix compiler crash on s1+ff.   why no crash when using 1-argument functioncall?

...


Need help with
^^^^^^^^^^^^^^
- c128 target: various machine specific things (free zp locations, how banking works, getting the floating point routines working, ...)
- other targets such as Atari 800XL: all required details about the machine, I have no clue whatsoever
- see the Porting Guide in the documentation for this.


Blocked by an official Commander-x16 r39 release
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- simplify cx16.joystick_get2() once this cx16 rom issue is resolved: https://github.com/commanderx16/x16-rom/issues/203
  (I hope this will be included into the r39 roms when they get released)


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- nameInAssemblyCode() should search smarter
- Typecastexpression.isSimple: make it 'expression.isSimple' rather than always false. (this breaks some things atm)
- IdentifierReference: fix equality to also include position. CallGraph can then also only store IdentifierRef instead of pair(ident, position) as keys.
- Fix: don't report as recursion if code assigns address of its own subroutine to something, rather than calling it
- allow "xxx" * constexpr  (where constexpr is not a number literal, now gives expression error not same type)
- can we promise a left-to-right function call argument evaluation? without sacrificing performance
- unify FunctioncallExpression + FunctioncallStatement and PipeExpression + Pipe statement, may require moving Expression/Statement into interfaces instead of abstract base classes
- for the pipe operator: recognise a placeholder (``?`` or ``%`` or ``_``) in a non-unary function call to allow things as ``4 |> mkword(?, $44) |> print_uw``
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``v_``
  then we can get rid of the instruction lists in the machinedefinitions as well?
- make it possible to inline non-asmsub routines that just contain a single statement (return, functioncall, assignment)
  but this requires all identifiers in the inlined expression to be changed to fully scoped names
- simplifyConditionalExpression() should not split expression if it still results in stack-based evaluation
- simplifyConditionalExpression() sometimes introduces needless assignment to r9 tempvar
- consider adding McCarthy evaluation to shortcircuit and and or expressions. First do ifs by splitting them up? Then do expressions that compute a value?
- use more of Result<> and Either<> to handle errors/ nulls better?
- rethink the whole "isAugmentable" business.  Because the way this is determined, should always also be exactly mirrorred in the AugmentableAssignmentAsmGen or you'll get a crash at code gen time.
- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
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
- get rid of all TODO's in the code ;)


More optimization ideas
^^^^^^^^^^^^^^^^^^^^^^^
- translateFunctioncall() in BuiltinFunctionsAsmGen: should be able to assign parameters to a builtin function directly from register(s), this will make the use of a builtin function in a pipe expression more efficient without using a temporary variable
- translateNormalAssignment() -> better code gen for assigning boolean comparison expressions
- when a for loop's loopvariable isn't referenced in the body, and the iterations are known, replace the loop by a repeatloop
- automatically convert if statements that test for multiple values (if X==1 or X==2..) to if X in [1,2,..] statements, instead of just a warning.
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code that uses a fixed number of predetermined value 'variables'?
- this removes the need for the BinExprSplitter? (which is problematic and very limited now)
  and perhaps as well the assignment splitting in  BeforeAsmAstChanger too
- introduce byte-index operator to avoid index multiplications in loops over arrays? see github issue #4
