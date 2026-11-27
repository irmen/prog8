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

``hwinfo``
    Hardware detection for bare-metal programs. Call ``gather()`` while the OS is
    still running, before ``custom.grab_system()``, because some of the detected
    information is not recoverable afterwards. ``gather()`` is idempotent and always
    returns true after a snapshot attempt; missing sources leave their fields at
    defaults. It fills in the module variables: ``ready``, ``cpu``
    (``CPU`` enum), ``fpu``, ``VBR``, ``videomode`` (``VIDEO`` enum),
    ``chipset``, ``chipRegion``, ``floppyCount`` and ``kickVersion``. Memory is
    reported as ``MemRegion`` structs (``base`` pointer, ``long size``
    and ``word priority``);
    ``chipRegion[0]`` holds the single chip RAM segment, ``slowRegions`` holds
    up to 4 entries with ``slowCount``, and
    ``fastRegions`` up to 8 entries with ``fastCount``. Additional regions
    beyond those limits are skipped.

``custom``
    Direct access to the custom chipset, with no library involved. Contains
    ``grab_system(replace_view)``/``restore_system()`` to take over the whole system and interrupt
    handlers for hardware banging programs (games, demos); pass true to load
    a blank NULL view first, false to leave the currently active View untouched
    (display DMA stays on). Bare-metal programs can also install interrupt
    handlers via ``set_handler(level, handler)`` / ``restore_handler(level)``
    (levels 1-7), which installs a generic VBR-aware trampoline at the
    autovector. Handlers must be integer-only (no FPU instructions): the
    trampoline saves D0-D7/A0-A6 but no FPU state. ``set_level3_handler(handler)``
    installs a handler at level 3 without enabling any source; use the
    per-source helpers to enable what you need:
    ``enable_irq(source)`` / ``disable_irq(source)`` / ``ack_irq(source)`` take an
    ``INTF_*`` constant (VERTB, COPER, BLIT, PORTS, EXTER, TBE, DSKBLK,
    SOFTINT, AUD0-3, RBF, DSKSYN, and others). These are the Amiga interrupt
    source bitmasks from the NDK headers. ``restore_system()`` restores any
    installed handlers automatically. A VERTB handler must call
    ``ack_irq(INTF_VERTB)`` to acknowledge the interrupt. See
    ``examples/amiga/vertb-demo.p8``.
    ``compute_display()`` accepts a vertical offset
    in scanlines (positive moves the display down) and computes shadow display
    register values (``DisplayRegs``) for a bare-metal playfield without
    Intuition or View/ViewPort setup. ``view_width``/``view_height`` are the
    visible viewport; ``bitmap_width`` sets the backing bitplane row pitch.
    The caller can allocate extra bitmap rows and update plane pointers for
    vertical scrolling. The struct carries the registers the computed display
    geometry depends on, including AGA's ``FMODE`` fetch mode: on AGA the
    DDFSTRT/DDFSTOP and BPLxMOD values are derived from its 32-bit fetch.
    Attribute registers that do not affect the geometry (``BPLCON3``,
    ``BPLCON4``) are deliberately left out; programs that need them write the
    registers directly or add their own copper MOVEs. Both registers are
    write-only on AGA (reads return undefined values), so never read-modify-write
    them: writing back a masked garbage read reintroduces stray bits such as
    BPLCON3's SPRES sprite resolution, which makes sprites display at half
    width in a lores playfield. Compose full register values instead, the way
    ``set_aga_color()`` and the ``sprites`` module do.
    The shared ``isPAL`` boolean (set by ``grab_system()``)
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

    Both CIA TOD counters are reachable through ``tod_read(cia)``,
    ``tod_write(cia, count)``, ``tod_alarm_set(cia, count)`` and
    ``tod_alarm_enable(cia, bool)``, where ``cia`` is ``custom.CIA::A`` or
    ``custom.CIA::B``. A TOD is a 24-bit binary *event* counter, not a clock:
    CIA-A counts 50/60 Hz events (mains tick, or VSync on later machines) and
    CIA-B counts horizontal sync at about 15.7 kHz, which makes it a line
    counter rather than a time of day. ``tod_read`` returns the raw count, keeps
    the latched HI, MID, LOW read order for you and wraps at ``$ffffff``; there
    is no hours/minutes/seconds decoding anywhere. Reading is harmless while the
    OS runs, but writing reloads and therefore disturbs a counter the OS owns:
    ``timer.device`` drives its system tick from CIA-A and ``graphics.library``
    uses CIA-B as its video beam follower, so ``tod_write`` and
    ``tod_alarm_set`` belong after ``custom.grab_system()``. Timed waits under
    the OS should use ``timer.device`` (``timer``) or ``ReadEClock`` instead.
    ``tod_alarm_set`` loads the alarm register and ``tod_alarm_enable`` masks
    the resulting interrupt (ICR bit 2, ``custom.CIAICRF_ALRM``); with the mask
    off the alarm flag can still be polled from ``CIAA_ICR``/``CIAB_ICR``,
    since reading an ICR acknowledges it. The alarm matches once, so a handler
    that wants a repeating alarm must re-arm it with the next count, for
    example ``tod_alarm_set(cia, tod_read(cia) + interval)``.

    The two 16-bit interval timers of each CIA are reachable through
    ``timer_start(cia, timer, reload, continuous)``, ``timer_stop(cia, timer)``,
    ``timer_value(cia, timer)`` and ``timer_flag(cia, timer)``, with ``timer``
    one of ``custom.TIMER::A`` or ``custom.TIMER::B``. They count the ~7 MHz
    ø2 clock (7.09 MHz PAL, 7.16 MHz NTSC) down from the loaded value: 65535
    ticks is about 9.2 ms, so they are for short precise waits and cycle-level
    measurement, not for long delays. One-shot mode stops after the underflow,
    continuous mode reloads and runs again. ``timer_value`` returns what is
    left of the count, so elapsed cycles are the loaded value minus that.
    These are polling helpers only: ``timer_flag`` tests (and acknowledges)
    the underflow flag by reading the CIA ICR, and Prog8 cannot install a CIA
    interrupt handler yet, so nothing here hooks one up. Like ``tod_write``,
    they poke registers the OS owns, so use them after ``grab_system()``.
    ``PBON`` is always left off, which keeps the timers from driving the
    CIA port B pins.

