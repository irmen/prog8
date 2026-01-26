%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        txt.plot(30, 24)
        txt.print("012345678")
        txt.plot(0, 10)
        txt.print("saving this screen...\n")
        sys.wait(100)
        txt.print_bool(diskio.save("@0:screendump.bin", $8000, 2000))
        txt.nl()
        txt.print(diskio.status())
        txt.nl()
        txt.cls()
        txt.print("\nrestoring screen...\n")
        sys.wait(100)
        void diskio.load("0:screendump.bin", 0)
        txt.print("\n\n\n\ndone restoring screen\n")

;        uword end_address = diskio.load("0:test.prg", $1200)
;        txt.print("end address: ")
;        txt.print_uwhex(end_address, true)
;        txt.nl()
;        check(end_address)
;        end_address = diskio.load("0:doesnotexist.prg", $1200)
;        txt.print("end address: ")
;        txt.print_uwhex(end_address, true)
;        txt.nl()
;        check(end_address)
;
;        end_address = diskio.load_raw("0:test.prg", $8000)
;        txt.print("end address: ")
;        txt.print_uwhex(end_address, true)
;        txt.nl()
;        check(end_address)
;        end_address = diskio.load_raw("0:doesnotexist.prg", $1200)
;        txt.print("end address: ")
;        txt.print_uwhex(end_address, true)
;        txt.nl()
;        check(end_address)
    }

    sub check(uword end) {
        if end==0 {
            txt.print(diskio.status())
            txt.nl()
        }
    }
}
