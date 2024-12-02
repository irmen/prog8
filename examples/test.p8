%import textio
%import diskio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {

        if diskio.f_open("lines.txt") {
            defer diskio.f_close()

            if diskio.f_open_w("@:copy.txt") {
                defer diskio.f_close_w()

                str buffer = " "*80
                ubyte length, status
                do {
                    length, status = diskio.f_readline(&buffer)
                    cbm.CLRCHN()
                    txt.print_uw(length)
                    txt.nl()
                    if length!=0 {
                        if not diskio.f_write(buffer, length)
                            return
                    }
                } until status!=0
            }
        }
    }
}
