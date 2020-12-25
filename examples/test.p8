%import textio
%import diskio
%import floats
%import graphics
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {

    asmsub derp(ubyte value @A, uword address @R0) {
        %asm {{
            rts
        }}
    }

    sub start () {
        uword bank = 1
        uword address = 1000
        ubyte value = 123
        bank++

        derp(value, address)
        cx16.vpoke(lsb(bank), address, value)

        test_stack.test()
    }
}
