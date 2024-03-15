%import diskio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    const ubyte HIRAM_START_BANK = 4
    uword large = memory("large", 20000, 256)

    bool verify = false

    ubyte[256] buffer = 0 to 255

    sub start() {
        txt.print("\n\ndisk benchmark on drive 8.\n")

        txt.print("with verification? y/n: ")
        cx16.r0L = cbm.CHRIN()
        txt.nl()
        if cx16.r0L=='y' {
            verify=true
            ; fill the large buffer with random data, and calculate the checksum
            for cx16.r0 in 0 to 19999 {
                large[cx16.r0] = math.rnd()
            }
            math.crc32(large, 20000)
            uword crc32_l = cx16.r0
            uword crc32_h = cx16.r1
        }

        txt.print("\n\x12diskio.save()\x92 writing 10*20kb=200kb total")
        cbm.SETTIM(0,0,0)
        ; save 10 times 20Kb to make it 200Kb total
        void diskio.save("@:benchmark0.dat", large, 20000)
        void diskio.save("@:benchmark1.dat", large, 20000)
        void diskio.save("@:benchmark2.dat", large, 20000)
        void diskio.save("@:benchmark3.dat", large, 20000)
        void diskio.save("@:benchmark4.dat", large, 20000)
        void diskio.save("@:benchmark5.dat", large, 20000)
        void diskio.save("@:benchmark6.dat", large, 20000)
        void diskio.save("@:benchmark7.dat", large, 20000)
        void diskio.save("@:benchmark8.dat", large, 20000)
        void diskio.save("@:benchmark9.dat", large, 20000)
        print_speed(200000.0, cbm.RDTIM16())
        if verify {
            txt.print("\nverifying...\n")
            verify_20k("benchmark0.dat", crc32_l, crc32_h)
            verify_20k("benchmark1.dat", crc32_l, crc32_h)
            verify_20k("benchmark2.dat", crc32_l, crc32_h)
            verify_20k("benchmark3.dat", crc32_l, crc32_h)
            verify_20k("benchmark4.dat", crc32_l, crc32_h)
            verify_20k("benchmark5.dat", crc32_l, crc32_h)
            verify_20k("benchmark6.dat", crc32_l, crc32_h)
            verify_20k("benchmark7.dat", crc32_l, crc32_h)
            verify_20k("benchmark8.dat", crc32_l, crc32_h)
            verify_20k("benchmark9.dat", crc32_l, crc32_h)
        }

        txt.print("\n\x12diskio.f_write()\x92 writing 256kb in blocks of 256 bytes")
        if diskio.f_open_w("@:benchmark256.dat") {
            cbm.SETTIM(0,0,0)
            repeat 256*1024/256 {
                if not diskio.f_write(buffer, 256) {
                    txt.print("\ni/o error! ")
                    txt.print(diskio.status())
                    sys.exit(1)
                }
            }
            diskio.f_close_w()
            print_speed(256*1024.0, cbm.RDTIM16())
        }

        txt.print("\n\x12diskio.load()\x92 reading 512kb into hiram (2*256)")
        cbm.SETTIM(0,0,0)
        cx16.rambank(HIRAM_START_BANK)
        uword result = diskio.load("benchmark256.dat", $a000)
        if result==0 {
            txt.print("\ni/o error! ")
            txt.print(diskio.status())
            sys.exit(1)
        }
        if result!=$bffe or cx16.getrambank()!=HIRAM_START_BANK+31 {
            ; note: it's 2 bytes short of 256kb because of the load header
            ; hence the end bank 31 and size $bffe instead of 32 and $c000
            txt.nl()
            txt.print("invalid read size!\n")
            sys.exit(1)
        }
        print_speed(256*1024.0, cbm.RDTIM16())
        cx16.rambank(HIRAM_START_BANK)

        ; TODO verify hiram load somehow....

        txt.print("\n\x12diskio.f_read()\x92 reading 256kb in blocks of 255")       ; 255 to avoid calling MACPTR 2 times
        if diskio.f_open("benchmark256.dat") {
            cbm.SETTIM(0,0,0)
            repeat 256*1024/255 {
                if diskio.f_read(buffer, 255)==0 {
                    txt.print("\ni/o error! ")
                    txt.print(diskio.status())
                    sys.exit(1)
                }
            }
            diskio.f_close()
            print_speed(256*1024.0, cbm.RDTIM16())
        }

        ; TODO verify block load somehow....

        txt.print("\npreparing 64kb test file")
        bool success = false
        if diskio.f_open_w("@:benchmark64.dat") {
            if not diskio.f_write(large, 20000)
                or not diskio.f_write(large, 20000)
                or not diskio.f_write(large, 20000)
                or not diskio.f_write(large, 5536) {
                    txt.print("\ni/o error! ")
                    txt.print(diskio.status())
                    sys.exit(1)
                }
            diskio.f_close_w()
        }

        txt.print("\n\x12diskio.vload()\x92 reading 512kb into vram (8*64kb)")
        cbm.SETTIM(0,0,0)
        repeat 8 {
            if not diskio.vload("benchmark64.dat", 0, $0000) {
                txt.print("\ni/o error! ")
                txt.print(diskio.status())
                sys.exit(1)
            }
        }
        print_speed(512*1024.0, cbm.RDTIM16())

        ; TODO verify vmem load somehow....


        txt.nl()
        txt.print(diskio.status())
        txt.print("\ndone.\n")

;        diskio.delete("benchmark0.dat")
;        diskio.delete("benchmark1.dat")
;        diskio.delete("benchmark2.dat")
;        diskio.delete("benchmark3.dat")
;        diskio.delete("benchmark4.dat")
;        diskio.delete("benchmark5.dat")
;        diskio.delete("benchmark6.dat")
;        diskio.delete("benchmark7.dat")
;        diskio.delete("benchmark8.dat")
;        diskio.delete("benchmark9.dat")
;        diskio.delete("benchmark64.dat")
;        diskio.delete("benchmark256.dat")
    }

    sub verify_20k(str filename, uword crc32_low, uword crc32_high) {
        txt.print(filename)
        txt.spc()
        sys.memset(large, 20000, 0)
        uword end = diskio.load(filename, large)
        if end!=large+20000 {
            txt.print("invalid read size!\n")
            sys.exit(1)
        }
        compare_crc32(large, 20000, crc32_low, crc32_high)
    }

    sub compare_crc32(uword ptr, uword size, uword crc32_low, uword crc32_high)
    {
        math.crc32(ptr, size)
        if cx16.r0!=crc32_low or cx16.r1!=crc32_high {
            txt.print_uwhex(cx16.r1, true)
            txt.print_uwhex(cx16.r0, false)
            txt.nl()
            txt.print_uwhex(crc32_high, true)
            txt.print_uwhex(crc32_low, false)
            txt.print("crc32")
            txt.print(" mismatch!\n")
            sys.exit(1)
        }
        txt.print("crc32")
        txt.print(" ok\n")
    }

    sub print_speed(float total_size, uword jiffies) {
        if jiffies==0 {
            txt.print("\n 0 jiffies measured, speed is extremely high\n")
            return
        }
        float speed = floats.floor(total_size / (jiffies as float / 60.0))
        txt.nl()
        txt.print_uw(jiffies)
        txt.print(" jiffies = ")
        floats.print(speed)
        txt.print(" bytes/sec\n")
    }

}
