%import textio
%zeropage basicsafe

main {
    struct Voice {
        uword f1
        uword f2
    }
    sub start()  {
        ^^Voice @shared vptr = $4000
        vptr.f1 = $1234
        vptr.f2 = $abcd

        ubyte b1,b2,b3,b4

        test1()
        printbytes()
        test2()
        printbytes()

        sub test1() {
            b1 = msb(vptr.f1)
            b2 = lsb(vptr.f1)
            b3 = msb(vptr.f2)
            b4 = lsb(vptr.f2)
        }

        sub test2() {
            b1 = @(&vptr.f1+1)
            b2 = @(&vptr.f1)
            b3 = @(&vptr.f2+1)
            b4 = @(&vptr.f2)
        }

        sub printbytes() {
            txt.print_ubhex(b1, false)
            txt.spc()
            txt.print_ubhex(b2, false)
            txt.nl()
            txt.print_ubhex(b3, false)
            txt.spc()
            txt.print_ubhex(b4, false)
            txt.nl()
        }
    }
}
