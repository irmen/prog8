%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {
        txt.print("hello\n")
    }
    sub convert_koalapic() {
        ubyte cx        ; TODO when making this a word, the code size increases drastically
        ubyte cy
        uword xx
        ubyte yy
        ubyte bb
        uword bitmap = $6000

        ubyte c0
        ubyte c1
        ubyte c2
        ubyte c3

        for cy in 0 to 24 {
            for cx in 0 to 39 {
                for bb in 0 to 7 {
                    yy = cy * 8 + bb
                    xx = cx * $0008
                    graphics.plot(xx, yy)
                    graphics.plot(xx+1, yy)
                    graphics.plot(xx+2, yy)
                    graphics.plot(xx+3, yy)
                    graphics.plot(xx+4, yy)
                    graphics.plot(xx+5, yy)
                    graphics.plot(xx+6, yy)
                    graphics.plot(xx+7, yy)
                }
            }
        }
    }
}
