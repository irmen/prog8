====
TODO
====


### Macros

@todo macros are meta-code that is executed by the compiler, in a preprecessing step
during the compilation, and can produce output that is then replaced on that point in the input source.
Allows us to create pre calculated sine tables and such.



Memory Block Operations
^^^^^^^^^^^^^^^^^^^^^^^

@todo matrix,list,string memory block operations:

- matrix type operations (whole matrix, per row, per column, individual row/column)
  operations: set, get, copy (from another matrix with the same dimensions, or list with same length),
  shift-N (up, down, left, right, and diagonals, meant for scrolling)
  rotate-N (up, down, left, right, and diagonals, meant for scrolling)
  clear (set whole matrix to the given value, default 0)

- list operations (whole list, individual element)
  operations: set, get, copy (from another list with the same length), shift-N(left,right), rotate-N(left,right)
  clear (set whole list to the given value, default 0)

- list and matrix operations ofcourse work identical on vars and on memory mapped vars of these types.

- strings: identical operations as on lists.

- matrix with row-interleave can only be a memory mapped variable and can be used to directly
  access a rectangular area within another piece of memory - such as a rectangle on the (character) screen

these should call (or emit inline) optimized pieces of assembly code, so they run as fast as possible



Bitmap Definition (for Sprites and Characters)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

to define CHARACTERS (8x8 monochrome or 4x8 multicolor = 8 bytes)
--> PLACE in memory on correct address (???k aligned)

and SPRITES (24x21 monochrome or 12x21 multicolor = 63 bytes)
--> PLACE in memory on correct address (base+sprite pointer, 64-byte aligned)

