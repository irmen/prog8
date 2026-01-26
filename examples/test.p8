%import strings
%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {
        uword end_address = diskio.load("0:test.prg", $1200)
        txt.print("end address: ")
        txt.print_uwhex(end_address, true)
        txt.nl()
        check(end_address)
        end_address = diskio.load("0:test2.prg", $1200)
        txt.print("end address: ")
        txt.print_uwhex(end_address, true)
        txt.nl()
        check(end_address)

        end_address = diskio.load_raw("0:test.prg", $1200)
        txt.print("end address: ")
        txt.print_uwhex(end_address, true)
        txt.nl()
        check(end_address)
        end_address = diskio.load_raw("0:test2.prg", $1200)
        txt.print("end address: ")
        txt.print_uwhex(end_address, true)
        txt.nl()
        check(end_address)
    }

    sub check(uword end) {
        if end==0 {
            txt.print(diskio.status())
            txt.nl()
        }
    }
}
