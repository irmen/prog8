# Amiga Copper & Blitter Library Modules

This document describes two new library modules for the Amiga500 target that provide convenience routines for the Amiga's blitter and copper coprocessors. Both modules are designed for bare-metal operation (after `custom.grab_system()`).

## Overview

**Files to create:**
- `compiler/res/prog8lib/amiga500/blitter.p8`
- `compiler/res/prog8lib/amiga500/copper.p8`

**Design principles:**
- Mixed implementation: performance-critical routines in assembly, simpler wrappers in Prog8
- Follow patterns from `ptplayer.p8` (asmsub wrappers + embedded assembly)
- All assembly uses vasm mot syntax
- All data must be in **CHIP RAM** (documented requirement, not enforced)
- Import `custom` module for register definitions

---

## Module 1: `blitter.p8`

### Purpose

Provides high-level routines for common blitter operations: rectangle copy/fill, line drawing, masked blits, and bitplane manipulation.

### Additional Minterm Constants

The module adds named minterm constants for common operations:

```prog8
const uword MINTERM_MASKED = $CA    ; (A AND B) OR (NOT A AND C) - masked sprite blit
const uword MINTERM_A_ONLY = $F0    ; channel A only (same as COPY)
const uword MINTERM_B_ONLY = $CC    ; channel B only
const uword MINTERM_A_XOR_C = $3C   ; A XOR C (erase/restore patterns)
```

### Routines

#### `wait()`
```prog8
asmsub wait()
```
Busy-wait until blitter finishes. Polls `DMACONR` bit 14 (BLTDONE).

**Implementation:** Assembly (tight loop)
```asm
btst.b  #14-8,custom.DMACONR    ; dummy read
1$:
btst.b  #14-8,custom.DMACONR    ; check BLTDONE
bne.s   1$                       ; loop while busy
rts
```

---

#### `copy_rect()`
```prog8
asmsub copy_rect(
    pointer src @A0,
    pointer dst @A1,
    uword width_words @D0,
    uword height @D1,
    word src_mod @D2,
    word dst_mod @D3,
    ubyte minterm @D4,
    bool descending @D5
)
```
Copy rectangular region from source to destination.

**Parameters:**
- `src`, `dst`: pointers to source and destination (must be CHIP RAM)
- `width_words`: width in 16-bit words (max 64 = 1024 pixels)
- `height`: number of scanlines (max 1024)
- `src_mod`, `dst_mod`: modulo values (bytes to skip after each line)
- `minterm`: blitter logic operation (e.g., `MINTERM_COPY`)
- `descending`: if true, blit from bottom-right to top-left (for overlapping regions)

**Implementation:** Assembly
- Channels: A (source) -> D (destination)
- BLTCON0: `BC0F_SRCA | BC0F_DEST | minterm`
- BLTCON1: `BC1F_DESC` if descending, else 0
- BLTAFWM/BLTALWM: `$FFFF` (full word masks)
- BLTSIZE: `(height << 6) | width_words`

**Edge cases:**
- If `width_words == 0` or `height == 0`, return immediately
- For sizes > 64 words wide, use ECS big blit registers (BLTSIZV/BLTSIZH)

---

#### `fill_rect()`
```prog8
asmsub fill_rect(
    pointer dst @A0,
    uword width_words @D0,
    uword height @D1,
    uword pattern @D2,
    word modulo @D3
)
```
Fill rectangle with constant pattern value.

**Parameters:**
- `dst`: pointer to destination (must be CHIP RAM)
- `width_words`: width in 16-bit words
- `height`: number of scanlines
- `pattern`: 16-bit pattern to fill with
- `modulo`: bytes to skip after each line

**Implementation:** Assembly
- Channels: C (constant) -> D (destination)
- BLTCON0: `BC0F_SRCC | BC0F_DEST | $C0` (copy C to D)
- BLTCDAT: pattern value
- BLTCPT/BLTDPT: both point to dst
- BLTCMOD/BLTDMOD: modulo

---

#### `line()`
```prog8
asmsub line(
    pointer dst_ptr @A0,
    uword x_start @D0,
    uword y_start @D1,
    word dx @D2,
    word dy @D3,
    word dst_mod @D4,
    uword pattern @D5,
    ubyte minterm @D6
)
```
Draw line using blitter Bresenham line mode.

**Parameters:**
- `dst_ptr`: pointer to destination bitplane (must be CHIP RAM)
- `x_start`, `y_start`: starting pixel coordinates
- `dx`, `dy`: signed line deltas (can be negative)
- `dst_mod`: modulo for destination bitplane
- `pattern`: 16-bit line pattern (use `$FFFF` for solid line)
- `minterm`: blitter logic operation

