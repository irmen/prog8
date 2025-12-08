%import textio
%import ciatimer

; note: Prog8 has a complete CRC16 routine in its library: math.crc16


main {
    sub start() {
        txt.lowercase()
        cia.calibrate()
        test.benchmark_name()
        test.benchmark()
        void test.benchmark_check()
        cia.print_time()
        repeat {}
    }    
}

test {

    const uword EXPECTED = $ffd0
    uword crc_result

    sub benchmark_name()
    {
        txt.print("crc16.p8\n")
        txt.print("Calculates the CRC16 of the C64 Kernal\n")
    }

    sub benchmark()
    {
        crc_result = CRC16($e000, $2000)
    }
    
    sub benchmark_check() -> bool
    {
        txt.print("CRC=")
        txt.print_uwhex(crc_result, true)

        if crc_result == EXPECTED
        {
            txt.print(" [OK]\n")
            return false
        }

        txt.print(" [FAIL] - expected ")
        txt.print_uwhex(EXPECTED, true)
        txt.nl()
        return true
    }
    
    sub CRC16(^^ubyte data, uword length) -> uword {
        ; CRC-16/XMODEM
        uword crc

        repeat length
        {
            crc ^= mkword(@(data),0)     ; currently there's no easy way to affect only the MSB of the variable

            repeat 8
            {
                crc <<=1
                if_cs
                    crc ^= $1021
            }
            data++
        }
        return crc
    }

}
