******************************
Amiga BOB and sprite animation
******************************

.. index:: pair: Amiga; BOBs
.. index:: pair: Amiga; hardware sprites

The Amiga helpers separate an object's movement and collision metadata from
its rendering. A ``mobs.Mob`` describes the object, while a ``mobs.Bob`` or
``mobs.Sprite`` points to that Mob and holds renderer-specific image data. Both
renderers can therefore use the same position, origin and collision geometry.

.. _amiga-moving-objects:

Moving objects and shared metadata
----------------------------------

``mobs.Mob`` holds renderer-independent metadata. ``x_pos`` and ``y_pos`` set
the object's anchor; ``origin_x`` and ``origin_y`` offset the image's top-left
from it::

    struct Mob {
        word x_pos
        word y_pos
        word origin_x
        word origin_y
        uword width
        uword height
        uword radius
    }

The object's top-left is ``(x_pos + origin_x, y_pos + origin_y)``. With an
origin of ``(0, 0)``, the anchor is the top-left corner. A ``radius`` of zero
makes the circle collision helpers use the Mob's rectangular bounds instead.

Position and size live in the ``Mob`` because the renderers and the geometry
helpers read them. Everything only game logic touches - velocity, timers,
AI state - stays in client variables next to the ``Mob``, typically parallel
arrays; the library never writes animation state.

Each renderer descriptor points to a ``Mob``, keeping object metadata separate
from renderer-specific image data::

    struct Bob {
        ^^mobs.Mob mob
        uword depth
        pointer data
        pointer mask
        uword id
        uword state
    }

    struct Sprite {
        ^^mobs.Mob mob
        pointer data
        uword slot
        uword id
        uword state
    }

``Bob.data`` points to plane 0 of the current frame. ``Bob.mask`` points to the
separate mask, or is zero for an opaque BOB. ``Sprite.data`` points to the
hardware sprite data list. The sprite descriptor does not yet include fields
for AGA width, palette-bank or fetch-mode settings.

The ``mobs`` geometry helpers work on a Mob regardless of how it is rendered.
They include ``collides()``, ``collides_circle()``, ``contains_point()``,
``contains_circle()`` and ``direction_to()``, along with distance and box
helpers. They use integer math and do not inspect image pixels. To test BOBs
pixel by pixel, combine their masks with a bitwise AND. Hardware sprite
collisions use ``custom.CLXCON`` and ``custom.CLXDAT``.

The CPU reads the Mob and renderer descriptors, so they can live in any memory.
DMA reads or writes the image planes, masks, sprite lists, destination
bitplanes and BOB save buffers, so those must be in **chip RAM**. Put them in a
block with ``%option amiga_chipram``. See :ref:`directives`.

Choosing a renderer
-------------------

**BOBs** (blitter objects) are bitmaps that the blitter cookie-cuts into the
bitplanes. They can vary in size and use the playfield's colours. Drawing a BOB
uses blitter and CPU time; moving one also requires saving and restoring its
background.

**Hardware sprites** are composited by the display hardware above the bitplanes,
so software does not draw them pixel by pixel. There are eight channels, each
16 pixels wide in OCS/ECS (32 or 64 with AGA), and their colours are limited.
Playfield graphics cannot cover them, and a channel cannot be repositioned more
than once on a scanline.

Both use the bare-metal ``custom`` register layer and work without AmigaOS.
``blitter`` provides BOB drawing; a sprite helper module is not yet available
(see `Library support`_).

.. _amiga-bobs:

BOBs
----

.. index:: single: Amiga; BOB data format

BOB data layout
~~~~~~~~~~~~~~~

A BOB is stored plane-major: all rows of plane 0, then all rows of plane 1, and
so on. Plane 0 contains the least significant colour bit, and the BOB's
``depth`` gives the number of planes. Rows are left-justified, with the
leftmost pixel in bit 15 of the first word.

Rows are tightly packed; no guard word is needed. With
``width_words = ceil(width / 16)``:

* ``row_words   = width_words``
* ``row_bytes   = row_words * 2``
* ``plane_bytes = height * row_bytes``
* plane ``p`` starts at ``data + p * plane_bytes``

Store the mask separately from the colour planes, using the same row format.
A mask bit of 1 draws the corresponding BOB pixel; a 0 leaves the background
unchanged. Set unused low bits in the last mask word to 0 so they remain
transparent.

