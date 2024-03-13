; conv_bug.p8
%import textio
%import conv
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte num8 = 99
        ubyte i
        ubyte jj = 99

        for i in 0 to 255 {
            txt.print(conv.str_ub(i))
            txt.spc()
            txt.print(conv.str_b(i as byte))
            txt.chrout(';')
            txt.nl()
        }
    }
}
