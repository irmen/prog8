; Prog8 definitions for the CommanderX16
; Including memory registers, I/O registers, Basic and Kernal subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%target cx16


c64 {

; ---- kernal routines, these are the same as on the Commodore-64 (hence the same block name) ----

; STROUT --> use txt.print
; CLEARSCR -> use txt.clear_screen
; HOMECRSR -> use txt.plot

romsub $FF81 = CINT() clobbers(A,X,Y)                           ; (alias: SCINIT) initialize screen editor and video chip
romsub $FF84 = IOINIT() clobbers(A, X)                          ; initialize I/O devices (CIA, SID, IRQ)
romsub $FF87 = RAMTAS() clobbers(A,X,Y)                         ; initialize RAM, tape buffer, screen
romsub $FF8A = RESTOR() clobbers(A,X,Y)                         ; restore default I/O vectors
romsub $FF8D = VECTOR(uword userptr @ XY, ubyte dir @ Pc) clobbers(A,Y)     ; read/set I/O vector table
romsub $FF90 = SETMSG(ubyte value @ A)                          ; set Kernal message control flag
romsub $FF93 = SECOND(ubyte address @ A) clobbers(A)            ; (alias: LSTNSA) send secondary address after LISTEN
romsub $FF96 = TKSA(ubyte address @ A) clobbers(A)              ; (alias: TALKSA) send secondary address after TALK
romsub $FF99 = MEMTOP(uword address @ XY, ubyte dir @ Pc) -> uword @ XY     ; read/set top of memory  pointer.   NOTE: as a Cx16 extension, also returns the number of RAM memory banks in register A !  See cx16.numbanks()
romsub $FF9C = MEMBOT(uword address @ XY, ubyte dir @ Pc) -> uword @ XY     ; read/set bottom of memory  pointer
romsub $FF9F = SCNKEY() clobbers(A,X,Y)                         ; scan the keyboard
romsub $FFA2 = SETTMO(ubyte timeout @ A)                        ; set time-out flag for IEEE bus
romsub $FFA5 = ACPTR() -> ubyte @ A                             ; (alias: IECIN) input byte from serial bus
romsub $FFA8 = CIOUT(ubyte databyte @ A)                        ; (alias: IECOUT) output byte to serial bus
romsub $FFAB = UNTLK() clobbers(A)                              ; command serial bus device to UNTALK
romsub $FFAE = UNLSN() clobbers(A)                              ; command serial bus device to UNLISTEN
romsub $FFB1 = LISTEN(ubyte device @ A) clobbers(A)             ; command serial bus device to LISTEN
romsub $FFB4 = TALK(ubyte device @ A) clobbers(A)               ; command serial bus device to TALK
romsub $FFB7 = READST() -> ubyte @ A                            ; read I/O status word
romsub $FFBA = SETLFS(ubyte logical @ A, ubyte device @ X, ubyte secondary @ Y)   ; set logical file parameters
romsub $FFBD = SETNAM(ubyte namelen @ A, str filename @ XY)     ; set filename parameters
romsub $FFC0 = OPEN() clobbers(X,Y) -> ubyte @Pc, ubyte @A      ; (via 794 ($31A)) open a logical file
romsub $FFC3 = CLOSE(ubyte logical @ A) clobbers(A,X,Y)         ; (via 796 ($31C)) close a logical file
romsub $FFC6 = CHKIN(ubyte logical @ X) clobbers(A,X) -> ubyte @Pc    ; (via 798 ($31E)) define an input channel
romsub $FFC9 = CHKOUT(ubyte logical @ X) clobbers(A,X)          ; (via 800 ($320)) define an output channel
romsub $FFCC = CLRCHN() clobbers(A,X)                           ; (via 802 ($322)) restore default devices
romsub $FFCF = CHRIN() clobbers(X, Y) -> ubyte @ A   ; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
romsub $FFD2 = CHROUT(ubyte char @ A)                           ; (via 806 ($326)) output a character
romsub $FFD5 = LOAD(ubyte verify @ A, uword address @ XY) -> ubyte @Pc, ubyte @ A, uword @ XY     ; (via 816 ($330)) load from device
romsub $FFD8 = SAVE(ubyte zp_startaddr @ A, uword endaddr @ XY) -> ubyte @ Pc, ubyte @ A          ; (via 818 ($332)) save to a device
romsub $FFDB = SETTIM(ubyte low @ A, ubyte middle @ X, ubyte high @ Y)      ; set the software clock
romsub $FFDE = RDTIM() -> ubyte @ A, ubyte @ X, ubyte @ Y       ; read the software clock (A=lo,X=mid,Y=high)
romsub $FFE1 = STOP() clobbers(X) -> ubyte @ Pz, ubyte @ A      ; (via 808 ($328)) check the STOP key (and some others in A)
romsub $FFE4 = GETIN() clobbers(X,Y) -> ubyte @Pc, ubyte @ A    ; (via 810 ($32A)) get a character
romsub $FFE7 = CLALL() clobbers(A,X)                            ; (via 812 ($32C)) close all files
romsub $FFEA = UDTIM() clobbers(A,X)                            ; update the software clock
romsub $FFED = SCREEN() -> ubyte @ X, ubyte @ Y                 ; read number of screen rows and columns
romsub $FFF0 = PLOT(ubyte col @ Y, ubyte row @ X, ubyte dir @ Pc) -> ubyte @ X, ubyte @ Y       ; read/set position of cursor on screen.  Use txt.plot for a 'safe' wrapper that preserves X.
romsub $FFF3 = IOBASE() -> uword @ XY                           ; read base address of I/O devices

; ---- utility

asmsub STOP2() -> ubyte @A  {
    ; -- check if STOP key was pressed, returns true if so.  More convenient to use than STOP() because that only sets the carry status flag.
    %asm {{
        phx
        jsr  c64.STOP
        beq  +
        plx
        lda  #0
        rts
+       plx
        lda  #1
        rts
    }}
}

asmsub RDTIM16() -> uword @AY {
    ; --  like RDTIM() but only returning the lower 16 bits in AY for convenience
    %asm {{
        phx
        jsr  c64.RDTIM
        pha
        txa
        tay
        pla
        plx
        rts
    }}
}

}

