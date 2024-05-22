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
            pl, ph, sl, sh = diskio.f_tell()

            txt.print("\npos: ")
            txt.print_uwhex(ph, true)
            txt.print_uwhex(pl, false)
            txt.print("\nsize: ")
            txt.print_uwhex(sh, true)
            txt.print_uwhex(sl, false)
            txt.nl()

            diskio.f_close()
        }
    }
}
