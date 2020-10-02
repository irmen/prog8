====
TODO
====

- get rid of all other TODO's in the code ;-)
- make it possible for array literals to not only contain compile time constants?
- implement @stack for asmsub parameters
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as '_'
- option to load the built-in library files from a directory instead of the embedded ones (for easier library development/debugging)
- see if we can group some errors together for instance the (now single) errors about unidentified symbols
- use VIC banking to move up the graphics bitmap memory location. Don't move it under the ROM though as that would require IRQ disabling and memory bank swapping for every bitmap manipulation
- add some primitives/subroutines/examples for using custom char sets, copying the default charset.
- recursive subroutines? via %option recursive, allocate all params and local vars on estack, don't allow nested subroutines, can begin by first not allowing any local variables just fixing the parameters

More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- further optimize assignment codegeneration, such as the following:
- binexpr splitting (beware self-referencing expressions and asm code ballooning though)
- subroutine calling convention? like: 1 byte arg -> pass in A, 2 bytes -> pass in A+Y, return value likewise.
- can such parameter passing to subroutines be optimized to avoid copying?
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
