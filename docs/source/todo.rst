====
TODO
====


Fixes
^^^^^
variable naming issue::

    main {

        sub start() {
            for A in 0 to 10 {
                ubyte note1 = 44
                Y+=note1
            }
            delay(1)

            sub delay(ubyte note1) {        ; TODO: redef of note1 above, conflicts because that one was moved to the zeropage
                A= note1
            }
        }
    }



Memory Block Operations integrated in language?
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

list,string memory block operations?

- list operations (whole list, individual element)
  operations: set, get, copy (from another list with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole list to the given value, default 0)

- list operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on lists.

these should call optimized pieces of assembly code, so they run as fast as possible

For now, we have the ``memcopy``, ``memset`` and ``strlen`` builtin functions.



More optimizations
^^^^^^^^^^^^^^^^^^

Add more compiler optimizations to the existing ones.

- on the language AST level
- on the StackVM intermediate code level
- on the final assembly source level
- can the parameter passing to subroutines be optimized to avoid copying?

- subroutines with 1 or 2 byte args (or 1 word arg) should be converted to asm calling convention with the args in A/Y register
  this requires rethinking the way parameters are represented, simply injecting vardecls to
  declare local variables for them is not always correct anymore


Also some library routines and code patterns could perhaps be optimized further


Eval stack redesign? (lot of work)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The eval stack is now a split lsb/msb stack using X as the stackpointer.
Is it easier/faster to just use a single page unsplit stack?
It could then even be moved into the zeropage to greatly reduce code size and slowness.

Or just move the LSB portion into a slab of the zeropage.

Allocate a fixed word in ZP that is the TOS so we can operate on TOS directly
without having to to index into the stack?


Misc
^^^^

- are there any other missing instructions in the code generator?
