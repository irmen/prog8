%import diskio
%import textio
;%import math
;%import verafx
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        txt.print("\\r=")
        txt.print_ub('\r')
        txt.print(" and \\n=")
        txt.print_ub('\n')
        txt.nl()

        ubyte[256] buf

        void diskio.delete("lines.txt")
        if diskio.f_open_w("@:lines.txt") {
            txt.print("writing...\n")
            repeat 10 {
                if not diskio.f_write("line\r", 5) {
                    diskio.f_close_w()
                    txt.print("write error\n")
                    break
                }
            }
            diskio.f_close_w()
        }
        txt.print("\nwritten.\n\n")

        if diskio.f_open("lines.txt") {
            txt.print("reading...\n")
            repeat {
                if diskio.f_readline(buf)==0 {
                    diskio.f_close()
                    txt.print("readline is 0\n")
                    break
                }
                if cbm.READST() {
                    txt.print(diskio.status())
                    diskio.f_close()
                    break
                }
                txt.print(buf)
                txt.nl()
            }
            diskio.f_close()
        } else {
            txt.print(diskio.status())
        }

        txt.print("\ndone.\n")

;        txt.print_uw(math.mul16_last_upper())
;        txt.nl()
;        uword value1=5678
;        uword value2=9999
;        uword result = value1*value2
;        uword upper16 = math.mul16_last_upper()
;        txt.print_uw(result)
;        txt.spc()
;        txt.print_uw(upper16)
;        txt.nl()


;        const word MULTIPLIER = 431
;
;        ; verify results:
;        for value in -50 to 50 {
;            if value*MULTIPLIER != verafx.muls(value, MULTIPLIER) {
;                txt.print("verafx muls error\n")
;                sys.exit(1)
;            }
;        }
;
;
;        word value
;        txt.print("verafx muls...")
;        cbm.SETTIM(0,0,0)
;        for value in -50 to 50 {
;            repeat 250 void verafx.muls(value, MULTIPLIER)
;        }
;        txt.print_uw(cbm.RDTIM16())
;        txt.nl()
;
;        txt.print("6502 muls...")
;        cbm.SETTIM(0,0,0)
;        for value in -50 to 50 {
;            repeat 250 cx16.r0s = value*MULTIPLIER
;        }
;        txt.print_uw(cbm.RDTIM16())
;        txt.nl()

    }
}

