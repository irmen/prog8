; Prog8 definitions for the CommanderX16
; Including memory registers, I/O registers, Basic and Kernal subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


c64 {

; ---- kernal routines, these are the same as on the Commodore-64 (hence the same block name) ----

; STROUT --> use screen.print
; CLEARSCR -> use screen.clear_screen
; HOMECRSR -> use screen.plot

romsub $FF81 = CINT() clobbers(A,X,Y)                           ; (alias: SCINIT) initialize screen editor and video chip
romsub $FF84 = IOINIT() clobbers(A, X)                          ; initialize I/O devices (CIA, SID, IRQ)
romsub $FF87 = RAMTAS() clobbers(A,X,Y)                         ; initialize RAM, tape buffer, screen
romsub $FF8A = RESTOR() clobbers(A,X,Y)                         ; restore default I/O vectors
romsub $FF8D = VECTOR(uword userptr @ XY, ubyte dir @ Pc) clobbers(A,Y)     ; read/set I/O vector table
romsub $FF90 = SETMSG(ubyte value @ A)                          ; set Kernal message control flag
romsub $FF93 = SECOND(ubyte address @ A) clobbers(A)            ; (alias: LSTNSA) send secondary address after LISTEN
romsub $FF96 = TKSA(ubyte address @ A) clobbers(A)              ; (alias: TALKSA) send secondary address after TALK
romsub $FF99 = MEMTOP(uword address @ XY, ubyte dir @ Pc) -> uword @ XY     ; read/set top of memory  pointer
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
romsub $FFBA = SETLFS(ubyte logical @ A, ubyte device @ X, ubyte address @ Y)   ; set logical file parameters
romsub $FFBD = SETNAM(ubyte namelen @ A, str filename @ XY)     ; set filename parameters
romsub $FFC0 = OPEN() clobbers(A,X,Y)                           ; (via 794 ($31A)) open a logical file
romsub $FFC3 = CLOSE(ubyte logical @ A) clobbers(A,X,Y)         ; (via 796 ($31C)) close a logical file
romsub $FFC6 = CHKIN(ubyte logical @ X) clobbers(A,X)           ; (via 798 ($31E)) define an input channel
romsub $FFC9 = CHKOUT(ubyte logical @ X) clobbers(A,X)          ; (via 800 ($320)) define an output channel
romsub $FFCC = CLRCHN() clobbers(A,X)                           ; (via 802 ($322)) restore default devices
romsub $FFCF = CHRIN() clobbers(Y) -> ubyte @ A                 ; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
romsub $FFD2 = CHROUT(ubyte char @ A)                           ; (via 806 ($326)) output a character
romsub $FFD5 = LOAD(ubyte verify @ A, uword address @ XY) -> ubyte @Pc, ubyte @ A, ubyte @ X, ubyte @ Y     ; (via 816 ($330)) load from device
romsub $FFD8 = SAVE(ubyte zp_startaddr @ A, uword endaddr @ XY) -> ubyte @ Pc, ubyte @ A                    ; (via 818 ($332)) save to a device
romsub $FFDB = SETTIM(ubyte low @ A, ubyte middle @ X, ubyte high @ Y)      ; set the software clock
romsub $FFDE = RDTIM() -> ubyte @ A, ubyte @ X, ubyte @ Y       ; read the software clock
romsub $FFE1 = STOP() clobbers(A,X) -> ubyte @ Pz, ubyte @ Pc   ; (via 808 ($328)) check the STOP key
romsub $FFE4 = GETIN() clobbers(X,Y) -> ubyte @ A               ; (via 810 ($32A)) get a character
romsub $FFE7 = CLALL() clobbers(A,X)                            ; (via 812 ($32C)) close all files
romsub $FFEA = UDTIM() clobbers(A,X)                            ; update the software clock
romsub $FFED = SCREEN() -> ubyte @ X, ubyte @ Y                 ; read number of screen rows and columns
romsub $FFF0 = PLOT(ubyte col @ Y, ubyte row @ X, ubyte dir @ Pc) -> ubyte @ X, ubyte @ Y       ; read/set position of cursor on screen.  Use screen.plot for a 'safe' wrapper that preserves X.
romsub $FFF3 = IOBASE() -> uword @ XY                           ; read base address of I/O devices

}

