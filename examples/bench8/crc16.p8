%import textio

main {

    sub crc16(uword data, uword length) -> uword {
        uword crc = 0
        repeat length {
            crc ^= @(data) << $0008
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
        txt.print("calculating...")
        cbm.SETTIM(0,0,0)
        uword crc = crc16($e000, $2000)
        txt.print_uwhex(crc, true)      ; should be $ffd0
        txt.nl()
        txt.print_uw(cbm.RDTIM16())
        txt.print(" jiffies")
        sys.wait(100)
    }
}
