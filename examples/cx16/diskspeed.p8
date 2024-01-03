%import diskio
%import floats
%zeropage basicsafe
%option no_sysinit

main {

    ubyte[256] buffer = 0 to 255

    sub print_speed(uword jiffies) {
        if jiffies==0 {
            txt.print("\n 0 jiffies measured, speed is extremely high\n")
            return
        }
        float speed = floats.floor(65536.0 / (jiffies as float / 60.0))
        txt.nl()
        txt.print_uw(jiffies)
        txt.print(" jiffies = ")
        floats.print(speed)
        txt.print(" bytes/sec\n")
    }

    sub start() {
        txt.print("\n\ndisk benchmark on drive 8.\n\n")

        txt.print("writing 64kb using save()")
        cbm.SETTIM(0,0,0)
        ; save 2 times 32Kb to make it 64Kb total
        void diskio.save("@:benchmark.dat", $100, 32768)
        void diskio.save("@:benchmark.dat", $100, 32768)
        print_speed(cbm.RDTIM16())

        txt.print("\nwriting 64kb sequentially")
        if diskio.f_open_w("@:benchmark.dat") {
            cbm.SETTIM(0,0,0)
            repeat 65536/256 {
                if not diskio.f_write(buffer, 256)
                    sys.exit(1)
            }
            diskio.f_close_w()
            print_speed(cbm.RDTIM16())
        }

        txt.print("\nreading 64kb using load() into hiram")
        cbm.SETTIM(0,0,0)
        cx16.rambank(4)
        if diskio.load("benchmark.dat", $a000)==0
            sys.exit(1)
        print_speed(cbm.RDTIM16())

        txt.print("\nreading 64kb using vload() into vram")
        cbm.SETTIM(0,0,0)
        if diskio.vload("benchmark.dat", 0, $0000)==0
            sys.exit(1)
        print_speed(cbm.RDTIM16())

        txt.print("\nreading 64kb sequentially")
        if diskio.f_open("benchmark.dat") {
            cbm.SETTIM(0,0,0)
            repeat 65536/255 {
                if diskio.f_read(buffer, 255)==0
                    sys.exit(1)
            }
            diskio.f_close()
            print_speed(cbm.RDTIM16())
        }

        txt.nl()
        txt.print(diskio.status())
        txt.print("\ndone.\n")

        diskio.delete("benchmark.dat")
    }
}
