************************
Amiga specific libraries
************************

The libraries in this chapter only exist on the Amiga targets (``amiga500`` and
``amiga1200``). They are not available on the ``qemu68k`` target, nor on the 6502
and virtual targets.

Libraries that exist on all targets but have Amiga/m68k specific additions stay
documented in :doc:`libraries`; the last section of this page briefly sums those
additions up.

.. _amigaos-libraries:

AmigaOS libraries
-----------------

.. index:: pair: Libraries; amigaos libraries
.. index:: single: Targets; AmigaOS library modules

The Amiga targets bind a number of AmigaOS libraries and device drivers as Prog8 library
modules. Import them the same way as any other library::

    %import graphics
    %import intuition

.. note::
    Most bindings are generated from the Amiga NDK include files by
    ``scripts/amigalibs2prog8.py`` and are therefore complete for those libraries.
    The modules that are not generated, and the extra hand-written routines and
    tweaks added on top, are listed in `Manual additions and tweaks`_ below.
    Many AmigaOS library calls take structures that Prog8
    does not have a type for; those appear as ``pointer``. See the
    :source:`library source code <compiler/res/prog8lib>` for the exact signatures.

**AmigaOS libraries**

.. note::
    The startup code of the amiga500 and amiga1200 targets already opens ``dos``,
    ``graphics``, ``intuition``, ``icon`` and ``utility`` for you, so their modules are
    ready to use without doing anything. ``utility.library`` is available from Kickstart 2.0;
    on Kickstart 1.3 its base is unavailable. (``exec`` needs no opening at all: ExecBase always
    lives at 4.w and is available as ``sys.SysBase``.) The remaining libraries below -
    ``iffparse``, ``lowlevel``, ``arexx``, ``asyncio``, ``asllib``, ``datatypes`` (amiga1200
    only, kickstart 3.0+) and ``diskfont`` - must be opened on demand by calling their
    ``openlib()`` subroutine, and closed again with ``closelib()``. You do not have to call
    ``closelib()`` yourself: the startup code closes every library that was actually opened
    (including the on-demand ones) automatically when the program exits. Calling ``closelib()``
    manually is still fine and recommended when you are done with a library well before the
    program ends, to free its resources early.

``exec`` (``exec.library``)
    Memory allocation, message ports, interrupts, device and resource management. Also the
    base for everything else: ``exec.OpenLibrary()`` is what opens the optional libraries.
    The ``exec`` module is always available, ``sys.SysBase`` is the ExecBase at 4.w.
    In addition to the library bindings it provides ``exec.NewList(list)`` to initialize
    an Exec list header in place. The portable ``lists`` module's ``init()`` calls this
    on the Amiga targets; see the :ref:`linked-list-iteration` section for the header
    layout and iteration rules.

``dos`` (``dos.library``)
    File and directory handling, locking, IOStreams, requesters and the DOS error codes.
    Text output goes through the ``textio`` module, which is built on top of ``dos``.

``graphics`` (``graphics.library``)
    Bitmaps, rastports, fonts, regions, sprites and the ``Blt*`` blitter calls.

``intuition`` (``intuition.library``)
    Screens, windows, gadgets and menus. Requires ``graphics``.
    Next to the library bindings it provides three inline helper routines that read the
    standard structures embedded in a ``Screen``: ``GetScreenViewPort(screen) -> pointer``,
    ``GetScreenRastPort(screen) -> ^^graphics.RastPort`` and
    ``GetScreenBitMap(screen) -> ^^graphics.BitMap``.

``utility`` (``utility.library``)
    Tag item handling (tags, tag lists) and the other small helpers from the utility library. Requires Kickstart 2.0 or newer.

``icon`` (``icon.library``)
    Disk objects, free lists and tooltypes. 

``iffparse`` (``iffparse.library``)
    Parsing IFF files. Requires Kickstart 2.0 or newer.

``lowlevel`` (``lowlevel.library``)
    Cache control, interrupt and timer related calls. Requires Kickstart 2.0 or newer.

``arexx`` (``rexxsyslib.library``)
    ARexx scripting support. Requires Kickstart 2.0 or newer.