cx16 {

; ---- Commander X-16 additions on top of C64 kernal routines ----
; spelling of the names is taken from the Commander X-16 rom sources

; the sixteen virtual 16-bit registers
&ubyte r0  = $02
&ubyte r0L = $02
&ubyte r0H = $03
&ubyte r1  = $04
&ubyte r1L = $04
&ubyte r1H = $05
&ubyte r2  = $06
&ubyte r2L = $06
&ubyte r2H = $07
&ubyte r3  = $08
&ubyte r3L = $08
&ubyte r3H = $09
&ubyte r4  = $0a
&ubyte r4L = $0a
&ubyte r4H = $0b
&ubyte r5  = $0c
&ubyte r5L = $0c
&ubyte r5H = $0d
&ubyte r6  = $0e
&ubyte r6L = $0e
&ubyte r6H = $0f
&ubyte r7  = $10
&ubyte r7L = $10
&ubyte r7H = $11
&ubyte r8  = $12
&ubyte r8L = $12
&ubyte r8H = $13
&ubyte r9  = $14
&ubyte r9L = $14
&ubyte r9H = $15
&ubyte r10 = $16
&ubyte r10L    = $16
&ubyte r10H    = $17
&ubyte r11     = $18
&ubyte r11L    = $18
&ubyte r11H    = $19
&ubyte r12     = $1a
&ubyte r12L    = $1a
&ubyte r12H    = $1b
&ubyte r13     = $1c
&ubyte r13L    = $1c
&ubyte r13H    = $1d
&ubyte r14     = $1e
&ubyte r14L    = $1e
&ubyte r14H    = $1f
&ubyte r15     = $20
&ubyte r15L    = $20
&ubyte r15H    = $21


; TODO subroutine args + soubroutine returnvalues + clobber registers

; supported C128 additions
romsub $ff4a = close_all()
romsub $ff59 = lkupla()
romsub $ff5c = lkupsa()
romsub $ff5f = screen_set_mode()
romsub $ff62 = screen_set_charset(ubyte charset @A, uword charsetptr @XY) clobbers(A,X,Y)      ; incompatible with C128  dlchr()
romsub $ff65 = pfkey()
romsub $ff6e = jsrfar()
romsub $ff74 = fetch()
romsub $ff77 = stash()
romsub $ff7a = cmpare()
romsub $ff7d = primm()

; X16 additions
romsub $ff44 = macptr()
romsub $ff47 = enter_basic()
romsub $ff68 = mouse_config()
romsub $ff6b = mouse_get()
romsub $ff71 = mouse_scan()
romsub $ff53 = joystick_scan()
romsub $ff56 = joystick_get()
romsub $ff4d = clock_set_date_time()
romsub $ff50 = clock_get_date_time()

; high level graphics & fonts
romsub $ff20 = GRAPH_init()
romsub $ff23 = GRAPH_clear()
romsub $ff26 = GRAPH_set_window()
romsub $ff29 = GRAPH_set_colors()
romsub $ff2c = GRAPH_draw_line()
romsub $ff2f = GRAPH_draw_rect()
romsub $ff32 = GRAPH_move_rect()
romsub $ff35 = GRAPH_draw_oval()
romsub $ff38 = GRAPH_draw_image()
romsub $ff3b = GRAPH_set_font()
romsub $ff3e = GRAPH_get_char_size()
romsub $ff41 = GRAPH_put_char()

; TODO framebuffer API not yet included, include it

romsub $fef0 = sprite_set_image()
romsub $fef3 = sprite_set_position()
romsub $fee4 = memory_fill()
romsub $fee7 = memory_copy()
romsub $feea = memory_crc()
romsub $feed = memory_decompress()
romsub $fedb = console_init()
romsub $fede = console_put_char()
romsub $fee1 = console_get_char()
romsub $fed8 = console_put_image()
romsub $fed5 = console_set_paging_message()
romsub $fed2 = kbdbuf_put()
romsub $fecf = entropy_get()
romsub $fecc = monitor()



; ---- end of kernal routines ----

asmsub init_system()  {
    ; Initializes the machine to a sane starting state.
    ; Called automatically by the loader program logic.
    %asm {{
        sei
        cld
        lda  #0
        sta  $00
        sta  $01
        jsr  c64.IOINIT
        jsr  c64.RESTOR
        jsr  c64.CINT
        lda  #0
        tax
        tay
        clc
        clv
        cli
        lda  #66
        clc
        jsr  console_put_char
        rts
    }}
}

}
