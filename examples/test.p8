%import floats
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    struct Task {
        bool dummy1
        ^^ubyte line
        ^^uword wptr
        ^^long lptr
        ^^float fptr
    }

    sub start() {

        ^^uword g_wptr
        cx16.r1 = g_wptr^^


        ^^Task tptr = 5000
        tptr.line = "irmen"
        tptr.wptr = 6000
        tptr.lptr = 7000
        tptr.fptr = 8000

        pokew(6000, 12345)
        pokew(6002, 54321)
        pokel(7000, 9988766)
        pokel(7004, 22334455)
        pokef(8000, 111.222)
        pokef(8000+sizeof(float), 333.444)

        txt.chrout(tptr.line[0])
        txt.chrout(' ')
        txt.chrout(tptr.line^^)
        txt.chrout(' ')
        txt.chrout(@(tptr.line))
        txt.nl()

        txt.print_uw(tptr.wptr[0])
        txt.chrout(' ')
        txt.print_uw(tptr.wptr^^)
        txt.chrout(' ')
        txt.print_uw(peekw(tptr.wptr))
        txt.nl()

        txt.print_l(tptr.lptr[0])
        txt.chrout(' ')
        txt.print_l(tptr.lptr^^)
        txt.chrout(' ')
        txt.print_l(peekl(tptr.lptr))
        txt.nl()

        txt.print_f(tptr.fptr[0])
        txt.chrout(' ')
        txt.print_f(tptr.fptr^^)
        txt.chrout(' ')
        txt.print_f(peekf(tptr.fptr))
        txt.nl()

        txt.print_uw(tptr.wptr[1])
        txt.chrout(' ')
        txt.print_uw(peekw(tptr.wptr+1))
        txt.nl()

        txt.print_l(tptr.lptr[1])
        txt.chrout(' ')
        txt.print_l(peekl(tptr.lptr+1))
        txt.nl()

        txt.print_f(tptr.fptr[1])
        txt.chrout(' ')
        txt.print_f(peekf(tptr.fptr+1))
        txt.nl()

        txt.chrout(tptr.line[0])
        txt.chrout(tptr.line[1])
        txt.chrout(tptr.line[2])
        txt.chrout(tptr.line[3])
        txt.chrout(tptr.line[4])
        txt.nl()
    }
}