``asyncio`` (``asyncio.library``)
    Asynchronous file IO.

``asllib`` (``asl.library``)
    Standard requesters: file, font and screen-mode. Requires Kickstart 2.0 or newer.
    See ``examples/amiga/aslfile.p8``.

``diskfont`` (``diskfont.library``)
    Loading and using fonts from disk. Opened on demand.

``datatypes`` (``datatypes.library``, **amiga1200 only**, AmigaOS 3.1 / V40)
    Essentials subset for loading and inspecting media (images, sounds, text) via DataType
    objects: object lifecycle and attribute queries (``GetDTAttrsA``),
    8SVX playback via ``DoDTMethodA`` / ``DTM_TRIGGER``, and picture rendering in
    Intuition windows via ``AddDTObject``. It includes ``DataType`` / ``DataTypeHeader``
    metadata and ``BitMapHeader``, the
    ``GID_*`` / ``DTF_*`` classification constants, and ``DTA_*`` / ``PDTA_*`` /
    ``SDTA_*`` / ``TDTA_*`` / ``ADTA_*`` tags.
    On OS 3.1, sound metadata includes sample length, period, volume and cycles; exact sample
    rate is not exposed by the V40 sound datatype.
    See ``examples/amiga/datatype.p8``.

**Devices and hardware**

``trackdisk`` (``trackdisk.device``)
    Raw floppy disk access: motor control, seeking, raw read and write, and drive geometry.
    Also exposes the ``TD_*`` driver command codes for anything the module doesn't wrap.

``audio`` (``audio.device``)
    Playback of 8 bit signed samples, per channel, in the background or synchronous.
    Exposes the ``ADCMD_*`` IO commands.

``timer`` (``timer.device``)
    Timing via IO requests: microhertz, vblank and eclock units, plus the
    ``TR_ADDREQUEST`` based wait-until. Exposes the ``TR_*`` driver command codes.

``ahi`` (``ahi.device``, **amiga1200 only**)
    Higher quality multi-channel sample playback. AHI v4 compatible, so only 8 and 16 bit
    sample types are available. Open a unit with ``ahi.open()`` (pass 0 for the system
    default output) and close it with ``ahi.close()``. Requires a system with ahi.device
    installed (real hardware or full emulation), so it does not work under vamos.

