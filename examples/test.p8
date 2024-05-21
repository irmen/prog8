%import diskio
%import textio
%zeropage basicsafe

main {
    sub start() {
        if diskio.f_open("tehtriz.asm") {
            repeat 10 {
                void diskio.f_read($6000, 9999)
            }
            uword pl, ph, sl, sh
            pl, ph, sl, sh = diskio.f_tell32()

            txt.print("\npos: ")
            txt.print_uwhex(ph, true)
            txt.print_uwhex(pl, false)
            txt.print("\nsize: ")
            txt.print_uwhex(sh, true)
            txt.print_uwhex(sl, false)
            txt.nl()

            diskio.f_close()
        }

        if diskio.f_open("test.p8ir") {
            repeat 5 {
                void diskio.f_read($6000, 999)
            }

            pl, sl = diskio.f_tell()
            txt.print("\npos16: ")
            txt.print_uwhex(pl, true)
            txt.print("\nsize16: ")
            txt.print_uwhex(sl, true)
            txt.nl()

            diskio.f_close()
        }
    }
}
