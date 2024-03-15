%import math
%import textio
%zeropage basicsafe
%option no_sysinit

; $029f

main {
    sub start() {
        txt.print("crc16\n")
        txt.print_uwhex(math.crc16($0800, 32768), true)
        txt.nl()

        cx16.r15 = 0
        math.crc16_start()
        for cx16.r9 in $0800 to $0800+32768-1 {
            math.crc16_update(@(cx16.r9))
        }
        txt.print_uwhex(math.crc16_end(), true)
        txt.nl()

        txt.print("crc32\n")
        cx16.r0 = cx16.r1 = 0
        math.crc32($0800, 32768)
        txt.print_uwhex(cx16.r15, true)
        txt.print_uwhex(cx16.r14, false)
        txt.nl()

        cx16.r0 = cx16.r1 = 0
        math.crc32_start()
        for cx16.r9 in $0800 to $0800+32768-1 {
            math.crc32_update(@(cx16.r9))
        }
        math.crc32_end()
        txt.print_uwhex(cx16.r15, true)
        txt.print_uwhex(cx16.r14, false)
        txt.nl()
    }
}


