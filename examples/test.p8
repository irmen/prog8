%import textio
%import floats
%zeropage basicsafe

main {

    long @shared lv1,lv2
    ^^long lptr = 20000
    uword @shared addr = &lv2
    ubyte @shared bytevar
    bool @shared boolvar
    float @shared fv

    sub start() {
        f_seek(12345678)

        no_offset()
        poke_constoffset()
        poke_varoffset()
        peek_constoffset()
        peek_varoffset()

        sub no_offset() {
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
        }

        sub poke_constoffset() {
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
        }

        sub poke_varoffset() {
            poke(&bytevar + bytevar, 99)
            pokebool(&boolvar+ bytevar, true)
            pokew(&addr+ bytevar, 9999)
            pokel(&lv2+ bytevar, $66778899)
            pokef(&fv+ bytevar, 1.234)
        }

        sub peek_constoffset() {
            cx16.r0L = @(&bytevar+4)
            bytevar = peek(&bytevar+4)

            ; TODO not optimized yet:
            boolvar = peekbool(&boolvar+4)
            addr = peekw(&addr+4)
            lv2 = peekl(&lv2+4)
            fv = peekf(&fv+4)
        }

        sub peek_varoffset() {
            cx16.r0L = @(&bytevar+bytevar)
            bytevar = peek(&bytevar+bytevar)

            ; TODO not optimized yet:
            boolvar = peekbool(&boolvar+bytevar)
            addr = peekw(&addr+bytevar)
            lv2 = peekl(&lv2+bytevar)
            fv = peekf(&fv+bytevar)
        }
    }

    sub f_seek(long position) {
        ubyte[6] command = ['p',0,0,0,0,0]

        pokel(&command+2, position)
        pokew(&command+2, cx16.r0)
        poke(&command+2, cx16.r0L)
        pokebool(&command+2, cx16.r0bL)

        pokel(&command+cx16.r0L, position)
        pokew(&command+cx16.r0L, cx16.r0)
        poke(&command+cx16.r0L, cx16.r0L)
        pokebool(&command+cx16.r0L, cx16.r0bL)
    }
}
