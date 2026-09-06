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
            jsr floats._tostr
            move.l a0,d1
            move.l sys.DOSBase,a6
            jmp -948(a6)          ; PutStr()
        }}
    }
}
