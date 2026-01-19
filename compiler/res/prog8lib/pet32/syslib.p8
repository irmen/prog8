; Prog8 definitions for the Commodore PET
; Including memory registers, I/O registers, Basic and Kernal subroutines.
; see: https://www.pagetable.com/?p=926  ,  http://www.zimmers.net/cbmpics/cbm/PETx/petmem.txt

%option no_symbol_prefixing, ignore_unused

cbm {
    ; Commodore (CBM) common variables, vectors and kernal routines

        &ubyte  TIME_HI         = $8d       ; software jiffy clock, hi byte
        &ubyte  TIME_MID        = $8e       ;  .. mid byte
        &ubyte  TIME_LO         = $8f       ;    .. lo byte. Updated by IRQ every 1/60 sec
        &ubyte  STATUS          = $96       ; kernal status variable for I/O
        &ubyte  FNLEN           = $D1       ; Length of filename
        &ubyte  LFN             = $D2       ; Current Logical File Number
        &ubyte  SECADR          = $D3       ; Secondary address
        &ubyte  DEVNUM          = $D4       ; Device number
        &ubyte  CURS_X          = $C6       ; Cursor column
        &ubyte  CURS_Y          = $D8       ; Cursor row
        &ubyte  FNADR           = $DA       ; Pointer to file name

        &uword  CINV            = $0090     ; IRQ vector (in ram)
        &uword  CBINV           = $0092     ; BRK vector (in ram)
        &uword  NMINV           = $0094     ; NMI vector (in ram)

        &uword  NMI_VEC         = $FFFA     ; 6502 nmi vector, determined by the kernal if banked in
        &uword  RESET_VEC       = $FFFC     ; 6502 reset vector, determined by the kernal if banked in
        &uword  IRQ_VEC         = $FFFE     ; 6502 interrupt vector, determined by the kernal if banked in

        ; the default addresses for the character screen chars and colors
        const  uword  Screen    = $8000     ; to have this as an array[40*25] the compiler would have to support array size > 255


extsub $FFC6 = CHKIN(ubyte logical @ X) clobbers(A,X) -> bool @Pc    ; define an input channel
extsub $FFC9 = CHKOUT(ubyte logical @ X) clobbers(A,X)          ; define an output channel
extsub $FFCC = CLRCHN() clobbers(A,X)                           ; restore default devices
extsub $FFCF = CHRIN() clobbers(X, Y) -> ubyte @ A              ; input a character (for keyboard, read a whole line from the screen) A=byte read.
extsub $FFD2 = CHROUT(ubyte character @ A)                           ; output a character
extsub $FFE1 = STOP() clobbers(X) -> bool @ Pz, ubyte @ A       ; check the STOP key (and some others in A)     also see STOP2
extsub $FFE4 = GETIN() clobbers(X,Y) -> bool @Pc, ubyte @ A     ; get a character       also see GETIN2
extsub $FFE7 = CLALL() clobbers(A,X)                            ; close all files
extsub $FFEA = UDTIM() clobbers(A,X)                            ; update the software clock


extsub $F563 = OPEN() clobbers(X,Y) -> bool @Pc, ubyte @A       ; open a logical file
extsub $F2E2 = CLOSE(ubyte logical @ A) clobbers(A,X,Y)         ; close a logical file

asmsub READST() -> ubyte @ A {
    ; read io status
    %asm {{
        lda     STATUS
        rts
    }}
}

asmsub SETLFS(ubyte logical @ A, ubyte device @ X, ubyte secondary @ Y)  {
    ; set logical file parameters
    %asm {{
        sta     LFN             ; LFN
        stx     DEVNUM          ; Device address
        sty     SECADR          ; Secondary address
        rts
    }}
}

asmsub SETNAM(ubyte namelen @ A, str filename @ XY) {
    ; set filename parameters
    %asm {{
        sta     FNLEN
        stx     FNADR
        sty     FNADR+1
        rts
    }}
}

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

asmsub SETTIM(ubyte low @ A, ubyte middle @ X, ubyte high @ Y) {
    ; PET stub to set the software clock
    %asm {{
        sty  TIME_HI
        stx  TIME_MID
        sta  TIME_LO
        rts
    }}
}

asmsub RDTIM() -> ubyte @ A, ubyte @ X, ubyte @ Y {
    ; PET stub to read the software clock (A=lo,X=mid,Y=high)
    %asm {{
        ldy  TIME_HI
        ldx  TIME_MID
        lda  TIME_LO
        rts
    }}
}

asmsub RDTIM16() clobbers(X) -> uword @AY {
    ; --  like RDTIM() but only returning the lower 16 bits in AY for convenience
    %asm {{
        lda  TIME_LO
        ldy  TIME_MID
        rts
    }}
}

asmsub SETTIML(long jiffies @R0R1) {
    ; -- just like SETTIM, but with a single 32 bit (lower 24 bits used) argument.
    %asm {{
        lda  cx16.r0
        ldx  cx16.r0+1
        ldy  cx16.r0+2
        jmp  SETTIM
    }}
}

asmsub RDTIML() clobbers(X) -> long @R0R1 {
    ; --  like RDTIM() and returning the timer value as a 32 bit (lower 24 bits used) value.
    %asm {{
        jsr  RDTIM
        sta  cx16.r0
        stx  cx16.r0+1
        sty  cx16.r0+2
        lda  #0
        sta  cx16.r0+3
        rts
    }}
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

pet {
    const uword  VIA1_BASE   = $e840                  ;VIA 6522
    &ubyte  via1prb    = VIA1_BASE + 0
    &ubyte  via1pra    = VIA1_BASE + 1
    &ubyte  via1ddrb   = VIA1_BASE + 2
    &ubyte  via1ddra   = VIA1_BASE + 3
    &uword  via1t1     = VIA1_BASE + 4
    &ubyte  via1t1l    = VIA1_BASE + 4
    &ubyte  via1t1h    = VIA1_BASE + 5
    &uword  via1t1lw   = VIA1_BASE + 6
    &ubyte  via1t1ll   = VIA1_BASE + 6
    &ubyte  via1t1lh   = VIA1_BASE + 7
    &uword  via1t2     = VIA1_BASE + 8
    &ubyte  via1t2l    = VIA1_BASE + 8
    &ubyte  via1t2h    = VIA1_BASE + 9
    &ubyte  via1sr     = VIA1_BASE + 10
    &ubyte  via1acr    = VIA1_BASE + 11
    &ubyte  via1pcr    = VIA1_BASE + 12
    &ubyte  via1ifr    = VIA1_BASE + 13
    &ubyte  via1ier    = VIA1_BASE + 14
    &ubyte  via1ora    = VIA1_BASE + 15

    extsub $ff93 = concat() clobbers (A,X,Y)
    extsub $ff96 = dopen() clobbers (A,X,Y)
    extsub $ff99 = dclose() clobbers (A,X,Y)
    extsub $ff9c = record() clobbers (A,X,Y)
    extsub $ff9f = header() clobbers (A,X,Y)
    extsub $ffa2 = collect() clobbers (A,X,Y)
    extsub $ffa5 = backup() clobbers (A,X,Y)
    extsub $ffa8 = copy() clobbers (A,X,Y)
    extsub $ffab = append() clobbers (A,X,Y)
    extsub $ffae = dsave() clobbers (A,X,Y)
    extsub $ffb1 = dload() clobbers (A,X,Y)
    extsub $ffb4 = catalog() clobbers (A,X,Y)
    extsub $ffb7 = rename() clobbers (A,X,Y)
    extsub $ffba = scratch() clobbers (A,X,Y)
    extsub $ffc0 = open() clobbers (A,X,Y)
    extsub $ffc3 = close() clobbers (A,X,Y)
    extsub $ffd5 = load() clobbers (A,X,Y)
    extsub $ffd8 = save() clobbers (A,X,Y)
    extsub $ffdb = verify() clobbers (A,X,Y)
    extsub $ffde = sys() clobbers (A,X,Y)

}

%import shared_sys_functions

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 32         ;  compilation target specifier.  255=virtual, 128=C128, 64=C64, 32=PET, 16=CommanderX16, 8=atari800XL, 7=Neo6502

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


    asmsub  reset_system()  {
        ; Soft-reset the system back to initial power-on Basic prompt.
        %asm {{
            sei
            jmp  (cbm.RESET_VEC)
        }}
    }

    asmsub waitvsync() clobbers(A) {
        ; --- busy wait till the next vsync has occurred (approximately), without depending on custom irq handling.
        ;     Note: on PET this simply waits until the next jiffy clock update, I don't know if a true vsync is possible there
        %asm {{
            lda  #1
            ldy  #0
            jmp  wait
        }}
    }

    asmsub wait(uword jiffies @AY) {
        ; --- wait approximately the given number of jiffies (1/60th seconds) (N or N+1)
        ;     note: the system irq handler has to be active for this to work as it depends on the system jiffy clock
        %asm {{
            stx  P8ZP_SCRATCH_B1
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1

_loop       lda  P8ZP_SCRATCH_W1
            ora  P8ZP_SCRATCH_W1+1
            bne  +
            ldx  P8ZP_SCRATCH_B1
            rts

+           lda  cbm.TIME_LO
            sta  P8ZP_SCRATCH_B1
-           lda  cbm.TIME_LO
            cmp  P8ZP_SCRATCH_B1
            beq  -

            lda  P8ZP_SCRATCH_W1
            bne  +
            dec  P8ZP_SCRATCH_W1+1
+           dec  P8ZP_SCRATCH_W1
            jmp  _loop
        }}
    }

    sub disable_caseswitch() {
        ; PET doesn't have a key to swap case, so no-op
    }

    sub enable_caseswitch() {
        ; PET doesn't have a key to swap case, so no-op
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

    ; signed long versions
    &long r0r1sl  = $7fe0
    &long r2r3sl  = $7fe4
    &long r4r5sl  = $7fe8
    &long r6r7sl  = $7fec
    &long r8r9sl  = $7fe0
    &long r10r11sl = $7fe4
    &long r12r13sl = $7fe8
    &long r14r15sl = $7fec

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
    ; Uppercase charset is activated.
    %asm {{
        sei
        lda  #142
        jsr  cbm.CHROUT     ; uppercase
        lda  #147
        jsr  cbm.CHROUT     ; clear screen
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
