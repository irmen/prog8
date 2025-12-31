%option no_symbol_prefixing, ignore_unused

; Tiny syslib for minimalistic programs that can run on a X16

%import shared_sys_functions

sys {
    extsub $FFD2 = CHROUT(ubyte character @ A)           ; output a character

    ; these push/pop routines are always required by the compiler:

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

    inline asmsub pushl(long value @R0R1_32) {
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

    inline asmsub popl() -> long @R0R1_32 {
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

    asmsub reset_system() {
        ; Soft-reset the system back to initial power-on Basic prompt.
        ; We do this via the SMC so that a true reset is performed that also resets the Vera fully.
        ; (note: this is an asmsub on purpose! don't change into a normal sub)
        %asm {{
            sei
            ldx  #$42
            ldy  #2
            lda  #0
            jmp  $fec9    ; i2c_write_byte
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
}


p8_sys_startup {
    %option force_output

    asmsub  init_system() {
        %asm {{
            rts
        }}
    }

    asmsub  init_system_phase2() {
        %asm {{
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


cx16 {
    ; the sixteen virtual 16-bit registers that the CX16 has defined in the zeropage
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

    ; signed long versions
    &long r0r1sl  = $0002
    &long r2r3sl  = $0006
    &long r4r5sl  = $000a
    &long r6r7sl  = $000e
    &long r8r9sl  = $0012
    &long r10r11sl = $0016
    &long r12r13sl = $001a
    &long r14r15sl = $001e

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
}
