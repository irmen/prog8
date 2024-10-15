%import textio
%option no_sysinit
%zeropage basicsafe

main {

    bool[] barray =  [true, false, true, false]
    bool value1
    bool value2 = barray[cx16.r0L]
    bool value3 = true
    bool value4 = false

    sub start() {
        txt.print_bool(value1)
        txt.spc()
        txt.print_bool(value2)
        txt.spc()
        txt.print_bool(value3)
        txt.nl()
        txt.print_bool(value4)
        txt.nl()
    }
}