``mobs``
    Shared movable-object descriptor types for the Amiga bare-metal helpers:
    ``mobs.Mob`` (x/y position, anchor offsets, width, height and collision
    radius), ``mobs.Bob`` (a ``Mob`` plus bitplane depth, data and mask pointers,
    id and state) and ``mobs.Sprite`` (a ``Mob`` plus a data pointer, hardware
    slot, id and state). Pure data, no OS or library dependencies; ``blitter``
    and ``sprites`` build on top of ``Mob``. Client code owns animation timing,
    frame selection and positions.
    Geometry helpers are integer-only and read the anchor and box fields:
    ``collides(a, b)`` and ``contains_point(m, px, py)`` are origin-aware AABB
    tests, each with a fast path when the origin is (0, 0).
    ``collides_circle(a, b)`` and ``contains_circle(m, px, py)`` use the
    ``radius`` field (0 falls back to the AABB tests). ``left()``, ``top()``,
    ``right()``, ``bottom()``, ``center_x()`` and ``center_y()`` give the
    origin-adjusted box edges without retyping the origin adds.
    ``delta(a, b)`` returns the signed displacement, ``direction_to(a, b)`` a
    0-23 compass direction (N=0, clockwise; an 8-way facing derives as
    ``((d+1)/3) & 7``), ``manhattan_distance()`` and ``chebyshev_distance()``
    replace the square root with adds/compares, and ``union_box()`` /
    ``intersection_box()`` combine two boxes into left/top/right/bottom.

