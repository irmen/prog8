%import textio
%import verafx
%import diskio
%zeropage dontuse

main {
    sub start() {
        txt.uppercase()
        txt.print("abcdefghijklmnopqrstuvwxyz\n")
        txt.print("ABCDEFGHIJKLMNOPQRSTUVWXYZ\n")
        txt.print("0123456789!@#$%^&*()-=[]<>\n")

        void diskio.vload_raw("fantasy.pf", 0, $4000)
;        for cx16.r0 in $4000 to $4000+256*$0008 {
;            cx16.vpoke(0, cx16.r0, %10101010)
;        }

        cbm.SETTIM(0,0,0)
        repeat 1000 {
            slowcopy(0, $4000, 1, $f000, 8*256/4)
        }
        txt.print("\nslow copy time: ")
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
        sys.wait(60)
        txt.lowercase()
        sys.wait(120)

        cbm.SETTIM(0,0,0)
        repeat 1000 {
            verafx.copy(0, $4000, 1, $f000, 8*256/4)
        }
        txt.print("verafx copy time: ")
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
        sys.wait(60)
;        txt.uppercase()
    }

    sub slowcopy(ubyte srcbank, uword srcaddr, ubyte tgtbank, uword tgtaddr, uword num_longwords) {
        cx16.vaddr(srcbank, srcaddr, 0, 1)
        cx16.vaddr(tgtbank, tgtaddr, 1, 1)
        repeat num_longwords {
            cx16.VERA_DATA1=cx16.VERA_DATA0
            cx16.VERA_DATA1=cx16.VERA_DATA0
            cx16.VERA_DATA1=cx16.VERA_DATA0
            cx16.VERA_DATA1=cx16.VERA_DATA0
        }
        cx16.VERA_CTRL = 0
    }
}
