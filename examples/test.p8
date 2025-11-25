%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1, lv2

        lv1 = 1111
        lv2 = 8888

        cx16.r0r1sl = lv1
        cx16.r14r15sl = lv2 + cx16.r0r1sl

        txt.print_l(cx16.r14r15sl)


    }
}
