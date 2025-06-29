; Prog8 definitions for the Foenix F256x

%option no_symbol_prefixing, ignore_unused

; compatiblity layer
cbm {
%option no_symbol_prefixing, ignore_unused

    ; dumb hack to make some code compile.
    ; TODO: figure out how the kernel clock works, not just
    ; reading the RTC hardware
    ubyte TIME_LO = $00

    ;
    ; Only reads keys right now.
    ;
    asmsub CHRIN() -> ubyte @A {
        %asm {{
-           stz f256.event.type         ; invalidate existing event type
            jsr f256.event.NextEvent
            lda f256.event.type
            cmp #f256.event.key.PRESSED
            bne -
            lda f256.event.key.ascii    ; return upper or lower ASCII
            rts
        }}
}

    ; I though the alias was working but it isn't.
    ;alias CHROUT = f256.chrout
    asmsub CHROUT(ubyte character @ A) {
        %asm {{
            jmp f256.chrout
        }}
    }

    ; TODO: set carry properly?
    ; TODO: on CBM any device other than keyboard or RS-232 should jump to CHRIN
    asmsub GETIN() -> bool @Pc, ubyte @A {
        %asm {{
-           stz f256.event.type         ; invalidate previous event type
            lda f256.event.pending      ; return zero if no pending event
            cmp #$ff                    ; $ff == no events pending
            beq +                       ; done.
            jsr f256.event.NextEvent
            lda f256.event.type
            cmp #f256.event.key.PRESSED
            bne -
            ;lda f256.event.key.raw     ; return key id / scan code? (case issue?)
            lda f256.event.key.ascii    ; return upper or lower ASCII
            clc                         ; clear carry when returning character
            rts                         ; return ascii key
+           sec                         ; set carry when no character returned
            lda #0                      ; return zero if no key
            rts
    }}
}


    asmsub GETINx() -> bool @Pc, ubyte @A {
        %asm {{
            rts
        }}
    }
}

