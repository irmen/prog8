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
            move.l a2,-(sp)
            jsr floats._tostr
            move.l a0,a2
.loop:
            move.b (a2)+,d0
            beq .done
            move.l sys.DOSBase,a6
            move.b d0,-(sp)
            jsr -60(a6)
            move.l d0,d1
            move.l sp,d2
            moveq #1,d3
            jsr -48(a6)
            addq.l #2,sp
            bra .loop
.done:
            move.l (sp)+,a2
            rts
        }}
    }
}