``custom``
    Direct access to the custom chipset, with no library involved. Contains
    ``grab_system(replace_view)``/``restore_system()`` to take over the whole system and interrupt
    handlers for hardware banging programs (games, demos); pass true to load
    a blank NULL view first, false to leave the currently active View untouched
    (display DMA stays on). ``compute_display()`` accepts a vertical offset
    in scanlines (positive moves the display down) and computes shadow display
    register values (``DisplayRegs``) for a bare-metal playfield without
    Intuition or View/ViewPort setup. ``view_width``/``view_height`` are the
    visible viewport; ``bitmap_width`` sets the backing bitplane row pitch.
    The caller can allocate extra bitmap rows and update plane pointers for
    vertical scrolling. The shared ``isPAL`` boolean (set by ``grab_system()``)
    reports the machine's TV standard. ``waitvsync()``,
    AGA colour register writes, and controller input routines. Canonical
    ``$00RRGGBB`` palette helpers ``load_colors()``/``set_color()``/
    ``cycle_forward()`` and ``pack12()`` load the bare-metal palette AGA-aware,
    and ``load_colors_ocs()`` takes raw 12-bit ``$0RGB`` words;
    ``load_iff_cmap()`` loads an IFF/ILBM CMAP chunk (packed RGB888 triples)
    and converts per chipset (exact 24-bit on AGA, quantised to 4 bits per
    channel on OCS/ECS)
    (``graphics`` has the matching ``ViewPort`` loaders
    ``load_palette()``/``set_palette_color()``/``load_iff_cmap()``, which go
    through graphics.library instead of poking the registers). ``detect_chipset()`` returns
    ``custom.CHIPSET::OCS``, ``custom.CHIPSET::ECS`` or
    ``custom.CHIPSET::AGA`` using the Denise ID and VPOSR hardware registers.
    ``joystick_axes(port)`` returns signed x/y values in -1..1.
    ``joystick_fire(port)`` and ``joystick_fire2(port)`` return true when the
    respective button is pressed. ``joystick_state(port)`` combines directions
    and both fire buttons into bit flags such as ``custom.JOY_UP`` and
    ``custom.JOY_FIRE2``; combine these flags with bitwise OR.
    ``get_button_states()`` samples the input registers once each and returns a
    ``uword`` mask with set bits for pressed buttons. Test a button with
    bitwise AND, for example
    ``(custom.get_button_states() & custom.BTN_JOY_FIRE) != 0``. The ``BTN_*``
    constants identify these physical inputs:

    * ``BTN_MOUSE_LEFT`` (``$0040``): connector 1 button 1, also joystick port 0 fire.
    * ``BTN_JOY_FIRE`` (``$0080``): connector 2 button 1 (joystick port 1 fire).
    * ``BTN_MOUSE_MIDDLE`` (``$0100``): connector 1 middle button.
    * ``BTN_MOUSE_RIGHT`` (``$0400``): connector 1 button 2, also joystick port 0 second fire.
    * ``BTN_JOY_FIRE2`` (``$4000``): connector 2 button 2 (joystick port 1 second fire).

    ``mouse_delta(port)`` returns signed, scaled x/y movement since its previous call.
    ``left_button()``, ``middle_button()`` and ``right_button()`` read the three
    mouse buttons on connector 1 and return true when pressed. Joystick ports are
    zero-based: 0 is JOY0DAT / connector 1, 1 is JOY1DAT / connector 2. The first
    fire input is read from CIA-A; the second is on pin 9 via POTGOR.
    Use the joystick routines with a digital joystick and the mouse routines with
    a mouse or trackball; these are direct hardware readers, not gameport.device calls.
    Call ``mouse_init(port, scale_divisor)`` before polling to set the sensitivity:
    a divisor of 4 returns about one movement unit per four hardware counts, with
    fractional counts accumulated between calls. Supported divisors are 1, 2, 4 and 8;
    1 returns raw counts, and other values are treated as 1.
    If ``mouse_init`` is not called, the first ``mouse_delta`` call initializes
    with a divisor of 1 and returns zero.
    Poll mouse deltas once per frame because each hardware counter is 8-bit.

``blitter``
    Bare-metal blitter programming, no OS calls: ``copy_rect()`` and
    ``fill_rect()`` (use pattern ``$0000`` to clear or ``$ffff`` to fill),
    ``masked_blit()``, and the line drawer (``line_init()``, ``line_draw()``,
    ``line_color()``).
    For polygon filling, ``line_draw(..., fill=true)`` draws top-to-bottom
    XOR boundaries, omits each edge's first pixel, and skips horizontal edges.
    Call ``area_fill()`` and then redraw the complete outline with
    ``fill=false``. ``area_fill()`` fills all scanlines between set source bits
    using the blitter's hardware FILL mode. ``set_blitpri()``
    sets the blitter DMA priority, ``wait()`` busy-waits until the blitter is
    idle. All pointers must be in CHIP RAM, width is limited to 64 words per
    operation, and every operation waits for the blitter at entry but not for
    its completion on return. The ``MINTERM_*`` constants select the logic
    function.

``playfield``
    Bare-metal bitplane manager to pair with ``custom.compute_display()``:
    ``create()`` accepts viewport and backing bitmap dimensions plus the same
    vertical offset, allocates chip-RAM bitplanes, and computes the shadow
    display registers. Larger bitmap dimensions provide scrolling room; callers
    move plane pointers to select the viewport.
    ``show()`` installs plane addresses into ``BPLxPT``,
    ``apply_registers()`` writes the rest of the shadow registers once per
    setup, ``patch_copper()`` patches plane addresses into a copper list, and
    ``set_scroll()`` sets fine horizontal scroll. For hardware scrolling
    within a larger backing bitmap, ``scroll_to()`` positions a
    register-driven display (and returns the fine value), while
    ``patch_copper_at()`` patches scrolled plane addresses into a copper
    list. ``waitvsync()`` aliases the frame pacer. For double buffering,
    ``create_backplane()`` allocates a matching second buffer, ``flip()`` swaps
    the two, and ``clear_backplane()``/``destroy_backplane()`` manage it
    (see ``examples/amiga/3d.p8``). Kickstart 1.3 compatible
    (the ``AllocBitMap`` fast path is only
    used on 3.0+). See ``examples/amiga/custominputs.p8`` for
    the equivalent manual setup (raw copper words and ``AllocMem`` bitplanes).