**Implementation:** Assembly with octant computation
- **Computes octant internally** from signs of dx/dy
- Octant determines which axis is major and direction of stepping
- Channels: A (pattern) + D (destination)
- BLTCON0: `BC0F_SRCA | BC0F_DEST | minterm`
- BLTCON1: `BC1F_LINEMODE | octant_bits`

**Octant encoding (BLTCON1 bits 2-4):**
```
Octant | SUD | SUL | AUL | Condition
   0   |  0  |  0  |  0  | dx>=0, dy>=0, dx>=dy (right-down, shallow)
   1   |  1  |  0  |  0  | dx>=0, dy>=0, dx<dy  (right-down, steep)
   2   |  1  |  0  |  1  | dx<0,  dy>=0, -dx<dy (left-down, steep)
   3   |  0  |  0  |  1  | dx<0,  dy>=0, -dx>=dy (left-down, shallow)
   4   |  0  |  1  |  1  | dx<0,  dy<0,  -dx>=-dy (left-up, shallow)
   5   |  1  |  1  |  1  | dx<0,  dy<0,  -dx<-dy (left-up, steep)
   6   |  1  |  1  |  0  | dx>=0, dy<0,  dx<-dy (right-up, steep)
   7   |  0  |  1  |  0  | dx>=0, dy<0,  dx>=-dy (right-up, shallow)
```

**Algorithm:**
1. Compute absolute values of dx, dy
2. Determine octant from signs and magnitude comparison
3. Set BLTAPT = 2*|minor|, BLTBPT = 2*|major| - 2*|minor|
4. Adjust destination pointer for starting position
5. BLTSIZE = line_length (major axis length)

---

#### `masked_blit()`
```prog8
asmsub masked_blit(
    pointer src @A0,
    pointer mask @A1,
    pointer dst @A2,
    uword width_words @D0,
    uword height @D1,
    word src_mod @D2,
    word mask_mod @D3,
    word dst_mod @D4
)
```
Sprite-like masked blit: where mask is 1, copy source; where mask is 0, keep destination.

**Parameters:**
- `src`: source data pointer (must be CHIP RAM)
- `mask`: mask data pointer (must be CHIP RAM)
- `dst`: destination pointer (must be CHIP RAM)
- `width_words`, `height`: dimensions
- `src_mod`, `mask_mod`, `dst_mod`: modulo values

**Implementation:** Assembly
- Channels: A (mask), B (source), C (destination background) -> D (result)
- Minterm: `$CA` = `(A AND B) OR (NOT A AND C)`
- BLTCON0: `BC0F_SRCA | BC0F_SRCB | BC0F_SRCC | BC0F_DEST | $CA`
- BLTAFWM/BLTALWM: `$FFFF`

---

#### `clear_plane()`
```prog8
asmsub clear_plane(
    pointer dst @A0,
    uword size_words @D0
)
```
Clear (zero-fill) a bitplane.

**Parameters:**
- `dst`: pointer to bitplane (must be CHIP RAM)
- `size_words`: total size in 16-bit words

**Implementation:** Assembly
- Channel: D only
- BLTCON0: `BC0F_DEST | 0` (clear minterm)
- BLTSIZE: `(1 << 6) | size_words` (height=1, width=size)
- For sizes > 64 words, loop with chunks

---

#### `copy_plane()`
```prog8
asmsub copy_plane(
    pointer src @A0,
    pointer dst @A1,
    uword size_words @D0
)
```
Copy entire bitplane.

**Parameters:**
- `src`, `dst`: pointers (must be CHIP RAM)
- `size_words`: total size in 16-bit words

**Implementation:** Assembly
- Channels: A (source) -> D (destination)
- BLTCON0: `BC0F_SRCA | BC0F_DEST | $F0` (copy minterm)
- BLTAFWM/BLTALWM: `$FFFF`
- BLTAMOD/BLTDMOD: 0 (no gaps)
- BLTSIZE: `(1 << 6) | size_words`
- For sizes > 64 words, loop with chunks

---

#### `invert_plane()`
```prog8
asmsub invert_plane(
    pointer dst @A0,
    uword size_words @D0
)
```
Invert (XOR with $FFFF) a bitplane.

**Parameters:**
- `dst`: pointer to bitplane (must be CHIP RAM)
- `size_words`: total size in 16-bit words

**Implementation:** Assembly
- Channels: C (source) -> D (destination), both point to dst
- BLTCON0: `BC0F_SRCC | BC0F_DEST | $55` (NOT minterm)
- BLTCMOD/BLTDMOD: 0
- BLTSIZE: `(1 << 6) | size_words`
- For sizes > 64 words, loop with chunks

