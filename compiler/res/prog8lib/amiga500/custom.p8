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


    ; Amiga custom chip registers (full addresses, base $dff000)
    ; Based on Commodore Amiga NDK hardware/custom.i
    ; All registers are 16-bit words accessed on even byte boundaries.

    ; ========== Read-only registers ==========

    &uword  BLTDDAT     = $dff000   ; blitter destination read data
    &uword  DMACONR     = $dff002   ; DMA control read
    &uword  VPOSR       = $dff004   ; vertical beam position read
    &uword  VHPOSR      = $dff006   ; vertical + horizontal beam position read
    &uword  DSKDATR     = $dff008   ; disk data read
    &uword  JOY0DAT     = $dff00a   ; joystick/mouse 0 data
    &uword  JOY1DAT     = $dff00c   ; joystick/mouse 1 data
    &uword  CLXDAT      = $dff00e   ; collision detect data

    &uword  ADKCONR     = $dff010   ; audio, disk control read
    &uword  POT0DAT     = $dff012   ; potentiometer 0 data
    &uword  POT1DAT     = $dff014   ; potentiometer 1 data
    &uword  POTINP      = $dff016   ; potentiometer input
    &uword  SERDATR     = $dff018   ; serial port data and status read
    &uword  DSKBYTR     = $dff01a   ; disk byte status read
    &uword  INTENAR     = $dff01c   ; interrupt enable read
    &uword  INTREQR     = $dff01e   ; interrupt request read

    ; ========== Write / Read-Write registers ==========

    ; Disk
    &uword  DSKPT       = $dff020   ; disk pointer (two writes: lo,hi)
    &uword  DSKLEN      = $dff024   ; disk length
    &uword  DSKDAT      = $dff026   ; disk data write
    &uword  REFPTR      = $dff028   ; refresh pointer

    ; Beam position (write)
    &uword  VPOSW       = $dff02a   ; vertical beam position write
    &uword  VHPOSW      = $dff02c   ; vertical + horizontal beam position write
    &uword  COPCON      = $dff02e   ; copper control

    ; Serial
    &uword  SERDAT      = $dff030   ; serial port data and status write
    &uword  SERPER      = $dff032   ; serial port period

    ; Pot/Game
    &uword  POTGO       = $dff034   ; potentiometer control
    &uword  JOYTEST     = $dff036   ; joystick test
    &uword  STREQU      = $dff038   ; strobe vertical equ
    &uword  STRVBL      = $dff03a   ; strobe vertical blank
    &uword  STRHOR      = $dff03c   ; strobe horizontal
    &uword  STRLONG     = $dff03e   ; strobe long frame

    ; Blitter
    &uword  BLTCON0     = $dff040   ; blitter control 0
    &uword  BLTCON1     = $dff042   ; blitter control 1
    &uword  BLTAFWM     = $dff044   ; blitter A first word mask
    &uword  BLTALWM     = $dff046   ; blitter A last word mask
    &uword  BLTCPT      = $dff048   ; blitter C pointer (two writes: lo,hi)
    &uword  BLTBPT      = $dff04c   ; blitter B pointer (two writes: lo,hi)
    &uword  BLTAPT      = $dff050   ; blitter A pointer (two writes: lo,hi)
    &uword  BLTDPT      = $dff054   ; blitter D pointer (two writes: lo,hi)
    &uword  BLTSIZE     = $dff058   ; blitter size (height|width)
    &ubyte  BLTCON0L    = $dff05b   ; blitter control 0 low byte (byte access only)
    &uword  BLTSIZV     = $dff05c   ; blitter size vertical (for 1024+ wide blits)
    &uword  BLTSIZH     = $dff05e   ; blitter size horizontal (for 1024+ wide blits)

    &uword  BLTCMOD     = $dff060   ; blitter C modulo
    &uword  BLTBMOD     = $dff062   ; blitter B modulo
    &uword  BLTAMOD     = $dff064   ; blitter A modulo
    &uword  BLTDMOD     = $dff066   ; blitter D modulo

    &uword  BLTCDAT     = $dff070   ; blitter C data
    &uword  BLTBDAT     = $dff072   ; blitter B data
    &uword  BLTADAT     = $dff074   ; blitter A data

    &uword  DENISEID    = $dff07c   ; Denise/Lisa chip ID
    &uword  DSKSYNC     = $dff07e   ; disk sync pattern

    ; Copper
    &uword  COP1LC      = $dff080   ; copper 1 list pointer (two writes: lo,hi)
    &uword  COP2LC      = $dff084   ; copper 2 list pointer (two writes: lo,hi)
    &uword  COPJMP1     = $dff088   ; copper 1 jump (write to activate)
    &uword  COPJMP2     = $dff08a   ; copper 2 jump (write to activate)
    &uword  COPINS      = $dff08c   ; copper instruction

    ; Display window / data fetch
    &uword  DIWSTRT     = $dff08e   ; display window start
    &uword  DIWSTOP     = $dff090   ; display window stop
    &uword  DDFSTRT     = $dff092   ; display data fetch start
    &uword  DDFSTOP     = $dff094   ; display data fetch stop

    ; Control registers
    &uword  DMACON      = $dff096   ; DMA control write
    &uword  CLXCON      = $dff098   ; collision control
    &uword  INTENA      = $dff09a   ; interrupt enable write
    &uword  INTREQ      = $dff09c   ; interrupt request write
    &uword  ADKCON      = $dff09e   ; audio, disk control

    ; ========== Audio channels ==========

    &uword  AUD0_PTR_LO = $dff0a0   ; audio 0 pointer low
    &uword  AUD0_PTR_HI = $dff0a2   ; audio 0 pointer high
    &uword  AUD0_LEN    = $dff0a4   ; audio 0 length
    &uword  AUD0_PER    = $dff0a6   ; audio 0 period
    &uword  AUD0_VOL    = $dff0a8   ; audio 0 volume
    &uword  AUD0_DAT    = $dff0aa   ; audio 0 sample data

    &uword  AUD1_PTR_LO = $dff0b0   ; audio 1 pointer low
    &uword  AUD1_PTR_HI = $dff0b2   ; audio 1 pointer high
    &uword  AUD1_LEN    = $dff0b4   ; audio 1 length
    &uword  AUD1_PER    = $dff0b6   ; audio 1 period
    &uword  AUD1_VOL    = $dff0b8   ; audio 1 volume
    &uword  AUD1_DAT    = $dff0ba   ; audio 1 sample data

    &uword  AUD2_PTR_LO = $dff0c0   ; audio 2 pointer low
    &uword  AUD2_PTR_HI = $dff0c2   ; audio 2 pointer high
    &uword  AUD2_LEN    = $dff0c4   ; audio 2 length
    &uword  AUD2_PER    = $dff0c6   ; audio 2 period
    &uword  AUD2_VOL    = $dff0c8   ; audio 2 volume
    &uword  AUD2_DAT    = $dff0ca   ; audio 2 sample data

    &uword  AUD3_PTR_LO = $dff0d0   ; audio 3 pointer low
    &uword  AUD3_PTR_HI = $dff0d2   ; audio 3 pointer high
    &uword  AUD3_LEN    = $dff0d4   ; audio 3 length
    &uword  AUD3_PER    = $dff0d6   ; audio 3 period
    &uword  AUD3_VOL    = $dff0d8   ; audio 3 volume
    &uword  AUD3_DAT    = $dff0da   ; audio 3 sample data

    ; ========== Bitplane pointers ==========

    &uword  BPL0_PTR_LO = $dff0e0   ; bitplane 0 pointer low
    &uword  BPL0_PTR_HI = $dff0e2   ; bitplane 0 pointer high
    &uword  BPL1_PTR_LO = $dff0e4   ; bitplane 1 pointer low
    &uword  BPL1_PTR_HI = $dff0e6   ; bitplane 1 pointer high
    &uword  BPL2_PTR_LO = $dff0e8   ; bitplane 2 pointer low
    &uword  BPL2_PTR_HI = $dff0ea   ; bitplane 2 pointer high
    &uword  BPL3_PTR_LO = $dff0ec   ; bitplane 3 pointer low
    &uword  BPL3_PTR_HI = $dff0ee   ; bitplane 3 pointer high
    &uword  BPL4_PTR_LO = $dff0f0   ; bitplane 4 pointer low
    &uword  BPL4_PTR_HI = $dff0f2   ; bitplane 4 pointer high
    &uword  BPL5_PTR_LO = $dff0f4   ; bitplane 5 pointer low
    &uword  BPL5_PTR_HI = $dff0f6   ; bitplane 5 pointer high
    &uword  BPL6_PTR_LO = $dff0f8   ; bitplane 6 pointer low
    &uword  BPL6_PTR_HI = $dff0fa   ; bitplane 6 pointer high
    &uword  BPL7_PTR_LO = $dff0fc   ; bitplane 7 pointer low
    &uword  BPL7_PTR_HI = $dff0fe   ; bitplane 7 pointer high

    ; ========== Bitplane control ==========

    &uword  BPLCON0     = $dff100   ; bitplane control 0
    &uword  BPLCON1     = $dff102   ; bitplane control 1 (horizontal scroll)
    &uword  BPLCON2     = $dff104   ; bitplane control 2 (priority/playfield)
    &uword  BPLCON3     = $dff106   ; bitplane control 3 (AGA+)
    &uword  BPL1MOD     = $dff108   ; bitplane modulo 1
    &uword  BPL2MOD     = $dff10a   ; bitplane modulo 2
    &uword  BPLCON4     = $dff10c   ; bitplane control 4 (AGA)
    &uword  CLXCON2     = $dff10e   ; collision control 2 (AGA)

    &uword  BPL0DAT     = $dff110   ; bitplane 0 data
    &uword  BPL1DAT     = $dff112   ; bitplane 1 data
    &uword  BPL2DAT     = $dff114   ; bitplane 2 data
    &uword  BPL3DAT     = $dff116   ; bitplane 3 data
    &uword  BPL4DAT     = $dff118   ; bitplane 4 data
    &uword  BPL5DAT     = $dff11a   ; bitplane 5 data
    &uword  BPL6DAT     = $dff11c   ; bitplane 6 data
    &uword  BPL7DAT     = $dff11e   ; bitplane 7 data

    ; ========== Sprite pointers ==========

    &uword  SPR0_PTR_LO = $dff120   ; sprite 0 pointer low
    &uword  SPR0_PTR_HI = $dff122   ; sprite 0 pointer high
    &uword  SPR1_PTR_LO = $dff124   ; sprite 1 pointer low
    &uword  SPR1_PTR_HI = $dff126   ; sprite 1 pointer high
    &uword  SPR2_PTR_LO = $dff128   ; sprite 2 pointer low
    &uword  SPR2_PTR_HI = $dff12a   ; sprite 2 pointer high
    &uword  SPR3_PTR_LO = $dff12c   ; sprite 3 pointer low
    &uword  SPR3_PTR_HI = $dff12e   ; sprite 3 pointer high
    &uword  SPR4_PTR_LO = $dff130   ; sprite 4 pointer low
    &uword  SPR4_PTR_HI = $dff132   ; sprite 4 pointer high
    &uword  SPR5_PTR_LO = $dff134   ; sprite 5 pointer low
    &uword  SPR5_PTR_HI = $dff136   ; sprite 5 pointer high
    &uword  SPR6_PTR_LO = $dff138   ; sprite 6 pointer low
    &uword  SPR6_PTR_HI = $dff13a   ; sprite 6 pointer high
    &uword  SPR7_PTR_LO = $dff13c   ; sprite 7 pointer low
    &uword  SPR7_PTR_HI = $dff13e   ; sprite 7 pointer high

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

    &uword  COLOR0      = $dff180   ; color 0 (background)
    &uword  COLOR1      = $dff182
    &uword  COLOR2      = $dff184
    &uword  COLOR3      = $dff186
    &uword  COLOR4      = $dff188
    &uword  COLOR5      = $dff18a
    &uword  COLOR6      = $dff18c
    &uword  COLOR7      = $dff18e
    &uword  COLOR8      = $dff190
    &uword  COLOR9      = $dff192
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

    ; ========== AGA/ECS extended registers ==========

    &uword  HTOTAL      = $dff1c0   ; horizontal total (AGA)
    &uword  HSSTOP      = $dff1c2   ; horizontal sync stop (AGA)
    &uword  HBSTRT      = $dff1c4   ; horizontal blank start (AGA)
    &uword  HBSTOP      = $dff1c6   ; horizontal blank stop (AGA)
    &uword  VTOTAL      = $dff1c8   ; vertical total (AGA)
    &uword  VSSTOP      = $dff1ca   ; vertical sync stop (AGA)
    &uword  VBSTRT      = $dff1cc   ; vertical blank start (AGA)
    &uword  VBSTOP      = $dff1ce   ; vertical blank stop (AGA)
    &uword  SPRHSTRT    = $dff1d0   ; sprite horizontal start (AGA)
    &uword  SPRHSTOP    = $dff1d2   ; sprite horizontal stop (AGA)
    &uword  BPLHSTRT    = $dff1d4   ; bitplane horizontal start (AGA)
    &uword  BPLHSTOP    = $dff1d6   ; bitplane horizontal stop (AGA)
    &uword  HHPOSW      = $dff1d8   ; horizontal hardware position write (AGA)
    &uword  HHPOSR      = $dff1da   ; horizontal hardware position read (AGA)
    &uword  BEAMCON0    = $dff1dc   ; beam counter control (AGA)
    &uword  HSSTRT      = $dff1de   ; horizontal sync start (AGA)
    &uword  VSSTRT      = $dff1e0   ; vertical sync start (AGA)
    &uword  HCENTER     = $dff1e2   ; horizontal center (AGA)
    &uword  DIWHIGH     = $dff1e4   ; display window high bits (AGA)
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

    ; ========== AGA palette utility ==========

    sub set_aga_color(ubyte color_index, long rgb24) {
        ; Write a 24-bit RGB color ($00RRGGBB) to any of the 256
        ; AGA palette entries (indices 0-255).
        ; Handles 24-bit to dual 16-bit conversion and BPLCON4
        ; bank switching automatically.
        ; On OCS/ECS only indices 0-31 are valid.
        uword red   = ((rgb24 >> 16) as uword) & $ff
        uword green = ((rgb24 >> 8) as uword) & $ff
        uword blue  = (rgb24 as uword) & $ff
        uword low_word  = ((red & $0f) << 8) | ((green & $0f) << 4) | (blue & $0f)
        uword high_word = ((red >> 4) << 8) | ((green >> 4) << 4) | (blue >> 4)
        uword offset = ((color_index & $1f) as uword) * 2

        uword bank = (color_index >> 5) as uword
        custom.BPLCON4 = bank

        pokew($dff180 + offset, low_word)
        pokew($dff180 + offset, high_word)
    }

    ; ========== mouse button status ==========

    sub left_button() -> bool {
        ; Returns true if the left mouse button (port 1) is pressed.
        ; Left button is CIA-A PRA bit 6, active low.
        return (custom.CIAA_PRA & %01000000) == 0
    }

    sub right_button() -> bool {
        ; Returns true if the right mouse button (port 1) is pressed.
        ; Right button is POTINP ($dff016) bit 10, active low.
        return (custom.POTINP & %0000010000000000) == 0
    }

    sub middle_button() -> bool {
        ; Returns true if the middle mouse button (port 1) is pressed.
        ; Middle button is POTINP ($dff016) bit 8, active low.
        return (custom.POTINP & %0000000100000000) == 0
    }

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
