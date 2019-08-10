%import c64utils
%import c64lib
%import c64flt
%zeropage dontuse


main {

    sub start() {

        word[] warr = [1111, 2222, 3333, 4444]
        byte[] barr = [11, 22, 33, 44]

        word ww = 9999
        byte bb = 99

        c64scr.print_b(barr[2])
        c64.CHROUT('\n')

        barr[2] = 55
        c64scr.print_b(barr[2])
        c64.CHROUT('\n')

        barr[2] = bb
        c64scr.print_b(barr[2])
        c64.CHROUT('\n')

        @($0400+72) = X

        c64scr.print_w(warr[2])
        c64.CHROUT('\n')

        warr[2] = 5555
        c64scr.print_w(warr[2])
        c64.CHROUT('\n')

        warr[2] = ww
        c64scr.print_w(warr[2])
        c64.CHROUT('\n')

        @($0400+73) = X
    }
}
