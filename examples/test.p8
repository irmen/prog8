%import textio
%zeropage basicsafe


main {
    sub start() {

        ubyte c
        ubyte radius = 1
        ubyte[] circle_radius = [5,10,15,20,25,30]

        for c in 0 to len(circle_radius)-1 {
            if distance(c) < (radius as uword) + circle_radius[c]
                txt.chrout('y')
            else
                txt.chrout('n')

            cx16.r15 = (radius as uword) + circle_radius[c]
            if distance(c) < cx16.r15
                txt.chrout('y')
            else
                txt.chrout('n')
            txt.nl()
        }
    }

    sub distance(ubyte cix) -> uword {
        uword sqx = cix+10
        return sqrt16(sqx*sqx)
    }
}
