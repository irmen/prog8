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


Bitmap Definition (for Sprites and Characters)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

to define CHARACTERS (8x8 monochrome or 4x8 multicolor = 8 bytes)
--> PLACE in memory on correct address (???k aligned)

and SPRITES (24x21 monochrome or 12x21 multicolor = 63 bytes)
--> PLACE in memory on correct address (base+sprite pointer, 64-byte aligned)
--> actually not needed because a block at the correct address, and an array using 3x21 binary values, more or less does exactly this already::

    ~ spritedata $0a00 {
        ; this memory block contains the sprite data
        ; it must start on an address aligned to 64 bytes.
        %option force_output    ; make sure the data in this block appears in the resulting program

        ubyte[63] balloonsprite = [ %00000000,%01111111,%00000000,
                                    %00000001,%11111111,%11000000,
                                    %00000011,%11111111,%11100000,
                                    %00000011,%11100011,%11100000,
                                    %00000111,%11011100,%11110000,
                                    %00000111,%11011101,%11110000,
                                    %00000111,%11011100,%11110000,
                                    %00000011,%11100011,%11100000,
                                    %00000011,%11111111,%11100000,
                                    %00000011,%11111111,%11100000,
                                    %00000010,%11111111,%10100000,
                                    %00000001,%01111111,%01000000,
                                    %00000001,%00111110,%01000000,
                                    %00000000,%10011100,%10000000,
                                    %00000000,%10011100,%10000000,
                                    %00000000,%01001001,%00000000,
                                    %00000000,%01001001,%00000000,
                                    %00000000,%00111110,%00000000,
                                    %00000000,%00111110,%00000000,
                                    %00000000,%00111110,%00000000,
                                    %00000000,%00011100,%00000000   ]
    }

