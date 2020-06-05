====
TODO
====

- finalize (most) of the still missing "new" assignment asm code generation
- aliases for imported symbols for example perhaps '%alias print = c64scr.print'
- option to load library files from a directory instead of the embedded ones (easier library development/debugging)
- investigate support for 8bitguy's Commander X16 platform https://murray2.com/forums/commander-x16.9/  and https://github.com/commanderx16/x16-docs


More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- more targeted optimizations for assigment asm code, such as the following:
- subroutine calling convention? like: 1 byte arg -> pass in A, 2 bytes -> pass in A+Y, return value likewise.
- remove unreachable code after an exit(), return or goto
- working subroutine inlining (start with trivial routines, grow to taking care of vars and identifier refs to them)
- add a compiler option to not include variable initialization code (useful if the program is expected to run only once, such as a game)
  the program will then rely solely on the values as they are in memory at the time of program startup.
- Also some library routines and code patterns could perhaps be optimized further
- can the parameter passing to subroutines be optimized to avoid copying?
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