``sprites``
    Bare-metal OCS/ECS hardware-sprite helpers for ``mobs.Sprite`` descriptors:
    ``init()``, ``pos()``, ``enable()``, ``disable()``, ``hide()``,
    ``set_priority(pairs_in_front)``, ``set_colors(pair, c1, c2, c3)``,
    ``set_colors16(colors)`` and ``attach(odd_sprite, on)``.
    The priority argument ranges from 0 (playfield in
    front) to 4 (all sprite pairs in front). Pair order is fixed, so this selects
    a leading number of pairs rather than arbitrary pairs; use the
    ``sprites.Priority`` enum values. In single-playfield
    mode it writes the count to BPLCON2 bits 5-3. ``disable()`` stops all sprite
    DMA, while ``hide(sprite)`` hides one channel. ``copper.base_list()`` leaves
    BPLCON2 unchanged; a Copper list can add a MOVE if priority should change
    by scanline.
    ``set_colors(pair, c1, c2, c3)`` sets the three visible 12-bit colours of a
    4-colour pair; colour 0 stays transparent. ``set_colors16(colors)`` writes all
    16 colours of the single shared attached-sprite bank (COLOR16-31).
    ``attach(odd_sprite, on)`` sets the attach bit in an odd channel's control
    word so it pairs with the preceding even channel as one 16-colour sprite;
    ``pos()`` preserves that bit when it rewrites the control word. Call
    ``pos()`` or ``aga_pos()`` on every displayed frame, even for a stationary
    sprite: sprite DMA advances ``SPRxPT`` through the data list as it reads,
    so reloading the start pointer each frame is what keeps the sprite visible.
    A copper MOVE to ``SPRxPT`` can replace that part, but it does not update
    POS/CTL; see the hardware-sprites chapter for the CPU, hybrid and copper
    approaches. ``pos()`` does not validate ``Mob.height``: keep
    ``y_pos + SPRITE_V_OFFSET + height <= 511`` and provide one data line per
    sprite scanline.
    Colours are 12-bit ``$0RGB`` values for OCS/ECS. On AGA the color routines select the
    bank-0/LOCT-0 write window themselves and keep the sprite resolution set
    with ``aga_set_resolution()``: BPLCON3 and BPLCON4 are write-only
    registers, so the module composes their values from its own state instead
    of reading them back, and ``aga_set_bank()`` is a full BPLCON4 write that
    clears the BPLAM bitplane color mask.

``blitter``
    Bare-metal blitter programming, no OS calls: ``copy_rect()`` and
    ``fill_rect()`` (use pattern ``$0000`` to clear or ``$ffff`` to fill),
    ``masked_blit()`` and mask-based ``erase()`` for BOBs, ``draw_bob()`` for a
    whole ``mobs.Bob`` (one masked blit per plane, position, shift and source
    plane stride derived from the Mob), and the line drawer
    (``line_init()``, ``line_draw()``, ``line_color()``).
    The module is designed for traditional non-interleaved planar screens, with
    independently allocated contiguous bitplanes. The rectangle and BOB primitives
    process one plane per call, so an 8-plane BOB requires eight blits. This costs
    per-plane setup, but keeps plane addressing flexible and lets every plane share
    one mask without interleaving assets or replicating the mask. Line-interleaved
    screens can reduce the number of setups by treating planes as additional blit
    rows, but require matching interleaved source data and repeated masks, reduce
    the maximum blit height by the plane count, and still need the per-plane DMA.
    ``masked_blit()`` uses the Jones trick (shifted blits are one word wider,
    no guard word stored), but neither it nor ``erase()`` clips against screen
    or bitmap edges. The caller must
    keep every destination word valid. Clip top or bottom by adjusting row pointers
    and height, and clip right by reducing ``width_words`` and recomputing all modulos.
    Direct left clipping is only word-aligned: advance the source and mask, reduce
    ``width_words``, and use shift zero. Other negative X positions require offscreen
    left padding or a dedicated left-clipping routine.
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
    the two (or ``flip_copper()`` to wait, swap and re-patch a copper list in
    one call), and ``clear_backplane()``/
    ``destroy_backplane()`` manage it (see ``examples/amiga/3d.p8`` and the
    double-buffered BOB recipe in :ref:`amiga-bobs`). Kickstart 1.3 compatible
    (the ``AllocBitMap`` fast path is only
    used on 3.0+). See ``examples/amiga/custominputs.p8`` for
    the equivalent manual setup (raw copper words and ``AllocMem`` bitplanes).

