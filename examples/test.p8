%import textio
%import diskio
%import floats
%import graphics
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {



    ; TODO COMPILER:  cycle_rate_ticks[ci]--  is broken!!!!!!!!!!


    ; TODO asmsub version generates LARGER CODE , why is this?
    sub vpoke(ubyte bank, uword address, ubyte value) {

    }

    asmsub vpokeasm(uword address @R0, ubyte bank @A, ubyte value @Y) {

    }

    sub start () {
        txt.print("hello\n")
    }
}
