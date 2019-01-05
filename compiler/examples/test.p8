%import c64utils

~ main {

    sub start() {
        memory byte b1 = $c000
        memory byte b2 = $c001
        memory word w1 = $c002
        memory word w2 = $c004

        float x =4.34
        ubyte xx= x as ubyte
        float y = x * 5.55
        y =xx as float

        b1=50
        b2=-50
        w1=100
        w2=-100

        c64scr.print_b(b1)
        c64.CHROUT('/')
        lsr(b1)
        c64scr.print_b(b1)
        c64.CHROUT('\n')
        c64scr.print_b(b2)
        c64.CHROUT('/')
        lsr(b2)
        c64scr.print_b(b2)
        c64.CHROUT('\n')
        c64scr.print_w(w1)
        c64.CHROUT('/')
        lsr(w1)
        c64scr.print_w(w1)
        c64.CHROUT('\n')
        c64scr.print_w(w2)
        c64.CHROUT('/')
        lsr(w2)
        c64scr.print_w(w2)
        c64.CHROUT('\n')



    }
}
