; Prog8 definitions for the Atari800XL
; Including memory registers, I/O registers, Basic and Kernal subroutines.

%option no_symbol_prefixing, ignore_unused

atari {

        &uword  NMI_VEC         = $FFFA     ; 6502 nmi vector, determined by the kernal if banked in
        &uword  RESET_VEC       = $FFFC     ; 6502 reset vector, determined by the kernal if banked in
        &uword  IRQ_VEC         = $FFFE     ; 6502 interrupt vector, determined by the kernal if banked in

        &uword COLCRS = 85
        &ubyte ROWCRS = 84

    extsub $F24A = getchar() -> ubyte @A
    extsub $F2B0 = outchar(ubyte character @ A)
    extsub $F2FD = waitkey() -> ubyte @A

}

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 8         ;  compilation target specifier.  255=virtual, 128=C128, 64=C64, 32=PET, 16=CommanderX16, 8=atari800XL, 7=Neo6502

    const ubyte SIZEOF_BOOL  = sizeof(bool)
    const ubyte SIZEOF_BYTE  = sizeof(byte)
    const ubyte SIZEOF_UBYTE = sizeof(ubyte)
    const ubyte SIZEOF_WORD  = sizeof(word)
    const ubyte SIZEOF_UWORD = sizeof(uword)
    const ubyte SIZEOF_LONG  = sizeof(long)
    const ubyte SIZEOF_FLOAT = 0    ; undefined, no floats supported
    const byte  MIN_BYTE     = -128
    const byte  MAX_BYTE     = 127
    const ubyte MIN_UBYTE    = 0
    const ubyte MAX_UBYTE    = 255
    const word  MIN_WORD     = -32768
    const word  MAX_WORD     = 32767
    const uword MIN_UWORD    = 0
    const uword MAX_UWORD    = 65535
    ; MIN_FLOAT and MAX_FLOAT are defined in the floats module if imported


    asmsub  reset_system()  {
        ; Soft-reset the system back to initial power-on Basic prompt.
        ; TODO
        %asm {{
            sei
            jmp  (atari.RESET_VEC)
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

    asmsub memcmp(uword address1 @R0, uword address2 @R1, uword size @AY) -> byte @A {
        ; Compares two blocks of memory
        ; Returns -1 (255), 0 or 1, meaning: block 1 sorts before, equal or after block 2.
        %asm {{
            sta  P8ZP_SCRATCH_REG   ; lsb(size)
            sty  P8ZP_SCRATCH_B1    ; msb(size)
            lda  cx16.r0
            ldy  cx16.r0+1
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            lda  cx16.r1
            ldy  cx16.r1+1
            sta  P8ZP_SCRATCH_W2
            sty  P8ZP_SCRATCH_W2+1

            ldx  P8ZP_SCRATCH_B1
            beq  _no_msb_size

_loop_msb_size
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            cmp  (P8ZP_SCRATCH_W2),y
            bcs  +
            lda  #-1
            rts
+           beq  +
            lda  #1
            rts
+           iny
            bne  -
            inc  P8ZP_SCRATCH_W1+1
            inc  P8ZP_SCRATCH_W2+1
            dec  P8ZP_SCRATCH_B1        ; msb(size) -= 1
            dex
            bne  _loop_msb_size

_no_msb_size
            lda  P8ZP_SCRATCH_REG       ; lsb(size)
            bne  +
            rts

+           ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            cmp  (P8ZP_SCRATCH_W2),y
            bcs  +
            lda  #-1
            rts
+           beq  +
            lda  #1
            rts
+           iny
            cpy  P8ZP_SCRATCH_REG       ; lsb(size)
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
            pla
            tay
            pla
        }}
    }

}

cx16 {
    ; the sixteen virtual 16-bit registers that the CX16 has defined in the zeropage
    ; they are simulated on the Atari as well but their location in memory is different
    ; TODO
    &uword r0  = $1b00
    &uword r1  = $1b02
    &uword r2  = $1b04
    &uword r3  = $1b06
    &uword r4  = $1b08
    &uword r5  = $1b0a
    &uword r6  = $1b0c
    &uword r7  = $1b0e
    &uword r8  = $1b10
    &uword r9  = $1b12
    &uword r10 = $1b14
    &uword r11 = $1b16
    &uword r12 = $1b18
    &uword r13 = $1b1a
    &uword r14 = $1b1c
    &uword r15 = $1b1e

    &word r0s  = $1b00
    &word r1s  = $1b02
    &word r2s  = $1b04
    &word r3s  = $1b06
    &word r4s  = $1b08
    &word r5s  = $1b0a
    &word r6s  = $1b0c
    &word r7s  = $1b0e
    &word r8s  = $1b10
    &word r9s  = $1b12
    &word r10s = $1b14
    &word r11s = $1b16
    &word r12s = $1b18
    &word r13s = $1b1a
    &word r14s = $1b1c
    &word r15s = $1b1e

    &ubyte r0L  = $1b00
    &ubyte r1L  = $1b02
    &ubyte r2L  = $1b04
    &ubyte r3L  = $1b06
    &ubyte r4L  = $1b08
    &ubyte r5L  = $1b0a
    &ubyte r6L  = $1b0c
    &ubyte r7L  = $1b0e
    &ubyte r8L  = $1b10
    &ubyte r9L  = $1b12
    &ubyte r10L = $1b14
    &ubyte r11L = $1b16
    &ubyte r12L = $1b18
    &ubyte r13L = $1b1a
    &ubyte r14L = $1b1c
    &ubyte r15L = $1b1e

    &ubyte r0H  = $1b01
    &ubyte r1H  = $1b03
    &ubyte r2H  = $1b05
    &ubyte r3H  = $1b07
    &ubyte r4H  = $1b09
    &ubyte r5H  = $1b0b
    &ubyte r6H  = $1b0d
    &ubyte r7H  = $1b0f
    &ubyte r8H  = $1b11
    &ubyte r9H  = $1b13
    &ubyte r10H = $1b15
    &ubyte r11H = $1b17
    &ubyte r12H = $1b19
    &ubyte r13H = $1b1b
    &ubyte r14H = $1b1d
    &ubyte r15H = $1b1f

    &byte r0sL  = $1b00
    &byte r1sL  = $1b02
    &byte r2sL  = $1b04
    &byte r3sL  = $1b06
    &byte r4sL  = $1b08
    &byte r5sL  = $1b0a
    &byte r6sL  = $1b0c
    &byte r7sL  = $1b0e
    &byte r8sL  = $1b10
    &byte r9sL  = $1b12
    &byte r10sL = $1b14
    &byte r11sL = $1b16
    &byte r12sL = $1b18
    &byte r13sL = $1b1a
    &byte r14sL = $1b1c
    &byte r15sL = $1b1e

    &byte r0sH  = $1b01
    &byte r1sH  = $1b03
    &byte r2sH  = $1b05
    &byte r3sH  = $1b07
    &byte r4sH  = $1b09
    &byte r5sH  = $1b0b
    &byte r6sH  = $1b0d
    &byte r7sH  = $1b0f
    &byte r8sH  = $1b11
    &byte r9sH  = $1b13
    &byte r10sH = $1b15
    &byte r11sH = $1b17
    &byte r12sH = $1b19
    &byte r13sH = $1b1b
    &byte r14sH = $1b1d
    &byte r15sH = $1b1f

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
        ; TODO
        %asm {{
            sei
            ; TODO reset screen mode etc etc
            cli
            rts
        }}
    }

    asmsub  init_system_phase2()  {
        %asm {{
            cld
            clc
            clv
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
