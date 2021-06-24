====
TODO
====

- simplify cx16.joystick_get2() once this cx16 rom issue is resolved: https://github.com/commanderx16/x16-rom/issues/203
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- get rid of all other TODO's in the code ;-)


Low prio
^^^^^^^^
- see if we can remove more ".typeOrElse(DataType.UNDEFINED)"
- optimize several inner loops in gfx2 even further?
- add modes 2 and 3 to gfx2 (lowres 4 color and 16 color)?
- add a flood fill routine to gfx2?
- add a f_seek() routine for the Cx16 that uses its seek dos api?
- refactor the asmgen into own submodule
- refactor the compiler optimizers into own submodule
- add a compiler option to not remove unused subroutines. this allows for building library programs. But this won't work with 64tass's .proc ...
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``v_``

More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- find a way to optimize if-statement codegen so that "if var & %10000" doesn't use stack & subroutine call, but also that the simple case "if X {...}" remains fast
- optimizer: detect variables that are written but never read - mark those as unused too and remove them, such as uword unused = memory("unused222", 20) - also remove the memory slab allocation
- further optimize assignment codegeneration, such as the following:
- rewrite expression code generator to not use eval stack but a fixed number of predetermined value 'variables' (1 per nesting level?)
- binexpr splitting (beware self-referencing expressions and asm code ballooning though)
- more optimizations on the language AST level
- more optimizations on the final assembly source level


Misc
^^^^

Several ideas were discussed on my reddit post
https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/
