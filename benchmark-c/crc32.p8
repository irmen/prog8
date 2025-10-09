%import textio
%import floats

; note: Prog8 has a complete CRC32 routine in its library: math.crc32


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

    const long EXPECTED = $e1fa84c6
    long crc_result

    sub benchmark_name()
    {
        txt.print("crc32.p8\n")
        txt.print("Calculates the CRC32 of the C64 Kernal\n")
    }

    sub benchmark()
    {
        crc_result = CRC32($e000, $2000)
    }
    
    sub benchmark_check() -> bool
    {
        txt.print("CRC=")
        txt.print_ulhex(crc_result, true)

        if crc_result == EXPECTED
        {
            txt.print(" [OK]")
            return false
        }

        txt.print(" [FAIL] - expected ")
        txt.print_ulhex(EXPECTED, true)
        return true
    }
    
    sub CRC32(^^ubyte data, uword length) -> long {
        ; CRC-32/CKSUM
        long crc

        repeat length
        {
            crc ^= (@(data) as long)<<24     ; currently there's no easy way to affect only the MSB of the variable

            repeat 8
            {
                crc <<=1
                if_cs
                    crc ^= $04c11db7
            }
            data++
        }
        crc ^= $ffffffff
        return crc
    }

}
