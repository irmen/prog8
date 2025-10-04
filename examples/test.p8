%import textio
%import math
%import verafx
%zeropage basicsafe

main {
    %option verafxmuls

    sub start() {

        cx16.r5s = 22
        cx16.r6s = -999

        cx16.r0s = cx16.r5s * cx16.r6s
        txt.print_w(cx16.r0s)
        txt.nl()

        long lv = cx16.r5s * cx16.r6s
        txt.print_l(lv)
        txt.nl()


        cx16.r5s = 5555
        cx16.r6s = -9999
        lv = cx16.r5s * cx16.r6s
        txt.print_l(lv)
        txt.nl()
        lv = verafx.muls(cx16.r5s, cx16.r6s)
        txt.print_l(lv)
        txt.nl()
    }
}
