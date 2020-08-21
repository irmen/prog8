%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        uword zz = $ee22

        const uword SCREEN1 = $E000
        const uword CHARSET = $E800
        const ubyte PAGE1 = ((SCREEN1 >> 6) & $F0) | ((CHARSET >> 10) & $0E)
        ubyte cmsb = msb(zz)
        ubyte clsb = lsb(zz)

        c64scr.print("\ncmsb=")
        c64scr.print_ubhex(cmsb, false)
        c64scr.print("\nclsb=")
        c64scr.print_ubhex(clsb, false)
        c64scr.print("\nPAGE1=")
        ubyte p1 = PAGE1            ; TODO fix type error of PAGE1
        c64scr.print_ubhex(PAGE1, false)
    }
}
