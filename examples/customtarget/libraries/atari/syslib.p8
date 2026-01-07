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

%import shared_sys_functions

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 8         ;  compilation target specifier.  255=virtual, 128=C128, 64=C64, 32=PET, 16=CommanderX16, 8=atari800XL, 7=Neo6502

    const ubyte SIZEOF_BOOL  = sizeof(bool)
    const ubyte SIZEOF_BYTE  = sizeof(byte)
    const ubyte SIZEOF_UBYTE = sizeof(ubyte)
    const ubyte SIZEOF_WORD  = sizeof(word)
    const ubyte SIZEOF_UWORD = sizeof(uword)
    const ubyte SIZEOF_LONG  = sizeof(long)
    const ubyte SIZEOF_POINTER = sizeof(&sys.wait)
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

    sub disable_caseswitch() {
        ; no-op
    }

    sub enable_caseswitch() {
        ; no-op
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

    inline asmsub pushl(long value @R0R1) {
        %asm {{
            lda  cx16.r0
            pha
            lda  cx16.r0+1
            pha
            lda  cx16.r0+2
            pha
            lda  cx16.r0+3
            pha
        }}
    }

    inline asmsub popl() -> long @R0R1 {
        %asm {{
            pla
            sta  cx16.r0+3
            pla
            sta  cx16.r0+2
            pla
            sta  cx16.r0+1
            pla
            sta  cx16.r0
        }}
    }

    sub cpu_is_65816() -> bool {
        ; Returns true when you have a 65816 cpu, false when it's a 6502.
        return false
    }

}

cx16 {
    ; the sixteen virtual 16-bit registers that the CX16 has defined in the zeropage
    ; they are simulated on the Atari as well but their location in memory is different
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

    ; signed word versions
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

    ; signed long versions
    &long r0r1sl  = $1b00
    &long r2r3sl  = $1b04
    &long r4r5sl  = $1b08
    &long r6r7sl  = $1b0c
    &long r8r9sl  = $1b00
    &long r10r11sl = $1b04
    &long r12r13sl = $1b08
    &long r14r15sl = $1b0c

    ; ubyte versions (low and high bytes)
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

    ; signed byte versions (low and high bytes)
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

    ; boolean versions
    &bool r0bL  = $1b00
    &bool r1bL  = $1b02
    &bool r2bL  = $1b04
    &bool r3bL  = $1b06
    &bool r4bL  = $1b08
    &bool r5bL  = $1b0a
    &bool r6bL  = $1b0c
    &bool r7bL  = $1b0e
    &bool r8bL  = $1b10
    &bool r9bL  = $1b12
    &bool r10bL = $1b14
    &bool r11bL = $1b16
    &bool r12bL = $1b18
    &bool r13bL = $1b1a
    &bool r14bL = $1b1c
    &bool r15bL = $1b1e

    &bool r0bH  = $1b01
    &bool r1bH  = $1b03
    &bool r2bH  = $1b05
    &bool r3bH  = $1b07
    &bool r4bH  = $1b09
    &bool r5bH  = $1b0b
    &bool r6bH  = $1b0d
    &bool r7bH  = $1b0f
    &bool r8bH  = $1b11
    &bool r9bH  = $1b13
    &bool r10bH = $1b15
    &bool r11bH = $1b17
    &bool r12bH = $1b19
    &bool r13bH = $1b1b
    &bool r14bH = $1b1d
    &bool r15bH = $1b1f

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