A 20 pixel wide, 2 line BOB with 2 planes has ``width_words`` 2, ``row_words`` 2,
``row_bytes`` 4 and ``plane_bytes`` 8::

    data:     plane 0                 plane 1
    row 0:    [ w0 ][ w1 ]            [ w0 ][ w1 ]
    row 1:    [ w0 ][ w1 ]            [ w0 ][ w1 ]

Drawing a BOB
~~~~~~~~~~~~~

``blitter.draw_bob()`` draws a complete BOB with one masked blit per plane::

    const uword ROW_BYTES = 40              ; 320 pixels per row
    blitter.draw_bob(bob, &bitmap.Planes, ROW_BYTES)

* ``bob`` - the ``^^mobs.Bob`` to draw,
* ``&bitmap.Planes`` - pointer to the destination bitmap's plane pointer array,
* ``ROW_BYTES`` - destination row pitch in bytes.

The destination planes may be separate allocations; the plane array supplies
each address. ``draw_bob`` computes the blitter modulo from the row pitch and
the number of words drawn per row. The Mob supplies the
pixel position; ``draw_bob`` derives the destination word and barrel shift, so
the source data does not need to be pre-shifted. The helper also derives the
source plane stride from the Mob's ``width`` and ``height``, so the planes must
be contiguous in the layout shown above.

Like ``masked_blit()``, ``draw_bob()`` uses the Jones trick: it writes
``ceil(width/16)`` destination words per row when word-aligned, and
``ceil(width/16)+1`` words when shifted, so the right-edge spill is preserved.
No guard word is stored in the source or mask.

There is no screen-edge clipping. The BOB's top-left position must be
nonnegative, and every destination word and row written by the blit must fit
inside the bitmap, including the extra spill word when shifted. Each row is
limited to 64 words (1024 pixels), so shifted BOBs are limited to 63 source
words.
``draw_bob`` waits for the blitter before each plane and returns after starting
the final plane. Call ``blitter.wait()`` before reading modified memory or
swapping buffers. It is a generic routine; for many small fixed-size bobs per
frame a tuned inline blit with hardcoded width and modulos pays off.

With a null ``mask``, ``draw_bob`` uses an all-ones mask and draws an opaque
rectangle. This is exact only when the draw position is word-aligned
(``x & 15 == 0``); otherwise the mask cannot preserve the partly covered edge
pixels. Supply a real mask for shifted positions.

Moving a BOB: erase, save, draw
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To move a BOB, restore the background at its old position, save the background
at its new position, then draw it there::

    ; constants that belong to the BOB
    const uword W = 5                     ; width_words = ceil(width/16)
    const uword DST_MOD = ROW_BYTES - W*2 ; row bytes minus one BOB row

    ; the BOB is at (old_x, old_y) and is moving to (new_x, new_y)
    ^^ubyte dst_old = screen_plane0 + old_y*ROW_BYTES + (old_x/16)*2
    ^^ubyte dst_new = screen_plane0 + new_y*ROW_BYTES + (new_x/16)*2

    ; 1. erase: put the saved background back where the BOB was
    blitter.copy_rect(&savebuf, dst_old, W, HEIGHT, 0, DST_MOD,
                      blitter.MINTERM_A_ONLY, false)
    ; 2. save: remember what is behind the new position
    blitter.copy_rect(dst_new, &savebuf, W, HEIGHT, DST_MOD, 0,
                      blitter.MINTERM_A_ONLY, false)
    ; 3. draw it at the new position
    bob.mob.x_pos = new_x
    bob.mob.y_pos = new_y
    blitter.draw_bob(bob, &bitmap.Planes, ROW_BYTES)

Each plane needs its own save buffer in chip RAM. A buffer is ``W`` words wide
when word-aligned and ``W+1`` words wide when shifted, ``HEIGHT`` rows tall,
with tightly packed rows (``src_mod`` 0). For
multi-plane BOBs, restore and save each plane using its own destination pointer
and buffer, then draw all planes in one call.

When BOBs overlap, restore them in **reverse draw order**. This restores the
last-drawn BOB first, preventing its saved background from erasing a neighbour.
You can restore all old positions first, save all new backgrounds next, and
then draw the BOBs in order.

This differs from ``blitter.erase()``, which clears destination pixels wherever
the shifted mask is set. It does not restore the background.

Double-buffered BOBs
~~~~~~~~~~~~~~~~~~~~

