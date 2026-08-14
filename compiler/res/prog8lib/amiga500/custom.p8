%import exec
%import syslib

custom {
    %option no_symbol_prefixing, ignore_unused


    inline asmsub grab_system() {
        ; take over the whole OS and system, to run hardware banging programs (games, demos, etc)
        ; inline because it stores stuff on the stack.
        %asm {{

exec_AttnFlags = 296
gfx_ActiView = $22
gfx_copinit = $26
gfx_LOFlist = $32
GfxLoadView    = -222
GfxWaitTOF = -270
IRQ1 = $64
IRQ2 = $68
IRQ3 = $6C
IRQ4 = $70
IRQ5 = $74
IRQ6 = $78
IRQ7 = $7C

            move.l  4.w,a6
            jsr     exec.Forbid(a6)        ; Do not run other tasks
            btst.b  #0,exec_AttnFlags+1(a6)    ; Check if > 68000 processor
            beq.s   1$            ; On 68000 no VBR (always zero)
            lea.l   _S_GetVBR(PC),a5    ; Function to call as supervisor
            jsr     exec.Supervisor(a6)    ; Call supervisor function in A5
            move.l  d0,S_VBR        ; Store the returned VBR contents
1$
            move.l  sys.GfxBase,a6     ; A6 = Graphics base
            move.l  gfx_ActiView(a6),-(sp)    ; Store current View pointer
            sub.l   a1,a1            ; NULL view = default settings
            jsr    GfxLoadView(a6)        ; Load the view
            jsr    GfxWaitTOF(a6)        ; Wait one screen refresh
            jsr    GfxWaitTOF(a6)        ; Wait a 2nd (in case of interlace)

            move.w  #$8000,d0        ; Value
            move.w  custom.DMACONR,-(sp)    ; Store system DMA channels
            or.w    d0,(sp)            ; SET/CLR set to SET
            move.w  custom.INTENAR,-(sp)    ; Store system enabled interrupts
            or.w    d0,(sp)            ; SET/CLR set to SET
            move.w  custom.ADKCONR,-(sp)    ; Audio, disk and UART
            or.w    d0,(sp)            ; SET/CLR set to SET
            move.w  custom.VPOSR,d0        ; Vertical pos and Agnus ID
            btst    #13,d0            ; When set: NTSC, when clear: PAL
            bne.s   2$                 ; Leave value 0 for NTSC
            move.w  #$FFFF,S_PAL        ; Set all bits for PAL

2$          btst.b  #14-8,custom.DMACONR    ; Dummy read
3$          btst.b  #14-8,custom.DMACONR    ; Blitter still busy?
            bne.s   3$                      ; If yes, wait a bit
            move.w  #$01FF,custom.DMACON    ; Disable all DMA
            move.w  #$3FFF,custom.INTENA    ; Disable all interrupts

            move.l  S_VBR,a0         ; A0 = Pointer to vector base
            move.l  IRQ1(a0),-(sp)        ; Store IRQ1 vector
            move.l  IRQ3(a0),-(sp)        ; Store IRQ3 vector
            move.l  IRQ4(a0),-(sp)        ; Store IRQ4 vector
            bra.s   _skip

_S_GetVBR:    dc.l    $4E7A0801            ; MOVEC VBR,d0  - privileged instruction
            rte                            ; Return from supervisor mode

_skip:

            SECTION .bss,bss
S_VBR:        ds.l    1
S_PAL:      ds.w    1

            SECTION .text,code
        }}
    }

    asmsub waitvsync() {
        %asm {{
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

    inline asmsub return_system() {
        ; return to the original OS and multitasking operation when the game/demo exits.
        ; inline because stuff was stored on the stack
        %asm {{
            btst.b  #14-8,custom.DMACONR    ; Dummy read
1$          btst.b  #14-8,custom.DMACONR   ; Blitter still busy?
            bne.s   1$                      ; If yes, wait a bit
            move.w  #$01FF,custom.DMACON    ; Disable all DMA
            move.w  #$3FFF,custom.INTENA   ; Disable all interrupts

            move.l  S_VBR,a0            ; A0 = Pointer to vector base
            move.l  (sp)+,IRQ4(a0)      ; Restore IRQ4 vector
            move.l  (sp)+,IRQ3(a0)      ; Restore IRQ3 vector
            move.l  (sp)+,IRQ1(a0)      ; Restore IRQ1 vector

            move.l  sys.GfxBase,a6      ; A6 = Graphics base
            move.l  gfx_copinit(a6),custom.COP1LC    ; Restore coplist pointer 1
            move.l  gfx_LOFlist(a6),custom.COP2LC    ; Restore coplist pointer 2
            clr.w   custom.COPJMP1       ; Make Copper use restored pointer

            move.w  (sp)+,custom.ADKCON    ; Restore audio, disk and UART
            move.w  (sp)+,custom.INTENA    ; Restore original interrupts
            move.w  (sp)+,custom.DMACON    ; Restore original DMA

            move.l  (sp)+,a1        ; Get original view pointer
            jsr     GfxLoadView(a6)        ; Restore the original view
            jsr     GfxWaitTOF(a6)        ; Wait one screen refresh
            jsr     GfxWaitTOF(a6)        ; Wait a 2nd (in case of interlace)
        }}
    }

    ; ========== AGA palette utilities ==========

    asmsub set_aga_color(ubyte color_index @D0, long rgb24 @D1) clobbers (D0,D1,D2,D3,D4,A0) {
        ; Write a 24-bit RGB color ($00RRGGBB) to an AGA palette entry.
        ; AGA uses eight banks of 32 COLOR registers and two writes per color:
        ; first the upper nibble of each channel, then the lower nibble.
        ; Keep this routine 68000-compatible: the amiga500 target is assembled as
        ; 68000 by default, even though AGA hardware is normally paired with a 68020.
        %asm {{
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
            rts
        }}
    }

    asmsub set_aga_color_nibbles(ubyte color_index @D0, uword rgb_high_nibbles @D1, uword rgb_low_nibbles @D2) clobbers (D0,D1,D2,D3,A0) {
        ; Write preformatted AGA palette nibbles. Each value is $0RGB. Result will still be 24 bit RRGGBB AGA color.
        ; Keep this routine 68000-compatible: the amiga500 target is assembled as
        ; 68000 by default, even though AGA hardware is normally paired with a 68020.
        %asm {{
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
            rts
        }}
    }

    ; ========== mouse button status ==========

    sub left_button() -> bool {
        ; Returns true if the left mouse button (port 1) is pressed.
        ; Left button is CIA-A PRA bit 6, active low.
        return (custom.CIAA_PRA & %01000000) == 0
    }

    sub right_button() -> bool {
        ; Returns true if the right mouse button (port 1) is pressed.
        ; Right button is POTGOR ($dff016) bit 10, active low.
        return (custom.POTGOR & %0000010000000000) == 0
    }

    sub middle_button() -> bool {
        ; Returns true if the middle mouse button (port 1) is pressed.
        ; Middle button is POTGOR ($dff016) bit 8, active low.
        return (custom.POTGOR & %0000000100000000) == 0
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
    &uword  BEAMCON0    = $dff1dc   ; beam counter control (ECS)
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
}