``copper``
    Bare-metal copper list builder: emits raw copper words into a caller-owned
    chip-RAM buffer, for use with or without the OS. ``move()`` returns the value
    word's index so the main loop patches values in place instead of
    rebuilding; ``wait()``/``skip()`` hide the instruction encoding,
    ``attach_planes()`` emits ``BPLxPT`` slots for ``playfield.patch_copper()``,
    and ``base_list(display, depth)`` adds those slots plus the common display
    registers from a ``custom.compute_display()`` result, including ``FMODE``
    (the copper list, not a direct register write, owns the fetch mode). It
    leaves BPLCON2 unchanged so callers own sprite/playfield priority, and it
    does not emit BPLCON3/BPLCON4, which callers set directly when needed (for
    example to reset AGA's SPRES sprite resolution, see the ``custom``
    section). It returns the base
    slot index for ``playfield.patch_copper()`` or ``set_planes()``. Select the
    display mode with ``DISPLAY_*`` flags when computing the display. The helper
    assumes ``copper.init()`` has been called on a chip-RAM buffer and does not
    enable DMA. It returns ``$ffff`` for a null display, invalid depth, or when
    the plane-pointer slots do not fit.
    Later register MOVE overflows increment
    ``overflows`` but do not change the returned base, so reserve space for the
    complete prefix and the caller's additions. The caller still adds DMA
    setup, colors and any extra copper instructions before ``copper.end()``.
    ``attach_planes()`` remains available when only plane-pointer slots are
    needed.
    ``rasterbar()`` builds a patchable per-scanline color skeleton, and
    ``aga_move()``/``aga_set()`` encapsulate the four-MOVE 24-bit AGA color
    sequence behind a single slot handle.
    Emissions beyond capacity are dropped and counted in ``overflows``.

``gfxtext``
    Bare-metal 8x8 text renderer: ``init()`` caches pitch, depth and plane
    pointers, ``char()`` draws one glyph and ``print()`` draws a string with
    LF handling and reverse video (bit 7 of the character byte). CPU-only, no
    OS calls, transparent glyph background, exact foreground color including 0.
    The embedded 8x8 font (128 glyphs) is the default after each ``init()``.
    ``set_font()`` points the renderer at a caller-owned 1024-byte table with
    8 rows per glyph, and ``set_font(0)`` restores the built-in font.
    ``set_glyph()`` overwrites one glyph, codepoint 0-127, in the active table
    from an 8-byte buffer; invalid codepoints are ignored. Bit 7 selects
    reverse video and is not part of the codepoint. Call the font routines
    after ``init()`` because ``init()`` resets the font. The active table must
    be writable RAM when ``set_glyph()`` is used. No clipping is performed.

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
    clip to the window. ``imsg_poll()`` fetches the next IDCMP message without
    blocking and returns 0 when the queue is empty; drain messages with
    ``imsg_poll()``/``imsg_reply()`` in animation loops. ``imsg_wait()`` blocks
    until a message arrives. The ``rastport`` and ``window`` variables expose the
    handles for direct library calls and IDCMP message handling.
    ``active_screen()`` returns the screen associated with the current window:
    the custom screen in fullscreen mode, the Workbench screen in windowed mode,
    or 0 when closed. The returned pointer is borrowed; use it to access the
    screen's bitmap or viewport, but let ``easygfx.close()`` close the screen.
    For example, after opening the display::

        ^^graphics.BitMap bitmap = intuition.GetScreenBitMap(easygfx.active_screen())

    See
    ``examples/amiga/penrose-triangle.p8`` for a polling loop. Kickstart 1.3
    compatible.

