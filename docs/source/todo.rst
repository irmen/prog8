====
TODO
====

- 64tass doesn't output all labels anymore in the vice-mon-list, so %breakpoint labels are no longer present....
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as '_'
- option to load the built-in library files from a directory instead of the embedded ones (for easier library development/debugging)
- see if we can group some errors together for instance the (now single) errors about unidentified symbols
- use VIC banking to move up the graphics bitmap memory location. Don't move it under the ROM though as that would require IRQ disabling and memory bank swapping for every bitmap manipulation
- some support for recursive subroutines? via %option recursive?: allocate all params and local vars on estack, don't allow nested subroutines, can begin by first not allowing any local variables just fixing the parameters
- get rid of all other TODO's in the code ;-)

More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- further optimize assignment codegeneration, such as the following:
- binexpr splitting (beware self-referencing expressions and asm code ballooning though)
- detect var->var argument passing to subroutines and avoid the second variable and copying of the value
- more optimizations on the language AST level
- more optimizations on the final assembly source level
- note: subroutine inlining is abandoned because of problems referencing non-local stuff. Can't move everything around.


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
