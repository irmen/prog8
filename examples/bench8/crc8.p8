%import textio

main {

    sub crc8(uword data, uword length) -> ubyte {
        ubyte crc = 0
        repeat length {
            crc ^= @(data)
            repeat 8 {
                if crc & $80
                    crc = (crc<<1)^$1d
                else
                    crc<<=1
            }
            data++
        }
        return crc
   }

    sub start() {
        txt.print("calculating (expecting $a2)...")
        cbm.SETTIM(0,0,0)
        ubyte crc = crc8($e000, $2000)
        txt.print_ubhex(crc, true)
        txt.nl()
        txt.print_uw(cbm.RDTIM16())
        txt.print(" jiffies")
        sys.wait(300)
    }
}
