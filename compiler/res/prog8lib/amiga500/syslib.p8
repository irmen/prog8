; Prog8 definitions for the Amiga500 target

%option no_symbol_prefixing, ignore_unused
%import shared_m68k_memory_routines

%import dos

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


    ; SysBase/ExecBase is always simply available at 4.w
    pointer @shared DOSBase
    pointer @shared GfxBase
    pointer @shared IntuitionBase
    pointer @shared IconBase


    sub  reset_system()  {
        %asm {{
            lea  2.l,a0
            reset
            jmp (a0)
        }}
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        dos.Delay(jiffies)
    }

    sub exit(ubyte returnvalue) {
        ; -- immediately exit the program with a return code in the D0 register
        %asm {{
            moveq.l  #0,d0
            move.b   sys.exit.returnvalue,d0
            move.l   p8_sys_startup.orig_stackpointer,sp
            jmp  p8_sys_startup.cleanup_at_exit
        }}
    }

    sub set_carry() {
        %asm {{
            ; set both C (comparison carry) and X (rotate carry) bits
            moveq  #$11,d0
            move.w  d0,ccr
        }}
    }

    sub clear_carry() {
        %asm {{
            ; clear C and X bits
            moveq  #0,d0
            move.w  d0,ccr
        }}
    }

    asmsub exec_version() -> uword @D0 {
        ; Returns the exec library version.
        ; This corresponds to the Kickstart/ROM version:
        ;   30 = KS 1.0, 33 = KS 1.2, 34 = KS 1.3,
        ;   37 = KS 2.0, 39 = KS 3.0, 40 = KS 3.1
        %asm {{
            move.l  $4,a0
            move.w  20(a0),d0
            rts
        }}
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
