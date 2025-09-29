; Prog8 definitions for the CommanderX16
; Including memory registers, I/O registers, Basic and Kernal subroutines.

%option no_symbol_prefixing, ignore_unused

cbm {
    ; Commodore (CBM) common variables, vectors and kernal routines

; irq, system and hardware vectors  (common across cbm machines):
    &uword  IERROR      = $0300
    &uword  IMAIN       = $0302
    &uword  ICRNCH      = $0304
    &uword  IQPLOP      = $0306
    &uword  IGONE       = $0308
    &uword  IEVAL       = $030a
    &ubyte  SAREG       = $030c     ; register storage for A for SYS calls
    &ubyte  SXREG       = $030d     ; register storage for X for SYS calls
    &ubyte  SYREG       = $030e     ; register storage for Y for SYS calls
    &ubyte  SPREG       = $030f     ; register storage for P (status register) for SYS calls
    &uword  USRADD      = $0311     ; vector for the USR() basic command
    ; $0313 is unused.
    &uword  CINV        = $0314     ; IRQ vector (in ram)
    &uword  CBINV       = $0316     ; BRK vector (in ram)
    &uword  NMINV       = $0318     ; NMI vector (in ram)
    &uword  IOPEN       = $031a
    &uword  ICLOSE      = $031c
    &uword  ICHKIN      = $031e
    &uword  ICKOUT      = $0320
    &uword  ICLRCH      = $0322
    &uword  IBASIN      = $0324
    &uword  IBSOUT      = $0326
    &uword  ISTOP       = $0328
    &uword  IGETIN      = $032a
    &uword  ICLALL      = $032c
    ; $032e has a X16 specific function (KEYHDL) so you'll find this as cx16.KEYHDL
    &uword  ILOAD       = $0330
    &uword  ISAVE       = $0332
    &uword  NMI_VEC     = $FFFA     ; 65c02 nmi vector, determined by the kernal if banked in
    &uword  RESET_VEC   = $FFFC     ; 65c02 reset vector, determined by the kernal if banked in
    &uword  IRQ_VEC     = $FFFE     ; 65c02 interrupt vector, determined by the kernal if banked in


; STROUT --> use txt.print
; CLEARSCR -> use txt.clear_screen
; HOMECRSR -> use txt.home or txt.plot

extsub $FF81 = CINT() clobbers(A,X,Y)                           ; (alias: SCINIT) initialize screen editor and video chip, including resetting to the default color palette. Note: also sets the video mode back to VGA
extsub $FF84 = IOINIT() clobbers(A, X)                          ; initialize I/O devices (CIA, IRQ, ...)
extsub $FF87 = RAMTAS() clobbers(A,X,Y)                         ; initialize RAM, screen
extsub $FF8A = RESTOR() clobbers(A,X,Y)                         ; restore default I/O vectors
extsub $FF8D = VECTOR(uword userptr @ XY, bool dir @ Pc) clobbers(A,Y)     ; read/set I/O vector table
extsub $FF90 = SETMSG(ubyte value @ A)                          ; set Kernal message control flag
extsub $FF93 = SECOND(ubyte address @ A) clobbers(A)            ; (alias: LSTNSA) send secondary address after LISTEN
extsub $FF96 = TKSA(ubyte address @ A) clobbers(A)              ; (alias: TALKSA) send secondary address after TALK
extsub $FF99 = MEMTOP(uword address @ XY, bool dir @ Pc) -> uword @ XY, ubyte @A     ; read/set top of memory  pointer.   NOTE: on the Cx16 also returns the number of RAM memory banks in A!  Also see cx16.numbanks()
extsub $FF9C = MEMBOT(uword address @ XY, bool dir @ Pc) -> uword @ XY     ; read/set bottom of memory  pointer
extsub $FF9F = SCNKEY() clobbers(A,X,Y)                         ; scan the keyboard, also called  kbd_scan
extsub $FFA2 = SETTMO(ubyte timeout @ A)                        ; set time-out flag for IEEE bus
extsub $FFA5 = ACPTR() -> ubyte @ A                             ; (alias: IECIN) input byte from serial bus
extsub $FFA8 = CIOUT(ubyte databyte @ A)                        ; (alias: IECOUT) output byte to serial bus
extsub $FFAB = UNTLK() clobbers(A)                              ; command serial bus device to UNTALK
extsub $FFAE = UNLSN() clobbers(A)                              ; command serial bus device to UNLISTEN
extsub $FFB1 = LISTEN(ubyte device @ A) clobbers(A)             ; command serial bus device to LISTEN
extsub $FFB4 = TALK(ubyte device @ A) clobbers(A)               ; command serial bus device to TALK
extsub $FFB7 = READST() -> ubyte @ A                            ; read I/O status word  (use CLEARST to reset it to 0)
extsub $FFBA = SETLFS(ubyte logical @ A, ubyte device @ X, ubyte secondary @ Y)   ; set logical file parameters
extsub $FFBD = SETNAM(ubyte namelen @ A, str filename @ XY)     ; set filename parameters
extsub $FFC0 = OPEN() clobbers(X,Y) -> bool @Pc, ubyte @A       ; (via 794 ($31A)) open a logical file
extsub $FFC3 = CLOSE(ubyte logical @ A) clobbers(A,X,Y)         ; (via 796 ($31C)) close a logical file
extsub $FFC6 = CHKIN(ubyte logical @ X) clobbers(A,X) -> bool @Pc    ; (via 798 ($31E)) define an input channel
extsub $FFC9 = CHKOUT(ubyte logical @ X) clobbers(A,X)          ; (via 800 ($320)) define an output channel
extsub $FFCC = CLRCHN() clobbers(A,X)                           ; (via 802 ($322)) restore default devices
extsub $FFCF = CHRIN() clobbers(X, Y) -> ubyte @ A   ; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
extsub $FFD2 = CHROUT(ubyte character @ A)                           ; (via 806 ($326)) output a character
extsub $FFD5 = LOAD(ubyte verify @ A, uword address @ XY) -> bool @Pc, ubyte @ A, uword @ XY     ; (via 816 ($330)) load from device
extsub $FFD8 = SAVE(ubyte zp_startaddr @ A, uword endaddr @ XY) clobbers (X, Y) -> bool @ Pc, ubyte @ A       ; (via 818 ($332)) save to a device.  See also BSAVE
extsub $FFDB = SETTIM(ubyte low @ A, ubyte middle @ X, ubyte high @ Y)      ; set the software clock
extsub $FFDE = RDTIM() -> ubyte @ A, ubyte @ X, ubyte @ Y       ; read the software clock (in little endian order: A=lo,X=mid,Y=high) , however use RDTIM_safe() instead
extsub $FFE1 = STOP() clobbers(X) -> bool @ Pz, ubyte @ A       ; (via 808 ($328)) check the STOP key (and some others in A)        also see STOP2
extsub $FFE4 = GETIN() clobbers(X,Y) -> bool @Pc, ubyte @ A     ; (via 810 ($32A)) get a character      also see GETIN2
extsub $FFE7 = CLALL() clobbers(A,X)                            ; (via 812 ($32C)) close all files
extsub $FFEA = UDTIM() clobbers(A,X)                            ; update the software clock
extsub $FFED = SCREEN() -> ubyte @ X, ubyte @ Y                 ; get size of text screen into X (columns) and Y (rows)
extsub $FFF0 = PLOT(ubyte col @ Y, ubyte row @ X, bool dir @ Pc) clobbers(A) -> ubyte @ Y, ubyte @ X       ; read/set position of cursor on screen (Y=column, X=row).  Also see txt.plot
extsub $FFF3 = IOBASE() -> uword @ XY                           ; read base address of I/O devices

; ---- utility

inline asmsub STOP2() clobbers(X,A) -> bool @Pz  {
    ; -- just like STOP, but omits the special keys result value in A.
    ;    just for convenience because most of the times you're only interested in the stop pressed or not status.
    %asm {{
        jsr  cbm.STOP
    }}
}

inline asmsub GETIN2() clobbers(X,Y) -> ubyte @A {
    ; -- just like GETIN, but omits the carry flag result value.
    ;    just for convenience because GETIN is so often used to just read keyboard input,
    ;    where you don't have to deal with a potential error status
    %asm {{
        jsr  cbm.GETIN
    }}
}

asmsub RDTIM_safe() -> ubyte @ A, ubyte @ X, ubyte @ Y {
    ; -- read the software clock (in little endian order: A=lo,X=mid,Y=high)
    ;    with safeguard for ram bank issue for irqs.
    %asm {{
        php
        sei
        jsr  cbm.RDTIM
        plp
        rts
    }}
}

asmsub RDTIM16() clobbers(X) -> uword @AY {
    ; --  like RDTIM_safe() but only returning the lower 16 bits in AY for convenience.
    %asm {{
        jsr  RDTIM_safe
        pha
        txa
        tay
        pla
        rts
    }}
}

sub CLEARST() {
    ; -- Set the ST status variable back to 0. (there's no direct kernal call for this)
    ;    Note: a drive error state (blinking led) isn't cleared! You can use diskio.status() to clear that.
    SETNAM(0, $0000)
    SETLFS(15, 3, 15)
    void OPEN()
    CLOSE(15)
}

asmsub kbdbuf_clear() {
    ; -- convenience helper routine to clear the keyboard buffer
    %asm {{
-       jsr  GETIN
        cmp  #0
        bne  -
        rts
    }}
}

}

cx16 {

; cx16 specific vectors and variables
    &uword  KEYHDL      = $032e     ; keyboard scan code handler see examples/cx16/keyboardhandler.p8

    &uword  edkeyvec    = $ac03     ; (ram bank 0): for intercepting BASIN/CHRIN key strokes. See set_chrin_keyhandler()
    &ubyte  edkeybk     = $ac05     ; ...the RAM bank of the handler routine, if not in low ram

    &ubyte  stavec      = $03b2     ; argument for stash()


; the sixteen virtual 16-bit registers in both normal unsigned mode and signed mode (s)
    &uword r0  = $0002
    &uword r1  = $0004
    &uword r2  = $0006
    &uword r3  = $0008
    &uword r4  = $000a
    &uword r5  = $000c
    &uword r6  = $000e
    &uword r7  = $0010
    &uword r8  = $0012
    &uword r9  = $0014
    &uword r10 = $0016
    &uword r11 = $0018
    &uword r12 = $001a
    &uword r13 = $001c
    &uword r14 = $001e
    &uword r15 = $0020

    ; signed word versions
    &word r0s  = $0002
    &word r1s  = $0004
    &word r2s  = $0006
    &word r3s  = $0008
    &word r4s  = $000a
    &word r5s  = $000c
    &word r6s  = $000e
    &word r7s  = $0010
    &word r8s  = $0012
    &word r9s  = $0014
    &word r10s = $0016
    &word r11s = $0018
    &word r12s = $001a
    &word r13s = $001c
    &word r14s = $001e
    &word r15s = $0020

    ; ubyte versions (low and high bytes)
    &ubyte r0L  = $0002
    &ubyte r1L  = $0004
    &ubyte r2L  = $0006
    &ubyte r3L  = $0008
    &ubyte r4L  = $000a
    &ubyte r5L  = $000c
    &ubyte r6L  = $000e
    &ubyte r7L  = $0010
    &ubyte r8L  = $0012
    &ubyte r9L  = $0014
    &ubyte r10L = $0016
    &ubyte r11L = $0018
    &ubyte r12L = $001a
    &ubyte r13L = $001c
    &ubyte r14L = $001e
    &ubyte r15L = $0020

    &ubyte r0H  = $0003
    &ubyte r1H  = $0005
    &ubyte r2H  = $0007
    &ubyte r3H  = $0009
    &ubyte r4H  = $000b
    &ubyte r5H  = $000d
    &ubyte r6H  = $000f
    &ubyte r7H  = $0011
    &ubyte r8H  = $0013
    &ubyte r9H  = $0015
    &ubyte r10H = $0017
    &ubyte r11H = $0019
    &ubyte r12H = $001b
    &ubyte r13H = $001d
    &ubyte r14H = $001f
    &ubyte r15H = $0021

    ; signed byte versions (low and high bytes)
    &byte r0sL  = $0002
    &byte r1sL  = $0004
    &byte r2sL  = $0006
    &byte r3sL  = $0008
    &byte r4sL  = $000a
    &byte r5sL  = $000c
    &byte r6sL  = $000e
    &byte r7sL  = $0010
    &byte r8sL  = $0012
    &byte r9sL  = $0014
    &byte r10sL = $0016
    &byte r11sL = $0018
    &byte r12sL = $001a
    &byte r13sL = $001c
    &byte r14sL = $001e
    &byte r15sL = $0020

    &byte r0sH  = $0003
    &byte r1sH  = $0005
    &byte r2sH  = $0007
    &byte r3sH  = $0009
    &byte r4sH  = $000b
    &byte r5sH  = $000d
    &byte r6sH  = $000f
    &byte r7sH  = $0011
    &byte r8sH  = $0013
    &byte r9sH  = $0015
    &byte r10sH = $0017
    &byte r11sH = $0019
    &byte r12sH = $001b
    &byte r13sH = $001d
    &byte r14sH = $001f
    &byte r15sH = $0021

    ; boolean versions
    &bool r0bL  = $0002
    &bool r1bL  = $0004
    &bool r2bL  = $0006
    &bool r3bL  = $0008
    &bool r4bL  = $000a
    &bool r5bL  = $000c
    &bool r6bL  = $000e
    &bool r7bL  = $0010
    &bool r8bL  = $0012
    &bool r9bL  = $0014
    &bool r10bL = $0016
    &bool r11bL = $0018
    &bool r12bL = $001a
    &bool r13bL = $001c
    &bool r14bL = $001e
    &bool r15bL = $0020

    &bool r0bH  = $0003
    &bool r1bH  = $0005
    &bool r2bH  = $0007
    &bool r3bH  = $0009
    &bool r4bH  = $000b
    &bool r5bH  = $000d
    &bool r6bH  = $000f
    &bool r7bH  = $0011
    &bool r8bH  = $0013
    &bool r9bH  = $0015
    &bool r10bH = $0017
    &bool r11bH = $0019
    &bool r12bH = $001b
    &bool r13bH = $001d
    &bool r14bH = $001f
    &bool r15bH = $0021


; VERA registers

    const uword VERA_BASE       = $9F20
    &ubyte  VERA_ADDR_L         = VERA_BASE + $0000
    &ubyte  VERA_ADDR_M         = VERA_BASE + $0001
    &uword  VERA_ADDR           = VERA_BASE + $0000 ; still need to do the _H separately
    &ubyte  VERA_ADDR_H         = VERA_BASE + $0002
    &ubyte  VERA_DATA0          = VERA_BASE + $0003
    &ubyte  VERA_DATA1          = VERA_BASE + $0004
    &ubyte  VERA_CTRL           = VERA_BASE + $0005
    &ubyte  VERA_IEN            = VERA_BASE + $0006
    &ubyte  VERA_ISR            = VERA_BASE + $0007
    &ubyte  VERA_IRQLINE_L      = VERA_BASE + $0008 ; write only
    &ubyte  VERA_SCANLINE_L     = VERA_BASE + $0008 ; read only
    &ubyte  VERA_DC_VIDEO       = VERA_BASE + $0009 ; DCSEL= 0
    &ubyte  VERA_DC_HSCALE      = VERA_BASE + $000A ; DCSEL= 0
    &ubyte  VERA_DC_VSCALE      = VERA_BASE + $000B ; DCSEL= 0
    &ubyte  VERA_DC_BORDER      = VERA_BASE + $000C ; DCSEL= 0
    &ubyte  VERA_DC_HSTART      = VERA_BASE + $0009 ; DCSEL= 1
    &ubyte  VERA_DC_HSTOP       = VERA_BASE + $000A ; DCSEL= 1
    &ubyte  VERA_DC_VSTART      = VERA_BASE + $000B ; DCSEL= 1
    &ubyte  VERA_DC_VSTOP       = VERA_BASE + $000C ; DCSEL= 1
    &ubyte  VERA_DC_VER0        = VERA_BASE + $0009 ; DCSEL=63
    &ubyte  VERA_DC_VER1        = VERA_BASE + $000A ; DCSEL=63
    &ubyte  VERA_DC_VER2        = VERA_BASE + $000B ; DCSEL=63
    &ubyte  VERA_DC_VER3        = VERA_BASE + $000C ; DCSEL=63
    &ubyte  VERA_L0_CONFIG      = VERA_BASE + $000D
    &ubyte  VERA_L0_MAPBASE     = VERA_BASE + $000E
    &ubyte  VERA_L0_TILEBASE    = VERA_BASE + $000F
    &ubyte  VERA_L0_HSCROLL_L   = VERA_BASE + $0010
    &ubyte  VERA_L0_HSCROLL_H   = VERA_BASE + $0011
    &uword  VERA_L0_HSCROLL     = VERA_BASE + $0010
    &ubyte  VERA_L0_VSCROLL_L   = VERA_BASE + $0012
    &ubyte  VERA_L0_VSCROLL_H   = VERA_BASE + $0013
    &uword  VERA_L0_VSCROLL     = VERA_BASE + $0012
    &ubyte  VERA_L1_CONFIG      = VERA_BASE + $0014
    &ubyte  VERA_L1_MAPBASE     = VERA_BASE + $0015
    &ubyte  VERA_L1_TILEBASE    = VERA_BASE + $0016
    &ubyte  VERA_L1_HSCROLL_L   = VERA_BASE + $0017
    &ubyte  VERA_L1_HSCROLL_H   = VERA_BASE + $0018
    &uword  VERA_L1_HSCROLL     = VERA_BASE + $0017
    &ubyte  VERA_L1_VSCROLL_L   = VERA_BASE + $0019
    &ubyte  VERA_L1_VSCROLL_H   = VERA_BASE + $001A
    &uword  VERA_L1_VSCROLL     = VERA_BASE + $0019
    &ubyte  VERA_AUDIO_CTRL     = VERA_BASE + $001B
    &ubyte  VERA_AUDIO_RATE     = VERA_BASE + $001C
    &ubyte  VERA_AUDIO_DATA     = VERA_BASE + $001D
    &ubyte  VERA_SPI_DATA       = VERA_BASE + $001E
    &ubyte  VERA_SPI_CTRL       = VERA_BASE + $001F

    ; Vera FX registers: (accessing depends on particular DCSEL value set in VERA_CTRL!)
    &ubyte  VERA_FX_CTRL        = VERA_BASE + $0009
    &ubyte  VERA_FX_TILEBASE    = VERA_BASE + $000a
    &ubyte  VERA_FX_MAPBASE     = VERA_BASE + $000b
    &ubyte  VERA_FX_MULT        = VERA_BASE + $000c
    &ubyte  VERA_FX_X_INCR_L    = VERA_BASE + $0009
    &ubyte  VERA_FX_X_INCR_H    = VERA_BASE + $000a
    &uword  VERA_FX_X_INCR      = VERA_BASE + $0009
    &ubyte  VERA_FX_Y_INCR_L    = VERA_BASE + $000b
    &ubyte  VERA_FX_Y_INCR_H    = VERA_BASE + $000c
    &uword  VERA_FX_Y_INCR      = VERA_BASE + $000b
    &ubyte  VERA_FX_X_POS_L     = VERA_BASE + $0009
    &ubyte  VERA_FX_X_POS_H     = VERA_BASE + $000a
    &uword  VERA_FX_X_POS       = VERA_BASE + $0009
    &ubyte  VERA_FX_Y_POS_L     = VERA_BASE + $000b
    &ubyte  VERA_FX_Y_POS_H     = VERA_BASE + $000c
    &uword  VERA_FX_Y_POS       = VERA_BASE + $000b
    &ubyte  VERA_FX_X_POS_S     = VERA_BASE + $0009
    &ubyte  VERA_FX_Y_POS_S     = VERA_BASE + $000a
    &ubyte  VERA_FX_POLY_FILL_L = VERA_BASE + $000b
    &ubyte  VERA_FX_POLY_FILL_H = VERA_BASE + $000c
    &uword  VERA_FX_POLY_FILL   = VERA_BASE + $000b
    &ubyte  VERA_FX_CACHE_L     = VERA_BASE + $0009
    &ubyte  VERA_FX_CACHE_M     = VERA_BASE + $000a
    &ubyte  VERA_FX_CACHE_H     = VERA_BASE + $000b
    &ubyte  VERA_FX_CACHE_U     = VERA_BASE + $000c
    &ubyte  VERA_FX_ACCUM       = VERA_BASE + $000a
    &ubyte  VERA_FX_ACCUM_RESET = VERA_BASE + $0009


; VERA_PSG_BASE     = $1F9C0
; VERA_PALETTE_BASE = $1FA00
; VERA_SPRITES_BASE = $1FC00

; I/O

    const uword  VIA1_BASE   = $9f00                  ;VIA 6522 #1
    &ubyte  via1prb    = VIA1_BASE + 0
    &ubyte  via1pra    = VIA1_BASE + 1
    &ubyte  via1ddrb   = VIA1_BASE + 2
    &ubyte  via1ddra   = VIA1_BASE + 3
    &ubyte  via1t1l    = VIA1_BASE + 4
    &ubyte  via1t1h    = VIA1_BASE + 5
    &ubyte  via1t1ll   = VIA1_BASE + 6
    &ubyte  via1t1lh   = VIA1_BASE + 7
    &ubyte  via1t2l    = VIA1_BASE + 8
    &ubyte  via1t2h    = VIA1_BASE + 9
    &ubyte  via1sr     = VIA1_BASE + 10
    &ubyte  via1acr    = VIA1_BASE + 11
    &ubyte  via1pcr    = VIA1_BASE + 12
    &ubyte  via1ifr    = VIA1_BASE + 13
    &ubyte  via1ier    = VIA1_BASE + 14
    &ubyte  via1ora    = VIA1_BASE + 15

    const uword  VIA2_BASE   = $9f10                  ;VIA 6522 #2
    &ubyte  via2prb    = VIA2_BASE + 0
    &ubyte  via2pra    = VIA2_BASE + 1
    &ubyte  via2ddrb   = VIA2_BASE + 2
    &ubyte  via2ddra   = VIA2_BASE + 3
    &ubyte  via2t1l    = VIA2_BASE + 4
    &ubyte  via2t1h    = VIA2_BASE + 5
    &ubyte  via2t1ll   = VIA2_BASE + 6
    &ubyte  via2t1lh   = VIA2_BASE + 7
    &ubyte  via2t2l    = VIA2_BASE + 8
    &ubyte  via2t2h    = VIA2_BASE + 9
    &ubyte  via2sr     = VIA2_BASE + 10
    &ubyte  via2acr    = VIA2_BASE + 11
    &ubyte  via2pcr    = VIA2_BASE + 12
    &ubyte  via2ifr    = VIA2_BASE + 13
    &ubyte  via2ier    = VIA2_BASE + 14
    &ubyte  via2ora    = VIA2_BASE + 15

; YM-2151 sound chip
    &ubyte  YM_ADDRESS	= $9f40
    &ubyte  YM_DATA	    = $9f41

    const uword  extdev	= $9f60


; ---- Commander X-16 additions on top of C64 kernal routines ----
; spelling of the names is taken from the Commander X-16 rom sources

extsub $ff4a = CLOSE_ALL(ubyte device @A)  clobbers(A,X,Y)
extsub $ff59 = LKUPLA(ubyte la @A)  clobbers(A,X,Y)
extsub $ff5c = LKUPSA(ubyte sa @Y)  clobbers(A,X,Y)
extsub $ff5f = screen_mode(ubyte mode @A, bool getCurrent @Pc) -> ubyte @A, ubyte @X, ubyte @Y, bool @Pc        ; also see SCREEN or get/set_screen_mode()
extsub $ff62 = screen_set_charset(ubyte charset @A, uword charsetptr @XY)  clobbers(A,X,Y)
extsub $ff6e = JSRFAR()  ; following word = address to call, byte after that=rom/ram bank it is in
extsub $ff74 = fetch(ubyte zp_startaddr @A, ubyte bank @X, ubyte index @Y)  clobbers(X) -> ubyte @A
extsub $ff77 = stash(ubyte data @A, ubyte bank @X, ubyte index @Y)  clobbers(X)     ;  note: The the zero page address containing the base address is passed in stavec ($03B2)
extsub $ff7d = PRIMM()

; high level graphics & fonts
extsub $ff20 = GRAPH_init(uword vectors @R0)  clobbers(A,X,Y)
extsub $ff23 = GRAPH_clear()  clobbers(A,X,Y)
extsub $ff26 = GRAPH_set_window(uword x @R0, uword y @R1, uword width @R2, uword height @R3)  clobbers(A,X,Y)
extsub $ff29 = GRAPH_set_colors(ubyte stroke @A, ubyte fill @X, ubyte background @Y)  clobbers (A,X,Y)
extsub $ff2c = GRAPH_draw_line(uword x1 @R0, uword y1 @R1, uword x2 @R2, uword y2 @R3)  clobbers(A,X,Y)
extsub $ff2f = GRAPH_draw_rect(uword x @R0, uword y @R1, uword width @R2, uword height @R3, uword cornerradius @R4, bool fill @Pc)  clobbers(A,X,Y)
extsub $ff32 = GRAPH_move_rect(uword sx @R0, uword sy @R1, uword tx @R2, uword ty @R3, uword width @R4, uword height @R5)  clobbers(A,X,Y)
extsub $ff35 = GRAPH_draw_oval(uword x @R0, uword y @R1, uword width @R2, uword height @R3, bool fill @Pc)  clobbers(A,X,Y)
extsub $ff38 = GRAPH_draw_image(uword x @R0, uword y @R1, uword ptr @R2, uword width @R3, uword height @R4)  clobbers(A,X,Y)
extsub $ff3b = GRAPH_set_font(uword fontptr @R0)  clobbers(A,X,Y)
extsub $ff3e = GRAPH_get_char_size(ubyte baseline @A, ubyte width @X, ubyte height_or_style @Y, bool is_control @Pc)  clobbers(A,X,Y)
extsub $ff41 = GRAPH_put_char(uword x @R0, uword y @R1, ubyte character @A)  clobbers(A,X,Y)
extsub $ff41 = GRAPH_put_next_char(ubyte character @A)  clobbers(A,X,Y)     ; alias for the routine above that doesn't reset the position of the initial character

; framebuffer
extsub $fef6 = FB_init()  clobbers(A,X,Y)
extsub $fef9 = FB_get_info()  clobbers(X,Y) -> byte @A, uword @R0, uword @R1    ; width=r0, height=r1
extsub $fefc = FB_set_palette(uword pointer @R0, ubyte index @A, ubyte colorcount @X)  clobbers(A,X,Y)      ; note: palette array must be @nosplit
extsub $feff = FB_cursor_position(uword x @R0, uword y @R1)  clobbers(A,X,Y)
extsub $ff02 = FB_cursor_next_line(uword x @R0)  clobbers(A,X,Y)
extsub $ff05 = FB_get_pixel()  clobbers(X,Y) -> ubyte @A
extsub $ff08 = FB_get_pixels(uword pointer @R0, uword count @R1)  clobbers(A,X,Y)
extsub $ff0b = FB_set_pixel(ubyte color @A)  clobbers(A,X,Y)
extsub $ff0e = FB_set_pixels(uword pointer @R0, uword count @R1)  clobbers(A,X,Y)
extsub $ff11 = FB_set_8_pixels(ubyte pattern @A, ubyte color @X)  clobbers(A,X,Y)
extsub $ff14 = FB_set_8_pixels_opaque(ubyte pattern @R0, ubyte mask @A, ubyte color1 @X, ubyte color2 @Y)  clobbers(A,X,Y)
extsub $ff17 = FB_fill_pixels(uword count @R0, uword pstep @R1, ubyte color @A)  clobbers(A,X,Y)
extsub $ff1a = FB_filter_pixels(uword pointer @ R0, uword count @R1)  clobbers(A,X,Y)
extsub $ff1d = FB_move_pixels(uword sx @R0, uword sy @R1, uword tx @R2, uword ty @R3, uword count @R4)  clobbers(A,X,Y)

; misc
extsub $fec6 = i2c_read_byte(ubyte device @X, ubyte offset @Y) clobbers (X,Y) -> ubyte @A, bool @Pc
extsub $fec9 = i2c_write_byte(ubyte device @X, ubyte offset @Y, ubyte data @A) clobbers (A,X,Y) -> bool @Pc
extsub $feb4 = i2c_batch_read(ubyte device @X, uword buffer @R0, uword length @R1, bool advance @Pc) clobbers(A,Y) -> bool @Pc
extsub $feb7 = i2c_batch_write(ubyte device @X, uword buffer @R0, uword length @R1, bool advance @Pc) clobbers(A,Y) -> bool @Pc

extsub $fef0 = sprite_set_image(uword pixels @R0, uword mask @R1, ubyte bpp @R2, ubyte number @A, ubyte width @X, ubyte height @Y, bool apply_mask @Pc)  clobbers(A,X,Y) -> bool @Pc
extsub $fef3 = sprite_set_position(uword x @R0, uword y @R1, ubyte number @A)  clobbers(A,X,Y)
extsub $fee4 = memory_fill(uword address @R0, uword num_bytes @R1, ubyte value @A)  clobbers(A,X,Y)
extsub $fee7 = memory_copy(uword source @R0, uword target @R1, uword num_bytes @R2)  clobbers(A,X,Y)
extsub $feea = memory_crc(uword address @R0, uword num_bytes @R1)  clobbers(A,X,Y) -> uword @R2
extsub $feed = memory_decompress(uword input @R0, uword output @R1)  clobbers(A,X,Y) -> uword @R1       ; last address +1 is result in R1
extsub $fedb = console_init(uword x @R0, uword y @R1, uword width @R2, uword height @R3)  clobbers(A,X,Y)
extsub $fede = console_put_char(ubyte character @A, bool wrapping @Pc)  clobbers(A,X,Y)
extsub $fee1 = console_get_char()  clobbers(X,Y) -> ubyte @A
extsub $fed8 = console_put_image(uword pointer @R0, uword width @R1, uword height @R2)  clobbers(A,X,Y)
extsub $fed5 = console_set_paging_message(uword msgptr @R0)  clobbers(A,X,Y)
extsub $fecf = entropy_get() -> ubyte @A, ubyte @X, ubyte @Y
;; extsub $fea8 = extapi16(ubyte callnumber @A) clobbers (A,X,Y)    ; not useful yet because is for 65816 cpu
extsub $feab = extapi(ubyte callnumber @A) clobbers (A,X,Y)
extsub $fecc = monitor()  clobbers(A,X,Y)

extsub $ff44 = MACPTR(ubyte length @A, uword buffer @XY, bool dontAdvance @Pc)  clobbers(A) -> bool @Pc, uword @XY
extsub $feb1 = MCIOUT(ubyte length @A, uword buffer @XY, bool dontAdvance @Pc)  clobbers(A) -> bool @Pc, uword @XY
extsub $FEBA = BSAVE(ubyte zp_startaddr @ A, uword endaddr @ XY) clobbers (X, Y) -> bool @ Pc, ubyte @ A      ; like cbm.SAVE, but omits the 2-byte prg header
extsub $ff47 = enter_basic(bool cold_or_warm @Pc)  clobbers(A,X,Y)
extsub $ff4d = clock_set_date_time(uword yearmonth @R0, uword dayhours @R1, uword minsecs @R2, uword jiffiesweekday @R3)  clobbers(A, X, Y)
extsub $ff50 = clock_get_date_time()  clobbers(A, X, Y)  -> uword @R0, uword @R1, uword @R2, uword @R3   ; result registers see clock_set_date_time()

; keyboard, mouse, joystick
; note: also see the cbm.kbdbuf_clear() helper routine
extsub $febd = kbdbuf_peek() -> ubyte @A, ubyte @X     ; key in A, queue length in X
extsub $fec0 = kbdbuf_get_modifiers() -> ubyte @A
extsub $fec3 = kbdbuf_put(ubyte key @A) clobbers(X)
extsub $fed2 = keymap(uword identifier @XY, bool read @Pc) -> bool @Pc
extsub $ff68 = mouse_config(byte shape @A, ubyte resX @X, ubyte resY @Y)  clobbers (A, X, Y)
extsub $ff6b = mouse_get(ubyte zdataptr @X) -> ubyte @A, byte @X    ;  use mouse_pos() instead
extsub $ff71 = mouse_scan()  clobbers(A, X, Y)
extsub $ff53 = joystick_scan()  clobbers(A, X, Y)
extsub $ff56 = joystick_get(ubyte joynr @A) -> uword @AX, bool @Y   ; note: everything is inverted even the boolean present flag.  Also see detect_joysticks() and get_all_joysticks()

; X16Edit (rom bank 13/14 but you ideally should use the routine search_x16edit() to search for the correct bank)
extsub $C000 = x16edit_default() clobbers(A,X,Y)
extsub $C003 = x16edit_loadfile(ubyte firstbank @X, ubyte lastbank @Y, str filename @R0, ubyte filenameLength @R1) clobbers(A,X,Y)
extsub $C006 = x16edit_loadfile_options(ubyte firstbank @X, ubyte lastbank @Y, str filename @R0,
                uword filenameLengthAndOptions @R1, uword tabstopAndWordwrap @R2,
                uword disknumberAndColors @R3, uword headerAndStatusColors @R4) clobbers(A,X,Y)

; Audio (rom bank 10)
; NOTE: because these are auto-banked, you should not call them from an IRQ handler routine (due to jsrfar race condition).
extsub @bank 10  $C09F = audio_init() clobbers(A,X,Y) -> bool @Pc     ; (re)initialize both vera PSG and YM audio chips
extsub @bank 10  $C000 = bas_fmfreq(ubyte channel @A, uword freq @XY, bool noretrigger @Pc) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C003 = bas_fmnote(ubyte channel @A, ubyte note @X, ubyte fracsemitone @Y, bool noretrigger @Pc) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C006 = bas_fmplaystring(ubyte length @A, str string @XY) clobbers(A,X,Y)
extsub @bank 10  $C009 = bas_fmvib(ubyte speed @A, ubyte depth @X) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C00C = bas_playstringvoice(ubyte channel @A) clobbers(Y)
extsub @bank 10  $C00F = bas_psgfreq(ubyte voice @A, uword freq @XY) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C012 = bas_psgnote(ubyte voice @A, ubyte note @X, ubyte fracsemitone @Y) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C015 = bas_psgwav(ubyte voice @A, ubyte waveform @X) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C018 = bas_psgplaystring(ubyte length @A, str string @XY) clobbers(A,X,Y)
extsub @bank 10  $C08D = bas_fmchordstring(ubyte length @A, str string @XY) clobbers(A,X,Y)
extsub @bank 10  $C090 = bas_psgchordstring(ubyte length @A, str string @XY) clobbers(A,X,Y)
extsub @bank 10  $C01B = notecon_bas2fm(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc
extsub @bank 10  $C01E = notecon_bas2midi(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc
extsub @bank 10  $C021 = notecon_bas2psg(ubyte note @X, ubyte fracsemitone @Y) clobbers(A) -> uword @XY, bool @Pc
extsub @bank 10  $C024 = notecon_fm2bas(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc
extsub @bank 10  $C027 = notecon_fm2midi(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc
extsub @bank 10  $C02A = notecon_fm2psg(ubyte note @X, ubyte fracsemitone @Y) clobbers(A) -> uword @XY, bool @Pc
extsub @bank 10  $C02D = notecon_freq2bas(uword freqHz @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc
extsub @bank 10  $C030 = notecon_freq2fm(uword freqHz @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc
extsub @bank 10  $C033 = notecon_freq2midi(uword freqHz @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc
extsub @bank 10  $C036 = notecon_freq2psg(uword freqHz @XY) clobbers(A) -> uword @XY, bool @Pc
extsub @bank 10  $C039 = notecon_midi2bas(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc
extsub @bank 10  $C03C = notecon_midi2fm(ubyte note @X) clobbers(A) -> ubyte @X, bool @Pc
extsub @bank 10  $C03F = notecon_midi2psg(ubyte note @X, ubyte fracsemitone @Y) clobbers(A) -> uword @XY, bool @Pc
extsub @bank 10  $C042 = notecon_psg2bas(uword freq @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc
extsub @bank 10  $C045 = notecon_psg2fm(uword freq @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc
extsub @bank 10  $C048 = notecon_psg2midi(uword freq @XY) clobbers(A) -> ubyte @X, ubyte @Y, bool @Pc
extsub @bank 10  $C04B = psg_init() clobbers(A,X,Y)               ; (re)init Vera PSG
extsub @bank 10  $C04E = psg_playfreq(ubyte voice @A, uword freq @XY) clobbers(A,X,Y)
extsub @bank 10  $C051 = psg_read(ubyte offset @X, bool cookedVol @Pc) clobbers(Y) -> ubyte @A
extsub @bank 10  $C054 = psg_setatten(ubyte voice @A, ubyte attenuation @X) clobbers(A,X,Y)
extsub @bank 10  $C057 = psg_setfreq(ubyte voice @A, uword freq @XY) clobbers(A,X,Y)
extsub @bank 10  $C05A = psg_setpan(ubyte voice @A, ubyte panning @X) clobbers(A,X,Y)
extsub @bank 10  $C05D = psg_setvol(ubyte voice @A, ubyte volume @X) clobbers(A,X,Y)
extsub @bank 10  $C060 = psg_write(ubyte value @A, ubyte offset @X) clobbers(Y)
extsub @bank 10  $C0A2 = psg_write_fast(ubyte value @A, ubyte offset @X) clobbers(Y)
extsub @bank 10  $C093 = psg_getatten(ubyte voice @A) clobbers(Y) -> ubyte @X
extsub @bank 10  $C096 = psg_getpan(ubyte voice @A) clobbers(Y) -> ubyte @X
extsub @bank 10  $C063 = ym_init() clobbers(A,X,Y) -> bool @Pc              ; (re)init YM chip
extsub @bank 10  $C066 = ym_loaddefpatches() clobbers(A,X,Y) -> bool @Pc    ; load default YM patches
extsub @bank 10  $C069 = ym_loadpatch(ubyte channel @A, uword patchOrAddress @XY, bool what @Pc) clobbers(A,X,Y)
extsub @bank 10  $C06C = ym_loadpatchlfn(ubyte channel @A, ubyte lfn @X) clobbers(X,Y) -> ubyte @A, bool @Pc
extsub @bank 10  $C06F = ym_playdrum(ubyte channel @A, ubyte note @X) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C072 = ym_playnote(ubyte channel @A, ubyte kc @X, ubyte kf @Y, bool notrigger @Pc) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C075 = ym_setatten(ubyte channel @A, ubyte attenuation @X) clobbers(Y) -> bool @Pc
extsub @bank 10  $C078 = ym_setdrum(ubyte channel @A, ubyte note @X) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C07B = ym_setnote(ubyte channel @A, ubyte kc @X, ubyte kf @Y) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C07E = ym_setpan(ubyte channel @A, ubyte panning @X) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C081 = ym_read(ubyte register @X, bool cooked @Pc) clobbers(Y) -> ubyte @A, bool @Pc
extsub @bank 10  $C084 = ym_release(ubyte channel @A) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C087 = ym_trigger(ubyte channel @A, bool noRelease @Pc) clobbers(A,X,Y) -> bool @Pc
extsub @bank 10  $C08A = ym_write(ubyte value @A, ubyte register @X) clobbers(Y) -> bool @Pc
extsub @bank 10  $C099 = ym_getatten(ubyte channel @A) clobbers(Y) -> ubyte @X
extsub @bank 10  $C09C = ym_getpan(ubyte channel @A) clobbers(Y) -> ubyte @X
extsub @bank 10  $C0A5 = ym_get_chip_type() clobbers(X) -> ubyte @A

; extapi call numbers
const ubyte  EXTAPI_clear_status = $01
const ubyte  EXTAPI_getlfs = $02
const ubyte  EXTAPI_mouse_sprite_offset = $03
const ubyte  EXTAPI_joystick_ps2_keycodes = $04
const ubyte  EXTAPI_iso_cursor_char = $05
const ubyte  EXTAPI_ps2kbd_typematic = $06
const ubyte  EXTAPI_pfkey = $07
const ubyte  EXTAPI_ps2data_fetch = $08
const ubyte  EXTAPI_ps2data_raw = $09
const ubyte  EXTAPI_cursor_blink = $0A
const ubyte  EXTAPI_led_update = $0B
const ubyte  EXTAPI_mouse_set_position = $0C
const ubyte  EXTAPI_scnsiz = $0D
const ubyte  EXTAPI_kbd_leds = $0E
const ubyte  EXTAPI_memory_decompress_from_func = $0F
const ubyte  EXTAPI_default_palette = $10

; extapi16 call numbers
const ubyte  EXTAPI16_test = $00
const ubyte  EXTAPI16_stack_push = $01
const ubyte  EXTAPI16_stack_pop = $02
const ubyte  EXTAPI16_stack_enter_kernal_stack = $03
const ubyte  EXTAPI16_stack_leave_kernal_stack = $04


asmsub set_screen_mode(ubyte mode @A) clobbers(A,X,Y) {
    ; -- convenience wrapper for screen_mode() to just set a new mode and ignore any return values
    %asm {{
        clc
        jmp  screen_mode
    }}
}

asmsub get_screen_mode() -> ubyte @A, ubyte @X, ubyte @Y {
    ; -- convenience wrapper for screen_mode() to just get the current mode in A, and size in characters in X (width) and Y (height)
    ;    Note: you can also just do the SEC yourself and simply call screen_mode() directly,
    ;          or use the existing SCREEN kernal routine for just getting the size in characters.
    %asm {{
        sec
        jmp  screen_mode
    }}
}

asmsub mouse_config2(byte shape @A) clobbers (A, X, Y) {
    ; -- convenience wrapper function that handles the screen resolution for mouse_config() for you
    %asm {{
        pha                         ; save shape
        sec
        jsr  cx16.screen_mode       ; set current screen mode and res in A, X, Y
        pla                         ; get shape back
        jmp  cx16.mouse_config
    }}
}

asmsub mouse_pos() -> ubyte @A, uword @R0, uword @R1, byte @X {
    ; -- short wrapper around mouse_get() kernal routine:
    ; -- gets the position of the mouse cursor in cx16.r0 and cx16.r1 (x/y coordinate), returns mouse button status in A, scroll wheel in X.
    ;    Note: mouse pointer needs to be enabled for this to do anything.
    %asm {{
        ldx  #cx16.r0
        jmp  cx16.mouse_get
    }}
}

sub mouse_present() -> bool {
    ; -- check if a mouse is connected to the machine
    cx16.r0L, void = cx16.i2c_read_byte($42, $22)         ; $22 = I2C_GET_MOUSE_DEVICE_ID
    if_cs
        return false
    return cx16.r0L != $fc       ; $fc = BAT_FAIL
}

; shims for the kernal routines called via the extapi call:

asmsub mouse_set_pos(uword xpos @R0, uword ypos @R1) clobbers(X) {
    ; -- sets the mouse sprite position
    ;    Note: mouse pointer needs to be enabled for this to do anything.
    %asm {{
        ldx  #cx16.r0L
        lda  #EXTAPI_mouse_set_position
        jmp  cx16.extapi
    }}
}

asmsub mouse_set_sprite_offset(word xoffset @R0, word yoffset @R1) clobbers(A,X,Y) {
    %asm {{
        clc
        lda  #EXTAPI_mouse_sprite_offset
        jmp  cx16.extapi
    }}
}

asmsub mouse_get_sprite_offset() clobbers(A,X,Y) -> word @R0, word @R1 {
    %asm {{
        sec
        lda  #EXTAPI_mouse_sprite_offset
        jmp  cx16.extapi
    }}
}

asmsub getlfs() -> ubyte @X, ubyte @A, ubyte @Y {
    ; -- return the result of the last call to SETLFS:  A=logical, X=device, Y=secondary.
    %asm {{
        lda  #EXTAPI_mouse_set_position
        jmp  cx16.extapi
    }}
}

asmsub iso_cursor_char(ubyte character @X) clobbers(A,X,Y) {
    ; -- set the screen code for the cursor character in ISO mode (the default is $9f).
    %asm {{
        clc
        lda  #EXTAPI_iso_cursor_char
        jmp  cx16.extapi
    }}
}

asmsub scnsiz(ubyte width @X, ubyte heigth @Y) clobbers(A,X,Y) {
    ; -- sets the screen editor size dimensions (without changing the graphical screen mode itself)
    ;    (rom R48+)
    %asm {{
        lda  #EXTAPI_scnsiz
        jmp  cx16.extapi
    }}
}

asmsub memory_decompress_from_func(uword datafunction @R4, uword output @R1)  clobbers(A,X,Y) -> uword @R1  {
    ; requires rom v49+
    %asm {{
        lda  #EXTAPI_memory_decompress_from_func
        jmp  cx16.extapi
    }}
}

asmsub get_default_palette() -> ubyte @A, uword @XY {
    ; returns memory address of default palette; bank in A and address in XY
    ; requires rom v49+
    %asm {{
        sec
        lda  #EXTAPI_default_palette
        jmp  cx16.extapi
    }}
}

asmsub set_default_palette() {
    ; sets default palette in to the Vera
    ; requires rom v49+
    %asm {{
        clc
        lda  #EXTAPI_default_palette
        jmp  cx16.extapi
    }}
}

asmsub get_charset() -> ubyte @A {
    ;Get charset mode.  result: 0=unknown, 1=ISO, 2=PETSCII upper case/gfx, 3=PETSCII lowercase.
    %asm {{

KERNAL_MODE = $0372         ; internal kernal variable, risky to read it, but it's ben stable for many releases.
        lda  KERNAL_MODE
        beq  _end

        bit  #$40                ;ISO mode flag
        beq  +                   ;usually KERNAL_MODE 1 or 6 (| $40)
        lda  #1
        bra  _end

+       bit  #1                  ;PETSCII upper case/graphics
        bne  +                   ;usually KERNAL_MODE 2 or 4
        lda  #2
        bra  _end

+       lda  #3                   ;PETSCII upper/lower case
_end:
        rts
    }}
}

; TODO : implement shims for the remaining extapi calls.


; ---- end of kernal routines ----


; ---- utilities -----

inline asmsub rombank(ubyte bank @A) {
    ; -- set the rom banks
    %asm {{
        sta  $01
    }}
}

inline asmsub rambank(ubyte bank @A) {
    ; -- set the ram bank
    %asm {{
        sta  $00
    }}
}

inline asmsub getrombank() -> ubyte @A {
    ; -- get the current rom bank
    %asm {{
        lda  $01
    }}
}

inline asmsub getrambank() -> ubyte @A {
    ; -- get the current RAM bank
    %asm {{
        lda  $00
    }}
}

inline asmsub push_rombank(ubyte newbank @A) clobbers(Y) {
    ; push the current rombank on the stack and makes the given rom bank active
    ; combined with pop_rombank() makes for easy temporary rom bank switch
    %asm {{
        ldy  $01
        phy
        sta  $01
    }}
}

inline asmsub pop_rombank() {
    ; sets the current rom bank back to what was stored previously on the stack
    %asm {{
        pla
        sta  $01
    }}
}

inline asmsub push_rambank(ubyte newbank @A) clobbers(Y) {
    ; push the current hiram bank on the stack and makes the given hiram bank active
    ; combined with pop_rombank() makes for easy temporary hiram bank switch
    %asm {{
        ldy  $00
        phy
        sta  $00
    }}
}

inline asmsub pop_rambank() {
    ; sets the current hiram bank back to what was stored previously on the stack
    %asm {{
        pla
        sta  $00
    }}
}

asmsub numbanks() clobbers(X) -> uword @AY {
    ; -- Returns the number of available RAM banks according to the kernal (each bank is 8 Kb).
    ;    Note that the number of such banks can be 256 so a word is returned.
    ;    But just looking at the A register (the LSB of the result word) could suffice if you know that A=0 means 256 banks:
    ;    The maximum number of RAM banks in the X16 is currently 256 (2 Megabytes of banked RAM).
    ;    Kernal's MEMTOP routine reports 0 in this case but that doesn't mean 'zero banks', instead it means 256 banks,
    ;    as there is no X16 without at least 1 page of banked RAM. So this routine returns 256 instead of 0.
    %asm {{
        sec
        jsr  cbm.MEMTOP
        ldy  #0
        cmp  #0
        bne  +
        iny
+       rts
    }}
}

asmsub vpeek(ubyte bank @A, uword address @XY) -> ubyte @A {
        ; -- get a byte from VERA's video memory
        ;    note: inefficient when reading multiple sequential bytes!
        %asm {{
                stz  cx16.VERA_CTRL
                sta  cx16.VERA_ADDR_H
                sty  cx16.VERA_ADDR_M
                stx  cx16.VERA_ADDR_L
                lda  cx16.VERA_DATA0
                rts
            }}
}

asmsub vaddr(ubyte bank @A, uword address @R0, ubyte addrsel @R1, byte autoIncrOrDecrByOne @Y) clobbers(A) {
        ; -- setup the VERA's data address register 0 or 1 with optional auto increment or decrement of 1.
        ;    This is a convenience routine, and not very efficient if you call it often;
        ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
        ;    Note that the vaddr_autoincr() and vaddr_autodecr() routines allow to set all possible strides, not just 1.
        ;    Note also that Vera's addrset is reset to 0 on exit, even if you set port #1's address.
        %asm {{
            pha
            lda  cx16.r1
            and  #1
            sta  cx16.VERA_CTRL
            lda  cx16.r0
            sta  cx16.VERA_ADDR_L
            lda  cx16.r0+1
            sta  cx16.VERA_ADDR_M
            pla
            cpy  #0
            bmi  ++
            beq  +
            ora  #%00010000
+           sta  cx16.VERA_ADDR_H
            stz  cx16.VERA_CTRL
            rts
+           ora  #%00011000
            sta  cx16.VERA_ADDR_H
            stz  cx16.VERA_CTRL
            rts
        }}
}

asmsub vaddr_clone(ubyte port @A) clobbers (A,X,Y) {
    ; -- clones Vera addresses from the given source port to the other one.
    ;    This is a convenience routine, and not very efficient if you call it often;
    ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
    %asm {{
        sta  VERA_CTRL
        ldx  VERA_ADDR_L
        ldy  VERA_ADDR_H
        phy
        ldy  VERA_ADDR_M
        eor  #1
        sta  VERA_CTRL
        stx  VERA_ADDR_L
        sty  VERA_ADDR_M
        ply
        sty  VERA_ADDR_H
        eor  #1
        stz  VERA_CTRL
        rts
    }}
}

asmsub vaddr_autoincr(ubyte bank @A, uword address @R0, ubyte addrsel @R1, uword autoIncrAmount @R2) clobbers(A,Y) {
        ; -- setup the VERA's data address register 0 or 1, including setting up optional auto increment amount.
        ;    Specifiying an unsupported amount results in amount of zero. See the Vera docs about what amounts are possible.
        ;    This is a convenience routine, and not very efficient if you call it often;
        ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
        %asm {{
            jsr  _setup
            lda  cx16.r2H
            ora  cx16.r2L
            beq  +
            jsr  _determine_incr_bits
+           ora  P8ZP_SCRATCH_REG
            sta  cx16.VERA_ADDR_H
            stz  cx16.VERA_CTRL
            rts

_setup      sta  P8ZP_SCRATCH_REG
            lda  cx16.r1
            and  #1
            sta  cx16.VERA_CTRL
            lda  cx16.r0
            sta  cx16.VERA_ADDR_L
            lda  cx16.r0+1
            sta  cx16.VERA_ADDR_M
            rts

_determine_incr_bits
            lda  cx16.r2H
            bne  _large
            lda  cx16.r2L
            ldy  #13
-           cmp  _strides_lsb,y
            beq  +
            dey
            bpl  -
+           tya
            asl  a
            asl  a
            asl  a
            asl  a
            rts
_large      ora  cx16.r2L
            cmp  #1         ; 256
            bne  +
            lda  #9<<4
            rts
+           cmp  #2         ; 512
            bne  +
            lda  #10<<4
            rts
+           cmp  #65        ; 320
            bne  +
            lda  #14<<4
            rts
+           cmp  #130       ; 640
            bne  +
            lda  #15<<4
            rts
+           lda  #0
            rts
_strides_lsb    .byte   0,1,2,4,8,16,32,64,128,255,255,40,80,160,255,255
            ; !notreached!
        }}
}

asmsub vaddr_autodecr(ubyte bank @A, uword address @R0, ubyte addrsel @R1, uword autoDecrAmount @R2) clobbers(A,Y) {
        ; -- setup the VERA's data address register 0 or 1 including setting up optional auto decrement amount.
        ;    Specifiying an unsupported amount results in amount of zero. See the Vera docs about what amounts are possible.
        ;    This is a convenience routine, and not very efficient if you call it often;
        ;    it's usually better to write a tailor made version of it that accounts for the repeated values.
        %asm {{
            jsr  vaddr_autoincr._setup
            lda  cx16.r2H
            ora  cx16.r2L
            beq  +
            jsr  vaddr_autoincr._determine_incr_bits
            ora  #%00001000         ; autodecrement
+           ora  P8ZP_SCRATCH_REG
            sta  cx16.VERA_ADDR_H
            stz  cx16.VERA_CTRL
            rts
        }}
}

asmsub vpoke(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers(A) {
    ; -- write a single byte to VERA's video memory
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        stz  cx16.VERA_CTRL
        sta  cx16.VERA_ADDR_H
        lda  cx16.r0
        sta  cx16.VERA_ADDR_L
        lda  cx16.r0+1
        sta  cx16.VERA_ADDR_M
        sty  cx16.VERA_DATA0
        rts
    }}
}

asmsub vpoke_or(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers (A) {
    ; -- or a single byte to the value already in the VERA's video memory at that location
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        stz  cx16.VERA_CTRL
        sta  cx16.VERA_ADDR_H
        lda  cx16.r0
        sta  cx16.VERA_ADDR_L
        lda  cx16.r0+1
        sta  cx16.VERA_ADDR_M
        tya
        ora  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        rts
    }}
}

asmsub vpoke_and(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers(A) {
    ; -- and a single byte to the value already in the VERA's video memory at that location
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        stz  cx16.VERA_CTRL
        sta  cx16.VERA_ADDR_H
        lda  cx16.r0
        sta  cx16.VERA_ADDR_L
        lda  cx16.r0+1
        sta  cx16.VERA_ADDR_M
        tya
        and  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        rts
    }}
}

asmsub vpoke_xor(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers (A) {
    ; -- xor a single byte to the value already in the VERA's video memory at that location
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        stz  cx16.VERA_CTRL
        sta  cx16.VERA_ADDR_H
        lda  cx16.r0
        sta  cx16.VERA_ADDR_L
        lda  cx16.r0+1
        sta  cx16.VERA_ADDR_M
        tya
        eor  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        rts
    }}
}

asmsub vpoke_mask(ubyte bank @A, uword address @R0, ubyte mask @X, ubyte value @Y) clobbers (A) {
    ; -- bitwise or a single byte to the value already in the VERA's video memory at that location
    ;    after applying the and-mask. Note: inefficient when writing multiple sequential bytes!
    %asm {{
        sty  P8ZP_SCRATCH_B1
        stz  cx16.VERA_CTRL
        sta  cx16.VERA_ADDR_H
        lda  cx16.r0
        sta  cx16.VERA_ADDR_L
        lda  cx16.r0+1
        sta  cx16.VERA_ADDR_M
        txa
        and  cx16.VERA_DATA0
        ora  P8ZP_SCRATCH_B1
        sta  cx16.VERA_DATA0
        rts
    }}
}

asmsub save_virtual_registers() clobbers(A,Y) {
    %asm {{
        ldy  #31
-       lda  cx16.r0,y
        sta  _cx16_vreg_storage,y
        dey
        bpl  -
        rts

        .section BSS
_cx16_vreg_storage
        .word ?,?,?,?,?,?,?,?
        .word ?,?,?,?,?,?,?,?
        .send BSS
        ; !notreached!
    }}
}

asmsub restore_virtual_registers() clobbers(A,Y) {
    %asm {{
        ldy  #31
-       lda  save_virtual_registers._cx16_vreg_storage,y
        sta  cx16.r0,y
        dey
        bpl  -
        rts
    }}
}


asmsub save_vera_context() clobbers(A) {
    ; -- use this at the start of your IRQ handler if it uses Vera registers, to save the state
    %asm {{
        ; note cannot store this on cpu hardware stack because this gets called as a subroutine
        lda  cx16.VERA_ADDR_L
        sta  _vera_storage
        lda  cx16.VERA_ADDR_M
        sta  _vera_storage+1
        lda  cx16.VERA_ADDR_H
        sta  _vera_storage+2
        lda  cx16.VERA_CTRL
        sta  _vera_storage+3
        eor  #1
        sta  _vera_storage+7
        sta  cx16.VERA_CTRL
        lda  cx16.VERA_ADDR_L
        sta  _vera_storage+4
        lda  cx16.VERA_ADDR_M
        sta  _vera_storage+5
        lda  cx16.VERA_ADDR_H
        sta  _vera_storage+6
        rts
        .section BSS
_vera_storage:  .byte ?,?,?,?,?,?,?,?
        .send BSS
        ; !notreached!
    }}
}

asmsub restore_vera_context() clobbers(A) {
    ; -- use this at the end of your IRQ handler if it uses Vera registers, to restore the state
    %asm {{
        lda  cx16.save_vera_context._vera_storage+7
        sta  cx16.VERA_CTRL
        lda  cx16.save_vera_context._vera_storage+6
        sta  cx16.VERA_ADDR_H
        lda  cx16.save_vera_context._vera_storage+5
        sta  cx16.VERA_ADDR_M
        lda  cx16.save_vera_context._vera_storage+4
        sta  cx16.VERA_ADDR_L
        lda  cx16.save_vera_context._vera_storage+3
        sta  cx16.VERA_CTRL
        lda  cx16.save_vera_context._vera_storage+2
        sta  cx16.VERA_ADDR_H
        lda  cx16.save_vera_context._vera_storage+1
        sta  cx16.VERA_ADDR_M
        lda  cx16.save_vera_context._vera_storage+0
        sta  cx16.VERA_ADDR_L
        rts
    }}
}


    asmsub set_chrin_keyhandler(ubyte handlerbank @A, uword handler @XY) clobbers(A) {
        ; Install a custom CHRIN (BASIN) key handler in a safe manner. Call this before each line you want to read.
        ; See https://github.com/X16Community/x16-docs/blob/101759f3bfa5e6cce4e8c5a0b67cb0f2f1c6341e/X16%20Reference%20-%2003%20-%20Editor.md#custom-basin-petscii-code-override-handler
        %asm {{
            sei
            sta  P8ZP_SCRATCH_REG
            lda  $00
            pha
            stz  $00
            lda  P8ZP_SCRATCH_REG
            sta  cx16.edkeybk
            stx  cx16.edkeyvec
            sty  cx16.edkeyvec+1
            pla
            sta  $00
            cli
            rts
        }}
    }

    asmsub get_chrin_keyhandler() -> ubyte @R0, uword @R1 {
        ; --- retrieve the currently set CHRIN keyhandler in a safe manner, bank in r0L, handler address in R1.
        %asm {{
            sei
            lda  $00
            pha
            stz  $00
            lda  cx16.edkeybk
            sta  cx16.r0L
            lda  cx16.edkeyvec
            ldy  cx16.edkeyvec+1
            sta  cx16.r1
            sty  cx16.r1+1
            pla
            sta  $00
            cli
            rts
        }}
    }

    sub joysticks_detect() -> ubyte {
        ; returns bits 0-4,  set to 1 if that joystick is present.
        ; bit 0 = keyboard joystick, bit 1 - 4 = joypads 1 to 4
        cx16.r0H = 255
        for cx16.r0L in 4 downto 0 {
            void cx16.joystick_get(cx16.r0L)
            %asm {{
                cpy  #1     ; present?
            }}
            rol(cx16.r0H)
        }
        return ~cx16.r0H
    }

    sub joysticks_getall(bool also_keyboard_js) -> uword {
        ; returns combined pressed buttons from all connected joysticks
        ; note: returns the 'normal' not inverted status bits for the buttons (1 = button pressed.)
        cx16.r0H = 1
        if also_keyboard_js
            cx16.r0H = 0
        cx16.r1 = $ffff
        for cx16.r0L in cx16.r0H to 4 {
            bool notpresent
            cx16.r2, notpresent = cx16.joystick_get(cx16.r0L)
            if not notpresent {
                cx16.r1 &= cx16.r2
            }
        }
        return ~cx16.r1
    }


; Commander X16 IRQ dispatcher routines

inline asmsub  disable_irqs() clobbers(A) {
    ; Disable all Vera IRQ sources. Note that it does NOT set the CPU IRQ disabled status bit!
    %asm {{
        lda  #%00001111
        trb  cx16.VERA_IEN
    }}
}

asmsub  enable_irq_handlers(bool disable_all_irq_sources @Pc) clobbers(A,Y)  {
    ; Install the "master IRQ handler" that will dispatch IRQs
    ; to the registered handler for each type.  (Only Vera IRQs supported for now).
    ; The handlers don't need to clear its ISR bit, but have to return 0 or 1 in A,
    ; where 1 means: continue with the system IRQ handler, 0 means: don't call that.
	%asm {{
        php
        sei
        bcc  +
        lda  #%00001111
        trb  cx16.VERA_IEN      ; disable all IRQ sources
+       lda  #<_irq_dispatcher
        ldy  #>_irq_dispatcher
        sta  cbm.CINV
        sty  cbm.CINV+1

		lda  #<_default_1_handler
		ldy  #>_default_1_handler
		sta  _vsync_vec
		sty  _vsync_vec+1
		lda  #<_default_0_handler
		ldy  #>_default_0_handler
		sta  _line_vec
		sty  _line_vec+1
		sta  _aflow_vec
		sty  _aflow_vec+1
		sta  _sprcol_vec
		sty  _sprcol_vec+1

        plp
        rts

        .section BSS
_vsync_vec   .word  ?
_line_vec    .word  ?
_aflow_vec   .word  ?
_sprcol_vec  .word  ?
_continue_with_system_handler   .byte  ?
        .send BSS

_irq_dispatcher
        ; order of handling: LINE, SPRCOL, AFLOW, VSYNC.
        jsr  sys.save_prog8_internals
        cld
        lda  cx16.VERA_ISR
        and  cx16.VERA_IEN          ; only consider the bits for sources that can actually raise the IRQ
        sta  cx16.VERA_ISR          ; note: AFLOW can only be cleared by filling the audio FIFO for at least 1/4. Not via the ISR bit.

        stz  _continue_with_system_handler

        bit  #2         ; make sure to test for LINE IRQ first to handle that as soon as we can
        beq  +
        pha
        jsr  _line_handler
        tsb  _continue_with_system_handler
        pla

+       lsr  a
        bcc  +
        pha
        jsr  _vsync_handler
        tsb  _continue_with_system_handler
        pla

+       lsr  a
        lsr  a
        bcc  +
        pha
        jsr  _sprcol_handler
        tsb  _continue_with_system_handler
        pla

+       lsr  a
        bcc  +
        jsr  _aflow_handler
        tsb  _continue_with_system_handler

+       jsr  sys.restore_prog8_internals
        lda  _continue_with_system_handler
        beq  _no_sys_handler
		jmp  (sys.restore_irq._orig_irqvec)   ; continue with normal kernal irq routine
_no_sys_handler
		ply
		plx
		pla
		rti

_default_0_handler
        lda  #0
        rts
_default_1_handler
        lda  #1
        rts

_vsync_handler
        jmp  (_vsync_vec)
_line_handler
        jmp  (_line_vec)
_sprcol_handler
        jmp  (_sprcol_vec)
_aflow_handler
        jmp  (_aflow_vec)
    }}
}

asmsub set_vsync_irq_handler(uword address @AY) clobbers(A) {
    ; Sets the VSYNC irq handler to use with enable_irq_handlers().  Also enables VSYNC irqs.
    %asm {{
        php
        sei
        sta  enable_irq_handlers._vsync_vec
        sty  enable_irq_handlers._vsync_vec+1
        lda  #1
        tsb  cx16.VERA_IEN
        plp
        rts
    }}
}

asmsub set_line_irq_handler(uword rasterline @R0, uword address @AY) clobbers(A,Y) {
    ; Sets the LINE irq handler to use with enable_irq_handlers(), for the given rasterline.  Also enables LINE irqs.
    ; You can use sys.set_rasterline() later to adjust the rasterline on which to trigger.
    %asm {{
        php
        sei
        sta  enable_irq_handlers._line_vec
        sty  enable_irq_handlers._line_vec+1
        lda  cx16.r0
        ldy  cx16.r0+1
        jsr  sys.set_rasterline
        lda  #2
        tsb  cx16.VERA_IEN
        plp
        rts
    }}
}

asmsub set_sprcol_irq_handler(uword address @AY) clobbers(A) {
    ; Sets the SPRCOL irq handler to use with enable_irq_handlers().  Also enables SPRCOL irqs.
    %asm {{
        php
        sei
        sta  enable_irq_handlers._sprcol_vec
        sty  enable_irq_handlers._sprcol_vec+1
        lda  #4
        tsb  cx16.VERA_IEN
        plp
        rts
    }}
}

asmsub set_aflow_irq_handler(uword address @AY) clobbers(A) {
    ; Sets the AFLOW irq handler to use with enable_irq_handlers().  Also enables AFLOW irqs.
    ; NOTE: the handler itself must fill the audio fifo buffer to at least 25% full again (1 KB) or the aflow irq will keep triggering!
    %asm {{
        php
        sei
        sta  enable_irq_handlers._aflow_vec
        sty  enable_irq_handlers._aflow_vec+1
        lda  #8
        tsb  cx16.VERA_IEN
        plp
        rts
    }}
}


inline asmsub  disable_irq_handlers() {
    ; back to the system default IRQ handler.
    %asm {{
        jsr  sys.restore_irq
    }}
}


sub search_x16edit() -> ubyte {
    ; -- Search the rom bank that contains x16edit. Returns bank number, or 255 if not found.
    cx16.r0L = cx16.getrombank()
    sys.set_irqd()
    str @shared signature = petscii:"x16edit"
    for cx16.r1L in 31 downto 0  {
        cx16.rombank(cx16.r1L)
        %asm {{
            ldy  #0
-           lda  signature,y
            cmp  $fff0,y
            bne  +
            iny
            cpy #7
            bne  -
            sec
            bcs  ++
+           clc
+
        }}
        if_cs {
            cx16.rombank(cx16.r0L)
            sys.clear_irqd()
            return cx16.r1L
        }
    }
    sys.clear_irqd()
    return 255
}

    sub set_program_args(str args_ptr, ubyte args_size) {
        ; -- Set the inter-program arguments.
        ; standardized way to pass arguments between programs is in ram bank 0, address $bf00-$bfff.
        ; see https://github.com/X16Community/x16-docs/blob/101759f3bfa5e6cce4e8c5a0b67cb0f2f1c6341e/X16%20Reference%20-%2008%20-%20Memory%20Map.md#bank-0
        sys.push(getrambank())
        rambank(0)
        sys.memcopy(args_ptr, $bf00, args_size)
        if args_size<255
            @($bf00+args_size) = 0
        rambank(sys.pop())
    }

    asmsub get_program_args(^^ubyte buffer @R0, ubyte buf_size @R1, bool binary @Pc) {
        ; -- Retrieve the inter-program arguments. If binary=false, it treats them as a string (stops copying at first zero).
        ; standardized way to pass arguments between programs is in ram bank 0, address $bf00-$bfff.
        ; see https://github.com/X16Community/x16-docs/blob/101759f3bfa5e6cce4e8c5a0b67cb0f2f1c6341e/X16%20Reference%20-%2008%20-%20Memory%20Map.md#bank-0
        %asm {{
            lda  #0
            rol  a
            sta  P8ZP_SCRATCH_REG
            lda  $00
            pha
            stz  $00
            stz  P8ZP_SCRATCH_W1
            lda  #$bf
            sta  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  (cx16.r0),y
            beq  +
_continue   iny
            cpy  cx16.r1L           ; max size?
            bne  -
            beq  ++
+           lda  P8ZP_SCRATCH_REG   ; binary?
            bne  _continue
+           pla
            sta  $00
            rts
        }}
    }

    sub reset_system() {
        ; -- Soft-reset the system back to initial power-on Basic prompt.
        goto sys.reset_system
    }

    sub poweroff_system() {
        ; -- use the SMC to shutdown the computer
        goto sys.poweroff_system
    }

    sub set_led_state(bool on) {
        ; -- sets the computer's activity led on/off
        cx16.r0L = 0
        if on
            cx16.r0 = 255
        void cx16.i2c_write_byte($42, $05, cx16.r0L)
    }

    asmsub rom_version() clobbers(Y) -> ubyte @A, bool @Pc {
        ; Returns the KERNEL ROM version. Carry set if pre-release, clear if offical release.
        %asm{{
            ; the ROM BANK is unknown on entry
            ldy  $01
            stz  $01        ; KERNEL ROM
            clc             ; prepare for released ROM
            lda  $FF80
            bpl  _final     ; pre-release versions are negative
            eor  #$FF       ; twos complement
            ina
            sec
    _final:
            sty  $01
            rts
        }}
    }
}

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 16         ;  compilation target specifier.  255=virtual, 128=C128, 64=C64, 32=PET, 16=CommanderX16, 8=atari800XL, 7=Neo6502

    const ubyte SIZEOF_BOOL  = sizeof(bool)
    const ubyte SIZEOF_BYTE  = sizeof(byte)
    const ubyte SIZEOF_UBYTE = sizeof(ubyte)
    const ubyte SIZEOF_WORD  = sizeof(word)
    const ubyte SIZEOF_UWORD = sizeof(uword)
    const ubyte SIZEOF_LONG  = sizeof(long)
    const ubyte SIZEOF_POINTER = sizeof(&sys.wait)
    const ubyte SIZEOF_FLOAT = sizeof(float)
    const byte  MIN_BYTE     = -128
    const byte  MAX_BYTE     = 127
    const ubyte MIN_UBYTE    = 0
    const ubyte MAX_UBYTE    = 255
    const word  MIN_WORD     = -32768
    const word  MAX_WORD     = 32767
    const uword MIN_UWORD    = 0
    const uword MAX_UWORD    = 65535
    ; MIN_FLOAT and MAX_FLOAT are defined in the floats module if imported


asmsub  set_irq(uword handler @AY) clobbers(A)  {
    ; Sets the handler for the VSYNC interrupt, and enable that interrupt.
	%asm {{
	    php
        sei
        sta  _vector
        sty  _vector+1
        lda  #<_irq_handler
        sta  cbm.CINV
        lda  #>_irq_handler
        sta  cbm.CINV+1
        lda  #1
        tsb  cx16.VERA_IEN      ; enable the vsync irq
        plp
        rts

_irq_handler
        jsr  sys.save_prog8_internals
        cld

        jsr  _run_custom
        pha
		jsr  sys.restore_prog8_internals
		pla
		beq  +
		jmp  (restore_irq._orig_irqvec)   ; continue with normal kernal irq routine
+		lda  #1
		sta  cx16.VERA_ISR      ; clear Vera Vsync irq status
		ply
		plx
		pla
		rti
_run_custom
		jmp  (_vector)
		.section BSS
_vector	.word ?
		.send BSS
        ; !notreached!
    }}
}

asmsub  restore_irq() clobbers(A) {
	%asm {{
	    php
	    sei
	    lda  _orig_irqvec
	    sta  cbm.CINV
	    lda  _orig_irqvec+1
	    sta  cbm.CINV+1
	    lda  cx16.VERA_IEN
	    and  #%11110000     ; disable all Vera IRQs but the vsync
	    ora  #%00000001
	    sta  cx16.VERA_IEN
	    plp
	    rts
        .section BSS_NOCLEAR
_orig_irqvec    .word  ?
        .send BSS_NOCLEAR
        ; !notreached!
    }}
}

asmsub  set_rasterirq(uword handler @AY, uword rasterpos @R0) clobbers(A) {
    ; Sets the handler for the LINE interrupt, and enable (only) that interrupt.
	%asm {{
	        php
            sei
            sta  user_vector
            sty  user_vector+1
            lda  cx16.r0
            ldy  cx16.r0+1
            lda  cx16.VERA_IEN
            and  #%11110000     ; disable all irqs but the line(raster) one
            ora  #%00000010
            sta  cx16.VERA_IEN
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  set_rasterline
            lda  #<_raster_irq_handler
            sta  cbm.CINV
            lda  #>_raster_irq_handler
            sta  cbm.CINV+1
            plp
            rts

_raster_irq_handler
            jsr  sys.save_prog8_internals
            cld
            jsr  _run_custom
            jsr  sys.restore_prog8_internals
            ; end irq processing - don't use kernal's irq handling
            lda  #2
            tsb  cx16.VERA_ISR      ; clear Vera line irq status
            ply
            plx
            pla
            rti
_run_custom
		    jmp  (user_vector)
		    .section BSS
user_vector	    .word ?
		    .send BSS

		    ; !notreached!
        }}
}

asmsub update_rasterirq(uword handler @AY, uword rasterpos @R0) clobbers(A) {
    ; -- just update the IRQ handler and raster line position for the raster IRQ
    ;    this is much more efficient than calling set_rasterirq() again every time.
    ;    (but you have to call that one initially at least once to setup the prog8 handler itself)
    %asm {{
        php
        sei
        sta  sys.set_rasterirq.user_vector
        sty  sys.set_rasterirq.user_vector+1
        lda  cx16.r0L
        ldy  cx16.r0H
        jsr  set_rasterline
        plp
        rts
    }}
}

asmsub  set_rasterline(uword line @AY) {
    %asm {{
        php
        sei
        sta  cx16.VERA_IRQLINE_L
        tya
        lsr  a
        bcs  +
        lda  #%10000000
        trb  cx16.VERA_IEN
        plp
        rts
+       lda  #%10000000
        tsb  cx16.VERA_IEN
        plp
        rts
    }}
}

    asmsub reset_system() {
        ; Soft-reset the system back to initial power-on Basic prompt.
        ; We do this via the SMC so that a true reset is performed that also resets the Vera fully.
        ; (note: this is an asmsub on purpose! don't change into a normal sub)
        %asm {{
            sei
-           ldx #$42
            ldy #2
            lda #0
            jsr  cx16.i2c_write_byte
            bra  -      ; to work around an issue where the routine does not in fact immediately reset the system
            ; !notreached!
        }}
    }

    sub poweroff_system() {
        ; use the SMC to shutdown the computer
        ; in a loop for the event where the shutdown command does not in fact immediately shutdown the system
        repeat {
            void cx16.i2c_write_byte($42, $01, $00)
        }
    }

    asmsub wait(uword jiffies @AY) clobbers(X) {
        ; --- wait approximately the given number of jiffies (1/60th seconds) (N or N+1)
        ;     note: the system irq handler has to be active for this to work as it depends on the system jiffy clock
        ;     note: this routine cannot be used from inside a irq handler
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1

_loop       lda  P8ZP_SCRATCH_W1
            ora  P8ZP_SCRATCH_W1+1
            bne  +
            rts

+           php
            sei
            jsr  cbm.RDTIM
            plp
            sta  P8ZP_SCRATCH_B1
-           php
            sei
            jsr  cbm.RDTIM
            plp
            cmp  P8ZP_SCRATCH_B1
            beq  -

            lda  P8ZP_SCRATCH_W1
            bne  +
            dec  P8ZP_SCRATCH_W1+1
+           dec  P8ZP_SCRATCH_W1
            bra  _loop
        }}
    }

    inline asmsub waitvsync()  {
        ; --- suspend execution until the next vsync has occurred, without depending on custom irq handling.
        ;     note: system vsync irq handler has to be active for this routine to work (and no other irqs-- which is the default).
        ;     note: a more accurate way to wait for vsync is to set up a vsync irq handler instead.
        %asm {{
            wai
        }}
    }

    asmsub waitrasterline(uword line @AY) {
        ; -- CPU busy wait until the given raster line is reached
        %asm {{
            cpy  #0
            bne  _larger
-           cmp  cx16.VERA_SCANLINE_L
            bne  -
            bit  cx16.VERA_IEN
            bvs  -
            rts
_larger
            cmp  cx16.VERA_SCANLINE_L
            bne  _larger
            bit  cx16.VERA_IEN
            bvc  _larger
            rts
        }}
    }


    asmsub internal_stringcopy(str source @R0, str target @AY) clobbers (A,Y) {
        ; Called when the compiler wants to assign a string value to another string.
        %asm {{
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  cx16.r0
		ldy  cx16.r0+1
		jmp  prog8_lib.strcpy
        }}
    }

    asmsub memcopy(uword source @R0, uword target @R1, uword count @AY) clobbers(A,X,Y) {
        ; note: only works for NON-OVERLAPPING memory regions!
        ;       If you have to copy overlapping memory regions, consider using
        ;       the cx16 specific kernal routine `memory_copy` (make sure kernal rom is banked in).
        ; note: can't be inlined because is called from asm as well.
        ;       also: doesn't use cx16 ROM routine so this always works even when ROM is not banked in.
        %asm {{
            cpy  #0
            bne  _longcopy

            ; copy <= 255 bytes
            tay
            bne  _copyshort
            rts     ; nothing to copy

_copyshort
            dey
            beq  +
-           lda  (cx16.r0),y
            sta  (cx16.r1),y
            dey
            bne  -
+           lda  (cx16.r0),y
            sta  (cx16.r1),y
            rts

_longcopy
            pha                         ; lsb(count) = remainder in last page
            tya
            tax                         ; x = num pages (1+)
            ldy  #0
-           lda  (cx16.r0),y
            sta  (cx16.r1),y
            iny
            bne  -
            inc  cx16.r0+1
            inc  cx16.r1+1
            dex
            bne  -
            ply
            bne  _copyshort
            rts
        }}
    }

    asmsub memset(uword mem @R0, uword numbytes @R1, ubyte value @A) clobbers(A,X,Y) {
        %asm {{
            ldy  cx16.r0
            sty  P8ZP_SCRATCH_W1
            ldy  cx16.r0+1
            sty  P8ZP_SCRATCH_W1+1
            ldx  cx16.r1
            ldy  cx16.r1+1
            jmp  prog8_lib.memset
        }}
    }

    asmsub memsetw(uword mem @R0, uword numwords @R1, uword value @AY) clobbers (A,X,Y) {
        %asm {{
            ldx  cx16.r0
            stx  P8ZP_SCRATCH_W1
            ldx  cx16.r0+1
            stx  P8ZP_SCRATCH_W1+1
            ldx  cx16.r1
            stx  P8ZP_SCRATCH_W2
            ldx  cx16.r1+1
            stx  P8ZP_SCRATCH_W2+1
            jmp  prog8_lib.memsetw
        }}
    }

    asmsub memcmp(uword address1 @R0, uword address2 @R1, uword size @AY) -> byte @A {
        ; Compares two blocks of memory
        ; Returns -1 (255), 0 or 1, meaning: block 1 sorts before, equal or after block 2.
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldx  P8ZP_SCRATCH_W1+1
            beq  _no_msb_size

_loop_msb_size
            ldy  #0
-           lda  (cx16.r0),y
            cmp  (cx16.r1),y
            bcs  +
            lda  #-1
            rts
+           beq  +
            lda  #1
            rts
+           iny
            bne  -
            inc  cx16.r0+1
            inc  cx16.r1+1
            dec  P8ZP_SCRATCH_W1+1
            dex
            bne  _loop_msb_size

_no_msb_size
            lda  P8ZP_SCRATCH_W1
            bne  +
            rts

+           ldy  #0
-           lda  (cx16.r0),y
            cmp  (cx16.r1),y
            bcs  +
            lda  #-1
            rts
+           beq  +
            lda  #1
            rts
+           iny
            cpy  P8ZP_SCRATCH_W1
            bne  -

            lda #0
            rts
        }}
    }

    inline asmsub read_flags() -> ubyte @A {
        %asm {{
            php
            pla
        }}
    }

    inline asmsub clear_carry() {
        %asm {{
        clc
        }}
    }

    inline asmsub set_carry() {
        %asm {{
        sec
        }}
    }

    inline asmsub clear_irqd() {
        %asm {{
        cli
        }}
    }

    inline asmsub set_irqd() {
        %asm {{
        sei
        }}
    }

    inline asmsub irqsafe_set_irqd() {
        %asm {{
        php
        sei
        }}
    }

    inline asmsub irqsafe_clear_irqd() {
        %asm {{
        plp
        }}
    }

    inline asmsub disable_caseswitch() {
        %asm {{
            lda  #8
            jsr  cbm.CHROUT
        }}
    }

    inline asmsub enable_caseswitch() {
        %asm {{
            lda  #9
            jsr  cbm.CHROUT
        }}
    }

    asmsub save_prog8_internals() {
        %asm {{
            lda  P8ZP_SCRATCH_B1
            sta  save_SCRATCH_ZPB1
            lda  P8ZP_SCRATCH_REG
            sta  save_SCRATCH_ZPREG
            lda  P8ZP_SCRATCH_W1
            sta  save_SCRATCH_ZPWORD1
            lda  P8ZP_SCRATCH_W1+1
            sta  save_SCRATCH_ZPWORD1+1
            lda  P8ZP_SCRATCH_W2
            sta  save_SCRATCH_ZPWORD2
            lda  P8ZP_SCRATCH_W2+1
            sta  save_SCRATCH_ZPWORD2+1
            rts
            .section BSS
save_SCRATCH_ZPB1	.byte  ?
save_SCRATCH_ZPREG	.byte  ?
save_SCRATCH_ZPWORD1	.word  ?
save_SCRATCH_ZPWORD2	.word  ?
            .send BSS
            ; !notreached!
        }}
    }

    asmsub restore_prog8_internals() {
        %asm {{
            lda  save_prog8_internals.save_SCRATCH_ZPB1
            sta  P8ZP_SCRATCH_B1
            lda  save_prog8_internals.save_SCRATCH_ZPREG
            sta  P8ZP_SCRATCH_REG
            lda  save_prog8_internals.save_SCRATCH_ZPWORD1
            sta  P8ZP_SCRATCH_W1
            lda  save_prog8_internals.save_SCRATCH_ZPWORD1+1
            sta  P8ZP_SCRATCH_W1+1
            lda  save_prog8_internals.save_SCRATCH_ZPWORD2
            sta  P8ZP_SCRATCH_W2
            lda  save_prog8_internals.save_SCRATCH_ZPWORD2+1
            sta  P8ZP_SCRATCH_W2+1
            rts
        }}
    }

    asmsub exit(ubyte returnvalue @A) {
        ; -- immediately exit the program with a return code in the A register
        %asm {{
            sta  p8_sys_startup.cleanup_at_exit._exitcode
            ldx  prog8_lib.orig_stackpointer
            txs
            jmp  p8_sys_startup.cleanup_at_exit
        }}
    }

    asmsub exit2(ubyte resulta @A, ubyte resultx @X, ubyte resulty @Y) {
        ; -- immediately exit the program with result values in the A, X and Y registers.
        %asm {{
            sta  p8_sys_startup.cleanup_at_exit._exitcode
            stx  p8_sys_startup.cleanup_at_exit._exitcodeX
            sty  p8_sys_startup.cleanup_at_exit._exitcodeY
            ldx  prog8_lib.orig_stackpointer
            txs
            jmp  p8_sys_startup.cleanup_at_exit
        }}
    }

    asmsub exit3(ubyte resulta @A, ubyte resultx @X, ubyte resulty @Y, bool carry @Pc) {
        ; -- immediately exit the program with result values in the A, X and Y registers, and the Carry flag in the status register.
        %asm {{
            sta  p8_sys_startup.cleanup_at_exit._exitcode
            lda  #0
            rol  a
            sta  p8_sys_startup.cleanup_at_exit._exitcarry
            stx  p8_sys_startup.cleanup_at_exit._exitcodeX
            sty  p8_sys_startup.cleanup_at_exit._exitcodeY
            ldx  prog8_lib.orig_stackpointer
            txs
            jmp  p8_sys_startup.cleanup_at_exit
        }}
    }

    inline asmsub progend() -> uword @AY {
        %asm {{
            lda  #<prog8_program_end
            ldy  #>prog8_program_end
        }}
    }

    inline asmsub progstart() -> uword @AY {
        %asm {{
            lda  #<prog8_program_start
            ldy  #>prog8_program_start
        }}
    }

    inline asmsub push(ubyte value @A) {
        %asm {{
            pha
        }}
    }

    inline asmsub pushw(uword value @AY) {
        %asm {{
            pha
            phy
        }}
    }

    inline asmsub push_returnaddress(uword address @XY) {
        %asm {{
            ; push like JSR would:  address-1,  MSB first then LSB
            cpx  #0
            bne  +
            dey
+           dex
            phy
            phx
        }}
    }

    asmsub get_as_returnaddress(uword address @XY) -> uword @AX {
        %asm {{
            ; return the address like JSR would push onto the stack:  address-1,  MSB first then LSB
            cpx  #0
            bne  +
            dey
+           dex
            tya
            rts
        }}
    }

    inline asmsub pop() -> ubyte @A {
        %asm {{
            pla
        }}
    }

    inline asmsub popw() -> uword @AY {
        %asm {{
            ply
            pla
        }}
    }

    asmsub cpu_is_65816() -> bool @A {
        ; -- Returns true when you have a 65816 cpu, false when it's a 6502.
        %asm {{
			php
			clv
			.byte $e2, $ea  ; SEP #$ea, should be interpreted as 2 NOPs by 6502. 65c816 will set the Overflow flag.
			bvc +
			lda #1
			plp
			rts
+			lda #0
			plp
			rts
        }}
    }
}

p8_sys_startup {
    ; program startup and shutdown machinery. Needs to reside in normal system ram.

asmsub  init_system()  {
    ; Initializes the machine to a sane starting state.
    ; Called automatically by the loader program logic.
    %asm {{
        sei
        lda  #0
        tax
        tay
        jsr  cx16.mouse_config  ; disable mouse
        lda  cx16.VERA_DC_VIDEO
        and  #%00000111 ; retain chroma + output mode
        sta  P8ZP_SCRATCH_REG
        lda  #$0a
        sta  $01        ; rom bank 10 (audio)
        jsr  cx16.audio_init ; silence
        stz  $01        ; rom bank 0 (kernal)
        jsr  cbm.IOINIT
        jsr  cbm.RESTOR
        jsr  cbm.CINT
        lda  cx16.VERA_DC_VIDEO
        and  #%11111000
        ora  P8ZP_SCRATCH_REG
        sta  cx16.VERA_DC_VIDEO  ; restore old output mode
        lda  #$90       ; black
        jsr  cbm.CHROUT
        lda  #1
        jsr  cbm.CHROUT ; swap fg/bg
        lda  #$9e       ; yellow
        jsr  cbm.CHROUT
        lda  #147       ; clear screen
        jsr  cbm.CHROUT
        lda  #8         ; disable charset case switch
        jsr  cbm.CHROUT
        lda  #PROG8_VARSHIGH_RAMBANK
        sta  $00    ; select ram bank
        lda  #0
        sta  $01    ; set ROM bank to kernal bank to speed up kernal calls
        tax
        tay
        cli
        rts
    }}
}

asmsub  init_system_phase2()  {
    %asm {{
        sei
        lda  cbm.CINV
        sta  sys.restore_irq._orig_irqvec
        lda  cbm.CINV+1
        sta  sys.restore_irq._orig_irqvec+1
        lda  #PROG8_VARSHIGH_RAMBANK
        sta  $00    ; select ram bank
        stz  $01    ; set ROM bank to kernal bank to speed up kernal calls
        cli
        cld
        clc
        clv
        rts
    }}
}

asmsub  cleanup_at_exit() {
    ; executed when the main subroutine does rts
    %asm {{
        lda  #1
        sta  $00        ; ram bank 1
        lda  #4
        sta  $01        ; rom bank 4 (basic)
        jsr  cbm.CLRCHN		; reset i/o channels
        lda  #9
        jsr  cbm.CHROUT     ; enable charset switch
        lda  _exitcarry
        lsr  a
        lda  _exitcode
        ldx  _exitcodeX
        ldy  _exitcodeY
        rts

        .section BSS
_exitcarry  .byte ?
_exitcode   .byte ?
_exitcodeX  .byte ?
_exitcodeY  .byte ?
        .send BSS

        ; !notreached!
    }}
}

}
