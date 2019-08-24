====
TODO
====

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

- on the language AST level
- on the final assembly source level
- can the parameter passing to subroutines be optimized to avoid copying?
- working subroutine inlining (taking care of vars and identifier refs to them)

Also some library routines and code patterns could perhaps be optimized further


Eval stack redesign? (lot of work)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The eval stack is now a split lsb/msb stack using X as the stackpointer.
Is it easier/faster to just use a single page unsplit stack?
It could then even be moved into the zeropage to greatly reduce code size and slowness.

Or just move the LSB portion into a slab of the zeropage.

Allocate a fixed word in ZP that is the TOS so we can always operate on TOS directly
without having to to index into the stack?


Bugs
^^^^
There are a few erroneous compiler errors when dealing with certain for loops::

    for bb in [1,2,3]   ;  byte loop variable can only loop over bytes
    for ww in [1111,3333,555,999]  ; word loop variable can only loop over bytes or words
    for uw in 20 to 10 step -1  ; 'can't cast BYTE into UWORD'


Misc
^^^^

Several ideas were discussed on my reddit post
https://www.reddit.com/r/programming/comments/alhj59/creating_a_programming_language_and_cross/

