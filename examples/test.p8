%import c64utils
%import c64flt
%option enable_floats
%zeropage basicsafe

main {

    sub start() {
        str meuk = "hello"
        ubyte bb1 = 99
        ubyte key=c64.GETIN()
        ubyte[] zzzz = [1,2,3]

        A = len(meuk)
        A = msb(meuk)
        ; A = strlen(meuk)
        func(meuk, zzzz)
        func(zzzz, "zzzz")
        func("hello2", meuk)
    }

    sub func(uword addr1, uword addr2) {
        c64scr.print_uwhex(addr1, 1)
        c64.CHROUT('\n')
        c64scr.print_uwhex(addr2, 1)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
    }


 }
