%import textio
%zeropage basicsafe
%option no_sysinit

main {
    ubyte bank

    extsub @bank bank  $a000 = routine_in_hiram(uword arg @AY) -> uword @AY

    sub start() {
        ; copy the routine into hiram bank 8
        bank = 8
        cx16.rambank(bank)
        sys.memcopy(&the_increment_routine, $a000, 255)
        cx16.rambank(1)

        txt.print("incremented by one=")
        txt.print_uw(routine_in_hiram(37119))
        txt.nl()
    }

    asmsub the_increment_routine(uword arg @AY) -> uword @AY {
        %asm {{
            clc
            adc  #1
            bcc  +
            iny
+           rts
        }}
    }
}