``copper``
    Bare-metal copper list builder: emits raw copper words into a caller-owned
    chip-RAM buffer, for use with or without the OS. ``move()`` returns the value
    word's index so the main loop patches values in place instead of
    rebuilding; ``wait()``/``skip()`` hide the instruction encoding,
    ``attach_planes()`` emits ``BPLxPT`` slots for ``playfield.patch_copper()``,
    ``rasterbar()`` builds a patchable per-scanline color skeleton, and
    ``aga_move()``/``aga_set()`` encapsulate the four-MOVE 24-bit AGA color
    sequence behind a single slot handle.
    Emissions beyond capacity are dropped and counted in ``overflows``.

``text``
    Bare-metal 8x8 text renderer: ``init()`` caches pitch, depth and plane
    pointers, ``char()`` draws one glyph and ``print()`` draws a string with
    LF handling and reverse video (bit 7 of the character byte). CPU-only, no
    OS calls, transparent glyph background, exact foreground color including 0.
    Limited to the embedded 8x8 font; no clipping is performed.

``sound``
    Direct Paula audio: hardware-banging 4-channel 8-bit PCM playback with no
    OS involved, to be used after ``custom.grab_system()``. Plays samples once,
    looped, as 14-bit channel pairs, or as double-buffered gapless streams.
    All sample data must be in CHIP RAM. One-shot samples must start with a
    single zero word; after the sample ends Paula loops on that word, so the
    channel falls silent. ``is_playing`` and ``stream_ready`` poll the audio
    interrupt request flag to notice the end, so no interrupt handler is
    installed. ``set_freqvol()`` takes a sample rate in hertz and converts it
    to the Paula playback period using the machine's PAL or NTSC chip clock
    (the other ``set_period()`` variant takes the raw period value directly).

``ptplayer``
    ProTracker module (MOD) player: load and play Amiga ProTracker modules via
    the ptplayer playroutine. Install the player with ``ptplayer.install()``,
    start playback with ``ptplayer.play()`` / ``ptplayer.stop()`` and query
    status with the module's routines. See ``examples/amiga/modplay.p8``.

``easygfx``
    Friendly OS graphics wrapper in the style of the ``gfx_*`` pixel modules:
    per-call pen, client-relative coordinates, no structs or tag lists to manage.
    ``fullscreen()`` opens a custom screen with a backdrop window (an empty title
    hides the screen bar, otherwise it stays), ``windowed()`` opens a fixed-size
    window on the Workbench screen, ``close()`` tears it all down again.
    Use ``fullscreen()`` when you need your own resolution, depth or palette;
    ``windowed()`` inherits all three from the Workbench.
    Primitives (``clear/plot/line/rect/fillrect/circle/oval/disc/filled_oval/text``),
    ``scroll/readpixel/textwidth``, ``palette/setcolor``, ``drawmode`` and bucket
    or boundary ``fill/fillto`` are all plain ``graphics.library`` calls, so they
    clip to the window. The ``rastport`` and ``window`` variables expose the handles
    for direct library calls and IDCMP message handling. Kickstart 1.3 compatible.

Manual additions and tweaks
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The bindings are generated from the NDK, but the following manual additions and
tweaks exist on top of that.

Hand-maintained modules (not generated from the NDK)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- ``custom``, ``blitter``, ``copper``, ``playfield``, ``text``, ``sound``,
  ``ptplayer``, ``easygfx`` - the bare-metal hardware modules.
- ``audio``, ``timer``, ``trackdisk`` - thin ``audio.device`` / ``timer.device`` /
  ``trackdisk.device`` wrappers.
- ``diskfont`` - trimmed to pre-3.0 content (up to Kickstart 2.0): the outline
  engine, diskfont control and tagged-font matching APIs need 3.0+ and were
  removed.
