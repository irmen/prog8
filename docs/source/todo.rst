====
TODO
====

- get rid of all other TODO's in the code ;-)
- move the 	ldx  #$ff | clc | cld  from the startup logic into the start() function as first instructions
- add an %option that omits the 'system-init' code at the start. Useful to create separate standalone routines that shouldn't re-init the whole machine every time they're called
- line-circle-gfx examples are now a few hundred bytes larger than before. Why is that, can it be fixed?
- until condition should be able to refer to variables defined IN the do-until block itself.
- add support? example? for processing arguments to a sys call : sys 999, 1, 2, "aaa"
- make it possible for array literals to not only contain compile time constants
- further optimize assignment codegeneration
- implement @stack for asmsub parameters
- make it possible to use cpu opcodes such as 'nop' as variable names by prefixing all asm vars with something such as '_'
- option to load the built-in library files from a directory instead of the embedded ones (for easier library development/debugging)
- see if we can group some errors together for instance the (now single) errors about unidentified symbols
- use VIC banking to move up the graphics bitmap memory location. Don't move it under the ROM though as that would require IRQ disabling and memory bank swapping for every bitmap manipulation
- add some primitives/support/examples for using custom char sets, copying the default charset.

More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- more targeted optimizations for assigment asm code, such as the following:
- subroutine calling convention? like: 1 byte arg -> pass in A, 2 bytes -> pass in A+Y, return value likewise.
- can such parameter passing to subroutines be optimized to avoid copying?
- add a compiler option to not include variable initialization code (useful if the program is expected to run only once, such as a game)
  the program will then rely solely on the values as they are in memory at the time of program startup.
- Also some library routines and code patterns could perhaps be optimized further
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
