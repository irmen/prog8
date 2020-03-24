====
TODO
====

- implement the asm for bitshift on arrays (last missing assembly code generation)

- add a routine to plot a single bitmap pixel
- create real assembly routines for the bresenham line and circle and disc code in bitmap gfx
- also add assembly routines for drawing rectangles (filled/open) in bitmap gfx
- add a turtle example once we have such highres drawing routines

- aliases for imported symbols for example perhaps '%alias print = c64scr.print'
- option to load library files from a directory instead of the embedded ones (easier library development/debugging)





Memory Block Operations integrated in language?
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

array/string memory block operations?

- array operations
  copy (from another array with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole array to the given value, default 0)

- array operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on array.

For now, we have the ``memcopy`` and ``memset`` builtin functions.


More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

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
It could then even be moved into the zeropage to greatly reduce code size and slowness.

Or just move the LSB portion into a slab of the zeropage.

Allocate a fixed word in ZP that is the Top Of Stack value so we can always operate on TOS directly
without having to index with X into the eval stack all the time?
This could GREATLY improvde code size and speed for operatios that work on just a single value.


Bug Fixing
^^^^^^^^^^
Ofcourse there are always bugs to fix ;)


Misc
^^^^

Several ideas were discussed on my reddit post
https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/

