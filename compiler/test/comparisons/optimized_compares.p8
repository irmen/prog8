%import textio
%zeropage basicsafe

main {
    sub start() {
        greater()
        greater_signed()
        less()
        less_signed()

        greatereq()
        greatereq_signed()
        lesseq()
        lesseq_signed()
    }

    sub value(ubyte arg) -> ubyte {
        cx16.r0++
        return arg
    }

    sub svalue(byte arg) -> byte {
        cx16.r0++
        return arg
    }

    sub greater () {
        ubyte b1 = 10
        ubyte b2 = 20
        ubyte b3 = 10

        txt.print(">(u):  101010: ")
        ubyte xx
        xx = b2>10
        txt.print_ub(xx)
        txt.spc()

        xx = b2>20
        txt.print_ub(xx)
        txt.spc()

        xx = b2>b1
        txt.print_ub(xx)
        txt.spc()

        xx = b3>b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2>value(10)
        txt.print_ub(xx)
        txt.spc()
        xx = b3>value(20)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

    sub greater_signed () {
        byte b1 = -20
        byte b2 = -10
        byte b3 = -20

        txt.print(">(s):  101010: ")
        ubyte xx
        xx = b2 > -20
        txt.print_ub(xx)
        txt.spc()

        xx = b2 > -10
        txt.print_ub(xx)
        txt.spc()

        xx = b2>b1
        txt.print_ub(xx)
        txt.spc()

        xx = b3>b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2>svalue(-20)
        txt.print_ub(xx)
        txt.spc()
        xx = b3>svalue(-10)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

    sub less () {
        ubyte b1 = 20
        ubyte b2 = 10
        ubyte b3 = 20

        txt.print("<(u):  101010: ")
        ubyte xx
        xx = b2<20
        txt.print_ub(xx)
        txt.spc()

        xx = b2<10
        txt.print_ub(xx)
        txt.spc()

        xx = b2<b1
        txt.print_ub(xx)
        txt.spc()

        xx = b3<b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2<value(20)
        txt.print_ub(xx)
        txt.spc()
        xx = b2<value(10)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

    sub less_signed () {
        byte b1 = -10
        byte b2 = -20
        byte b3 = -10

        txt.print("<(s):  101010: ")
        ubyte xx
        xx = b2 < -10
        txt.print_ub(xx)
        txt.spc()

        xx = b2 < -20
        txt.print_ub(xx)
        txt.spc()

        xx = b2<b1
        txt.print_ub(xx)
        txt.spc()

        xx = b3<b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2<svalue(-10)
        txt.print_ub(xx)
        txt.spc()
        xx = b3<svalue(-20)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

    sub greatereq () {
        ubyte b1 = 19
        ubyte b2 = 20
        ubyte b3 = 21
        ubyte b4 = 20

        txt.print(">=(u): 110110110: ")
        ubyte xx
        xx = b2>=19
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=20
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=21
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=b4
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=b3
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=value(19)
        txt.print_ub(xx)
        txt.spc()
        xx = b2>=value(20)
        txt.print_ub(xx)
        txt.spc()
        xx = b2>=value(21)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

    sub greatereq_signed () {
        byte b1 = -19
        byte b2 = -20
        byte b3 = -21
        byte b4 = -20

        txt.print(">=(s): 011011011: ")
        ubyte xx
        xx = b2>= -19
        txt.print_ub(xx)
        txt.spc()

        xx = b2>= -20
        txt.print_ub(xx)
        txt.spc()

        xx = b2>= -21
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=b4
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=b3
        txt.print_ub(xx)
        txt.spc()

        xx = b2>=value(-19)
        txt.print_ub(xx)
        txt.spc()
        xx = b2>=value(-20)
        txt.print_ub(xx)
        txt.spc()
        xx = b2>=value(-21)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

    sub lesseq () {
        ubyte b1 = 19
        ubyte b2 = 20
        ubyte b3 = 21
        ubyte b4 = 20

        txt.print("<=(u): 011011011: ")
        ubyte xx
        xx = b2<=19
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=20
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=21
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=b4
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=b3
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=value(19)
        txt.print_ub(xx)
        txt.spc()
        xx = b2<=value(20)
        txt.print_ub(xx)
        txt.spc()
        xx = b2<=value(21)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

    sub lesseq_signed () {
        byte b1 = -19
        byte b2 = -20
        byte b3 = -21
        byte b4 = -20

        txt.print("<=(s): 110110110: ")
        ubyte xx
        xx = b2<= -19
        txt.print_ub(xx)
        txt.spc()

        xx = b2<= -20
        txt.print_ub(xx)
        txt.spc()

        xx = b2<= -21
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=b1
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=b4
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=b3
        txt.print_ub(xx)
        txt.spc()

        xx = b2<=value(-19)
        txt.print_ub(xx)
        txt.spc()
        xx = b2<=value(-20)
        txt.print_ub(xx)
        txt.spc()
        xx = b2<=value(-21)
        txt.print_ub(xx)
        txt.spc()

        txt.nl()
    }

}
