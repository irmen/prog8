====
TODO
====

Memory Block Operations integrated in language?
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

@todo list,string memory block operations?

- list operations (whole list, individual element)
  operations: set, get, copy (from another list with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole list to the given value, default 0)

- list operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on lists.

these should call (or emit inline) optimized pieces of assembly code, so they run as fast as possible

For now, we have the ``memcopy`` and ``memset`` builtin functions.



More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- on the language AST level
- on the StackVM intermediate code level
- on the final assembly source level


Also some library routines and code patterns could perhaps be optimized further


Should use the zeropage for variables
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Variables should be allocated in the zeropage as long as it has space.
- add some sort of ``zp`` modifier keyword on vardecls to force them into zeropage?

