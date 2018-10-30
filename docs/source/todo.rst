====
TODO
====


### Macros

@todo macros are meta-code that is executed by the compiler, in a preprecessing step
during the compilation, and can produce output that is then replaced on that point in the input source.
Allows us to create pre calculated sine tables and such.



Memory Block Operations
^^^^^^^^^^^^^^^^^^^^^^^

@todo list,string memory block operations:

- list operations (whole list, individual element)
  operations: set, get, copy (from another list with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole list to the given value, default 0)

- list operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on lists.

these should call (or emit inline) optimized pieces of assembly code, so they run as fast as possible



Bitmap Definition (for Sprites and Characters)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

to define CHARACTERS (8x8 monochrome or 4x8 multicolor = 8 bytes)
--> PLACE in memory on correct address (???k aligned)

and SPRITES (24x21 monochrome or 12x21 multicolor = 63 bytes)
--> PLACE in memory on correct address (base+sprite pointer, 64-byte aligned)

