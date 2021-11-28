TODO
====

For next compiler release (7.4)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Use GoSub to call subroutines (statements):
    - [DONE] allow separate assigns to subroutine's parameter variables / registers
    - [DONE] turn a regular subroutine call into assignments to the parameters + GoSub (take code from gosub branch)
    - [DONE] also do this for asmsubs taking >0 parameters

    - make that push(x+1) doesn't use stack evaluation, via a temp var cx16.R9?

Optimize Function calls in expressions:
    - move args to assignments to params
    - add tempvar immediately in front of expression with the fuction call
    - replace the function call in the expression with the tempvar


...


Blocked by an official Commander-x16 v39 release
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- simplify cx16.joystick_get2() once this cx16 rom issue is resolved: https://github.com/commanderx16/x16-rom/issues/203
  (I hope this will still be included into the final v39 roms release for the cx16)


Future
^^^^^^
- rethink the whole "isAugmentable" business.  Because the way this is determined, should always also be exactly mirrorred in the AugmentableAssignmentAsmGen or you'll get a crash at code gen time.
- simplifyConditionalExpression() should not split expression if it still results in stack-based evaluation
- fix the asm-labels problem (github issue #62)
- get rid of all TODO's in the code
- improve testability further, add more tests
- replace certain uses of inferredType.getOr(DataType.UNDEFINED) by i.getOrElse({ errorhandler })
- see if we can remove more "[InferredType].getOr(DataType.UNDEFINED)"
- use more of Result<> and Either<> to handle errors/ nulls better
- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- optimize several inner loops in gfx2 even further?
- add modes 2 and 3 to gfx2 (lowres 4 color and 16 color)?
- add a flood fill routine to gfx2?
- add a diskio.f_seek() routine for the Cx16 that uses its seek dos api?
- make it possible for diskio to read and write from more than one file at the same time (= use multiple io channels)?
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``v_``
- [problematic due to 64tass:] add a compiler option to not remove unused subroutines. this allows for building library programs. But this won't work with 64tass's .proc ...
  Perhaps replace all uses of .proc/.pend by .block/.bend will fix that?
  (but we lose the optimizing aspect of the assembler where it strips out unused code.
  There's not really a dynamic switch possible as all assembly lib code is static and uses one or the other)


More code optimization ideas
^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- find a way to optimize asm-subroutine param passing where it now sometimes uses the evalstack?
- find a way to let registerArgsViaStackEvaluation not use the stack anymore
- remove special code generation for while and util expression
  by rewriting while and until expressions into if+jump (just consider them syntactic sugar)
  but the result should not produce larger code ofcourse!
- while-expression should now also get the simplifyConditionalExpression() treatment
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code that uses a fixed number of predetermined value 'variables'
- this removes the need for the BinExprSplitter (which is problematic now)
- introduce byte-index operator to avoid index multiplications in loops over arrays? see github issue #4
