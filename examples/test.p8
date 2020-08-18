%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
        byte[] data = [11,22,33,44,55,66]
        ubyte[256] data256 = 1
        word[] dataw = [1111,2222,3333,4444,5555,6666]
        uword[128] dataw128 = 1

        ubyte u
        byte d
        word w
        uword uw

        for u in "hello" {
            c64scr.print_ub(u)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for d in data {
            c64scr.print_b(d)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        data256[0]=0
        data256[254]=254
        data256[255]=255
        for u in data256 {
            c64scr.print_ub(u)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for w in dataw {
            c64scr.print_w(w)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        dataw128[0] = 0
        dataw128[126] =126
        dataw128[127] =127
        for uw in dataw128 {
            c64scr.print_uw(uw)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
    }
}
