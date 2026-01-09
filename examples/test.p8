%import textio
%import strings
%import floats
%zeropage basicsafe

main {

    sub f_seek(long position) {
        ubyte[6] command = ['p',0,0,0,0,0]

        pokel(&command+2, position)
        pokew(&command+2, cx16.r0)
        poke(&command+2, cx16.r0L)
        pokebool(&command+2, cx16.r0bL)
    }


    sub start() {
        long @shared lv1,lv2
        ^^long lptr = 20000
        uword @shared addr = &lv2
        ubyte @shared bytevar
        float @shared fv
        bool @shared boolvar

        f_seek(12345678)

        lv2 = $aabbccdd
        pokel(2000, $11223344)
        pokel(2000, lv2)
        pokel(addr, $11223344)
        pokel(addr, lv2)
        poke(&bytevar, 99)
        pokew(&addr, 9999)
        pokel(&lv2, $99887766)
        pokef(&fv, 1.234)
        @(&bytevar) = 99

        txt.print_ulhex(lv2, true)
        txt.nl()


        @(&bytevar+4) = 99
        poke(&bytevar+4, 99)
        pokebool(&boolvar+4, true)
        pokew(&addr+4, 9999)
        pokel(&lv2+4, $aabbccdd)
        pokef(&fv+4, 1.234)

        @(&bytevar+4) = bytevar
        poke(&bytevar+4, bytevar)
        pokebool(&boolvar+4, boolvar)
        pokew(&addr+4, addr)
        pokel(&lv2+4, lv2)
        pokef(&fv+4, fv)

        ; TODO not optimized yet:
        poke(&bytevar + bytevar, 99)
        pokebool(&boolvar+ bytevar, true)
        pokew(&addr+ bytevar, 9999)
        pokel(&lv2+ bytevar, $66778899)
        pokef(&fv+ bytevar, 1.234)

        cx16.r0L = @(&bytevar+4)
        bytevar = peek(&bytevar+4)
        boolvar = peekbool(&boolvar+4)
        addr = peekw(&addr+4)
        lv2 = peekl(&lv2+4)
        fv = peekf(&fv+4)

        ; TODO not optimized yet:
        cx16.r0L = @(&bytevar+bytevar)
        bytevar = peek(&bytevar+bytevar)
        boolvar = peekbool(&boolvar+bytevar)
        addr = peekw(&addr+bytevar)
        lv2 = peekl(&lv2+bytevar)
        fv = peekf(&fv+bytevar)
    }
}
