====
TODO
====

Memory Block Operations
^^^^^^^^^^^^^^^^^^^^^^^

@todo list,string memory block operations:

- list operations (whole list, individual element)
  operations: set, get, copy (from another list with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole list to the given value, default 0)

- list operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on lists.

these should call (or emit inline) optimized pieces of assembly code, so they run as fast as possible

At least we have memcopy() already and some screen related routines in asm
@todo add memset() to easily set a part of memory to a specific byte value
