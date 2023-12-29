%import textio
%import floats

main {
    sub crc32(uword data, uword length) {
        ; because prog8 doesn't have 32 bits integers, we have to split up the calucation over 2 words.
        ; result in cx16.r0 (high word) and cx1.r1 (low word).
        cx16.r0 = 0
        cx16.r1 = 0
        repeat length {
            cx16.r0 ^= mkword(@(data), 0)
            repeat 8 {
                if cx16.r0 & $8000 {
                    sys.clear_carry()
                    rol(cx16.r1)
                    rol(cx16.r0)
                    cx16.r0 ^= $04c1
                    cx16.r1 ^= $1db7
                }
                else {
                    sys.clear_carry()
                    rol(cx16.r1)
                    rol(cx16.r0)
                }
            }
            data++
        }
        cx16.r0 ^= $ffff
        cx16.r1 ^= $ffff
    }

    sub start() {
        txt.print("calculating (expecting $e1fa84c6)...\n")
        txt.print("  if mismatch: first check if kernal is maybe updated?\n")
        cbm.SETTIM(0,0,0)
        crc32($e000, $2000)
        txt.print_uwhex(cx16.r0, true)
        txt.print_uwhex(cx16.r1, false)
        txt.nl()
        floats.print(cbm.RDTIM16() / 60.0)
        txt.print(" seconds")
        sys.wait(9999)
    }
}
