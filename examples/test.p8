%import textio

main {

    sub start() {

        ubyte xx = 1
        ubyte yy = 2
        byte b1
        byte b2=10

        ; result should be:  29  42  40  87  75  35

        xx=6
        yy=8
        yy = (xx+5)+(yy+10)
        txt.print_ub(yy)        ; 29
        txt.nl()

        xx=6
        yy=8
        yy = (xx*3)+(yy*3)
        txt.print_ub(yy)        ; 42
        txt.nl()

        b1=13
        b2=5
        b2 = (b1*5)-(b2*5)
        txt.print_b(b2)        ; 40
        txt.nl()

        b1=100
        b2=8
        b2 = (b1+5)-(b2+10)
        txt.print_b(b2)        ; 87
        txt.nl()

        b1=50
        b2=40
        b2 = (b1-5)+(b2-10)
        txt.print_b(b2)        ; 75
        txt.nl()

        b1=50
        b2=20
        b2 = (b1-5)-(b2-10)
        txt.print_b(b2)        ; 35
        txt.nl()

        repeat {
        }
    }
}