---

### Assembly Calling Convention

All assembly routines must:
- Preserve registers: `d2-d7`, `a2-a6`
- Use `movem.l d2-d7/a2-a6,-(sp)` at entry, restore at exit
- Return values in `d0` (primary) or `a0` (pointer)

---

## Module 2: `copper.p8`

### Purpose

Provides routines for building and managing Copper lists. The Copper is a coprocessor that executes instructions synchronized with the beam, allowing register changes at specific screen positions.

### Copper Instruction Encoding

Each instruction is 4 bytes (2 words):

| Type | Word 1 | Word 2 |
|------|--------|--------|
| **MOVE** | `reg_offset & $1FE` (register offset from $dff000, bit 0 = 0) | value |
| **WAIT** | `vpos << 8 | hpos << 1 | 1` | mask, bit 0 = 0 |
| **SKIP** | `vpos << 8 | hpos << 1 | 1` | mask, bit 0 = 1 |
| **END** | `$FFFF` | `$FFFE` |

**MOVE encoding detail:**
- Register address is the offset from $dff000 (e.g., $dff180 → offset $180)
- Since all custom registers are word-aligned, bit 0 of offset is always 0
- Word 1 = register offset with bit 0 masked clear (`and.w #$1FE`)
- Word 2: 16-bit value

**WAIT/SKIP encoding detail:**
- Word 1 bits 15-8: vertical position (0-255)
- Word 1 bits 7-1: horizontal position (0-255, must be even)
- Word 1 bit 0: 1 (WAIT/SKIP flag)
- Word 2 bits 15-8: vertical mask
- Word 2 bits 7-1: horizontal mask
- Word 2 bit 0: 0 for WAIT, 1 for SKIP

---

### Internal State

```prog8
private pointer _copper_pos  ; current write position in copper list
```

---

### List Building Routines (Assembly)

#### `init()`
```prog8
asmsub init(pointer list_ptr @A0)
```
Initialize builder state with pointer to copper list memory.

**Implementation:**
```asm
move.l  a0,_copper_pos
rts
```

---

#### `move()`
```prog8
asmsub move(uword reg_addr @D0, uword value @D1)
```
Add a MOVE instruction to write value to custom chip register.

**Implementation:**
```asm
move.l  _copper_pos,a0
move.w  d0,d2           ; d2 = reg_addr (full address like $dff180)
and.w   #$1FE,d2        ; extract offset from $dff000, bit 0 guaranteed clear
move.w  d2,(a0)+        ; write upper word (register address)
move.w  d1,(a0)+        ; write lower word (value)
move.l  a0,_copper_pos
rts
```

---

#### `wait()`
```prog8
asmsub wait(ubyte vpos @D0, ubyte hpos @D1, uword compare_mask @D2)
```
Add a WAIT instruction that pauses copper until beam reaches position.

**Parameters:**
- `vpos`: vertical position (0-255)
- `hpos`: horizontal position (0-255, must be even)
- `compare_mask`: which bits to compare (use `$FF7F` to ignore both)

**Implementation:**
```asm
move.l  _copper_pos,a0
move.w  d0,d3           ; d3 = vpos
lsl.w   #8,d3           ; vpos to bits 15-8
move.w  d1,d4           ; d4 = hpos
lsl.w   #1,d4           ; hpos to bits 7-1
or.w    d4,d3
ori.w   #1,d3           ; bit 0 = 1 for WAIT/SKIP
move.w  d3,(a0)+        ; write upper word
move.w  d2,d3           ; d3 = compare_mask
and.w   #$FFFE,d3       ; clear bit 0 (WAIT, not SKIP)
move.w  d3,(a0)+        ; write lower word
move.l  a0,_copper_pos
rts
```

---

#### `skip()`
```prog8
asmsub skip(ubyte vpos @D0, ubyte hpos @D1, uword compare_mask @D2)
```
Add a SKIP instruction (conditional jump over next instruction).

**Implementation:** Same as `wait()` but lower word bit 0 = 1.

---

#### `end()`
```prog8
asmsub end()
```
Terminate the copper list with standard end marker.

**Implementation:**
```asm
move.l  _copper_pos,a0
move.w  #$FFFF,(a0)+
move.w  #$FFFE,(a0)+
move.l  a0,_copper_pos
rts
```

---

#### `get_pos()`
```prog8
asmsub get_pos() -> pointer @A0
```
Return current write position (useful to calculate list size).

**Implementation:**
```asm
move.l  _copper_pos,a0
rts
```

---

### Activation Routines (Assembly)

#### `start()`
```prog8
asmsub start(pointer list_ptr @A0)
```
Activate a copper list by setting COP1LC and strobing COPJMP1.

