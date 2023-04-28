%import diskio
%import cx16diskio
%import floats
%zeropage basicsafe
%option no_sysinit

main {

    ubyte[256] buffer = 0 to 255
    const ubyte REPEATS = 2

    sub print_speed(uword jiffies) {
        if jiffies==0 {
            txt.print("\n 0 jiffies measured, speed is extremely high\n")
            return
        }
        float speed = 65536.0 * REPEATS / (jiffies as float / 60.0)
        txt.nl()
        txt.print_uw(jiffies)
        txt.print(" jiffies = ")
        floats.print_f(speed)
        txt.print(" bytes/sec\n")
    }

    sub start() {
        txt.print("\n\ndisk benchmark. repeats = ")
        txt.print_ub(REPEATS)

        uword batchtotaltime

        txt.print("\n\nwriting 64kb using save")
        batchtotaltime = 0
        repeat REPEATS {
            cbm.SETTIM(0,0,0)
            void diskio.save(8, "@:benchmark.dat", $100, 32768)
            void diskio.save(8, "@:benchmark.dat", $100, 32768)
            batchtotaltime += cbm.RDTIM16()
            txt.chrout('.')
        }
        print_speed(batchtotaltime)

        txt.print("\nwriting 64kb sequentially")
        batchtotaltime = 0
        repeat REPEATS {
            if diskio.f_open_w(8, "@:benchmark.dat") {
                cbm.SETTIM(0,0,0)
                repeat 65536/256 {
                    if not diskio.f_write(buffer, 256)
                        sys.exit(1)
                }
                batchtotaltime += cbm.RDTIM16()
                diskio.f_close_w()
            }
            txt.chrout('.')
        }
        print_speed(batchtotaltime)

        txt.print("\nreading 64kb using load into hiram")
        batchtotaltime = 0
        repeat REPEATS {
            cbm.SETTIM(0,0,0)
            if not cx16diskio.load(8, "benchmark.dat", 4, $a000)
                sys.exit(1)
            batchtotaltime += cbm.RDTIM16()
            txt.chrout('.')
        }
        print_speed(batchtotaltime)

        txt.print("\nreading 64kb using vload into videoram")
        batchtotaltime = 0
        repeat REPEATS {
            cbm.SETTIM(0,0,0)
            if not cx16diskio.vload("benchmark.dat", 8, 0, $0000)
                sys.exit(1)
            batchtotaltime += cbm.RDTIM16()
            txt.chrout('.')
        }
        print_speed(batchtotaltime)

        txt.print("\nreading 64kb sequentially")
        batchtotaltime = 0
        repeat REPEATS {
            if diskio.f_open(8, "benchmark.dat") {
                cbm.SETTIM(0,0,0)
                repeat 65536/255 {
                    if not diskio.f_read(buffer, 255)
                        sys.exit(1)
                }
                batchtotaltime += cbm.RDTIM16()
                diskio.f_close()
            }
            txt.chrout('.')
        }
        print_speed(batchtotaltime)

        txt.print("\nreading 64kb sequentially (x16 optimized)")
        batchtotaltime = 0
        repeat REPEATS {
            if diskio.f_open(8, "benchmark.dat") {
                cbm.SETTIM(0,0,0)
                repeat 65536/255 {
                    if not cx16diskio.f_read(buffer, 255)
                        sys.exit(1)
                }
                batchtotaltime += cbm.RDTIM16()
                diskio.f_close()
            }
            txt.chrout('.')
        }
        print_speed(batchtotaltime)

        txt.nl()
        txt.print(diskio.status(8))
        txt.print("\ndone.\n")
    }
}
