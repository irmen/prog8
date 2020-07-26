%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


main {

    float[]  fa = [1,2,3,4]

    sub start() {
        float x = 9.9

        fa[2] = 8.8

        p()

        ubyte b = 2
        fa[b] = 9.8
        p()
        fa[b] = 9.9
        p()

    }

    sub p() {
        byte i
        for i in 0 to len(fa)-1 {
            c64flt.print_f(fa[i])
            c64.CHROUT(',')
            c64.CHROUT(' ')
        }
        c64.CHROUT('\n')
    }
}