Drawing into the visible bitmap tears mid-frame. Double buffer instead:
``playfield.create_backplane()`` allocates a matching second bitmap, and each
frame flips them with ``playfield.flip_copper()`` (flip + copper re-patch in
one call, e.g. ``examples/amiga/beachballs.p8``). The rules:

* Wait for the blitter *before* flipping - a blit still writing to the old
  back buffer would corrupt the buffer that just became the front.
  ``flip_copper()`` does this wait itself; the per-frame pattern is just
  ``waitvsync``, ``flip_copper()``.
* Save buffers *and* old positions exist per screen buffer (two sets).
  After a flip, the hidden buffer still shows whatever was drawn into it two
  frames ago, so restoring from the other buffer's saves would smear.
* Prime both save sets before the loop, while both buffers still show the
  same image (copy the background into the back buffer once, then save
  from it into both sets).
* Per frame: restore old (reverse draw order), move, save new, draw -
  all against the hidden buffer. ``blitter.wait()`` after the loop covers
  only the exit path; inside the loop the next blit call waits on entry,
  so the last draw overlaps the key poll and vsync.

Non-word-aligned positions
~~~~~~~~~~~~~~~~~~~~~~~~~~

``draw_bob`` handles the sub-word shift, but save and restore operate on whole
words. It writes ``ceil(width/16)`` words starting at ``x/16`` when
word-aligned, and one extra word when shifted (Jones trick). Save and restore
the same word range::

    ; destination words written by draw_bob()
    uword words = (width + 15) / 16
    if (x & 15) != 0 {
        words += 1
    }
    uword dst_mod = ROW_BYTES - words*2

    ^^ubyte dst = screen_plane0 + y*ROW_BYTES + (x/16)*2
    blitter.copy_rect(dst, &savebuf, words, HEIGHT, dst_mod, 0,
                      blitter.MINTERM_A_ONLY, false)

This matches the variable-width blit behavior of ``masked_blit()`` and needs no
guard word in either the source or mask.

Animating BOBs
~~~~~~~~~~~~~~

To animate a BOB, point ``bob.data`` and ``bob.mask`` to another frame of the
same dimensions before drawing::

    bob.data = &walk_planes + frame * FRAME_BYTES
    bob.mask = &walk_masks + frame * MASK_BYTES
    blitter.draw_bob(bob, &bitmap.Planes, ROW_BYTES)

The library stores no frame index or timer, so the program decides when to
change frames. All frames must have the same dimensions because the Mob's
``width`` and ``height`` determine the plane stride. Convert sprite sheets to
the packed plane layout at build time; the blitter does not repack images.

Clipping at the screen edges
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``draw_bob`` always draws the full BOB and does not clip at screen edges. Make
sure every destination row and word it writes is inside the bitmap.

To crop a BOB, either repack the cropped rows of every plane and the mask into a
new packed frame, then update the Mob's dimensions and data pointers, or call
``masked_blit()`` once per plane with pointers and modulos for the cropped
region. Do not just advance ``Bob.data`` and reduce ``Mob.height``:
``draw_bob`` derives each plane's offset from the Mob's current dimensions, but
the original plane-major buffer still uses the old height.

.. _amiga-hardware-sprites:

Hardware sprites
----------------

.. index:: single: Amiga; hardware sprite

Sprites are overlaid by the display hardware, so they cost nothing per frame
beyond keeping their data and pointers current. The restrictions are real:
eight channels, 16 pixels wide each in OCS/ECS, and a colour set shared by a
pair of channels.

Sprite data structure
~~~~~~~~~~~~~~~~~~~~~

A sprite is described by a list of words in chip RAM, pointed to by
``custom.SPRnPTH`` and ``custom.SPRnPTL``:

* two control words: the position word and the control word;
* per display line, the image words: two for the OCS 2-plane format, four for
  the AGA 4-plane format;
* two zero words terminate the list.

The position word holds the vertical start (bits 15-8, low eight bits) and the
high bits of the horizontal position (bits 7-0). The control word holds the
vertical stop (bits 15-8), the attach bit (bit 7), the ninth bit of the vertical
start (bit 2), the ninth bit of the vertical stop (bit 1) and the lowest
horizontal bit (bit 0). The vertical stop is the line *after* the last line of
the sprite, so ``VSTOP - VSTART`` is the sprite height.