**Implementation:**
```asm
move.l  a0,custom.COP1LC        ; write pointer (auto-increments: low then high)
move.w  #$8080,custom.DMACON    ; enable Copper DMA (bit 7 + SETCLR bit 15)
clr.w   custom.COPJMP1          ; strobe to start
rts
```

---

#### `double_buffer_swap()`
```prog8
asmsub double_buffer_swap(pointer list1_ptr @A0, pointer list2_ptr @A1)
```
Swap between two copper lists for double-buffering.

**Implementation:**
```asm
move.l  a0,custom.COP1LC
move.l  a1,custom.COP2LC
; Wait for vblank (line 250+ for PAL)
1$:
move.w  custom.VPOSR,d0
and.w   #$1FF,d0
cmp.w   #250,d0
blt.s   1$
clr.w   custom.COPJMP1          ; activate list 1
rts
```

---

### Effects Routines (Prog8)

#### `raster_bars()`
```prog8
sub raster_bars(pointer list_ptr, ubyte vpos_start, uword[] colors, ubyte num_lines)
```
Generate copper list for horizontal raster color bars.

**Parameters:**
- `list_ptr`: pointer to copper list memory (must be CHIP RAM)
- `vpos_start`: starting vertical position
- `colors[]`: array of 12-bit Amiga RGB colors
- `num_lines`: number of color bars (one per scanline)

**Implementation (Prog8):**
```prog8
copper.init(list_ptr)
ubyte vpos = vpos_start
for ubyte i in 0..num_lines-1 {
    copper.wait(vpos, 0, $FF7F)              ; wait for line
    copper.move(custom.COLOR0, colors[i])    ; set background color
    vpos++
}
copper.end()
```

---

## Usage Example

```prog8
%import custom
%import blitter
%import copper

%option amiga_chipram

main {
    uword[128] coplist @alignword
    uword[100] screen_plane @alignword
    uword[100] sprite_data @alignword
    uword[100] sprite_mask @alignword

    sub start() {
        custom.grab_system()

        ; Build copper list for split-screen colors
        copper.init(coplist)
        copper.wait(44, 0, $FF7F)
        copper.move(custom.COLOR0, $000)     ; black top
        copper.wait(130, 0, $FF7F)
        copper.move(custom.COLOR0, $F00)     ; red bottom
        copper.end()
        copper.start(coplist)

        ; Blit operations
        blitter.clear_plane(screen_plane, 100)
        blitter.fill_rect(screen_plane, 5, 10, $AAAA, 0)
        blitter.copy_rect(screen_plane, screen_plane+200, 5, 10, 0, 0,
                          custom.MINTERM_COPY, false)
        blitter.masked_blit(sprite_data, sprite_mask, screen_plane,
                            2, 16, 0, 0, 0)
        blitter.line(screen_plane, 10, 10, 50, 30, 0, $FFFF,
                     custom.MINTERM_OR)

        custom.return_system()
    }
}
```

---

## Implementation Checklist

- [ ] Create `compiler/res/prog8lib/amiga500/blitter.p8`
  - [ ] Add minterm constants
  - [ ] Implement `wait()`
  - [ ] Implement `copy_rect()` with descending parameter
  - [ ] Implement `fill_rect()`
  - [ ] Implement `line()` with internal octant computation
  - [ ] Implement `masked_blit()`
  - [ ] Implement `clear_plane()`
  - [ ] Implement `copy_plane()`
  - [ ] Implement `invert_plane()`
  - [ ] Handle sizes > 64 words (loop or ECS big blit)

- [ ] Create `compiler/res/prog8lib/amiga500/copper.p8`
  - [ ] Add `_copper_pos` variable
  - [ ] Implement `init()`
  - [ ] Implement `move()`
  - [ ] Implement `wait()`
  - [ ] Implement `skip()`
  - [ ] Implement `end()`
  - [ ] Implement `get_pos()`
  - [ ] Implement `start()`
  - [ ] Implement `double_buffer_swap()`
  - [ ] Implement `raster_bars()`

- [ ] Test with example program
- [ ] Build and verify: `gradle installdist installshadowdist`
- [ ] Update documentation if needed

---

## Notes

- All pointers passed to these routines must point to **CHIP RAM**
- The blitter can only access CHIP RAM, not FAST RAM
- Copper lists must also be in CHIP RAM
- Use `%option amiga_chipram` on blocks containing copper/blitter data
- Register preservation: all assembly routines preserve d2-d7/a2-a6
- For line drawing, octant is computed internally from signed dx/dy
- For overlapping rectangle copies, use `descending=true` parameter
