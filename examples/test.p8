%import textio
%import diskio
%import floats
%import graphics
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {


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
        uword bank = 1
        uword address = 1000
        ubyte value = 123
        bank++

        test_stack.test()
        vpoke(lsb(bank), address, value)
        test_stack.test()
        vpokeasm(address, lsb(bank), value)      ; TODO generates params on stack if expression is used such as lsb(bank).  CHECK STACK UNWINDING!!!
        test_stack.test()
        ; TODO also see if we can do this via R0-R15 temp registers rather than using the estack???
    }
}