Within a line the words are stored low bitplane first. Bit 15 of a data word is
the leftmost pixel, so the words of the 2-plane format are the low and the high
colour bit of the same 16 pixels.

The list has to contain two words for every displayed line and end with two
zero words. The sprite pointer registers are advanced by DMA while the list is
read, so software must reload the starting pointer before the sprite is used
again on the next frame.

Sprite colours
~~~~~~~~~~~~~~

The eight sprites use colour registers 16-31, organised per channel pair:
channels 0/1 use 16-19, 2/3 use 20-23, 4/5 use 24-27 and 6/7 use 28-31. Within
each group of four registers the first one is transparent, so pixel values 1, 2
and 3 select the other three colours. Two channels can be *attached* (the attach
bit in the odd channel's control word) into one 16-colour sprite: four planes,
four words per line, values 0-15 selecting the 16 registers, at the cost of two
channels.

Animating and multiplexing sprites
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Repositioning a sprite updates its position/control words, so an object can
move every frame without changing pixel data. Animation can use another list
of the same height, with its pointer reloaded before the next use.

Multiplexing reuses one channel for several objects per frame: the list is
retriggered further down the screen, and the hardware overlays the same channel
again at the new vertical position. The usual way is to let the copper rewrite
the pointers and control words at specific scanlines, which costs no CPU time.
At least one display line must separate two uses of the same channel, and every
extra use is another entry in the copper list, so a multiplexed design is a
copper list design.

Sprite collision detection
~~~~~~~~~~~~~~~~~~~~~~~~~~

Collisions between sprites, and between sprites and the playfield, are detected
by the hardware. ``custom.CLXCON`` selects the planes and sprite pairs that take
part, and reading ``custom.CLXDAT`` returns the collision bits and clears them.
That is free and pixel exact, unlike the rectangle tests in ``mobs``.

AGA sprite modes
~~~~~~~~~~~~~~~~

AGA extends the same sprite hardware:

* 4 planes per sprite, so 16 colours instead of 4, still 16 pixels wide, four
  words per line;
* 32 or 64 pixel wide sprites through the width bits in the control word, with
  the matching number of words per line;
* ``custom.FMODE`` bits 0-1 select the sprite DMA fetch width (16, 32 or 64
  bits). It has to be high enough for the widest sprite in use; until an AGA
  register is written, sprites stay in the 16-pixel OCS compatible mode;
* ``custom.BPLCON4`` selects the palette bank: bits 0-3 for the even channels
  (0, 2, 4, 6) and bits 4-7 for the odd ones (1, 3, 5, 7), each bank being 16
  consecutive colours. A single 16-colour sprite uses its bank; an attached pair
  uses both banks at once for 256 colours.

.. caution::
    Fetch width and data layout must agree. A sprite whose width bits ask for
    64 pixels while ``FMODE`` still fetches 16 bits per access, or whose data
    structure stores fewer words than its width needs, shows a garbled or
    truncated sprite.

Library support
~~~~~~~~~~~~~~~

The ``sprites`` module provides bare-metal OCS/ECS ``init()``, ``pos()``,
``enable()``, ``disable()``, ``hide()`` and ``set_priority(pairs_in_front)``
helpers for ``mobs.Sprite`` descriptors. ``disable()`` stops sprite DMA globally;
``hide(sprite)`` hides one sprite while DMA remains enabled.
``pairs_in_front`` ranges from 0 (playfield in
front) to 4 (all sprite pairs in front). The hardware orders pairs 0/1, 2/3,
4/5 and 6/7, so only a leading number of pairs can be placed ahead of the
playfield. On a single playfield, the helper writes this value to BPLCON2
bits 5-3. Use the ``sprites.Priority`` enum for the argument. Set ``sprite.mob``,
``sprite.data`` and ``sprite.slot`` first;
``init(sprite)`` selects the assigned hardware channel, and ``pos(sprite)``
packs the linked Mob's position and height into the data header while reloading
that channel's pointer. ``hide(sprite)`` terminates its data header; another
``pos(sprite)`` writes the position header again and shows it.
``copper.base_list()`` leaves BPLCON2 alone. Set priority with the CPU helper
or add a BPLCON2 MOVE to the Copper list if priority should change by scanline.
AGA width, palette-bank and fetch-mode helpers are not implemented yet.

.. seealso::
    ``examples/amiga/agabobs.p8`` demonstrates BOBs on an AGA planar playfield.
