; Prog8 definitions for the Amiga500 target

%option no_symbol_prefixing, ignore_unused
%import shared_m68k_memory_routines

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 50         ;  compilation target specifier.

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
    const long  MIN_LONG     = -2147483648
    const long  MAX_LONG     = 2147483647
    ; MIN_FLOAT and MAX_FLOAT are defined in the floats module if imported


    sub  reset_system()  {
        ; TODO
    }

    sub poweroff_system() {
        ; TODO
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        ; TODO implement the wait
    }

    sub exit(ubyte returnvalue) {
        ; -- immediately exit the program with a return code in the D0 register
        %asm {{
            moveq.l  #0,d0
            move.b   sys.exit.returnvalue,d0
            illegal   ; TODO implement the exit
        }}
    }

    sub set_carry() {
        %asm {{
            moveq  #1,d0
            move.w  d0,ccr
        }}
    }

    sub clear_carry() {
        %asm {{
            moveq  #0,d0
            move.w  d0,ccr
        }}
    }


    sub clear_irqd() {
        ; TODO
    }

    sub set_irqd() {
        ; TODO
    }

    inline asmsub progstart() -> long @A0 {
        %asm {{
            lea  prog8_program_start,a0
        }}
    }

    inline asmsub progend() -> long @A0 {
        %asm {{
            lea  prog8_program_end,a0
        }}
    }
}
