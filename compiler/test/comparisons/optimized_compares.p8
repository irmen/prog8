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
        ubyte @shared b1 = 10
        ubyte @shared b2 = 20
        ubyte @shared b3 = 10

        txt.print(">(u):  101010: ")
        bool xx
        xx = b2>10
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b3>b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>value(10)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b3>value(20)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

    sub greater_signed () {
        byte @shared b1 = -20
        byte @shared b2 = -10
        byte @shared b3 = -20

        txt.print(">(s):  101010: ")
        bool xx
        xx = b2 > -20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2 > -10
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b3>b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>svalue(-20)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b3>svalue(-10)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

    sub less () {
        ubyte @shared b1 = 20
        ubyte @shared b2 = 10
        ubyte @shared b3 = 20

        txt.print("<(u):  101010: ")
        bool xx
        xx = b2<20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<10
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b3<b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<value(20)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2<value(10)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

    sub less_signed () {
        byte @shared b1 = -10
        byte @shared b2 = -20
        byte @shared b3 = -10

        txt.print("<(s):  101010: ")
        bool xx
        xx = b2 < -10
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2 < -20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b3<b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<svalue(-10)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b3<svalue(-20)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

    sub greatereq () {
        ubyte @shared b1 = 19
        ubyte @shared b2 = 20
        ubyte @shared b3 = 21
        ubyte @shared b4 = 20

        txt.print(">=(u): 110110110: ")
        bool xx
        xx = b2>=19
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=21
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=b4
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=b3
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=value(19)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2>=value(20)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2>=value(21)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

    sub greatereq_signed () {
        byte @shared b1 = -19
        byte @shared b2 = -20
        byte @shared b3 = -21
        byte @shared b4 = -20

        txt.print(">=(s): 011011011: ")
        bool xx
        xx = b2>= -19
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>= -20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>= -21
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=b4
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=b3
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2>=svalue(-19)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2>=svalue(-20)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2>=svalue(-21)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

    sub lesseq () {
        ubyte @shared b1 = 19
        ubyte @shared b2 = 20
        ubyte @shared b3 = 21
        ubyte @shared b4 = 20

        txt.print("<=(u): 011011011: ")
        bool xx
        xx = b2<=19
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=21
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=b4
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=b3
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=value(19)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2<=value(20)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2<=value(21)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

    sub lesseq_signed () {
        byte @shared b1 = -19
        byte @shared b2 = -20
        byte @shared b3 = -21
        byte @shared b4 = -20

        txt.print("<=(s): 110110110: ")
        bool xx
        xx = b2<= -19
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<= -20
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<= -21
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=b1
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=b4
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=b3
        txt.print_ub(xx as ubyte)
        txt.spc()

        xx = b2<=svalue(-19)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2<=svalue(-20)
        txt.print_ub(xx as ubyte)
        txt.spc()
        xx = b2<=svalue(-21)
        txt.print_ub(xx as ubyte)
        txt.spc()

        txt.nl()
    }

}
