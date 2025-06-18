%option no_symbol_prefixing, ignore_unused

; Tiny syslib for minimalistic programs that can run on a PET

sys {
    extsub $FFD2 = CHROUT(ubyte character @ A)           ; output a character

    ; these push/pop routines are always required by the compiler:

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
            jmp  ($fffc)    ; reset vector
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
    ; they are simulated on the PET as well but their location in memory is different
    ; (because there's no room for them in the zeropage)
    ; we select the top page of RAM (assume 32Kb)
    &uword r0  = $7fe0
    &uword r1  = $7fe2
    &uword r2  = $7fe4
    &uword r3  = $7fe6
    &uword r4  = $7fe8
    &uword r5  = $7fea
    &uword r6  = $7fec
    &uword r7  = $7fee
    &uword r8  = $7ff0
    &uword r9  = $7ff2
    &uword r10 = $7ff4
    &uword r11 = $7ff6
    &uword r12 = $7ff8
    &uword r13 = $7ffa
    &uword r14 = $7ffc
    &uword r15 = $7ffe

    ; signed word versions
    &word r0s  = $7fe0
    &word r1s  = $7fe2
    &word r2s  = $7fe4
    &word r3s  = $7fe6
    &word r4s  = $7fe8
    &word r5s  = $7fea
    &word r6s  = $7fec
    &word r7s  = $7fee
    &word r8s  = $7ff0
    &word r9s  = $7ff2
    &word r10s = $7ff4
    &word r11s = $7ff6
    &word r12s = $7ff8
    &word r13s = $7ffa
    &word r14s = $7ffc
    &word r15s = $7ffe

    ; ubyte versions (low and high bytes)
    &ubyte r0L  = $7fe0
    &ubyte r1L  = $7fe2
    &ubyte r2L  = $7fe4
    &ubyte r3L  = $7fe6
    &ubyte r4L  = $7fe8
    &ubyte r5L  = $7fea
    &ubyte r6L  = $7fec
    &ubyte r7L  = $7fee
    &ubyte r8L  = $7ff0
    &ubyte r9L  = $7ff2
    &ubyte r10L = $7ff4
    &ubyte r11L = $7ff6
    &ubyte r12L = $7ff8
    &ubyte r13L = $7ffa
    &ubyte r14L = $7ffc
    &ubyte r15L = $7ffe

    &ubyte r0H  = $7fe1
    &ubyte r1H  = $7fe3
    &ubyte r2H  = $7fe5
    &ubyte r3H  = $7fe7
    &ubyte r4H  = $7fe9
    &ubyte r5H  = $7feb
    &ubyte r6H  = $7fed
    &ubyte r7H  = $7fef
    &ubyte r8H  = $7ff1
    &ubyte r9H  = $7ff3
    &ubyte r10H = $7ff5
    &ubyte r11H = $7ff7
    &ubyte r12H = $7ff9
    &ubyte r13H = $7ffb
    &ubyte r14H = $7ffd
    &ubyte r15H = $7fff

    ; signed byte versions (low and high bytes)
    &byte r0sL  = $7fe0
    &byte r1sL  = $7fe2
    &byte r2sL  = $7fe4
    &byte r3sL  = $7fe6
    &byte r4sL  = $7fe8
    &byte r5sL  = $7fea
    &byte r6sL  = $7fec
    &byte r7sL  = $7fee
    &byte r8sL  = $7ff0
    &byte r9sL  = $7ff2
    &byte r10sL = $7ff4
    &byte r11sL = $7ff6
    &byte r12sL = $7ff8
    &byte r13sL = $7ffa
    &byte r14sL = $7ffc
    &byte r15sL = $7ffe

    &byte r0sH  = $7fe1
    &byte r1sH  = $7fe3
    &byte r2sH  = $7fe5
    &byte r3sH  = $7fe7
    &byte r4sH  = $7fe9
    &byte r5sH  = $7feb
    &byte r6sH  = $7fed
    &byte r7sH  = $7fef
    &byte r8sH  = $7ff1
    &byte r9sH  = $7ff3
    &byte r10sH = $7ff5
    &byte r11sH = $7ff7
    &byte r12sH = $7ff9
    &byte r13sH = $7ffb
    &byte r14sH = $7ffd
    &byte r15sH = $7fff

    ; boolean versions
    &bool r0bL  = $7fe0
    &bool r1bL  = $7fe2
    &bool r2bL  = $7fe4
    &bool r3bL  = $7fe6
    &bool r4bL  = $7fe8
    &bool r5bL  = $7fea
    &bool r6bL  = $7fec
    &bool r7bL  = $7fee
    &bool r8bL  = $7ff0
    &bool r9bL  = $7ff2
    &bool r10bL = $7ff4
    &bool r11bL = $7ff6
    &bool r12bL = $7ff8
    &bool r13bL = $7ffa
    &bool r14bL = $7ffc
    &bool r15bL = $7ffe

    &bool r0bH  = $7fe1
    &bool r1bH  = $7fe3
    &bool r2bH  = $7fe5
    &bool r3bH  = $7fe7
    &bool r4bH  = $7fe9
    &bool r5bH  = $7feb
    &bool r6bH  = $7fed
    &bool r7bH  = $7fef
    &bool r8bH  = $7ff1
    &bool r9bH  = $7ff3
    &bool r10bH = $7ff5
    &bool r11bH = $7ff7
    &bool r12bH = $7ff9
    &bool r13bH = $7ffb
    &bool r14bH = $7ffd
    &bool r15bH = $7fff
}