f256 {
%option no_symbol_prefixing, ignore_unused, force_output
    ;
    ; Foenix F256 hardware definitions
    ;

    ; MMU controls
    &ubyte mem_ctrl     = $0000
    &ubyte io_ctrl      = $0001

    ; text screen size
    const ubyte DEFAULT_WIDTH = 80
    const ubyte DEFAULT_HEIGHT = 60

    ; screen / color memory
    const uword Colors  = $C000 ; IO page 2
    const uword Screen  = $C000 ; IO page 3

    ; self tracked screen coordinates
    ; potentially could be at $04/$05 in reserved ZP area?
    ubyte screen_row = 0
    ubyte screen_col = 0
    ubyte screen_color = $f2    ; default text/background color
    &uword screen_ptr = $02 ; and $03.  used to calculate screen/color ram offsets

    ;
    ; calculates screen memory pointer for the start of a row
    ; in screen_ptr in zeropage.
    ; ldy column
    ; sta (screen_ptr), y
    ;
    asmsub rowptr(ubyte row @Y) {
        %asm {{
            stz screen_ptr      ; reset to start of screen ram
            lda #>f256.Screen
            sta screen_ptr+1
            cpy #0      ; row in @Y will be our loop counter
            beq ptr_done
    rowloop:
            clc
            lda screen_ptr      ; load count
            adc #DEFAULT_WIDTH
            bcc +
            inc screen_ptr+1
    +       sta screen_ptr
            dey
            bne rowloop
    ptr_done:
            rts
        }}
    }

    ;
    ; calculates screen memory pointer for the specific col/row
    ; in screen_ptr in zeropage. Points directly to character after.
    ; ldy #0
    ; sta (screen_ptr), y
    ;
    asmsub chrptr(ubyte col @X, ubyte row @Y) clobbers(A) {
        %asm {{
            phx             ; preserve col
            jsr  rowptr     ; calculate pointer to row
            pla             ; restore col
            clc
            adc  screen_ptr
            sta  screen_ptr 
            bcc  +
            inc  screen_ptr+1
    +       rts
        }}
    }

    asmsub chrout(ubyte character @ A) {
        %asm {{
            phx                     ; preserve x
            phy                     ; preserve y
            cmp #$0d                ; check for carriage return
            beq crlf
            cmp #$0a                ; check for line feed
            beq crlf
            pha                     ; preserve a
            ldy screen_row
            jsr rowptr              ; calculates screen pointer to start of row
            ldy screen_col      ; column will be our index against the row pointer
            lda #2
            sta f256.io_ctrl        ; map in screen memory
            pla
            sta (screen_ptr),y
            lda #3
            sta f256.io_ctrl        ; map in color memory
            lda screen_color
            sta (screen_ptr),y
            lda #0
            sta f256.io_ctrl        ; return to default map
            inc screen_col
            lda screen_col
            cmp #DEFAULT_WIDTH
            bcc +                   ; less than DEFAULT_WIDTH
    crlf:
            stz screen_col
            inc screen_row
            lda screen_row
            cmp #DEFAULT_HEIGHT
            bcc +
            sec
            jsr scroll_up
            dec screen_row
    +       ply
            plx
            rts
        }}
    }

    ;
    ; implement here so chrout can work without importing textio.
    ;
    asmsub  scroll_up  (bool alsocolors @ Pc) clobbers(A,X)  {
        ; ---- scroll the whole screen 1 character up
        ;      contents of the bottom row are unchanged, you should refill/clear this yourself
        ;      Carry flag determines if screen color data must be scrolled too
        %asm {{
            bcc  _scroll_screen

    +               ; scroll the screen and the color memory
            ldx #DEFAULT_WIDTH-1
    -
            lda #2
            sta f256.io_ctrl        ; map in screen memory
            .for row=1, row<=DEFAULT_HEIGHT, row+=1
                lda  f256.Screen + DEFAULT_WIDTH*row,x
                sta  f256.Screen + DEFAULT_WIDTH*(row-1),x
            .next
            lda #3
            sta f256.io_ctrl        ; map in color memory
            .for row=1, row<=DEFAULT_HEIGHT, row+=1
                lda  f256.Colors + DEFAULT_WIDTH*row,x
                sta  f256.Colors + DEFAULT_WIDTH*(row-1),x
            .next


            dex
            bpl  -
            lda #0
            sta f256.io_ctrl        ; restore I/O configuration
            rts

    _scroll_screen  ; scroll only the screen memory
            ldx #DEFAULT_WIDTH-1
    -
            lda #2
            sta f256.io_ctrl        ; map in screen memory
            .for row=1, row<=DEFAULT_HEIGHT, row+=1
                lda  f256.Screen + DEFAULT_WIDTH*row,x
                sta  f256.Screen + DEFAULT_WIDTH*(row-1),x
            .next
            dex
            bpl  -
            lda #0
            sta f256.io_ctrl        ; restore I/O configuration
            rts
        }}
    }

    ; args
    sub args() {
        &uword ext        = $00f8
        &ubyte extlen     = $00fa
        &uword buf        = $00fb
        &ubyte buflen     = $00fd
        &uword ptr        = $00fe
    }

    ; kernel event calls & event types
    sub event() {
        &uword dest             = $00f0
        &ubyte pending          = $00f2
        &ubyte[8] packet        = $00e8   ; 8-byte buffer in ZP for kernel events.

        ; event buffer fields
        &ubyte type             = &packet   ; event type (definitions below)
        &ubyte buf              = &packet+1 ; page id or zero
        &ubyte ext              = &packet+2 ; page id or zero

        ; kernel event calls
        extsub $ff00 = NextEvent()  ; Copy next event to application buffer.
        extsub $ff04 = ReadData()   ; Copy primary bulk event data to application.
        extsub $ff08 = ReadExt()    ; Copy secondary bulk event data to application.

        sub init() {
            f256.event.dest = &f256.event.packet
        }

        ; event type definitions
        const uword reserved        = $00 ; $01
        const uword deprecated      = $02 ; $03
        const uword JOYSTICK        = $04 ; $05 ; game controller changes
        const uword DEVICE          = $06 ; $07 ; device added or removed
        sub key() {
            const uword PRESSED     = $08 ; $09
            const uword RELEASED    = $0a ; $0b
            &ubyte keyboard         = &packet+3 ; keyboard id
            &ubyte raw              = &packet+4 ; raw key id/code
            &ubyte ascii            = &packet+5 ; ASCII value
            &ubyte flags            = &packet+6 ; flag (META)
            const ubyte META        = $80       ; meta key no ASCII value
        }
        sub mouse() {
            const uword DELTA       = $0c ; $0d
            const uword CLICKS      = $0e ; $0f
        }
        sub block() {
            const uword NAME        = $10 ; $11
            const uword SIZE        = $12 ; $13
            const uword DATA        = $14 ; $15 ; read request succeeded
            const uword WROTE       = $16 ; $17 ; write request completed
            const uword FORMATTED   = $18 ; $19 ; low-level format completed
            const uword ERROR       = $1a ; $1b
        }
        sub fs() {
            const uword SIZE        = $1c ; $1d
            const uword CREATED     = $1e ; $1f
            const uword CHECKED     = $20 ; $21
            const uword DATA        = $22 ; $23 ; read request succeeded
            const uword WROTE       = $24 ; $25 ; write request completed
            const uword ERROR       = $26 ; $27
        }
        sub file() {
            const uword NOT_FOUND   = $28 ; $29 ; file was not found
            const uword OPENED      = $2a ; $2b ; file successfully opened
            const uword DATA        = $2c ; $2d ; read request succeeded
            const uword WROTE       = $2e ; $2f ; write request completed
            const uword EOF         = $30 ; $31 ; all file data has been read
            const uword CLOSED      = $32 ; $33 ; close request completed
            const uword RENAMED     = $34 ; $35 ; renamed request completed
            const uword DELETED     = $36 ; $37 ; delete request completed
            const uword ERROR       = $38 ; $39 ; error occurred, close file if opened
            const uword SEEK        = $3a ; $3b ; seek request completed
        }
        sub directory() {
            const uword OPENED      = $3c ; $3d ; directory open succeeded
            const uword VOLUME      = $3e ; $3f ; volume record found
            const uword FILE        = $40 ; $41 ; file record found
            const uword FREE        = $42 ; $43 ; file-system free-space record found
            const uword EOF         = $44 ; $45 ; all data read
            const uword CLOSED      = $46 ; $47 ; directory file closed
            const uword ERROR       = $48 ; $49 ; error occurred, close if opened
            const uword CREATED     = $4a ; $4b ; directory created
            const uword DELETED     = $4a ; $4b ; directory deleted
        }
        sub net() {
            const uword TCP         = $4c ; $4d
            const uword UDP         = $4e ; $4f
        }
        sub timer() {
            const uword EXPIRED     = $50 ; $51
        }
        sub clock() {
            const uword TICK        = $52 ; $53
        }
        sub irq() {
            const uword IRQ         = $54 ; $55
        }
    }

    ; kernel display calls
    sub display() {
        &ubyte x          = $00f3
        &ubyte y          = $00f4
        &uword color      = &f256.args.ext
        &uword text       = &f256.args.buf
        &ubyte buflen     = &f256.args.buflen
        const uword GetSize_    = $ffd0     ; Get screen dimensions
        const uword DrawRow_    = $ffd4     ; Draw text/color buffers left-to-right
        const uword DrawColumn_ = $ffd8     ; Draw text/color buffers top-to-bottom
    }



    ;
    &uword  NMI_VEC   = $FFFA  ; 6502 nmi vector
    &uword  RESET_VEC = $FFFC  ; 6502 reset vector
    &uword  IRQ_VEC   = $FFFE  ; 6502 interrupt vector

    ;%asminclude "api.asm"
}

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 25         ;  compilation target specifier.  255=virtual, 128=C128, 64=C64, 32=PET, 25=Foenix F256, 16=CommanderX16, 8=atari800XL, 7=Neo6502

    const ubyte SIZEOF_BOOL  = sizeof(bool)
    const ubyte SIZEOF_BYTE  = sizeof(byte)
    const ubyte SIZEOF_UBYTE = sizeof(ubyte)
    const ubyte SIZEOF_WORD  = sizeof(word)
    const ubyte SIZEOF_UWORD = sizeof(uword)
    const ubyte SIZEOF_LONG  = sizeof(long)
