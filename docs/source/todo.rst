====
TODO
====

- optimize comparisons followed by a conditional jump ; try to not have to jsr to the comparison routines. (so if/while/do-until are faster)

- optimize several inner loops in gfx2
- hoist all variable declarations up to the subroutine scope *before* even the constant folding takes place (to avoid undefined symbol errors when referring to a variable from another nested scope in the subroutine)
- optimize swap of two memread values with index, using the same pointer expression/variable, like swap(@(ptr+1), @(ptr+2))
- add a flood fill routine to gfx2?
- add modes 2 and 3 to gfx2 (lowres 4 color and 16 color) ?
- add a f_seek() routine for the Cx16 that uses its seek dos api?
- refactor the asmgen into their own submodule?
- refactor the compiler optimizers into their own submodule?
- optimizer: detect variables that are written but never read - mark those as unused too and remove them, such as uword unused = memory("unused222", 20) - also remove the memory slab allocation
- add a compiler option to not remove unused subroutines. this allows for building library programs
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as ``v_``
- option to load the built-in library files from a directory instead of the embedded ones (for easier library development/debugging)
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)
- some support for recursive subroutines?
    - via %option recursive?: allocate all params and local vars on estack, don't allow nested subroutines, can begin by first not allowing any local variables just fixing the parameters
    - Or via a special recursive call operation that copies the current values of all local vars (including arguments) to the stack, replaces the arguments, jsr subroutine, and after returning copy the stack back to the local variables
- get rid of all other TODO's in the code ;-)

More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- further optimize assignment codegeneration, such as the following:
- rewrite expression code generator to not use eval stack but a fixed number of predetermined value 'variables' (1 per nesting level?)
- binexpr splitting (beware self-referencing expressions and asm code ballooning though)
- more optimizations on the language AST level
- more optimizations on the final assembly source level


Misc
^^^^

Several ideas were discussed on my reddit post
https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/
