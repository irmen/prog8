%import textio
%import floats

main {

    sub crc16(uword data, uword length) -> uword {
        uword crc = 0
        repeat length {
            crc ^= mkword(@(data), 0)
            repeat 8 {
                if crc & $8000
                    crc = (crc<<1)^$1021
                else
                    crc<<=1
            }
            data++
        }
        return crc
    }

    sub start() {
        txt.print("calculating (expecting $ffd0)...")
        cbm.SETTIM(0,0,0)
        uword crc = crc16($e000, $2000)
        txt.print_uwhex(crc, true)
        txt.nl()
        floats.print_f(cbm.RDTIM16() / 60.0)
        txt.print(" seconds")
        sys.wait(9999)
    }
}
