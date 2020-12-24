%import textio
%import diskio
%import floats
%import graphics
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {


    ; TODO asmsub version generates LARGER CODE , why is this?
    sub vpoke(ubyte bank, uword address, ubyte value) {
        %asm {{
            rts
        }}
    }

    asmsub vpokeasm(uword address @R0, ubyte bank @A, ubyte value @Y) {
        %asm {{
            rts
        }}
    }

    sub start () {
        txt.chrout('!')
        ubyte bank = 1
        uword address = 1000
        ubyte value = 123
        bank++
        vpoke(bank, address, value)
        vpokeasm(address, bank, value)
    }
}
