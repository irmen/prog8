%import textio
%import diskio
%import test_stack
%zeropage basicsafe

main {
    sub start() {
        repeat 80 {
            txt.print("screen .1.   ")
        }

        diskio.delete(8, "screen1.bin")
        diskio.delete(8, "screen2.bin")

        if not diskio.save(8, "screen1.bin", $0400, 40*25) {
            txt.print("can't save screen1\n")
            diskio.status(8)
            exit(1)
        }

        repeat 80 {
            txt.print("screen *2*   ")
        }

        if not diskio.save(8, "screen2.bin", $0400, 40*25) {
            txt.print("can't save screen2\n")
            diskio.status(8)
            exit(1)
        }

        txt.clear_screen()
        uword length = diskio.load(8, "screen1.bin", $0400)
        txt.print_uw(length)
        txt.chrout('\n')
        if not length {
            txt.print("can't load screen1\n")
            diskio.status(8)
            exit(1)
        }
        length = diskio.load(8, "screen2.bin", $0400)
        txt.print_uw(length)
        txt.chrout('\n')
        if not length {
            txt.print("can't load screen2\n")
            diskio.status(8)
            exit(1)
        }
        length = diskio.load(8, "screen3.bin", $0400)
        txt.print_uw(length)
        txt.chrout('\n')
        if not length {
            txt.print("can't load screen3\n")
            diskio.status(8)
            exit(1)
        }

    }
}
