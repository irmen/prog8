%import textio
%import floats


main {
    sub start() {
        txt.lowercase()
        test.benchmark_name()
        cbm.SETTIM(0,0,0)
        test.benchmark()
        txt.print_f(floats.time() / 60)
        txt.print(" seconds\n")
        void test.benchmark_check()
        repeat {}
    }    
}


test {

    sub benchmark_name()
    {
        txt.print("crc8.p8\n")
        txt.print("Calculates the CRC8 of the C64 Kernal\n")
    }

    sub benchmark()
    {
        crc_result = CRC8($e000, $2000)
    }
    
    sub benchmark_check() -> bool
    {
        txt.print("CRC=")
        txt.print_ubhex(crc_result, true)

        if crc_result == EXPECTED
        {
            txt.print(" [OK]")
            return false
        }

        txt.print(" [FAIL] - expected ")
        txt.print_ubhex(EXPECTED, true)
        return true
    }
    

    const ubyte EXPECTED = $a2
    ubyte crc_result

    sub CRC8(^^ubyte data, uword length) -> ubyte
    {
        ; CRC-8/GSM-A
        ubyte crc

        repeat length
        {
            crc ^= @(data)

            repeat 8
            {
                if (crc & $80) != 0
                    crc = (crc << 1) ^ $1d
                else
                    crc <<= 1
            }
            data++
        }
        return crc
    }
}
