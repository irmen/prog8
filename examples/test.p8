                %import math
                %import textio

main {

    sub start() {

            ubyte @shared st = 2

            on st goto (lblA, lblB, lblC, lblD)
            lblA:
                txt.print("path a\n")
                goto lblDone
            lblB:
                txt.print("path b\n")
                goto 2 goto
            lblC:
                txt.print("path c\n")
                goto lblDone
            lblD:
                txt.print("path d\n")

            lblDone:
                txt.print("done\n")
    }
}