OCS-style bare-metal displays on AGA machines
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. index:: pair: Libraries; AGA compatibility

An OCS/ECS-style display (lores bitplanes, 16-pixel sprites, 12-bit colors)
usually runs unmodified on an AGA machine: on real hardware such a program
either never runs an AGA screen's copper list (the chipset powers up and
resets in ECS compatibility mode, with ``FMODE`` = 0 and neutral
``BPLCON3``/``BPLCON4``), or graphics.library builds the copper list and
writes the AGA registers itself for every view. The problem case is a
bare-metal program that replaces the OS copper list with its own, for
example after ``grab_system()`` on top of a Workbench screen: the AGA-only
registers ``FMODE`` ($dff1fc), ``BPLCON3`` ($dff106) and ``BPLCON4``
($dff10c) then keep whatever the OS screen last wrote, and the display
inherits the Workbench settings. Typical symptoms: a shredded or shifted
display (leftover ``FMODE``), colors from the wrong palette slice (leftover
BANK/LOCT window), and sprites at half width in a lores display (leftover
sprite resolution in BPLCON3's SPRES bits), the classic "fine on an A500,
squished on an A1200" report.

The classic fix is a two-instruction copper insert described in the
demoscene AGA documentation (Randy of COMAX, "the AGA doc" V2.5, section
"To make a standard Amiga demo run on AGA chipset"; parts of that document
derive from Commodore's own C-18 AGA documentation)::

    dc.w $106,$c00   ; AGA sprites, palette and dual playfield reset
    dc.w $1FC,0      ; AGA sprites and burst reset

The prog8 library modules cover this for you, split by register ownership:

- ``FMODE`` travels with the display geometry: ``custom.compute_display()``
  stores it in ``DisplayRegs.fmode`` (on AGA ``$0001``, with the
  DDFSTRT/DDFSTOP and BPLxMOD values computed for its 32-bit fetch) and
  ``copper.base_list()`` emits the MOVE, so the copper list, not a direct
  register write, owns the fetch mode. Do not force it to 0 on AGA unless
  you also switch the DDF/modulo values to the ECS ones.
- ``BPLCON3`` and ``BPLCON4`` are attribute registers, deliberately absent
  from ``DisplayRegs`` and from ``base_list()``. The ``sprites`` module owns
  the sprite-related bits: its color routines select the bank-0/LOCT-0 write
  window themselves, and ``aga_set_resolution()``/``aga_set_bank()`` set the
  sprite resolution and palette banks (see
  ``examples/amiga/beachballs-sprites.p8`` and its 16-color and 64-pixel
  variants). A program that does not use the sprites module can add its own
  ``copper.move($0106, $0c00)`` line instead.

.. caution::
    ``BPLCON3`` and ``BPLCON4`` are write-only registers on AGA: reads
    return undefined values. Never read-modify-write them; compose the full
    register value from state you track yourself, the way
    ``custom.set_aga_color()`` and the ``sprites`` module do. Writing back a
    masked garbage read reintroduces stray bits such as SPRES, which shows
    up as horizontally squished sprites. Note that several emulators return
    0 or the last-written value for such reads, which makes the bug invisible
    there, so test on real hardware or on an emulator that models this.

Manual additions and tweaks
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The bindings are generated from the NDK, but the following manual additions and
tweaks exist on top of that.

Hand-maintained modules (not generated from the NDK)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- ``custom``, ``blitter``, ``copper``, ``playfield``, ``gfxtext``, ``sound``,
  ``ptplayer``, ``easygfx``, ``hwinfo`` - the bare-metal hardware modules.
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

Bare-metal interrupt handling
-----------------------------

Programs that take over the machine with ``custom.grab_system()`` can install
their own interrupt handlers on the 68000 autovector levels. The ``custom``
module provides a generic, VBR-aware mechanism plus per-source helpers.
Handlers must be integer-only (no FPU instructions): the trampoline saves
D0-D7/A0-A6 but no FPU state.

``custom.set_handler(level, handler)`` / ``custom.restore_handler(level)``
install and remove a handler on autovector level 1-7. The trampoline derives
the level from the status register, looks up the handler in an internal
registry, and calls it (a Prog8 ``sub`` with no parameters and no return
value). ``custom.restore_system()`` restores any installed handlers
automatically.

``custom.set_level3_handler(handler)`` installs a handler at level 3 without
enabling any source. Use the per-source helpers to enable what you need:
``custom.enable_irq(source)`` / ``custom.disable_irq(source)`` /
``custom.ack_irq(source)``, where ``source`` is an ``INTF_*`` constant
(VERTB, COPER, BLIT, PORTS, EXTER, TBE, DSKBLK, SOFTINT, AUD0-3, RBF,
DSKSYN, and others). These are the Amiga interrupt source bitmasks from
the NDK headers.

Note that the Amiga multiplexes several sources onto each autovector level:
level 3 carries VERTB, COPER and BLIT; levels 2 and 6 carry all CIA-A/CIA-B
sources (timers, TOD alarm, serial, flag pin) behind a single enable bit.
The ``set_level3_handler`` installs the trampoline only; enable the
source(s) you want via ``enable_irq()``. A level-3 handler sees just the
enabled source(s); CIA handlers must read ICR and service every pending bit,
because reading ICR acknowledges all of them at once.

For the vertical-blank interrupt, a typical setup is::

    custom.set_level3_handler(&handler)
    custom.ack_irq(custom.INTF_VERTB)
    custom.enable_irq(custom.INTF_VERTB)

and the handler must call ``custom.ack_irq(custom.INTF_VERTB)`` to
acknowledge each interrupt. See ``examples/amiga/vertb-demo.p8``.

For CIA timer interrupts, ``set_ciaa_handler`` / ``set_ciab_handler``
install at levels 2 and 6, ``enable_ciaa`` / ``disable_ciaa`` /
``enable_ciab`` / ``disable_ciab`` control INTENA, and
``read_ciaa_icr`` / ``read_ciab_icr`` read (and acknowledge) the ICR.
Reading the ICR also clears the custom chip's latched INTREQ bit
(``PORTS`` or ``EXTER``); without that write the level request stays
asserted and the CPU re-enters the handler forever.
Convenience functions ``set_ciaa_timer_handler`` /
``set_ciab_timer_handler`` / ``set_ciaa_tod_handler`` /
``set_ciab_tod_handler`` combine install, mask, clear, and enable in one
call. TOD alarm interrupts use the same mechanism: ``tod_alarm_set`` and
``tod_alarm_enable`` configure the alarm, and the handler checks the
``CIAICRF_ALRM`` bit (bit 2) of the value returned by ``read_ciaa_icr``
or ``read_ciab_icr``. See ``examples/amiga/tod-alarm-demo.p8``.

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
- ``floats`` - requires a 68020 or better CPU with an 68881/68882 FPU. The amiga500 target is
  assembled with ``-m68000`` normally, and with ``-m68020 -m68881`` when floats are used, so such
  a program will not run on a stock Amiga 500. The amiga1200 target is always assembled with
  ``-m68020 -m68881``, but that only enables assembling FPU instructions: a program that does not
  use floats still runs on a stock Amiga 1200 without an FPU. The qemu68k target always emulates
  an FPU, so floats are available there without extra hardware.
  Every float operation, including ``print()`` and ``parse()``, is a direct FPU instruction, so a
  program using floats warns at compile time and crashes with an illegal instruction when run
  without an FPU. AmigaOS ships software floating-point libraries (``mathieee*.library``) that
  need no FPU, but Prog8 does not use them at this time.
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
