%option no_symbol_prefixing, ignore_unused

; Tiny syslib for minimalistic programs that can run on a C64

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

    asmsub reset_system()  {
        ; Soft-reset the system back to initial power-on Basic prompt.
        %asm {{
            sei
            lda  #14
            sta  $01        ; bank the kernal in
            jmp  ($fffc)    ; reset vector
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
_exitcodeCarry = *+1
            lda  #0
            lsr  a
_exitcode = *+1
            lda  #0        ; exit code possibly modified in exit()
_exitcodeX = *+1
            ldx  #0
_exitcodeY = *+1
            ldy  #0
            rts
        }}
    }
}


cx16 {
    ; the sixteen virtual 16-bit registers that the CX16 has defined in the zeropage
    ; they are simulated on the C64 as well but their location in memory is different
    ; (because there's no room for them in the zeropage in the default configuration)
    ; Note that when using ZP options that free up more of the zeropage (such as %zeropage kernalsafe)
    ; there might be enough space to put them there after all, and the compiler will change these addresses!
    &uword r0  = $cfe0
    &uword r1  = $cfe2
    &uword r2  = $cfe4
    &uword r3  = $cfe6
    &uword r4  = $cfe8
    &uword r5  = $cfea
    &uword r6  = $cfec
    &uword r7  = $cfee
    &uword r8  = $cff0
    &uword r9  = $cff2
    &uword r10 = $cff4
    &uword r11 = $cff6
    &uword r12 = $cff8
    &uword r13 = $cffa
    &uword r14 = $cffc
    &uword r15 = $cffe

    &word r0s  = $cfe0
    &word r1s  = $cfe2
    &word r2s  = $cfe4
    &word r3s  = $cfe6
    &word r4s  = $cfe8
    &word r5s  = $cfea
    &word r6s  = $cfec
    &word r7s  = $cfee
    &word r8s  = $cff0
    &word r9s  = $cff2
    &word r10s = $cff4
    &word r11s = $cff6
    &word r12s = $cff8
    &word r13s = $cffa
    &word r14s = $cffc
    &word r15s = $cffe

    &ubyte r0L  = $cfe0
    &ubyte r1L  = $cfe2
    &ubyte r2L  = $cfe4
    &ubyte r3L  = $cfe6
    &ubyte r4L  = $cfe8
    &ubyte r5L  = $cfea
    &ubyte r6L  = $cfec
    &ubyte r7L  = $cfee
    &ubyte r8L  = $cff0
    &ubyte r9L  = $cff2
    &ubyte r10L = $cff4
    &ubyte r11L = $cff6
    &ubyte r12L = $cff8
    &ubyte r13L = $cffa
    &ubyte r14L = $cffc
    &ubyte r15L = $cffe

    &ubyte r0H  = $cfe1
    &ubyte r1H  = $cfe3
    &ubyte r2H  = $cfe5
    &ubyte r3H  = $cfe7
    &ubyte r4H  = $cfe9
    &ubyte r5H  = $cfeb
    &ubyte r6H  = $cfed
    &ubyte r7H  = $cfef
    &ubyte r8H  = $cff1
    &ubyte r9H  = $cff3
    &ubyte r10H = $cff5
    &ubyte r11H = $cff7
    &ubyte r12H = $cff9
    &ubyte r13H = $cffb
    &ubyte r14H = $cffd
    &ubyte r15H = $cfff

    &byte r0sL  = $cfe0
    &byte r1sL  = $cfe2
    &byte r2sL  = $cfe4
    &byte r3sL  = $cfe6
    &byte r4sL  = $cfe8
    &byte r5sL  = $cfea
    &byte r6sL  = $cfec
    &byte r7sL  = $cfee
    &byte r8sL  = $cff0
    &byte r9sL  = $cff2
    &byte r10sL = $cff4
    &byte r11sL = $cff6
    &byte r12sL = $cff8
    &byte r13sL = $cffa
    &byte r14sL = $cffc
    &byte r15sL = $cffe

    &byte r0sH  = $cfe1
    &byte r1sH  = $cfe3
    &byte r2sH  = $cfe5
    &byte r3sH  = $cfe7
    &byte r4sH  = $cfe9
    &byte r5sH  = $cfeb
    &byte r6sH  = $cfed
    &byte r7sH  = $cfef
    &byte r8sH  = $cff1
    &byte r9sH  = $cff3
    &byte r10sH = $cff5
    &byte r11sH = $cff7
    &byte r12sH = $cff9
    &byte r13sH = $cffb
    &byte r14sH = $cffd
    &byte r15sH = $cfff
}