cx16 {

; irq and hardware vectors:
    &uword  CINV            = $0314     ; IRQ vector (in ram)
    &uword  NMI_VEC         = $FFFA     ; 65c02 nmi vector, determined by the kernal if banked in
    &uword  RESET_VEC       = $FFFC     ; 65c02 reset vector, determined by the kernal if banked in
    &uword  IRQ_VEC         = $FFFE     ; 65c02 interrupt vector, determined by the kernal if banked in


; the sixteen virtual 16-bit registers
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


; VERA registers

    const uword VERA_BASE       = $9F20
    &ubyte  VERA_ADDR_L         = VERA_BASE + $0000
    &ubyte  VERA_ADDR_M         = VERA_BASE + $0001
    &ubyte  VERA_ADDR_H         = VERA_BASE + $0002
    &ubyte  VERA_DATA0          = VERA_BASE + $0003
    &ubyte  VERA_DATA1          = VERA_BASE + $0004
    &ubyte  VERA_CTRL           = VERA_BASE + $0005
    &ubyte  VERA_IEN            = VERA_BASE + $0006
    &ubyte  VERA_ISR            = VERA_BASE + $0007
    &ubyte  VERA_IRQ_LINE_L     = VERA_BASE + $0008
    &ubyte  VERA_DC_VIDEO       = VERA_BASE + $0009
    &ubyte  VERA_DC_HSCALE      = VERA_BASE + $000A
    &ubyte  VERA_DC_VSCALE      = VERA_BASE + $000B
    &ubyte  VERA_DC_BORDER      = VERA_BASE + $000C
    &ubyte  VERA_DC_HSTART      = VERA_BASE + $0009
    &ubyte  VERA_DC_HSTOP       = VERA_BASE + $000A
    &ubyte  VERA_DC_VSTART      = VERA_BASE + $000B
    &ubyte  VERA_DC_VSTOP       = VERA_BASE + $000C
    &ubyte  VERA_L0_CONFIG      = VERA_BASE + $000D
    &ubyte  VERA_L0_MAPBASE     = VERA_BASE + $000E
    &ubyte  VERA_L0_TILEBASE    = VERA_BASE + $000F
    &ubyte  VERA_L0_HSCROLL_L   = VERA_BASE + $0010
    &ubyte  VERA_L0_HSCROLL_H   = VERA_BASE + $0011
    &ubyte  VERA_L0_VSCROLL_L   = VERA_BASE + $0012
    &ubyte  VERA_L0_VSCROLL_H   = VERA_BASE + $0013
    &ubyte  VERA_L1_CONFIG      = VERA_BASE + $0014
    &ubyte  VERA_L1_MAPBASE     = VERA_BASE + $0015
    &ubyte  VERA_L1_TILEBASE    = VERA_BASE + $0016
    &ubyte  VERA_L1_HSCROLL_L   = VERA_BASE + $0017
    &ubyte  VERA_L1_HSCROLL_H   = VERA_BASE + $0018
    &ubyte  VERA_L1_VSCROLL_L   = VERA_BASE + $0019
    &ubyte  VERA_L1_VSCROLL_H   = VERA_BASE + $001A
    &ubyte  VERA_AUDIO_CTRL     = VERA_BASE + $001B
    &ubyte  VERA_AUDIO_RATE     = VERA_BASE + $001C
    &ubyte  VERA_AUDIO_DATA     = VERA_BASE + $001D
    &ubyte  VERA_SPI_DATA       = VERA_BASE + $001E
    &ubyte  VERA_SPI_CTRL       = VERA_BASE + $001F
; VERA_PSG_BASE     = $1F9C0
; VERA_PALETTE_BASE = $1FA00
; VERA_SPRITES_BASE = $1FC00

; I/O

    const uword  via1   = $9f00                  ;VIA 6522 #1
    &ubyte  d1prb	= via1+0
    &ubyte  d1pra	= via1+1
    &ubyte  d1ddrb	= via1+2
    &ubyte  d1ddra	= via1+3
    &ubyte  d1t1l	= via1+4
    &ubyte  d1t1h	= via1+5
    &ubyte  d1t1ll	= via1+6
    &ubyte  d1t1lh	= via1+7
    &ubyte  d1t2l	= via1+8
    &ubyte  d1t2h	= via1+9
    &ubyte  d1sr	= via1+10
    &ubyte  d1acr	= via1+11
    &ubyte  d1pcr	= via1+12
    &ubyte  d1ifr	= via1+13
    &ubyte  d1ier	= via1+14
    &ubyte  d1ora	= via1+15

    const uword  via2   = $9f10                  ;VIA 6522 #2
    &ubyte  d2prb	= via2+0
    &ubyte  d2pra	= via2+1
    &ubyte  d2ddrb	= via2+2
    &ubyte  d2ddra	= via2+3
    &ubyte  d2t1l	= via2+4
    &ubyte  d2t1h	= via2+5
    &ubyte  d2t1ll	= via2+6
    &ubyte  d2t1lh	= via2+7
    &ubyte  d2t2l	= via2+8
    &ubyte  d2t2h	= via2+9
    &ubyte  d2sr	= via2+10
    &ubyte  d2acr	= via2+11
    &ubyte  d2pcr	= via2+12
    &ubyte  d2ifr	= via2+13
    &ubyte  d2ier	= via2+14
    &ubyte  d2ora	= via2+15

    &ubyte  ym2151adr	= $9f40
    &ubyte  ym2151dat	= $9f41

    const uword  extdev	= $9f60


; ---- Commander X-16 additions on top of C64 kernal routines ----
; spelling of the names is taken from the Commander X-16 rom sources

; supported C128 additions
romsub $ff4a = close_all(ubyte device @A)  clobbers(A,X,Y)
romsub $ff59 = lkupla(ubyte la @A)  clobbers(A,X,Y)
romsub $ff5c = lkupsa(ubyte sa @Y)  clobbers(A,X,Y)
romsub $ff5f = screen_set_mode(ubyte mode @A)  clobbers(A, X, Y) -> ubyte @Pc
romsub $ff62 = screen_set_charset(ubyte charset @A, uword charsetptr @XY)  clobbers(A,X,Y)      ; incompatible with C128  dlchr()
; not yet supported: romsub $ff65 = pfkey()  clobbers(A,X,Y)
romsub $ff6e = jsrfar()
romsub $ff74 = fetch(ubyte bank @X, ubyte index @Y)  clobbers(X) -> ubyte @A
romsub $ff77 = stash(ubyte data @A, ubyte bank @X, ubyte index @Y)  clobbers(X)
romsub $ff7a = cmpare(ubyte data @A, ubyte bank @X, ubyte index @Y)  clobbers(X)
romsub $ff7d = primm()

; X16 additions
romsub $ff44 = macptr()  clobbers(A,X,Y)
romsub $ff47 = enter_basic(ubyte cold_or_warm @Pc)  clobbers(A,X,Y)
romsub $ff68 = mouse_config(ubyte shape @A, ubyte scale @X)  clobbers (A, X, Y)
romsub $ff6b = mouse_get(ubyte zpdataptr @X)  clobbers(A)
romsub $ff71 = mouse_scan()  clobbers(A, X, Y)
romsub $ff53 = joystick_scan()  clobbers(A, X, Y)
romsub $ff56 = joystick_get(ubyte joynr @A) -> ubyte @A, ubyte @X, ubyte @Y
romsub $ff4d = clock_set_date_time(uword yearmonth @R0, uword dayhours @R1, uword minsecs @R2, ubyte jiffies @R3)  clobbers(A, X, Y)
romsub $ff50 = clock_get_date_time()  clobbers(A, X, Y)  -> uword @R0, uword @R1, uword @R2, ubyte @R3   ; result registers see clock_set_date_time()

; TODO specify the correct clobbers for alle these functions below, we now assume all 3 regs are clobbered

; high level graphics & fonts
romsub $ff20 = GRAPH_init(uword vectors @R0)  clobbers(A,X,Y)
romsub $ff23 = GRAPH_clear()  clobbers(A,X,Y)
romsub $ff26 = GRAPH_set_window(uword x @R0, uword y @R1, uword width @R2, uword height @R3)  clobbers(A,X,Y)
romsub $ff29 = GRAPH_set_colors(ubyte stroke @A, ubyte fill @X, ubyte background @Y)  clobbers (A,X,Y)
romsub $ff2c = GRAPH_draw_line(uword x1 @R0, uword y1 @R1, uword x2 @R2, uword y2 @R3)  clobbers(A,X,Y)
romsub $ff2f = GRAPH_draw_rect(uword x @R0, uword y @R1, uword width @R2, uword height @R3, uword cornerradius @R4, ubyte fill @Pc)  clobbers(A,X,Y)
romsub $ff32 = GRAPH_move_rect(uword sx @R0, uword sy @R1, uword tx @R2, uword ty @R3, uword width @R4, uword height @R5)  clobbers(A,X,Y)
romsub $ff35 = GRAPH_draw_oval(uword x @R0, uword y @R1, uword width @R2, uword height @R3, ubyte fill @Pc)  clobbers(A,X,Y)
romsub $ff38 = GRAPH_draw_image(uword x @R0, uword y @R1, uword ptr @R2, uword width @R3, uword height @R4)  clobbers(A,X,Y)
romsub $ff3b = GRAPH_set_font(uword fontptr @R0)  clobbers(A,X,Y)
romsub $ff3e = GRAPH_get_char_size(ubyte baseline @A, ubyte width @X, ubyte height_or_style @Y, ubyte is_control @Pc)  clobbers(A,X,Y)
romsub $ff41 = GRAPH_put_char(uword x @R0, uword y @R1, ubyte char @A)  clobbers(A,X,Y)
romsub $ff41 = GRAPH_put_next_char(ubyte char @A)  clobbers(A,X,Y)     ; alias for the routine above that doesn't reset the position of the initial character

; framebuffer
romsub $fef6 = FB_init()  clobbers(A,X,Y)
romsub $fef9 = FB_get_info()  clobbers(X,Y) -> byte @A, uword @R0, uword @R1    ; width=r0, height=r1
romsub $fefc = FB_set_palette(uword pointer @R0, ubyte index @A, ubyte bytecount @X)  clobbers(A,X,Y)
romsub $feff = FB_cursor_position(uword x @R0, uword y @R1)  clobbers(A,X,Y)
romsub $feff = FB_cursor_position2()  clobbers(A,X,Y)           ;  alias for the previous routine, but avoiding having to respecify both x and y every time
romsub $ff02 = FB_cursor_next_line(uword x @R0)  clobbers(A,X,Y)
romsub $ff05 = FB_get_pixel()  clobbers(X,Y) -> ubyte @A
romsub $ff08 = FB_get_pixels(uword pointer @R0, uword count @R1)  clobbers(A,X,Y)
romsub $ff0b = FB_set_pixel(ubyte color @A)  clobbers(A,X,Y)
romsub $ff0e = FB_set_pixels(uword pointer @R0, uword count @R1)  clobbers(A,X,Y)
romsub $ff11 = FB_set_8_pixels(ubyte pattern @A, ubyte color @X)  clobbers(A,X,Y)
romsub $ff14 = FB_set_8_pixels_opaque(ubyte pattern @R0, ubyte mask @A, ubyte color1 @X, ubyte color2 @Y)  clobbers(A,X,Y)
romsub $ff17 = FB_fill_pixels(uword count @R0, uword pstep @R1, ubyte color @A)  clobbers(A,X,Y)
romsub $ff1a = FB_filter_pixels(uword pointer @ R0, uword count @R1)  clobbers(A,X,Y)
romsub $ff1d = FB_move_pixels(uword sx @R0, uword sy @R1, uword tx @R2, uword ty @R3, uword count @R4)  clobbers(A,X,Y)

; misc
romsub $fef0 = sprite_set_image(uword pixels @R0, uword mask @R1, ubyte bpp @R2, ubyte number @A, ubyte width @X, ubyte height @Y, ubyte apply_mask @Pc)  clobbers(A,X,Y) -> ubyte @Pc
romsub $fef3 = sprite_set_position(uword x @R0, uword y @R1, ubyte number @A)  clobbers(A,X,Y)
romsub $fee4 = memory_fill(uword address @R0, uword num_bytes @R1, ubyte value @A)  clobbers(A,X,Y)
romsub $fee7 = memory_copy(uword source @R0, uword target @R1, uword num_bytes @R2)  clobbers(A,X,Y)
romsub $feea = memory_crc(uword address @R0, uword num_bytes @R1)  clobbers(A,X,Y) -> uword @R2
romsub $feed = memory_decompress(uword input @R0, uword output @R1)  clobbers(A,X,Y) -> uword @R1       ; last address +1 is result in R1
romsub $fedb = console_init(uword x @R0, uword y @R1, uword width @R2, uword height @R3)  clobbers(A,X,Y)
romsub $fede = console_put_char(ubyte char @A, ubyte wrapping @Pc)  clobbers(A,X,Y)
romsub $fee1 = console_get_char()  clobbers(X,Y) -> ubyte @A
romsub $fed8 = console_put_image(uword pointer @R0, uword width @R1, uword height @R2)  clobbers(A,X,Y)
romsub $fed5 = console_set_paging_message(uword msgptr @R0)  clobbers(A,X,Y)
romsub $fed2 = kbdbuf_put(ubyte key @A)  clobbers(A,X,Y)
romsub $fecf = entropy_get() -> ubyte @A, ubyte @X, ubyte @Y
romsub $fecc = monitor()  clobbers(A,X,Y)

; ---- end of kernal routines ----


; ---- utilities -----

inline asmsub rombank(ubyte rombank @A) {
    ; -- set the rom banks
    %asm {{
        sta  $01            ; rom bank register (v39+, used to be cx16.d1prb $9f60 in v38)
    }}
}

inline asmsub rambank(ubyte rambank @A) {
    ; -- set the ram bank
    %asm {{
        sta  $00            ; ram bank register (v39+, used to be cx16.d1pra $9f61 in v38)
    }}
}

asmsub numbanks() -> ubyte @A {
    ; -- uses MEMTOP's cx16 extension to query the number of available RAM banks. (each is 8 Kb)
    %asm {{
        phx
        sec
        jsr  c64.MEMTOP
        plx
        rts
    }}
}

asmsub vpeek(ubyte bank @A, uword address @XY) -> ubyte @A {
        ; -- get a byte from VERA's video memory
        ;    note: inefficient when reading multiple sequential bytes!
        %asm {{
                pha
                lda  #1
                sta  cx16.VERA_CTRL
                pla
                and  #1
                sta  cx16.VERA_ADDR_H
                sty  cx16.VERA_ADDR_M
                stx  cx16.VERA_ADDR_L
                lda  cx16.VERA_DATA1
                rts
            }}
}

asmsub vaddr(ubyte bank @A, uword address @R0, ubyte addrsel @R1, byte autoIncrOrDecrByOne @Y) clobbers(A) {
        ; -- setup the VERA's data address register 0 or 1
        %asm {{
            and  #1
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
            rts
+           ora  #%00011000
            sta  cx16.VERA_ADDR_H
            rts
        }}
}

asmsub vpoke(ubyte bank @A, uword address @R0, ubyte value @Y) clobbers(A) {
    ; -- write a single byte to VERA's video memory
    ;    note: inefficient when writing multiple sequential bytes!
    %asm {{
        stz  cx16.VERA_CTRL
        and  #1
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
        and  #1
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
        and  #1
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
        and  #1
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

asmsub vload(str name @R0, ubyte device @Y, ubyte bank @A, uword address @R1) -> ubyte @A {
    ; -- like the basic command VLOAD "filename",device,bank,address
    ;    loads a file into video memory in the given bank:address, returns success in A
    ;    !! NOTE !! the V38 ROMs contain a bug in the LOAD code that makes the load address not work correctly,
    ;               it works fine when loading from local filesystem
    %asm {{
        ; -- load a file into video ram
        phx
        pha
        tya
        tax
        lda  #1
        ldy  #0
        jsr  c64.SETLFS
        lda  cx16.r0
        ldy  cx16.r0+1
        jsr  prog8_lib.strlen
        tya
        ldx  cx16.r0
        ldy  cx16.r0+1
        jsr  c64.SETNAM
        pla
        clc
        adc  #2
        ldx  cx16.r1
        ldy  cx16.r1+1
        stz  P8ZP_SCRATCH_B1
        jsr  c64.LOAD
        bcs  +
        inc  P8ZP_SCRATCH_B1
+       jsr  c64.CLRCHN
        lda  #1
        jsr  c64.CLOSE
        plx
        lda  P8ZP_SCRATCH_B1
        rts
    }}
}

inline asmsub joystick_get2(ubyte joynr @A) clobbers(Y) -> uword @AX  {
    ; convenience routine to get the joystick state without requiring inline assembly that deals with the multiple return values.
    ; Also disables interrupts to avoid the IRQ race condition mentioned here: https://github.com/commanderx16/x16-rom/issues/203
    ; TODO once that issue is resolved, this routine can be redefined as:  romsub $ff56 = joystick_get2(ubyte joynr @A) clobbers(Y) -> uword @AX
    %asm {{
        sei
        jsr  cx16.joystick_get
        cli
    }}
}


sub FB_set_pixels_from_buf(uword buffer, uword count) {
    %asm {{
            ; -- This is replacement code for the normal FB_set_pixels subroutine in ROM
            ;    However that routine contains a bug in the current v38 ROM that makes it crash when count > 255.
            ;    So the code below replaces that. Once the ROM is patched this routine is no longer necessary.
            ;    See https://github.com/commanderx16/x16-rom/issues/179
            phx
            lda  buffer
            ldy  buffer+1
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            jsr  _pixels
            plx
            rts

_pixels     lda  count+1
            beq  +
            ldx  #0
-           jsr  _loop
            inc  P8ZP_SCRATCH_W1+1
            dec  count+1
            bne  -

+           ldx  count
_loop       ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  cx16.VERA_DATA0
            iny
            dex
            bne  -
            rts
        }}
}

; ---- system stuff -----
asmsub  init_system()  {
    ; Initializes the machine to a sane starting state.
    ; Called automatically by the loader program logic.
    %asm {{
        sei
        cld
        lda  #$80
        sta  VERA_CTRL
        stz  $01        ; select rom bank 0 (enable kernal)
        jsr  c64.IOINIT
        jsr  c64.RESTOR
        jsr  c64.CINT
        lda  #$90       ; black
        jsr  c64.CHROUT
        lda  #1         ; swap fg/bg
        jsr  c64.CHROUT
        lda  #$9e       ; yellow
        jsr  c64.CHROUT
        lda  #147       ; clear screen
        jsr  c64.CHROUT
        lda  #0
        tax
        tay
        clc
        clv
        cli
        rts
    }}
}

asmsub  init_system_phase2()  {
    %asm {{
        sei
        lda  cx16.CINV
        sta  restore_irq._orig_irqvec
        lda  cx16.CINV+1
        sta  restore_irq._orig_irqvec+1
        cli
        rts
    }}
}

asmsub  set_irq(uword handler @AY, ubyte useKernal @Pc) clobbers(A)  {
	%asm {{
	        sta  _modified+1
	        sty  _modified+2
	        lda  #0
	        adc  #0
	        sta  _use_kernal
		sei
		lda  #<_irq_handler
		sta  cx16.CINV
		lda  #>_irq_handler
		sta  cx16.CINV+1
                lda  cx16.VERA_IEN
                ora  #%00000001     ; enable the vsync irq
                sta  cx16.VERA_IEN
		cli
		rts

_irq_handler    jsr  _irq_handler_init
_modified	jsr  $ffff                      ; modified
		jsr  _irq_handler_end
		lda  _use_kernal
		bne  +
		; end irq processing - don't use kernal's irq handling
		lda  cx16.VERA_ISR
		ora  #1
		sta  cx16.VERA_ISR      ; clear Vera Vsync irq status
		ply
		plx
		pla
		rti
+		jmp  (restore_irq._orig_irqvec)   ; continue with normal kernal irq routine

_use_kernal     .byte  0

_irq_handler_init
		; save all zp scratch registers and the X register as these might be clobbered by the irq routine
		stx  IRQ_X_REG
		lda  P8ZP_SCRATCH_B1
		sta  IRQ_SCRATCH_ZPB1
		lda  P8ZP_SCRATCH_REG
		sta  IRQ_SCRATCH_ZPREG
		lda  P8ZP_SCRATCH_W1
		sta  IRQ_SCRATCH_ZPWORD1
		lda  P8ZP_SCRATCH_W1+1
		sta  IRQ_SCRATCH_ZPWORD1+1
		lda  P8ZP_SCRATCH_W2
		sta  IRQ_SCRATCH_ZPWORD2
		lda  P8ZP_SCRATCH_W2+1
		sta  IRQ_SCRATCH_ZPWORD2+1
		; stack protector; make sure we don't clobber the top of the evaluation stack
		dex
		dex
		dex
		dex
		dex
		dex
		cld
		rts

_irq_handler_end
		; restore all zp scratch registers and the X register
		lda  IRQ_SCRATCH_ZPB1
		sta  P8ZP_SCRATCH_B1
		lda  IRQ_SCRATCH_ZPREG
		sta  P8ZP_SCRATCH_REG
		lda  IRQ_SCRATCH_ZPWORD1
		sta  P8ZP_SCRATCH_W1
		lda  IRQ_SCRATCH_ZPWORD1+1
		sta  P8ZP_SCRATCH_W1+1
		lda  IRQ_SCRATCH_ZPWORD2
		sta  P8ZP_SCRATCH_W2
		lda  IRQ_SCRATCH_ZPWORD2+1
		sta  P8ZP_SCRATCH_W2+1
		ldx  IRQ_X_REG
		rts

IRQ_X_REG		.byte  0
IRQ_SCRATCH_ZPB1	.byte  0
IRQ_SCRATCH_ZPREG	.byte  0
IRQ_SCRATCH_ZPWORD1	.word  0
IRQ_SCRATCH_ZPWORD2	.word  0

		}}
}


asmsub  restore_irq() clobbers(A) {
	%asm {{
	    sei
	    lda  _orig_irqvec
	    sta  cx16.CINV
	    lda  _orig_irqvec+1
	    sta  cx16.CINV+1
	    lda  cx16.VERA_IEN
	    and  #%11110000     ; disable all Vera IRQs
	    ora  #%00000001     ; enable only the vsync Irq
	    sta  cx16.VERA_IEN
	    cli
	    rts
_orig_irqvec    .word  0
        }}
}

asmsub  set_rasterirq(uword handler @AY, uword rasterpos @R0) clobbers(A) {
	%asm {{
            sta  _modified+1
            sty  _modified+2
            lda  cx16.r0
            ldy  cx16.r0+1
            sei
            lda  cx16.VERA_IEN
            and  #%11110000     ; clear other IRQs
            ora  #%00000010     ; enable the line (raster) irq
            sta  cx16.VERA_IEN
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  set_rasterline
            lda  #<_raster_irq_handler
            sta  cx16.CINV
            lda  #>_raster_irq_handler
            sta  cx16.CINV+1
            cli
            rts

_raster_irq_handler
            jsr  set_irq._irq_handler_init
_modified   jsr  $ffff                      ; modified
            jsr  set_irq._irq_handler_end
            ; end irq processing - don't use kernal's irq handling
            lda  cx16.VERA_ISR
            ora  #%00000010
            sta  cx16.VERA_ISR      ; clear Vera line irq status
            ply
            plx
            pla
            rti
        }}
}

asmsub  set_rasterline(uword line @AY) {
    %asm {{
        sta  cx16.VERA_IRQ_LINE_L
        lda  cx16.VERA_IEN
        and  #%01111111
        sta  cx16.VERA_IEN
        tya
        lsr  a
        ror  a
        and  #%10000000
        ora  cx16.VERA_IEN
        sta  cx16.VERA_IEN
        rts
    }}
}

}


sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 16         ;  compilation target specifier.  64 = C64,  16 = CommanderX16.


    asmsub reset_system() {
        ; Soft-reset the system back to initial power-on Basic prompt.
        %asm {{
            sei
            stz  $01            ; bank the kernal in
            jmp  (cx16.RESET_VEC)
        }}
    }

    asmsub wait(uword jiffies @AY) {
        ; --- wait approximately the given number of jiffies (1/60th seconds) (N or N+1)
        ;     note: regular system vsync irq handler must be running, and no nother irqs
        %asm {{
-           wai             ; wait for irq (assume it was vsync)
            cmp  #0
            bne  +
            dey
+           dec  a
            bne  -
            cpy  #0
            bne  -
            rts
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

    inline asmsub memcopy(uword source @R0, uword target @R1, uword count @AY) clobbers(A,X,Y) {
        %asm {{
            sta  cx16.r2
            sty  cx16.r2+1
            jsr  cx16.memory_copy
        }}
    }

    inline asmsub memset(uword mem @R0, uword numbytes @R1, ubyte value @A) clobbers(A,X,Y) {
        %asm {{
            jsr  cx16.memory_fill
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

    inline asmsub rsave() {
        ; save cpu status flag and all registers A, X, Y.
        ; see http://6502.org/tutorials/register_preservation.html
        %asm {{
            php
            pha
            phy
            phx
        }}
    }

    inline asmsub rrestore() {
        ; restore all registers and cpu status flag
        %asm {{
            plx
            ply
            pla
            plp
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

    inline asmsub exit(ubyte returnvalue @A) {
        ; -- immediately exit the program with a return code in the A register
        %asm {{
            jsr  c64.CLRCHN		; reset i/o channels
            ldx  prog8_lib.orig_stackpointer
            txs
            rts		; return to original caller
        }}
    }

    inline asmsub progend() -> uword @AY {
        %asm {{
            lda  #<prog8_program_end
            ldy  #>prog8_program_end
        }}
    }
}
