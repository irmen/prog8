%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv1
        long lv2

        lv1 = $77777777
        lv2 = $55555555
        cmp(lv1, lv2)

        ubyte flags = sys.read_flags()
        if(flags & %10000000 != 0) {
            txt.print("neg ")
        } else {
            txt.print("pos ")
        }
        if(flags & %10 !=0) {
            txt.print("zero ")
        } else {
            txt.print("nonzero ")
        }
        if(flags & 1 !=0) {
            txt.print("cs ")
        } else {
            txt.print("cc ")
        }
        txt.nl()

        lv1 = $11111111
        lv2 = $55555555
        cmp(lv1, lv2)

        flags = sys.read_flags()
        if(flags & %10000000 != 0) {
            txt.print("neg ")
        } else {
            txt.print("pos ")
        }
        if(flags & %10 !=0) {
            txt.print("zero ")
        } else {
            txt.print("nonzero ")
        }
        if(flags & 1 !=0) {
            txt.print("cs ")
        } else {
            txt.print("cc ")
        }
        txt.nl()


        lv1 = -1
        lv2 = -1
        cmp(lv1, lv2)

        flags = sys.read_flags()
        if(flags & %10000000 != 0) {
            txt.print("neg ")
        } else {
            txt.print("pos ")
        }
        if(flags & %10 !=0) {
            txt.print("zero ")
        } else {
            txt.print("nonzero ")
        }
        if(flags & 1 !=0) {
            txt.print("cs ")
        } else {
            txt.print("cc ")
        }
        txt.nl()
    }
}