- ``datatypes`` (amiga1200 only) - a V39/V40 essentials subset (object lifecycle,
  selected attribute/method/window calls and tags); later APIs, method tables,
  printing and drag selection are omitted.
- ``ahi`` (amiga1200 only) - AHI v4 sample playback.

Hand-written routines added to the generated modules
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

These are not part of the NDK and were added by hand:

- ``exec`` - ``NewList(list)`` initializes an Exec list header in place.
- ``dos`` - ``read_file(filename, chip_ram) -> pointer, long`` loads a whole file
  into freshly allocated memory.
- ``graphics`` - the ``HIRES``/``LORES``/``LACE``/``SUPERHIRES`` view-mode
  constants, the palette loaders ``load_palette()``/``set_palette_color()``/
  ``load_iff_cmap()``, and the ``gfxmacros.h`` style flag routines ``BNDRYOFF()``,
  ``SetOPen()``, ``SetDrPt()``, ``SetAfPt()``, ``SetWrMsk()``,
  ``SafeSetOutlinePen()`` and ``SafeSetWriteMask()``.
- ``intuition`` - ``GetScreenViewPort()``, ``GetScreenRastPort()`` and
  ``GetScreenBitMap()`` reach the structures embedded in a ``Screen``.
- ``arexx``, ``iffparse``, ``lowlevel``, ``asllib`` - the ``openlib()`` /
  ``closelib()`` pairs used to open these libraries on demand.

Tweaks applied by the generator
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- ``asl`` is exposed as the ``asllib`` module, because ``asl`` is a 68k shift
  instruction.
- ``BOOL`` struct fields become ``uword`` (a 16-bit field on m68k, which a 1-byte
  ``bool`` would read as zero on big-endian); function return values do become
  ``bool``.
- The generated ``Screen`` struct stops before the embedded ``ViewPort``, so the
  ``intuition.GetScreen*`` helpers above are used to reach the ``ViewPort``,
  ``RastPort`` and ``BitMap``.
- The ``graphics`` palette loaders build their tables in fixed static scratch
  buffers instead of allocating memory per call.

Amiga/m68k specifics in cross-target libraries
----------------------------------------------

These libraries exist on multiple targets and remain documented in
:doc:`libraries`. On the Amiga and m68k targets they gain the following
additions:

- ``adpcm`` - on m68k the decoder writes to a caller-provided memory buffer
  (``decode_block_mono(nibblesptr, outptr)``) instead of the X16 VERA FIFO.
- ``arena`` - on m68k and virtual targets the slab and allocation sizes are
  ``long`` (slabs above 64 KB) and allocations are rounded to even addresses.
- ``emudbg`` - on the Amiga targets ``console_write``, ``console_nl`` and
  ``exitemu()`` use the WinUAE/Copperline uaelib trap, the remaining routines
  are Copperline-only, and the module must not be used on real hardware or
  FS-UAE.
- ``floats`` - requires a 68020 or better CPU with an 68881/68882 FPU; the
  amiga500 target is assembled with ``-m68020 -m68881`` and amiga1200 with
  ``-m68881``.
- ``lists`` - on amiga500/amiga1200 it wraps ``exec.library`` and uses the real
  Exec ``MinList``/``MinNode``/``Node`` layouts.
- ``math`` - ``mul32`` compiles to a single m68k multiply instruction.
- ``sorting`` - m68k provides the ``uw``/``ul`` (word/long) variants only, no
  byte variants.
- ``sys`` - m68k adds ``cpuAtLeast68020()``, ``exec_version()`` and
  ``stack_size()``; ``die()`` shows an Intuition alert, ``exit()`` passes a
  word in D0, ``memsetw``/``memsetl`` require alignment, and
  ``set_carry``/``clear_carry`` also manage the X flag.
- ``wavfile`` - Paula helpers convert 8-bit unsigned and 16-bit little-endian
  samples to Paula's signed/big-endian format, and ``sound.set_freqvol()``
  converts a sample rate to a Paula period.

A few more cross-target modules (``compression``, ``conv``, ``coroutines``,
``lineclip`` and ``strings``) ship a separate m68k implementation but expose
the same API as on the other targets.