;    const ubyte SIZEOF_POINTER = sizeof(&sys.wait)
    const ubyte SIZEOF_FLOAT = 0    ; undefined, no floats supported
    const byte  MIN_BYTE     = -128
    const byte  MAX_BYTE     = 127
    const ubyte MIN_UBYTE    = 0
    const ubyte MAX_UBYTE    = 255
    const word  MIN_WORD     = -32768
    const word  MAX_WORD     = 32767
    const uword MIN_UWORD    = 0
    const uword MAX_UWORD    = 65535
    ; MIN_FLOAT and MAX_FLOAT are defined in the floats module if importec


    asmsub  reset_system()  {
        ; Soft-reset the system back to initial power-on status
        ; TODO
        %asm {{
            sei
            jmp  (f256.RESET_VEC)
        }}
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        ;     TODO
    }

    asmsub waitvsync() clobbers(A) {
        ; --- busy wait till the next vsync has occurred (approximately), without depending on custom irq handling.
        ;     TODO
        %asm {{
            nop
            rts
        }}
    }

    asmsub internal_stringcopy(uword source @R0, uword target @AY) clobbers (A,Y) {
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
        ; note: can't be inlined because is called from asm as well
        %asm {{
            ldx  cx16.r0
            stx  P8ZP_SCRATCH_W1        ; source in ZP
            ldx  cx16.r0+1
            stx  P8ZP_SCRATCH_W1+1
            ldx  cx16.r1
            stx  P8ZP_SCRATCH_W2        ; target in ZP
            ldx  cx16.r1+1
            stx  P8ZP_SCRATCH_W2+1
            cpy  #0
            bne  _longcopy

            ; copy <= 255 bytes
            tay
            bne  _copyshort
            rts     ; nothing to copy

_copyshort
            dey
            beq  +
-           lda  (P8ZP_SCRATCH_W1),y
            sta  (P8ZP_SCRATCH_W2),y
            dey
            bne  -
+           lda  (P8ZP_SCRATCH_W1),y
            sta  (P8ZP_SCRATCH_W2),y
            rts

_longcopy
            sta  P8ZP_SCRATCH_B1        ; lsb(count) = remainder in last page
            tya
            tax                         ; x = num pages (1+)
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  (P8ZP_SCRATCH_W2),y
            iny
            bne  -
            inc  P8ZP_SCRATCH_W1+1
            inc  P8ZP_SCRATCH_W2+1
            dex
            bne  -
            ldy  P8ZP_SCRATCH_B1
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

    asmsub memsetw(uword mem @R0, uword numwords @R1, uword value @AY) clobbers(A,X,Y) {
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

    sub disable_caseswitch() {
        ; no-op
    }

    sub enable_caseswitch() {
        ; no-op
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
            tya
            pha
        }}
    }

    inline asmsub push_returnaddress(uword address @XY) {
        %asm {{
            ; push like JSR would:  address-1,  MSB first then LSB
            cpx  #0
            bne  +
            dey
+           dex
            tya
            pha
            txa
            pha
        }}
    }

    inline asmsub pop() -> ubyte @A {
        %asm {{
            pla
        }}
    }

    inline asmsub popw() -> uword @AY {
        %asm {{
            pla
            tay
            pla
        }}
    }

}

cx16 {
; the sixteen virtual 16-bit registers in both normal unsigned mode and signed mode (s)
    &uword r0  = $0010
    &uword r1  = $0012
    &uword r2  = $0014
    &uword r3  = $0016
    &uword r4  = $0018
    &uword r5  = $001a
    &uword r6  = $001c
    &uword r7  = $001e
    &uword r8  = $0020
    &uword r9  = $0022
    &uword r10 = $0024
    &uword r11 = $0026
    &uword r12 = $0028
    &uword r13 = $002a
    &uword r14 = $002c
    &uword r15 = $002e

    ; signed word versions
    &word r0s  = $0010
    &word r1s  = $0012
    &word r2s  = $0014
    &word r3s  = $0016
    &word r4s  = $0018
    &word r5s  = $001a
    &word r6s  = $001c
    &word r7s  = $001e
    &word r8s  = $0020
    &word r9s  = $0022
    &word r10s = $0024
    &word r11s = $0026
    &word r12s = $0028
    &word r13s = $002a
    &word r14s = $002c
    &word r15s = $002e

    ; ubyte versions (low and high bytes)
    &ubyte r0L  = $0010
    &ubyte r1L  = $0012
    &ubyte r2L  = $0014
    &ubyte r3L  = $0016
    &ubyte r4L  = $0018
    &ubyte r5L  = $001a
    &ubyte r6L  = $001c
    &ubyte r7L  = $001e
    &ubyte r8L  = $0020
    &ubyte r9L  = $0022
    &ubyte r10L = $0024
    &ubyte r11L = $0026
    &ubyte r12L = $0028
    &ubyte r13L = $002a
    &ubyte r14L = $002c
    &ubyte r15L = $002e

    &ubyte r0H  = $0011
    &ubyte r1H  = $0013
    &ubyte r2H  = $0015
    &ubyte r3H  = $0017
    &ubyte r4H  = $0019
    &ubyte r5H  = $001b
    &ubyte r6H  = $001d
    &ubyte r7H  = $001f
    &ubyte r8H  = $0021
    &ubyte r9H  = $0023
    &ubyte r10H = $0025
    &ubyte r11H = $0027
    &ubyte r12H = $0029
    &ubyte r13H = $002b
    &ubyte r14H = $002d
    &ubyte r15H = $002f

    ; signed byte versions (low and high bytes)
    &byte r0sL  = $0010
    &byte r1sL  = $0012
    &byte r2sL  = $0014
    &byte r3sL  = $0016
    &byte r4sL  = $0018
    &byte r5sL  = $001a
    &byte r6sL  = $001c
    &byte r7sL  = $001e
    &byte r8sL  = $0020
    &byte r9sL  = $0022
    &byte r10sL = $0024
    &byte r11sL = $0026
    &byte r12sL = $0028
    &byte r13sL = $002a
    &byte r14sL = $002c
    &byte r15sL = $002e

    &byte r0sH  = $0011
    &byte r1sH  = $0013
    &byte r2sH  = $0015
    &byte r3sH  = $0017
    &byte r4sH  = $0019
    &byte r5sH  = $001b
    &byte r6sH  = $001d
    &byte r7sH  = $001f
    &byte r8sH  = $0021
    &byte r9sH  = $0023
    &byte r10sH = $0025
    &byte r11sH = $0027
    &byte r12sH = $0029
    &byte r13sH = $002b
    &byte r14sH = $002d
    &byte r15sH = $002f

    ; boolean versions
    &bool r0bL  = $0010
    &bool r1bL  = $0012
    &bool r2bL  = $0014
    &bool r3bL  = $0016
    &bool r4bL  = $0018
    &bool r5bL  = $001a
    &bool r6bL  = $001c
    &bool r7bL  = $001e
    &bool r8bL  = $0020
    &bool r9bL  = $0022
    &bool r10bL = $0024
    &bool r11bL = $0026
    &bool r12bL = $0028
    &bool r13bL = $002a
    &bool r14bL = $002c
    &bool r15bL = $002e

    &bool r0bH  = $0011
    &bool r1bH  = $0013
    &bool r2bH  = $0015
    &bool r3bH  = $0017
    &bool r4bH  = $0019
    &bool r5bH  = $001b
    &bool r6bH  = $001d
    &bool r7bH  = $001f
    &bool r8bH  = $0021
    &bool r9bH  = $0023
    &bool r10bH = $0025
    &bool r11bH = $0027
    &bool r12bH = $0029
    &bool r13bH = $002b
    &bool r14bH = $002d
    &bool r15bH = $002f


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

    sub cpu_is_65816() -> bool {
        ; Returns true when you have a 65816 cpu, false when it's a 6502.
        return false
    }

}

p8_sys_startup {
    ; program startup and shutdown machinery. Needs to reside in normal system ram.

    asmsub  init_system()  {
        ; Initializes the machine to a sane starting state.
        ; Called automatically by the loader program logic.
        %asm {{
            sei
            cld
            clc
            ; TODO reset screen mode etc etc?
            clv
            ; TODO what about IRQ handler?
            cli
            rts
        }}
    }

    asmsub  init_system_phase2()  {
        %asm {{
            ; initialize kernel event interface
            lda  #<f256.event.packet
            sta  f256.event.dest+0
            stz  f256.event.dest+1
            ;sta  f256.args.ext+0   ; check cc65 crt0.s for setup?
            ;stz  f256.args.ext+1
            rts
        }}
    }

    asmsub  cleanup_at_exit() {
        ; executed when the main subroutine does rts
        %asm {{
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
