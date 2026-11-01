%import exec
%import syslib

custom {
    %option merge, no_symbol_prefixing, ignore_unused

    ; OS-interaction note:
    ;   The read-only helpers below (joystick_*, mouse_*, key_pressed,
    ;   raw_keyboard_poll reads) only read hardware registers and are safe
    ;   to use while the OS is running. However:
    ;     - For keyboard, do NOT run raw_keyboard_poll() at the same time as
    ;       OS keyboard input (IDCMP/input.device): both acknowledge the same
    ;       CIAA_SDR event and will race, causing missed/duplicated keys.
    ;       Use either OS input or custom hardware polling, not both.
    ;     - raw_keyboard_poll() uses CIA-A Timer A (one-shot) for the key
    ;       acknowledge; do not use that timer from elsewhere at the same time.
    ;     - grab_system()/restore_system() and direct writes to DMACON,
    ;       INTENA, BLTCON0, AUDxPER, COP1LC, etc. must NOT be used while the
    ;       OS is running - they take over or conflict with OS drivers.
    ;   Rule of thumb: use custom for input reading alongside the OS for
    ;   joystick/mouse, but for keyboard and timers pick one owner (OS input
    ;   or custom hardware polling), never both. Games that need raw keyboard
    ;   input usually grab_system() and use custom exclusively; windowed apps
    ;   should use IDCMP and leave raw_keyboard_poll() alone.

    enum CHIPSET {
        OCS,
        ECS,
        AGA
    }

    sub detect_chipset() -> ubyte {
        ; returns the custom chipset in this Amiga (OCS, ECS or AGA)
        ubyte denise_id = lsb(DENISEID)
        bool stable = true
        repeat 31 {
            if lsb(DENISEID)!=denise_id {
                stable = false
                break
            }
        }
        if stable and denise_id==$f8
            return CHIPSET::AGA
        if stable and denise_id==$fc
            return CHIPSET::ECS

        ubyte agnus_id = ((VPOSR >> 8) as ubyte) & $7f
        if (agnus_id & $70)>=$20
            return CHIPSET::ECS
        return CHIPSET::OCS
    }

    asmsub grab_system(bool replace_view @D0) {
        ; take over the whole OS and system, to run hardware banging programs (games, demos, etc)
        ; if replace_view is true, a blank NULL view is loaded first and all DMA is
        ; disabled; otherwise the currently active View (and its copper list) is left
        ; untouched and display DMA (copper, raster) is kept running so it stays visible.
        %asm {{

exec_AttnFlags = 296
gfx_ActiView = $22
gfx_copinit = $26
gfx_LOFlist = $32
VBlankFrequency = $212
GfxLoadView    = -222
GfxWaitTOF = -270
IRQ1 = $64
IRQ2 = $68
IRQ3 = $6C
IRQ4 = $70
IRQ5 = $74
IRQ6 = $78
IRQ7 = $7C

            move.l  d6,-(sp)        ; OS calls do not preserve d6
            move.l  a5,-(sp)        ; a5 is the frame pointer register, but it is needed below for Supervisor()
            move.l  d0,-(sp)        ; stash replace_view (d0 is clobbered by the Supervisor/OS calls below)
            move.l  4.w,a6
            jsr     exec.Forbid(a6)        ; Do not run other tasks
            btst.b  #0,exec_AttnFlags+1(a6)    ; Check if > 68000 processor
            beq.s   1$            ; On 68000 no VBR (always zero)
            lea.l   .getvbr(PC),a5    ; Function to call as supervisor
            jsr     exec.Supervisor(a6)    ; Call supervisor function in A5
            move.l  d0,custom.cpuVBR        ; Store the returned VBR contents
1$
            cmp.b   #50,VBlankFrequency(a6)
            seq.b   custom.isPAL

            move.l  sys.GfxBase,a6     ; A6 = Graphics base
            move.l  gfx_ActiView(a6),custom.savedView    ; Store current View pointer
            tst.b   3(sp)              ; replace the current View?
            bne.s   .replaceview
            move.w  #$007F,d1        ; DMA mask: audio, disk, blitter and sprites only,
                                     ; keep copper/raster so the View stays visible
            bra.s   .havemask
.replaceview:
            sub.l   a1,a1            ; NULL view = default settings
            jsr    GfxLoadView(a6)        ; Load the view
            jsr    GfxWaitTOF(a6)        ; Wait one screen refresh
            jsr    GfxWaitTOF(a6)        ; Wait a 2nd (in case of interlace)
            move.w  #$01FF,d1        ; DMA mask: disable everything
.havemask:

            move.w  #$8000,d0        ; Value
            move.w  custom.DMACONR,custom.savedDMACON    ; Store system DMA channels
            or.w    d0,custom.savedDMACON            ; SET/CLR set to SET
            move.w  custom.INTENAR,custom.savedINTENA    ; Store system enabled interrupts
            or.w    d0,custom.savedINTENA            ; SET/CLR set to SET
            move.w  custom.ADKCONR,custom.savedADKCON    ; Audio, disk and UART
            or.w    d0,custom.savedADKCON            ; SET/CLR set to SET

            btst.b  #14-8,custom.DMACONR    ; Dummy read
3$          btst.b  #14-8,custom.DMACONR    ; Blitter still busy?
            bne.s   3$                      ; If yes, wait a bit
            move.w  d1,custom.DMACON     ; Disable selected DMA channels
            move.w  #$3FFF,custom.INTENA    ; Disable all interrupts

            move.l  custom.cpuVBR,a0         ; A0 = Pointer to vector base
            move.l  IRQ1(a0),custom.savedIRQ1        ; Store IRQ1 vector
            move.l  IRQ3(a0),custom.savedIRQ3        ; Store IRQ3 vector
            move.l  IRQ4(a0),custom.savedIRQ4        ; Store IRQ4 vector
            bra.s   .skip

.getvbr:    dc.l    $4E7A0801            ; MOVEC VBR,d0  - privileged instruction
            rte

.skip:      addq.w  #4,sp         ; drop stashed replace_view
            move.l  (sp)+,a5        ; restore the caller's frame pointer
            move.l  (sp)+,d6
            rts

        }}
    }

    asmsub restore_system() {
        ; restore the original OS and multitasking operation when the game/demo exits.
        %asm {{
            move.l  d6,-(sp)        ; OS calls do not preserve d6
            btst.b  #14-8,custom.DMACONR    ; Dummy read
1$          btst.b  #14-8,custom.DMACONR   ; Blitter still busy?
            bne.s   1$                      ; If yes, wait a bit
            move.w  #$01FF,custom.DMACON    ; Disable all DMA
            move.w  #$3FFF,custom.INTENA   ; Disable all interrupts

            move.l  custom.cpuVBR,a0            ; A0 = Pointer to vector base
            move.l  custom.savedIRQ4,d0      ; Restore IRQ4 vector
            move.l  d0,IRQ4(a0)
            move.l  custom.savedIRQ3,d0      ; Restore IRQ3 vector
            move.l  d0,IRQ3(a0)
            move.l  custom.savedIRQ1,d0      ; Restore IRQ1 vector
            move.l  d0,IRQ1(a0)

            move.l  sys.GfxBase,a6      ; A6 = Graphics base
            move.l  gfx_copinit(a6),custom.COP1LC    ; Restore coplist pointer 1
            move.l  gfx_LOFlist(a6),custom.COP2LC    ; Restore coplist pointer 2
            clr.w   custom.COPJMP1       ; Make Copper use restored pointer

            move.w  custom.savedADKCON,custom.ADKCON    ; Restore audio, disk and UART
            move.w  custom.savedINTENA,custom.INTENA    ; Restore original interrupts
            move.w  custom.savedDMACON,custom.DMACON    ; Restore original DMA

            move.l  custom.savedView,a1        ; Get original view pointer
            jsr     GfxLoadView(a6)        ; Restore the original view
            jsr     GfxWaitTOF(a6)        ; Wait one screen refresh
            jsr     GfxWaitTOF(a6)        ; Wait a 2nd (in case of interlace)

            move.l  4.w,a6
            jsr     exec.Permit(a6)        ; Re-enable multitasking
            move.l  (sp)+,d6
            rts
        }}
    }

    asmsub waitvsync() {
        %asm {{
; synchronize to the screen's vsync, this routine doesn't require the OS to be active (sys.waitvsync is the OS-compatible version of this routine)
; Wait until the raster beam reaches line 300
.WaitLine300:
    MOVE.L  $DFF004, D0     ; Read VPOSR + VHPOSR simultaneously
    LSR.L   #8, D0          ; Shift vertical position bits into low word
    AND.W   #$01FF, D0      ; Mask out horizontal beam bits (get 9-bit line #)
    CMP.W   #300, D0        ; Are we at raster line 300?
    BNE.S   .WaitLine300

    ; Wait until the beam MOVES PAST line 300
    ; (Prevents fast CPUs like 030/040/060 from executing twice on the same line)
.WaitLineNext:
    MOVE.L  $DFF004, D0
    LSR.L   #8, D0
    AND.W   #$01FF, D0
    CMP.W   #300, D0
    BEQ.S   .WaitLineNext
    rts
        }}
    }

    struct DisplayRegs {  ; total size: 18
        uword diwstrt     ; 0
        uword diwstop     ; 2
        uword ddfstrt     ; 4
        uword ddfstop     ; 6
        uword bplcon0     ; 8
        uword bpl1mod     ; 12
        uword bpl2mod     ; 14
        uword fmode       ; 16
    }

    private ^^DisplayRegs regs = []

    const uword DISPLAY_DEFAULT = $0000  ; lores, machine's own TV standard
    const uword DISPLAY_HIRES   = $0001  ; 640-pixel mode (else 320-pixel)
    const uword DISPLAY_EHB     = $0002  ; extra half-brite (needs depth 6)
    const uword DISPLAY_HAM     = $0004  ; hold-and-modify (needs depth 6, or 8 on AGA)

    sub compute_display(word width, word height, ubyte depth, uword mode, word stride) -> ^^DisplayRegs {
        ; Compute the shadow display registers for a bare-metal playfield.
        ; No hardware is touched; the caller writes the struct fields to the
        ; custom registers itself (install BPLxPT every frame, the rest once).
        ;
        ; width/height describe the visible area in pixels. width must be a
        ; multiple of 16 (min 32). mode is a combination of the DISPLAY_ flags.
        ; stride is the bitplane row pitch in bytes (0 uses width / 8).
        ; Height is just the visible line count and must fit that
        ; standard's frame. depth is 1-8 bitplanes (7-8 need AGA). Only
        ; plain lores/hires (+EHB/HAM) are supported: no interlace, no
        ; DBLPAL/DBLNTSC, no superhires, no dual playfield.
        ;
        ; Plane pointers are not part of the struct; the caller manages the
        ; bitplanes and BPLxPT itself (usually an InitBitMap + AllocRaster
        ; bitmap in CHIP RAM).
        ;
        ; Returns a pointer to the single statically allocated DisplayRegs
        ; instance, or 0 if the mode is not supported (bad geometry, or more
        ; than 6 planes on OCS/ECS). On AGA, 32-bit bitplane fetch is
        ; selected with compensated DDF values; reset FMODE to $0000 before
        ; handing the display back to the OS.
        bool hires = (mode & DISPLAY_HIRES) != 0
        bool ehb = (mode & DISPLAY_EHB) != 0
        bool ham = (mode & DISPLAY_HAM) != 0
        if depth == 0 or depth > 8
            return 0
        if width < 32 or (width & 15) != 0
            return 0
        if height > 267
            return 0
        if ehb and ham
            return 0
        if ehb and depth != 6
            return 0
        if ham and depth != 6 and depth != 8
            return 0
        bool is_aga = detect_chipset() == CHIPSET::AGA
        if depth > 6 and not is_aga
            return 0

        uword wpl = ((width as uword) + 15) >> 4
        uword rowBytes = (width as uword) >> 3
        if stride != 0
            rowBytes = stride as uword
        if is_aga {
            ; AGA longword fetches cover 32 pixels (16 DDF units lores, 8 hires).
            uword fetchLongwords = ((width as uword) + 31) >> 5
            uword fetchedBytes = fetchLongwords * 4
            regs.fmode = $0001
            if hires {
                regs.ddfstrt = $003c
                regs.ddfstop = $003c + (fetchLongwords - 1) * 8
            } else {
                regs.ddfstrt = $0038
                regs.ddfstop = $0038 + (fetchLongwords - 1) * 16
            }
            regs.bpl1mod = ((rowBytes as word) - (fetchedBytes as word)) as uword
        } else {
            regs.fmode = $0000
            if hires {
                regs.ddfstrt = $0038
                regs.ddfstop = $00d8
                regs.bpl1mod = ((rowBytes as word) - (((wpl + 2) as word) * 2)) as uword
            } else {
                regs.ddfstrt = $0038
                regs.ddfstop = $0038 + wpl * 8
                regs.bpl1mod = ((rowBytes as word) - (((wpl + 1) as word) * 2)) as uword
            }
        }
        regs.bpl2mod = regs.bpl1mod

        ; Display window. Units are lores pixels, so hires widths are halved.
        ; VSTART is always $2c; VSTOP wraps at 256 which yields $f4 for a
        ; 200-line (NTSC) field and $2c for a 256-line (PAL) field.
        regs.diwstrt = $2c81
        uword lores_width = width as uword
        if hires
            lores_width = lores_width >> 1
        uword hstop = $81 + lores_width - $0100
        uword vstop = ($2c + (height as uword)) & $ff
        regs.diwstop = (vstop << 8) | hstop

        ; Bitplane control. BPU holds the depth; BPU3 (bit 4, AGA) extends
        ; it past 6 planes. HIRES, EHB and HAM are bits 15, 7 and 11.
        uword con0 = ((depth & 7) as uword) << 12
        if hires
            con0 |= $8000
        if ehb
            con0 |= $0080
        if ham
            con0 |= $0800
        if depth == 8
            con0 |= $0010
        con0 |= $0200    ; color burst enable
        regs.bplcon0 = con0
        return regs
    }


    pointer @shared cpuVBR     ; after calling grab_system: contains CPU VBR register
    bool @shared isPAL         ; after calling grab_system: is true when system is PAL, false if NTSC

    private pointer @shared savedView       ; original graphics view pointer
    private uword @shared savedDMACON       ; original DMA control with SET/CLR bit
    private uword @shared savedINTENA       ; original interrupt enable with SET/CLR bit
    private uword @shared savedADKCON       ; original ADK control with SET/CLR bit
    private pointer @shared savedIRQ1          ; original IRQ1 vector
    private pointer @shared savedIRQ3          ; original IRQ3 vector
    private pointer @shared savedIRQ4          ; original IRQ4 vector


    ; ========== AGA palette utilities ==========

    asmsub set_aga_color(ubyte color_index @D0, long rgb24 @D1) {
        ; Write a 24-bit RGB color ($00RRGGBB) to an AGA palette entry.
        ; AGA uses eight banks of 32 COLOR registers and two writes per color:
        ; first the upper nibble of each channel, then the lower nibble.
        ; Keep this routine 68000-compatible: the amiga500 target is assembled as
        ; 68000 by default, even though AGA hardware is normally paired with a 68020.
        %asm {{
            movem.l d2-d4,-(sp)
            and.l   #$ff,d0
            move.l  d0,d4
            lsr.w   #5,d4
            lsl.w   #8,d4
            lsl.w   #5,d4

            and.w   #$1f,d0
            lsl.w   #1,d0
            move.l  #$dff180,a0
            adda.w   d0,a0

            move.l  d1,d2
            and.l   #$f00000,d2
            lsr.l   #8,d2
            lsr.l   #4,d2
            move.l  d1,d3
            and.l   #$00f000,d3
            lsr.l   #8,d3
            or.w    d3,d2
            move.l  d1,d3
            and.l   #$0000f0,d3
            lsr.l   #4,d3
            or.w    d3,d2

            move.l  d1,d3
            and.l   #$0f0000,d3
            lsr.l   #8,d3
            move.l  d1,d0
            and.l   #$000f00,d0
            lsr.l   #4,d0
            or.w    d0,d3
            move.l  d1,d0
            and.l   #$00000f,d0
            or.w    d0,d3

            move.w  d4,custom.BPLCON3
            move.w  d2,(a0)
            ori.w   #$0200,d4
            move.w  d4,custom.BPLCON3
            move.w  d3,(a0)
            andi.w  #$fdff,d4
            move.w  d4,custom.BPLCON3
            movem.l (sp)+,d2-d4
            rts
        }}
    }

    asmsub set_aga_color_nibbles(ubyte color_index @D0, uword rgb_high_nibbles @D1, uword rgb_low_nibbles @D2) {
        ; Write preformatted AGA palette nibbles. Each value is $0RGB. Result will still be 24 bit RRGGBB AGA color.
        ; Keep this routine 68000-compatible: the amiga500 target is assembled as
        ; 68000 by default, even though AGA hardware is normally paired with a 68020.
        %asm {{
            move.l  d3,-(sp)
            and.l   #$ff,d0
            move.l  d0,d3
            lsr.w   #5,d3
            lsl.w   #8,d3
            lsl.w   #5,d3

            and.w   #$1f,d0
            lsl.w   #1,d0
            move.l  #$dff180,a0
            adda.w   d0,a0

            move.w  d3,custom.BPLCON3
            move.w  d1,(a0)
            ori.w   #$0200,d3
            move.w  d3,custom.BPLCON3
            move.w  d2,(a0)
            andi.w  #$fdff,d3
            move.w  d3,custom.BPLCON3
            move.l  (sp)+,d3
            rts
        }}
    }

    ; ========== audio filter =======

    asmsub filter_on() {
        ; ENABLE Audio Filter (Muffled audio / Dimmed Power LED)
        %asm {{
            bclr    #1,custom.CIAA_PRA       ; Set Bit 1 to LOW -> Filter ON
            rts
        }}
    }

    asmsub filter_off() {
        ; DISABLE Audio Filter (Clear, crisp audio / Bright Power LED)
        %asm {{
            bset    #1,custom.CIAA_PRA       ; Set Bit 1 to HIGH -> Filter OFF
            rts
        }}
    }

    ; Bit masks; combine these flags with |.
    const ubyte JOY_UP = %00000001
    const ubyte JOY_DOWN = %00000010
    const ubyte JOY_LEFT = %00000100
    const ubyte JOY_RIGHT = %00001000
    const ubyte JOY_FIRE = %00010000
    const ubyte JOY_FIRE2 = %00100000

    private uword[2] mouse_previous
    private bool[2] mouse_initialized
    private word[2] mouse_x_remainder
    private word[2] mouse_y_remainder
    private ubyte[2] mouse_delta_divisor

    sub joystick_axes(ubyte port) -> byte, byte {
        ; Return screen axes in -1..1: x right, y down.
        if port>1
            return 0, 0
        uword state = ~read_joydat(port) ; JOYxDAT direction signals are active low.
        byte x = 0
        byte y = 0
        if (state & $0002)!=0
            x = -1
        if (state & $0200)!=0
            x += 1
        state = ~(state ^ (state << 1)) ; Decode vertical direction from its quadrature pair.
        if (state & $0002)!=0
            y = -1
        if (state & $0200)!=0
            y += 1
        return x, y
    }

    sub joystick_fire(ubyte port) -> bool {
        ; Fire switches are active low on CIA-A PRA bits 6 and 7.
        when port {
            0 -> return (CIAA_PRA & $40)==0
            1 -> return (CIAA_PRA & $80)==0
            else -> return false
        }
    }

    sub joystick_fire2(ubyte port) -> bool {
        ; Second fire is active low on pin 9: POTGOR bit 10 or 14.
        when port {
            0 -> return (POTGOR & $0400)==0
            1 -> return (POTGOR & $4000)==0
            else -> return false
        }
    }

    sub joystick_state(ubyte port) -> ubyte {
        ; Read directions directly; opposite switches cancel like joystick_axes().
        if port>1
            return 0
        uword directions = ~read_joydat(port)
        uword vertical = ~(directions ^ (directions << 1))
        ubyte state = 0
        if (vertical & $0002)!=0 and (vertical & $0200)==0
            state |= JOY_UP
        if (vertical & $0200)!=0 and (vertical & $0002)==0
            state |= JOY_DOWN
        if (directions & $0002)!=0 and (directions & $0200)==0
            state |= JOY_LEFT
        if (directions & $0200)!=0 and (directions & $0002)==0
            state |= JOY_RIGHT
        if joystick_fire(port)
            state |= JOY_FIRE
        if joystick_fire2(port)
            state |= JOY_FIRE2
        return state
    }

    sub mouse_init(ubyte port, ubyte scale_divisor) {
        ; Set the starting counter values and sensitivity used by mouse_delta().
        if port>1
            return
        ; Only power-of-two divisors are supported; invalid values mean raw counts.
        if scale_divisor!=1 and scale_divisor!=2 and scale_divisor!=4 and scale_divisor!=8
            scale_divisor = 1
        mouse_previous[port] = read_joydat(port)
        mouse_initialized[port] = true
        mouse_delta_divisor[port] = scale_divisor
        mouse_x_remainder[port] = 0
        mouse_y_remainder[port] = 0
    }

    private inline sub scale_mouse_delta(word total, ubyte divisor) -> byte, word {
        ; The supported divisors are powers of two, so scale with constant shifts.
        ; Divide the magnitude first to make negative results truncate toward zero.
        bool negative = total<0
        word magnitude = total
        if negative
            magnitude = -total
        word pixels = 0
        word remainder = 0
        when divisor {
            1 -> pixels = magnitude
            2 -> {
                pixels = magnitude >> 1
                remainder = magnitude & 1
            }
            4 -> {
                pixels = magnitude >> 2
                remainder = magnitude & 3
            }
            8 -> {
                pixels = magnitude >> 3
                remainder = magnitude & 7
            }
            else -> pixels = magnitude
        }
        if negative {
            pixels = -pixels
            remainder = -remainder
        }
        return pixels as byte, remainder
    }

    sub mouse_delta(ubyte port) -> byte, byte {
        ; Poll once per frame; each 8-bit counter wraps independently.
        ; Scale by the divisor selected by mouse_init(), retaining fractions.
        if port>1
            return 0, 0
        uword current = read_joydat(port)
        if not mouse_initialized[port] {
            mouse_previous[port] = current
            mouse_initialized[port] = true
            mouse_delta_divisor[port] = 1
            mouse_x_remainder[port] = 0
            mouse_y_remainder[port] = 0
            return 0, 0
        }
        uword previous = mouse_previous[port]
        mouse_previous[port] = current
        byte raw_dx = (lsb(current)-lsb(previous)) as byte
        byte raw_dy = (msb(current)-msb(previous)) as byte
        word total_x = mouse_x_remainder[port] + (raw_dx as word)
        word total_y = mouse_y_remainder[port] + (raw_dy as word)
        byte dx
        byte dy
        word remainder_x
        word remainder_y
        dx, remainder_x = scale_mouse_delta(total_x, mouse_delta_divisor[port])
        dy, remainder_y = scale_mouse_delta(total_y, mouse_delta_divisor[port])
        mouse_x_remainder[port] = remainder_x
        mouse_y_remainder[port] = remainder_y
        return dx, dy
    }

    private inline sub read_joydat(ubyte port) -> uword {
        ; Read the custom-chip data register for the selected port.
        return if port==0 then JOY0DAT else JOY1DAT
    }

    ; ========== mouse button status ==========

    sub left_button() -> bool {
        ; Returns true if the left mouse button (port 1) is pressed.
        ; Left button is CIA-A PRA bit 6, active low.
        return (CIAA_PRA & %01000000) == 0
    }

    sub right_button() -> bool {
        ; Returns true if the right mouse button (port 1) is pressed.
        ; Right button is POTGOR ($dff016) bit 10, active low.
        return (POTGOR & %0000010000000000) == 0
    }

    sub middle_button() -> bool {
        ; Returns true if the middle mouse button (port 1) is pressed.
        ; Middle button is POTGOR ($dff016) bit 8, active low.
        return (POTGOR & %0000000100000000) == 0
    }


    ; Amiga custom chip registers (full addresses, base $dff000)
    ; Regenerated from https://github.com/alfishe/amiga-bootcamp/blob/main/14_references/custom_chip_registers.md
    ; All registers are 16-bit words accessed on even byte boundaries.

    ; ========== Read-only registers ==========

    &uword  BLTDDAT     = $dff000   ; blitter destination early read
    &uword  DMACONR     = $dff002   ; DMA control read
    &uword  VPOSR       = $dff004   ; beam position (V high bits + LOF)
    &uword  VHPOSR      = $dff006   ; beam position (V low + H)
    &uword  DSKDATR     = $dff008   ; disk data early read
    &uword  JOY0DAT     = $dff00a   ; joystick/mouse port 0
    &uword  JOY1DAT     = $dff00c   ; joystick/mouse port 1
    &uword  CLXDAT      = $dff00e   ; collision detection
    &uword  ADKCONR     = $dff010   ; audio/disk control read
    &uword  POT0DAT     = $dff012   ; pot port 0 data
    &uword  POT1DAT     = $dff014   ; pot port 1 data
    &uword  POTGOR      = $dff016   ; pot port data read
    &uword  SERDATR     = $dff018   ; serial port data + status
    &uword  DSKBYTR     = $dff01a   ; disk data byte + status
    &uword  INTENAR     = $dff01c   ; interrupt enable read
    &uword  INTREQR     = $dff01e   ; interrupt request read

    ; ========== Write / Read-Write registers ==========

    ; Disk
    &uword  DSKPTH      = $dff020   ; disk DMA pointer (high)
    &uword  DSKPTL      = $dff022   ; disk DMA pointer (low)
    &pointer DSKPT       = $dff020   ; disk DMA pointer (full 32-bit, hi/lo auto-increment)
    &uword  DSKLEN      = $dff024   ; disk DMA length
    &uword  DSKDAT      = $dff026   ; disk DMA data write
    &uword  REFPTR      = $dff028   ; refresh pointer

    ; Beam position (write)
    &uword  VPOSW       = $dff02a   ; beam position write (V)
    &uword  VHPOSW      = $dff02c   ; beam position write (H)
    &uword  COPCON      = $dff02e   ; copper control

    ; Serial
    &uword  SERDAT      = $dff030   ; serial port data write
    &uword  SERPER      = $dff032   ; serial port period/control

    ; Pot/Game
    &uword  POTGO       = $dff034   ; pot port control
    &uword  JOYTEST     = $dff036   ; joystick counter test
    &uword  STREQU      = $dff038   ; short frame strobe (ECS)
    &uword  STRVBL      = $dff03a   ; vertical blank strobe (ECS)
    &uword  STRHOR      = $dff03c   ; horizontal sync strobe (ECS)
    &uword  STRLONG     = $dff03e   ; long frame strobe (ECS)

    ; Blitter
    &uword  BLTCON0     = $dff040   ; blitter control 0
    &uword  BLTCON1     = $dff042   ; blitter control 1
    &uword  BLTAFWM     = $dff044   ; blitter A first word mask
    &uword  BLTALWM     = $dff046   ; blitter A last word mask
    &uword  BLTCPTH     = $dff048   ; blitter C pointer (high)
    &uword  BLTCPTL     = $dff04a   ; blitter C pointer (low)
    &pointer BLTCPT      = $dff048   ; blitter C pointer (full 32-bit, hi/lo auto-increment)
    &uword  BLTBPTH     = $dff04c   ; blitter B pointer (high)
    &uword  BLTBPTL     = $dff04e   ; blitter B pointer (low)
    &pointer BLTBPT      = $dff04c   ; blitter B pointer (full 32-bit, hi/lo auto-increment)
    &uword  BLTAPTH     = $dff050   ; blitter A pointer (high)
    &uword  BLTAPTL     = $dff052   ; blitter A pointer (low)
    &pointer BLTAPT      = $dff050   ; blitter A pointer (full 32-bit, hi/lo auto-increment)
    &uword  BLTDPTH     = $dff054   ; blitter D pointer (high)
    &uword  BLTDPTL     = $dff056   ; blitter D pointer (low)
    &pointer BLTDPT      = $dff054   ; blitter D pointer (full 32-bit, hi/lo auto-increment)
    &uword  BLTSIZE     = $dff058   ; blitter size (starts blit)
    &uword  BLTCON0L    = $dff05a   ; blitter control 0 (lower bits, ECS)
    &uword  BLTSIZV     = $dff05c   ; blitter V size (ECS)
    &uword  BLTSIZH     = $dff05e   ; blitter H size (ECS, starts blit)
    &uword  BLTCMOD     = $dff060   ; blitter C modulo
    &uword  BLTBMOD     = $dff062   ; blitter B modulo
    &uword  BLTAMOD     = $dff064   ; blitter A modulo
    &uword  BLTDMOD     = $dff066   ; blitter D modulo
    &uword  BLTCDAT     = $dff070   ; blitter C data
    &uword  BLTBDAT     = $dff072   ; blitter B data
    &uword  BLTADAT     = $dff074   ; blitter A data
    &uword  SPRHDAT     = $dff078   ; ext sprite data (ECS)
    &uword  DENISEID    = $dff07c   ; Denise/Lisa chip ID (ECS/AGA)
    &uword  DSKSYNC     = $dff07e   ; disk sync pattern

    ; Copper
    &uword  COP1LCH     = $dff080   ; copper 1 list pointer (high)
    &uword  COP1LCL     = $dff082   ; copper 1 list pointer (low)
    &pointer COP1LC      = $dff080   ; copper 1 list pointer (full 32-bit, hi/lo auto-increment)
    &uword  COP2LCH     = $dff084   ; copper 2 list pointer (high)
    &uword  COP2LCL     = $dff086   ; copper 2 list pointer (low)
    &pointer COP2LC      = $dff084   ; copper 2 list pointer (full 32-bit, hi/lo auto-increment)
    &uword  COPJMP1     = $dff088   ; copper jump strobe 1
    &uword  COPJMP2     = $dff08a   ; copper jump strobe 2
    &uword  COPINS      = $dff08c   ; copper instruction fetch

    ; Display window / data fetch
    &uword  DIWSTRT     = $dff08e   ; display window start
    &uword  DIWSTOP     = $dff090   ; display window stop
    &uword  DDFSTRT     = $dff092   ; data fetch start
    &uword  DDFSTOP     = $dff094   ; data fetch stop

    ; Control registers
    &uword  DMACON      = $dff096   ; DMA control write
    &uword  CLXCON      = $dff098   ; collision control
    &uword  INTENA      = $dff09a   ; interrupt enable
    &uword  INTREQ      = $dff09c   ; interrupt request
    &uword  ADKCON      = $dff09e   ; audio/disk control

    ; ========== Audio channels ==========

    &uword  AUD0PTH     = $dff0a0   ; audio 0 pointer (high)
    &uword  AUD0PTL     = $dff0a2   ; audio 0 pointer (low)
    &pointer AUD0PT      = $dff0a0   ; audio 0 pointer (full 32-bit, hi/lo auto-increment)
    &uword  AUD0LEN     = $dff0a4   ; audio 0 length
    &uword  AUD0PER     = $dff0a6   ; audio 0 period
    &uword  AUD0VOL     = $dff0a8   ; audio 0 volume
    &uword  AUD0DAT     = $dff0aa   ; audio 0 sample data

    &uword  AUD1PTH     = $dff0b0   ; audio 1 pointer (high)
    &uword  AUD1PTL     = $dff0b2   ; audio 1 pointer (low)
    &pointer AUD1PT      = $dff0b0   ; audio 1 pointer (full 32-bit, hi/lo auto-increment)
    &uword  AUD1LEN     = $dff0b4   ; audio 1 length
    &uword  AUD1PER     = $dff0b6   ; audio 1 period
    &uword  AUD1VOL     = $dff0b8   ; audio 1 volume
    &uword  AUD1DAT     = $dff0ba   ; audio 1 sample data

    &uword  AUD2PTH     = $dff0c0   ; audio 2 pointer (high)
    &uword  AUD2PTL     = $dff0c2   ; audio 2 pointer (low)
    &pointer AUD2PT      = $dff0c0   ; audio 2 pointer (full 32-bit, hi/lo auto-increment)
    &uword  AUD2LEN     = $dff0c4   ; audio 2 length
    &uword  AUD2PER     = $dff0c6   ; audio 2 period
    &uword  AUD2VOL     = $dff0c8   ; audio 2 volume
    &uword  AUD2DAT     = $dff0ca   ; audio 2 sample data

    &uword  AUD3PTH     = $dff0d0   ; audio 3 pointer (high)
    &uword  AUD3PTL     = $dff0d2   ; audio 3 pointer (low)
    &pointer AUD3PT      = $dff0d0   ; audio 3 pointer (full 32-bit, hi/lo auto-increment)
    &uword  AUD3LEN     = $dff0d4   ; audio 3 length
    &uword  AUD3PER     = $dff0d6   ; audio 3 period
    &uword  AUD3VOL     = $dff0d8   ; audio 3 volume
    &uword  AUD3DAT     = $dff0da   ; audio 3 sample data

    ; ========== Bitplane pointers ==========

    &uword  BPL1PTH     = $dff0e0   ; bitplane 1 pointer (high)
    &uword  BPL1PTL     = $dff0e2   ; bitplane 1 pointer (low)
    &pointer BPL1PT      = $dff0e0   ; bitplane 1 pointer (full 32-bit, hi/lo auto-increment)
    &uword  BPL2PTH     = $dff0e4   ; bitplane 2 pointer (high)
    &uword  BPL2PTL     = $dff0e6   ; bitplane 2 pointer (low)
    &pointer BPL2PT      = $dff0e4   ; bitplane 2 pointer (full 32-bit, hi/lo auto-increment)
    &uword  BPL3PTH     = $dff0e8   ; bitplane 3 pointer (high)
    &uword  BPL3PTL     = $dff0ea   ; bitplane 3 pointer (low)
    &pointer BPL3PT      = $dff0e8   ; bitplane 3 pointer (full 32-bit, hi/lo auto-increment)
    &uword  BPL4PTH     = $dff0ec   ; bitplane 4 pointer (high)
    &uword  BPL4PTL     = $dff0ee   ; bitplane 4 pointer (low)
    &pointer BPL4PT      = $dff0ec   ; bitplane 4 pointer (full 32-bit, hi/lo auto-increment)
    &uword  BPL5PTH     = $dff0f0   ; bitplane 5 pointer (high)
    &uword  BPL5PTL     = $dff0f2   ; bitplane 5 pointer (low)
    &pointer BPL5PT      = $dff0f0   ; bitplane 5 pointer (full 32-bit, hi/lo auto-increment)
    &uword  BPL6PTH     = $dff0f4   ; bitplane 6 pointer (high)
    &uword  BPL6PTL     = $dff0f6   ; bitplane 6 pointer (low)
    &pointer BPL6PT      = $dff0f4   ; bitplane 6 pointer (full 32-bit, hi/lo auto-increment)
    &uword  BPL7PTH     = $dff0f8   ; bitplane 7 pointer (high)
    &uword  BPL7PTL     = $dff0fa   ; bitplane 7 pointer (low)
    &pointer BPL7PT      = $dff0f8   ; bitplane 7 pointer (full 32-bit, hi/lo auto-increment)
    &uword  BPL8PTH     = $dff0fc   ; bitplane 8 pointer (high)
    &uword  BPL8PTL     = $dff0fe   ; bitplane 8 pointer (low)
    &pointer BPL8PT      = $dff0fc   ; bitplane 8 pointer (full 32-bit, hi/lo auto-increment)

    ; ========== Bitplane control ==========

    &uword  BPLCON0     = $dff100   ; bitplane control 0
    &uword  BPLCON1     = $dff102   ; bitplane control 1 (scroll)
    &uword  BPLCON2     = $dff104   ; bitplane control 2 (priority)
    &uword  BPLCON3     = $dff106   ; bitplane control 3 (AGA)
    &uword  BPL1MOD     = $dff108   ; bitplane modulo (odd planes)
    &uword  BPL2MOD     = $dff10a   ; bitplane modulo (even planes)
    &uword  BPLCON4     = $dff10c   ; bitplane control 4 (AGA)

    &uword  BPL1DAT     = $dff110   ; bitplane 1 data
    &uword  BPL2DAT     = $dff112   ; bitplane 2 data
    &uword  BPL3DAT     = $dff114   ; bitplane 3 data
    &uword  BPL4DAT     = $dff116   ; bitplane 4 data
    &uword  BPL5DAT     = $dff118   ; bitplane 5 data
    &uword  BPL6DAT     = $dff11a   ; bitplane 6 data
    &uword  BPL7DAT     = $dff11c   ; bitplane 7 data
    &uword  BPL8DAT     = $dff11e   ; bitplane 8 data

    ; ========== Sprite pointers ==========

    &uword  SPR0PTH     = $dff120   ; sprite 0 pointer (high)
    &uword  SPR0PTL     = $dff122   ; sprite 0 pointer (low)
    &pointer SPR0PT      = $dff120   ; sprite 0 pointer (full 32-bit, hi/lo auto-increment)
    &uword  SPR1PTH     = $dff124   ; sprite 1 pointer (high)
    &uword  SPR1PTL     = $dff126   ; sprite 1 pointer (low)
    &pointer SPR1PT      = $dff124   ; sprite 1 pointer (full 32-bit, hi/lo auto-increment)
    &uword  SPR2PTH     = $dff128   ; sprite 2 pointer (high)
    &uword  SPR2PTL     = $dff12a   ; sprite 2 pointer (low)
    &pointer SPR2PT      = $dff128   ; sprite 2 pointer (full 32-bit, hi/lo auto-increment)
    &uword  SPR3PTH     = $dff12c   ; sprite 3 pointer (high)
    &uword  SPR3PTL     = $dff12e   ; sprite 3 pointer (low)
    &pointer SPR3PT      = $dff12c   ; sprite 3 pointer (full 32-bit, hi/lo auto-increment)
    &uword  SPR4PTH     = $dff130   ; sprite 4 pointer (high)
    &uword  SPR4PTL     = $dff132   ; sprite 4 pointer (low)
    &pointer SPR4PT      = $dff130   ; sprite 4 pointer (full 32-bit, hi/lo auto-increment)
    &uword  SPR5PTH     = $dff134   ; sprite 5 pointer (high)
    &uword  SPR5PTL     = $dff136   ; sprite 5 pointer (low)
    &pointer SPR5PT      = $dff134   ; sprite 5 pointer (full 32-bit, hi/lo auto-increment)
    &uword  SPR6PTH     = $dff138   ; sprite 6 pointer (high)
    &uword  SPR6PTL     = $dff13a   ; sprite 6 pointer (low)
    &pointer SPR6PT      = $dff138   ; sprite 6 pointer (full 32-bit, hi/lo auto-increment)
    &uword  SPR7PTH     = $dff13c   ; sprite 7 pointer (high)
    &uword  SPR7PTL     = $dff13e   ; sprite 7 pointer (low)
    &pointer SPR7PT      = $dff13c   ; sprite 7 pointer (full 32-bit, hi/lo auto-increment)

    ; ========== Sprite data ==========

    &uword  SPR0POS     = $dff140   ; sprite 0 position
    &uword  SPR0CTL     = $dff142   ; sprite 0 control
    &uword  SPR0DATA    = $dff144   ; sprite 0 data A
    &uword  SPR0DATB    = $dff146   ; sprite 0 data B
    &uword  SPR1POS     = $dff148   ; sprite 1 position
    &uword  SPR1CTL     = $dff14a   ; sprite 1 control
    &uword  SPR1DATA    = $dff14c   ; sprite 1 data A
    &uword  SPR1DATB    = $dff14e   ; sprite 1 data B
    &uword  SPR2POS     = $dff150   ; sprite 2 position
    &uword  SPR2CTL     = $dff152   ; sprite 2 control
    &uword  SPR2DATA    = $dff154   ; sprite 2 data A
    &uword  SPR2DATB    = $dff156   ; sprite 2 data B
    &uword  SPR3POS     = $dff158   ; sprite 3 position
    &uword  SPR3CTL     = $dff15a   ; sprite 3 control
    &uword  SPR3DATA    = $dff15c   ; sprite 3 data A
    &uword  SPR3DATB    = $dff15e   ; sprite 3 data B
    &uword  SPR4POS     = $dff160   ; sprite 4 position
    &uword  SPR4CTL     = $dff162   ; sprite 4 control
    &uword  SPR4DATA    = $dff164   ; sprite 4 data A
    &uword  SPR4DATB    = $dff166   ; sprite 4 data B
    &uword  SPR5POS     = $dff168   ; sprite 5 position
    &uword  SPR5CTL     = $dff16a   ; sprite 5 control
    &uword  SPR5DATA    = $dff16c   ; sprite 5 data A
    &uword  SPR5DATB    = $dff16e   ; sprite 5 data B
    &uword  SPR6POS     = $dff170   ; sprite 6 position
    &uword  SPR6CTL     = $dff172   ; sprite 6 control
    &uword  SPR6DATA    = $dff174   ; sprite 6 data A
    &uword  SPR6DATB    = $dff176   ; sprite 6 data B
    &uword  SPR7POS     = $dff178   ; sprite 7 position
    &uword  SPR7CTL     = $dff17a   ; sprite 7 control
    &uword  SPR7DATA    = $dff17c   ; sprite 7 data A
    &uword  SPR7DATB    = $dff17e   ; sprite 7 data B

    ; ========== Color palette ==========

    &uword  COLOR00     = $dff180   ; color 0 (background)
    &uword  COLOR01     = $dff182
    &uword  COLOR02     = $dff184
    &uword  COLOR03     = $dff186
    &uword  COLOR04     = $dff188
    &uword  COLOR05     = $dff18a
    &uword  COLOR06     = $dff18c
    &uword  COLOR07     = $dff18e
    &uword  COLOR08     = $dff190
    &uword  COLOR09     = $dff192
    &uword  COLOR10     = $dff194
    &uword  COLOR11     = $dff196
    &uword  COLOR12     = $dff198
    &uword  COLOR13     = $dff19a
    &uword  COLOR14     = $dff19c
    &uword  COLOR15     = $dff19e
    &uword  COLOR16     = $dff1a0
    &uword  COLOR17     = $dff1a2
    &uword  COLOR18     = $dff1a4
    &uword  COLOR19     = $dff1a6
    &uword  COLOR20     = $dff1a8
    &uword  COLOR21     = $dff1aa
    &uword  COLOR22     = $dff1ac
    &uword  COLOR23     = $dff1ae
    &uword  COLOR24     = $dff1b0
    &uword  COLOR25     = $dff1b2
    &uword  COLOR26     = $dff1b4
    &uword  COLOR27     = $dff1b6
    &uword  COLOR28     = $dff1b8
    &uword  COLOR29     = $dff1ba
    &uword  COLOR30     = $dff1bc
    &uword  COLOR31     = $dff1be

    ; ========== ECS/AGA extended registers ==========

    &uword  HTOTAL      = $dff1c0   ; H total (ECS)
    &uword  HSSTOP      = $dff1c2   ; H sync stop (ECS)
    &uword  HBSTRT      = $dff1c4   ; H blank start (ECS)
    &uword  HBSTOP      = $dff1c6   ; H blank stop (ECS)
    &uword  VTOTAL      = $dff1c8   ; V total (ECS)
    &uword  VSSTOP      = $dff1ca   ; V sync stop (ECS)
    &uword  VBSTRT      = $dff1cc   ; V blank start (ECS)
    &uword  VBSTOP      = $dff1ce   ; V blank stop (ECS)
    &uword  SPRHSTRT    = $dff1d0   ; sprite horizontal start (AGA)
    &uword  SPRHSTOP    = $dff1d2   ; sprite horizontal stop (AGA)
    &uword  BPLHSTRT    = $dff1d4   ; bitplane horizontal start (AGA)
    &uword  BPLHSTOP    = $dff1d6   ; bitplane horizontal stop (AGA)
    &uword  HHPOSW      = $dff1d8   ; horizontal hardware position write (AGA)
    &uword  HHPOSR      = $dff1da   ; horizontal hardware position read (AGA)
    &uword  HSSTRT      = $dff1de   ; H sync start (ECS)
    &uword  VSSTRT      = $dff1e0   ; V sync start (ECS)
    &uword  DIWHIGH     = $dff1e4   ; display window high bits (ECS)
    &uword  FMODE       = $dff1fc   ; fetch mode (AGA)

    ; ========== Blitter minterm constants ==========

    const uword ABC    = $80    ; A and B and C
    const uword ABNC   = $40    ; A and B and not C
    const uword ANBC   = $20    ; A and not B and C
    const uword ANBNC  = $10    ; A and not B and not C
    const uword NABC   = $8     ; not A and B and C
    const uword NABNC  = $4     ; not A and not B and C
    const uword NANBC  = $2     ; not A and B and not C
    const uword NANBNC = $1     ; not A and not B and not C

    ; Common blitter minterms
    const uword MINTERM_COPY     = ABC | ABNC | ANBC | NABC          ; A (straight copy)
    const uword MINTERM_OR       = ABC | ABNC | ANBC | NABC | ANBNC | NANBC   ; A or B
    const uword MINTERM_AND      = ABC                                   ; A and B
    const uword MINTERM_XOR      = ANBC | NABC                          ; A xor B
    const uword MINTERM_NOT      = ANBNC | NANBNC                       ; not A
    const uword MINTERM_CLEAR    = 0                                    ; clear all
    const uword MINTERM_NOP      = ABNC | ANBNC                         ; no operation

    ; ========== DMA control bits (dmacon) ==========

    const uword DMAF_SETCLR  = $8000
    const uword DMAF_AUD0    = $0001
    const uword DMAF_AUD1    = $0002
    const uword DMAF_AUD2    = $0004
    const uword DMAF_AUD3    = $0008
    const uword DMAF_AUDIO   = $000f
    const uword DMAF_DISK    = $0010
    const uword DMAF_SPRITE  = $0020
    const uword DMAF_BLITTER = $0040
    const uword DMAF_COPPER  = $0080
    const uword DMAF_RASTER  = $0100
    const uword DMAF_MASTER  = $0200
    const uword DMAF_BLITHOG = $0400
    const uword DMAF_ALL     = $01ff

    ; DMA status read bits
    const uword DMAF_BLTDONE  = $4000
    const uword DMAF_BLTNZERO = $2000

    ; ========== Interrupt bits (intena/intreq) ==========

    const uword INTF_SETCLR  = $8000
    const uword INTF_INTEN   = $4000
    const uword INTF_EXTER   = $2000
    const uword INTF_DSKSYNC = $1000
    const uword INTF_RBF     = $0800
    const uword INTF_AUD3    = $0400
    const uword INTF_AUD2    = $0200
    const uword INTF_AUD1    = $0100
    const uword INTF_AUD0    = $0080
    const uword INTF_BLIT    = $0040
    const uword INTF_VERTB   = $0020
    const uword INTF_COPER   = $0010
    const uword INTF_PORTS   = $0008
    const uword INTF_SOFTINT = $0004
    const uword INTF_DSKBLK  = $0002
    const uword INTF_TBE     = $0001

    ; ========== ADK control bits (adkcon) ==========

    const uword ADKF_SETCLR   = $8000
    const uword ADKF_PRECOMP1 = $4000
    const uword ADKF_PRECOMP0 = $2000
    const uword ADKF_MFMPREC  = $1000
    const uword ADKF_UARTBRK  = $0800
    const uword ADKF_WORDSYNC = $0400
    const uword ADKF_MSBSYNC  = $0200
    const uword ADKF_FAST     = $0100
    const uword ADKF_USE3PN   = $0080
    const uword ADKF_USE2P3   = $0040
    const uword ADKF_USE1P2   = $0020
    const uword ADKF_USE0P1   = $0010
    const uword ADKF_USE3VN   = $0008
    const uword ADKF_USE2V3   = $0004
    const uword ADKF_USE1V2   = $0002
    const uword ADKF_USE0V1   = $0001

    ; ========== Blitter control bits (bltcon0) ==========

    const uword BC0F_DEST  = $0100
    const uword BC0F_SRCC  = $0200
    const uword BC0F_SRCB  = $0400
    const uword BC0F_SRCA  = $0800

    ; ========== Blitter control bits (bltcon1) ==========

    const uword BC1F_DESC      = $0002
    const uword BC1F_LINEMODE  = $0001
    const uword BC1F_FILL_OR   = $0008
    const uword BC1F_FILL_XOR  = $0010
    const uword BC1F_FILL_CARRYIN = $0004
    const uword BC1F_ONEDOT    = $0002
    const uword BC1F_OVFLAG    = $0020
    const uword BC1F_SIGNFLAG  = $0040
    const uword BC1F_SUD       = $0010
    const uword BC1F_SUL       = $0008
    const uword BC1F_AUL       = $0004

    ; ========== CIA registers ==========

    ; CIA-A at $bfe001 (odd, accessed on D8-D15)
    ; CIA-B at $bfd000 (even, accessed on D0-D7)
    ; Each CIA register is spaced $100 apart

    &ubyte  CIAA_PRA     = $bfe001   ; CIA-A peripheral A (serial data, floppy, keyboard)
    &ubyte  CIAA_PRB     = $bfe101   ; CIA-A peripheral B (parallel port)
    &ubyte  CIAA_DDRA    = $bfe201   ; CIA-A data direction A
    &ubyte  CIAA_DDRB    = $bfe301   ; CIA-A data direction B
    &ubyte  CIAA_TALO    = $bfe401   ; CIA-A timer A low
    &ubyte  CIAA_TAHI    = $bfe501   ; CIA-A timer A high
    &ubyte  CIAA_TBLO    = $bfe601   ; CIA-A timer B low
    &ubyte  CIAA_TBHI    = $bfe701   ; CIA-A timer B high
    &ubyte  CIAA_TODLOW  = $bfe801   ; CIA-A TOD low
    &ubyte  CIAA_TODMID  = $bfe901   ; CIA-A TOD mid
    &ubyte  CIAA_TODHI   = $bfea01   ; CIA-A TOD high
    &ubyte  CIAA_SDR     = $bfec01   ; CIA-A serial data
    &ubyte  CIAA_ICR     = $bfed01   ; CIA-A interrupt control
    &ubyte  CIAA_CRA     = $bfee01   ; CIA-A control A
    &ubyte  CIAA_CRB     = $bfef01   ; CIA-A control B

    &ubyte  CIAB_PRA     = $bfd000   ; CIA-B peripheral A (RS-232)
    &ubyte  CIAB_PRB     = $bfd100   ; CIA-B peripheral B (printer)
    &ubyte  CIAB_DDRA    = $bfd200   ; CIA-B data direction A
    &ubyte  CIAB_DDRB    = $bfd300   ; CIA-B data direction B
    &ubyte  CIAB_TALO    = $bfd400   ; CIA-B timer A low
    &ubyte  CIAB_TAHI    = $bfd500   ; CIA-B timer A high
    &ubyte  CIAB_TBLO    = $bfd600   ; CIA-B timer B low
    &ubyte  CIAB_TBHI    = $bfd700   ; CIA-B timer B high
    &ubyte  CIAB_TODLOW  = $bfd800   ; CIA-B TOD low
    &ubyte  CIAB_TODMID  = $bfd900   ; CIA-B TOD mid
    &ubyte  CIAB_TODHI   = $bfda00   ; CIA-B TOD high
    &ubyte  CIAB_SDR     = $bfdc00   ; CIA-B serial data
    &ubyte  CIAB_ICR     = $bfdd00   ; CIA-B interrupt control
    &ubyte  CIAB_CRA     = $bfde00   ; CIA-B control A
    &ubyte  CIAB_CRB     = $bfdf00   ; CIA-B control B

    ; ========== Amiga raw keyboard ==========
    ; Raw keyboard programming sources:
    ; - Amiga Bootcamp keyboard guide
    ;   https://github.com/alfishe/amiga-bootcamp/blob/main/10_devices/keyboard.md
    ; - Hardware Reference Manual, keyboard appendix
    ;   https://www.amigarealm.com/computing/knowledge/hardref/aph.htm
    ; - Amiga 500 direct keyboard access
    ;   https://bumbershootsoft.wordpress.com/2024/08/17/amiga-500-direct-keyboard-access/

    enum KeyCode {
        GRAVE           = $00,
        KEY_1           = $01,
        KEY_2           = $02,
        KEY_3           = $03,
        KEY_4           = $04,
        KEY_5           = $05,
        KEY_6           = $06,
        KEY_7           = $07,
        KEY_8           = $08,
        KEY_9           = $09,
        KEY_0           = $0a,
        MINUS           = $0b,
        EQUALS          = $0c,
        INTL_3          = $0d,
        UNDEFINED_0E    = $0e,
        NP_0            = $0f,
        Q               = $10,
        W               = $11,
        E               = $12,
        R               = $13,
        T               = $14,
        Y               = $15,
        U               = $16,
        I               = $17,
        O               = $18,
        P               = $19,
        LBRACKET        = $1a,
        RBRACKET        = $1b,
        UNDEFINED_1C    = $1c,
        NP_1            = $1d,
        NP_2            = $1e,
        NP_3            = $1f,
        A               = $20,
        S               = $21,
        D               = $22,
        F               = $23,
        G               = $24,
        H               = $25,
        J               = $26,
        K               = $27,
        L               = $28,
        SEMICOLON       = $29,
        QUOTE           = $2a,
        INTL_1          = $2b,
        UNDEFINED_2C    = $2c,
        NP_4            = $2d,
        NP_5            = $2e,
        NP_6            = $2f,
        INTL_2          = $30,
        Z               = $31,
        X               = $32,
        C               = $33,
        V               = $34,
        B               = $35,
        N               = $36,
        M               = $37,
        COMMA           = $38,
        PERIOD          = $39,
        SLASH           = $3a,
        BRAZIL_SLASH    = $3b,
        NP_PERIOD       = $3c,
        NP_7            = $3d,
        NP_8            = $3e,
        NP_9            = $3f,
        SPACE           = $40,
        BACKSPACE       = $41,
        TAB             = $42,
        NP_ENTER        = $43,
        RETURN          = $44,
        ESC             = $45,
        DELETE          = $46,
        INSERT          = $47,
        PAGE_UP         = $48,
        PAGE_DOWN       = $49,
        NP_MINUS        = $4a,
        F11             = $4b,
        CURSOR_UP       = $4c,
        CURSOR_DOWN     = $4d,
        CURSOR_RIGHT    = $4e,
        CURSOR_LEFT     = $4f,
        F1              = $50,
        F2              = $51,
        F3              = $52,
        F4              = $53,
        F5              = $54,
        F6              = $55,
        F7              = $56,
        F8              = $57,
        F9              = $58,
        F10             = $59,
        NP_LPAREN       = $5a,
        NP_RPAREN       = $5b,
        NP_SLASH        = $5c,
        NP_ASTERISK     = $5d,
        NP_PLUS         = $5e,
        HELP            = $5f,
        LSHIFT          = $60,
        RSHIFT          = $61,
        CAPSLOCK        = $62,
        CONTROL         = $63,
        LALT            = $64,
        RALT            = $65,
        LAMIGA          = $66,
        RAMIGA          = $67,
        LMOUSE          = $68,
        RMOUSE          = $69,
        MMOUSE          = $6a,
        MENU            = $6b,
        NP_COMMA        = $6c,
        PRINTSCREEN     = $6d,
        BREAK           = $6e,
        F12             = $6f,
        HOME            = $70,
        END             = $71,
        AUD_STOP        = $72,
        AUD_PLAY_PAUSE  = $73,
        AUD_PREV_TRACK  = $74,
        AUD_NEXT_TRACK  = $75,
        AUD_SHUFFLE     = $76,
        AUD_REPEAT      = $77,
        RESET_WARNING   = $78,
        UNDEFINED_79    = $79,
        UNDEFINED_7A    = $7a,
        UNDEFINED_7B    = $7b,
        UNDEFINED_7C    = $7c,
        UNDEFINED_7D    = $7d,
        UNDEFINED_7E    = $7e,
        UNDEFINED_7F    = $7f,
        KEYCODE_BAD     = $f9,
        KEYBOARD_OVERFLOW = $fa,
        SELFTEST_FAILED = $fc,
        POWERUP_START   = $fd,
        POWERUP_END     = $fe,
    }

    ; 128-key state matrix: one bit per Amiga raw key code (0-127).
    ; Bit 7 of a received code means key-up (release); bit 7 clear means key-down.
    ; Read with custom.key_pressed(code), or directly: byte = code >> 3, bit = code & 7.
    ubyte[16] key_state = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]

    sub raw_keyboard_poll() -> bool, ubyte {
        ; Poll CIA-A for a new raw key event and update key_state.
        ; On a real event, acknowledge by holding KDAT low using CIA-A
        ; Timer A in one-shot mode (~107 us), so timing is CPU-speed independent.
        if (CIAA_ICR & $08)==0
            return false, 0
        ubyte inverted = ~CIAA_SDR
        ubyte keycode = ((inverted >> 1) | (inverted << 7)) as ubyte
        ubyte code = keycode & $7f
        bool released = (keycode & $80) != 0
        ubyte byte_index = code >> 3
        ubyte bit_index = code & 7
        ubyte mask = 1 << bit_index
        if released
            key_state[byte_index] &= ~mask
        else
            key_state[byte_index] |= mask
        if keycode!=KeyCode::SELFTEST_FAILED {
            ubyte ciaa_cra = CIAA_CRA
            ubyte ciaa_talo = CIAA_TALO
            ubyte ciaa_tahi = CIAA_TAHI
            CIAA_ICR = $01
            CIAA_CRA = $48
            CIAA_SDR = 0
            CIAA_TALO = 75
            CIAA_TAHI = 0
            repeat {
                if (CIAA_ICR & $01)!=0
                    break
            }
            CIAA_CRA = ciaa_cra
            CIAA_TALO = ciaa_talo
            CIAA_TAHI = ciaa_tahi
        }
        return true, keycode
    }

    sub key_pressed(ubyte code) -> bool {
        ; Query the 128-key state matrix for the given 7-bit key code.
        ; code is 0-127; byte index = code >> 3, bit index = code & 7.
        ubyte byte_index = code >> 3
        ubyte bit_index = code & 7
        return (key_state[byte_index] & (1 << bit_index)) != 0
    }

    asmsub any_key_pressed() -> bool @D0 {
        ; True if any of the 128 tracked keys is currently held down.
        %asm {{
            lea     custom.key_state,a0
            moveq   #15,d0
1$:         tst.b   (a0)+
            bne.s   2$
            dbra    d0,1$
            moveq   #0,d0
            rts
2$:         moveq   #1,d0
            rts
        }}
    }
}
