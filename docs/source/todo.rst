TODO
====

For next compiler release
^^^^^^^^^^^^^^^^^^^^^^^^^
replace checks of InferredType against multiple types with .oneOf(...)
replace certain uses of inferredType.getOr(UNKNOWN) by i.getOrElse({ errorhandler })
replace registerOrPair in setOf(..) by .oneOf(..) varargs function


Blocked by Commander-x16 v39 release
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- simplify cx16.joystick_get2() once this cx16 rom issue is resolved: https://github.com/commanderx16/x16-rom/issues/203
  (I hope this will still be included into the final v39 roms release for the cx16)


Future
^^^^^^
- get rid of all TODO's and FIXME's in the code
- improve testability further, add more tests, address more questions/issues from the testability discussions.
- can we get rid of pieces of asmgen.AssignmentAsmGen by just reusing the AugmentableAssignment ? generated code should not suffer
- see if we can remove more "[InferredType].getOr(DataType.UNDEFINED)"
- use more of Result<> and Either<> to handle errors/ nulls better
- fix the asm-labels problem (github issue #62)
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- optimize several inner loops in gfx2 even further?
- add modes 2 and 3 to gfx2 (lowres 4 color and 16 color)?
- add a flood fill routine to gfx2?
- add a diskio.f_seek() routine for the Cx16 that uses its seek dos api?
- make it possible for diskio to read and write from more than one file at the same time (= use multiple io channels)?
- refactor the asmgen into own project submodule
- refactor the compiler optimizers into own project submodule
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``v_``
- [problematic due to 64tass:] add a compiler option to not remove unused subroutines. this allows for building library programs. But this won't work with 64tass's .proc ...
  Perhaps replace all uses of .proc/.pend by .block/.bend will fix that?
  (but we lose the optimizing aspect of the assembler where it strips out unused code.
  There's not really a dynamic switch possible as all assembly lib code is static and uses one or the other)
- introduce byte-index operator to avoid index multiplications in loops over arrays?
  see https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/eg898b9?utm_source=share&utm_medium=web2x&context=3


More code optimization ideas
^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- a way to optimize if-statement codegen so that "if var & %10000" doesn't use stack & subroutine call, but also that the simple case "if X {...}" remains fast
- detect variables that are written but never read - mark those as unused too and remove them, such as ``uword unused = memory("unused222", 20)`` - also remove this memory slab allocation
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code that uses a fixed number of predetermined value 'variables'
- this removes the need for the BinExprSplitter (which is problematic now)
