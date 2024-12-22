%import textio
%import math

%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        str input = iso:"the quick brown fox jumps over the lazy dog"


        txt.print_uwhex(math.crc16(input, len(input)), true)
        txt.nl()

        math.crc32(input, len(input))

        txt.print_uwhex(cx16.r15, true)
        txt.print_uwhex(cx16.r14, false)
        txt.nl()

        math.crc32_start()
        for cx16.r0L in input
            math.crc32_update(cx16.r0L)
        uword hiw,low
        hiw,low = math.crc32_end_result()
        txt.print_uwhex(hiw, true)
        txt.print_uwhex(low, false)
        txt.nl()
    }
}
