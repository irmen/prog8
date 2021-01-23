====
TODO
====

- allow  uwordpointer[index] syntax -> transform into @(uwordpointer+index)  allow index to be >255!
- add offsetof() to get the byte offset of struct members
- add any2(), all2(), max2(), min2(), reverse2(), sum2(), sort2() that take (array, startindex, length) arguments
- optimize for loop iterations better to allow proper inx, cpx #value, bne loop  instructions  (like repeat loop)
- why is there a beq  _prog8_label_2_repeatend  at the end of repeat loops? seems unused
- optimize swap of two memread values with index, using the same pointer expression/variable, like swap(@(ptr+1), @(ptr+2))
- implement the linked_list millfork benchmark

- implement highres 4 color mode in gfx2
- make a retro amiga workbench 1.3 and/or 2.0 workbench "simulator" using that new gfx mode
- can we get rid of the --longOptionName command line options and only keep the short versions? https://github.com/Kotlin/kotlinx-cli/issues/50
- add a f_seek() routine for the Cx16 that uses its seek dos api?
- optimizer: detect variables that are written but never read - mark those as unused too and remove them, such as uword unused = memory("unused222", 20) - also remove the memory slab allocation
- add a compiler option to not remove unused subroutines. this allows for building library programs
- hoist all variable declarations up to the subroutine scope *before* even the constant folding takes place (to avoid undefined symbol errors when referring to a variable from another nested scope in the subroutine)
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as '_'
- option to load the built-in library files from a directory instead of the embedded ones (for easier library development/debugging)
- c64: make the graphics.BITMAP_ADDRESS configurable
- some support for recursive subroutines?
    - via %option recursive?: allocate all params and local vars on estack, don't allow nested subroutines, can begin by first not allowing any local variables just fixing the parameters
    - Or via a special recursive call operation that copies the current values of all local vars (including arguments) to the stack, replaces the arguments, jsr subroutine, and after returning copy the stack back to the local variables
- get rid of all other TODO's in the code ;-)

More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- further optimize assignment codegeneration, such as the following:
- binexpr splitting (beware self-referencing expressions and asm code ballooning though)
- more optimizations on the language AST level
- more optimizations on the final assembly source level


Eval stack redesign? (lot of work)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The eval stack is now a split lsb/msb stack using X as the stackpointer.
Is it easier/faster to just use a single page unsplit stack?
It could then even be moved into the zeropage to reduce code size and slowness.

Or just move the LSB portion into a slab of the zeropage.

Allocate a fixed word in ZP that is the Top Of Stack value so we can always operate on TOS directly
without having to index with X into the eval stack all the time?
This could GREATLY improve code size and speed for operations that work on just a single value.


Bug Fixing
^^^^^^^^^^
Ofcourse there are always bugs to fix ;)


Misc
^^^^

Several ideas were discussed on my reddit post
https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/
