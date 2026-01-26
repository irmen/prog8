%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword buffer = memory("buffer", 2000, 0)

        txt.print("drive number? ")
        diskio.drivenumber = cbm.CHRIN()-'0'
        txt.nl()

        txt.print_ub(diskio.list_filenames(0, buffer, 2000))
        txt.print(" entries on disk:\n\n")

        uword ptr = buffer
        while @(ptr)!=0 {
            txt.print(ptr)
            txt.nl()
            ptr += strings.length(ptr)+1
        }


        txt.plot(30, 24)
        txt.print("012345678")
        txt.plot(0, 10)
        txt.print("***** saving this screen... *****\n***** ")
        sys.wait(100)
        diskio.delete("screendump.bin")
        txt.print_bool(diskio.save("screendump.bin", $8000, 1000))
        txt.print("***** ")
        txt.print(diskio.status())
        txt.nl()
        sys.wait(100)
        txt.cls()
        txt.print("\n***** restoring screen... *****\n")
        sys.wait(100)
        void diskio.load("screendump.bin", 0)
        txt.home()
        txt.print("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n***** done restoring screen *****\n***** ")
        txt.print(diskio.status())
        txt.nl()

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
