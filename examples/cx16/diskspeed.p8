%import math
%import diskio
%import floats
%zeropage basicsafe
%option no_sysinit


main {
    const ubyte HIRAM_START_BANK = 4
    uword large = memory("large", 20000, 256)

    ubyte[256] buffer = 0 to 255
    uword @zp buf_ptr

    sub start() {
        txt.print("\n\ndisk benchmark on drive 8.\n")

        txt.print("with verification? y/n: ")
        bool verify = cbm.CHRIN()=='y'
        txt.print("\nfast serial mode r+w? y/n: ")
        bool fastserial = cbm.CHRIN()=='y'
        txt.nl()

        if verify {
            ; fill the buffers with random data, and calculate the checksum
            for cx16.r0 in 0 to 19999 {
                large[cx16.r0] = math.rnd()
            }
            for cx16.r0L in 0 to 255 {
                buffer[cx16.r0L] = math.rnd()
            }
            long crc32value = math.crc32(large, 20000)
        }

        if fastserial
            void diskio.fastmode(3)
        else
            void diskio.fastmode(0)

        test_save()
        test_save_blocks()
        test_load_hiram()
        test_read_blocks()
        test_vload()

        txt.nl()
        txt.print(diskio.status())
        txt.print("\ndone.\n")

        diskio.delete("benchmark0.dat")
        diskio.delete("benchmark1.dat")
        diskio.delete("benchmark2.dat")
        diskio.delete("benchmark3.dat")
        diskio.delete("benchmark4.dat")
        diskio.delete("benchmark5.dat")
        diskio.delete("benchmark6.dat")
        diskio.delete("benchmark7.dat")
        diskio.delete("benchmark8.dat")
        diskio.delete("benchmark9.dat")
        diskio.delete("benchmark256.dat")
        diskio.delete("benchmark64.dat")
        return

        sub test_save() {
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
                verify_20k("benchmark0.dat", crc32value)
                verify_20k("benchmark1.dat", crc32value)
                verify_20k("benchmark2.dat", crc32value)
                verify_20k("benchmark3.dat", crc32value)
                verify_20k("benchmark4.dat", crc32value)
                verify_20k("benchmark5.dat", crc32value)
                verify_20k("benchmark6.dat", crc32value)
                verify_20k("benchmark7.dat", crc32value)
                verify_20k("benchmark8.dat", crc32value)
                verify_20k("benchmark9.dat", crc32value)
            }
        }

        sub test_save_blocks() {
            txt.print("\n\x12diskio.f_write()\x92 writing 256kb in blocks of 256 bytes")
            if diskio.f_open_w("@:benchmark256.dat") {
                cbm.SETTIM(0,0,0)
                repeat 256*1024/256 {
                    if not diskio.f_write(buffer, 256) {
                        io_error()
                    }
                }
                diskio.f_close_w()
                print_speed(256*1024.0, cbm.RDTIM16())
            } else sys.exit(1)

            if verify {
                txt.print("\ncalculating crc32 (takes a while)...")
                math.crc32_start()
                buf_ptr = &buffer
                repeat 256*1024/256 {
                    for cx16.r9L in 0 to 255 {
                        math.crc32_update(buf_ptr[cx16.r9L])
                    }
                }
                crc32value = math.crc32_end()
                txt.nl()
            }
        }

        sub test_load_hiram() {
            txt.print("\n\x12diskio.load()\x92 reading 256kb into hiram")
            cbm.SETTIM(0,0,0)
            cx16.rambank(HIRAM_START_BANK)
            uword result = diskio.load_raw("benchmark256.dat", $a000)
            if result==0 {
                io_error()
            }
            if result!=$a000 or cx16.getrambank()!=HIRAM_START_BANK+32 {
                txt.nl()
                txt.print("invalid read size!\n")
                sys.exit(1)
            }
            print_speed(256*1024.0, cbm.RDTIM16())
            cx16.rambank(HIRAM_START_BANK)

            if verify {
                txt.print("\ncalculating crc32 (takes a while)...")
                math.crc32_start()
                ubyte ram_bank
                for ram_bank in HIRAM_START_BANK to HIRAM_START_BANK+32-1 {
                    cx16.rambank(ram_bank)
                    for buf_ptr in $a000 to $bfff {
                        math.crc32_update(@(buf_ptr))
                    }
                }
                long crc = math.crc32_end()
                compare_crc32(crc, crc32value)
            }
        }

        sub test_read_blocks() {
            txt.print("\n\x12diskio.f_read()\x92 reading 256kb in blocks of 256")
            if diskio.f_open("benchmark256.dat") {
                cbm.SETTIM(0,0,0)
                repeat 256*1024/256 {
                    if diskio.f_read(buffer, 256)==0 {
                        io_error()
                    }
                }
                diskio.f_close()
                print_speed(256*1024.0, cbm.RDTIM16())
            } else sys.exit(1)

            if verify {
                txt.print("\ncrc checking block reads...")
                math.crc32_start()
                if diskio.f_open("benchmark256.dat") {
                    repeat 256*1024/256 {
                        if diskio.f_read(buffer, 256)==0 {
                            io_error()
                        }
                        buf_ptr = &buffer
                        for cx16.r9L in 0 to 255 {
                            math.crc32_update(buf_ptr[cx16.r9L])
                        }
                    }
                    diskio.f_close()
                } else sys.exit(1)
                long crc = math.crc32_end()
                compare_crc32(crc, crc32value)
            }
        }

        sub test_vload() {
            txt.print("\npreparing 64kb test file")
            if diskio.f_open_w("@:benchmark64.dat") {
                if not diskio.f_write(large, 20000)
                    or not diskio.f_write(large, 20000)
                    or not diskio.f_write(large, 20000)
                    or not diskio.f_write(large, 5536) {
                        io_error()
                    }
                diskio.f_close_w()
            }

            if verify {
                txt.print("\ncalculating crc32 (takes a while)...")
                math.crc32_start()
                repeat 3 {
                    for buf_ptr in large to large+20000-1 {
                        math.crc32_update(@(buf_ptr))
                    }
                }
                for buf_ptr in large to large+5536-1 {
                    math.crc32_update(@(buf_ptr))
                }
                crc32value = math.crc32_end()
                txt.nl()
            }

            txt.print("\n\x12diskio.vload()\x92 reading 512kb into vram (8*64kb)")
            cbm.SETTIM(0,0,0)
            repeat 8 {
                if not diskio.vload_raw("benchmark64.dat", 0, $0000) {
                    io_error()
                }
            }
            print_speed(512*1024.0, cbm.RDTIM16())

            if verify {
                math.crc32_start()
                txt.print("\ncalculating crc32 (takes a while)...")
                cx16.vaddr(0,0, 0, 1)
                repeat 1024 {
                    repeat 64 {
                        math.crc32_update(cx16.VERA_DATA0)
                    }
                }
                long crc = math.crc32_end()
                compare_crc32(crc, crc32value)
            }
        }
    }

    sub verify_20k(str filename, long crccheck) {
        txt.print(filename)
        txt.spc()
        sys.memset(large, 20000, 0)
        uword end = diskio.load(filename, large)
        if end!=large+20000 {
            txt.print("invalid read size!\n")
            sys.exit(1)
        }
        long crc = math.crc32(large, 20000)
        compare_crc32(crc, crccheck)
    }

    sub compare_crc32(long crc1, long crc2)
    {
        if crc1!=crc2 {
            txt.nl()
            txt.print_ulhex(crc1, true)
            txt.spc()
            txt.print_ulhex(crc2, true)
            txt.spc()
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

    sub io_error() {
        txt.print("\ni/o error! ")
        txt.print(diskio.status())
        sys.exit(1)
    }
}
