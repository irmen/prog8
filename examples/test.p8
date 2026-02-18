%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {
        txt.print("create 0 byte file...")
        if diskio.f_open_w("@:0byte.dat") {
            txt.print("ok\n")
            diskio.f_close_w()
        }
        else
            txt.print("fail\n")

        txt.print("create 1 byte file...")
        if diskio.f_open_w("@:1byte.dat") {
            diskio.reset_write_channel()
            cbm.CHROUT('1')
            cbm.CLRCHN()
            txt.print("ok\n")
            diskio.f_close_w()
        }
        else
            txt.print("fail\n")

        txt.print("create 2 byte file...")
        if diskio.f_open_w("@:2byte.dat") {
            diskio.reset_write_channel()
            cbm.CHROUT('1')
            cbm.CHROUT('2')
            cbm.CLRCHN()
            txt.print("ok\n")
            diskio.f_close_w()
        }
        else
            txt.print("fail\n")

        txt.nl()
        read("doesnotexist")
        read("0byte.dat")
        read("1byte.dat")
        read("2byte.dat")
    }

    sub read(str filename) {
        txt.print(filename)
        txt.nl()
        if diskio.exists(filename)
            txt.print("  exists\n")
        else
            txt.print("  does not exist\n")
        if diskio.f_open(filename) {
            txt.print("  open ok\n")
            txt.print("  reading bytes: ")
            diskio.reset_read_channel()
            while cbm.READST()==0 {
                cx16.r0L = cbm.CHRIN()
                txt.print_ubhex(cx16.r0L, false)
                txt.spc()
            }
            diskio.f_close()
            txt.nl()
        }
        else txt.print("  open failed\n")
    }
}
