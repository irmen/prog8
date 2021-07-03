TODO
====

For next release
^^^^^^^^^^^^^^^^

- refactor code to improve testability and other things, see [CompilerDevelopment](CompilerDevelopment.md)
- simplify cx16.joystick_get2() once this cx16 rom issue is resolved: https://github.com/commanderx16/x16-rom/issues/203
  (I hope this will still be included into the final v39 roms release for the cx16)


Future
^^^^^^
- get rid of all other TODO's in the code ;-)
- see if we can remove more ".typeOrElse(DataType.UNDEFINED)"
- should we introduce more optionals to get rid of more null pointers?
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
- introduce byte-index operator to avoid index multiplications in loops over arrays?
  see https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/eg898b9?utm_source=share&utm_medium=web2x&context=3


More code optimizations
^^^^^^^^^^^^^^^^^^^^^^^
- a way to optimize if-statement codegen so that "if var & %10000" doesn't use stack & subroutine call, but also that the simple case "if X {...}" remains fast
- detect proper variables that are written but never read - mark those as unused too and remove them, such as ``uword unused = memory("unused222", 20)`` - also remove this memory slab allocation
- rewrite expression tree evaluation such that it doesn't use an eval stack but flatten the tree into linear code that uses a fixed number of predetermined value 'variables'
- this removes the need for the BinExprSplitter (which is problematic now)
