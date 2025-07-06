%option enable_floats
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    ubyte @shared thingIndex = 10
    uword[20] @shared dummy
    uword[10] @split curframesplit
    uword[10] @nosplit curframe
    uword p1, p2
    float f

    sub start() {
        classic()
        ; new()
    }

    sub classic() {
        txt.print("float pointer+1: ")
        txt.print_uwhex(&f, true)
        txt.spc()
        txt.print_uwhex(&f + 1, true)
        txt.nl()

        p1 = &curframesplit[thingIndex]
        p2 = &curframe[thingIndex]

        txt.print("&array (split): ")
        txt.print_uwhex(&curframesplit, true)
        txt.spc()
        txt.print_uwhex(p1, true)
        txt.spc()
        txt.print_uw(p1 - &curframesplit)
        txt.spc()
        txt.print_uwhex(p1 + &curframesplit, true)
        txt.nl()

        txt.print("&array (normal): ")
        txt.print_uwhex(&curframe, true)
        txt.spc()
        txt.print_uwhex(p2, true)
        txt.spc()
        txt.print_uw(p2 - &curframe)
        txt.spc()
        txt.print_uwhex(p2 + &curframe, true)
        txt.nl()
    }

    ; 6502 data size: $0251
}
