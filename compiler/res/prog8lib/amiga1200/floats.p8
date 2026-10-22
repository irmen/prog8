%import shared_m68k_floats
%import textio

txt {
    %option merge, ignore_unused
    alias print_f = floats.print
}

floats {
    %option no_symbol_prefixing, ignore_unused
    %asminclude "library:shared_m68k_floats.asm"

    asmsub print(float value @FP0) {
        %asm {{
            move.l  d6,-(sp)        ; OS calls do not preserve d6
            jsr     floats._tostr
            move.l  a0,d1
            move.l  sys.DOSBase,a6
            jsr     -948(a6)          ; PutStr()
            move.l  (sp)+,d6
            rts
        }}
    }
}
