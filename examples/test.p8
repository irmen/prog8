%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {
    ; Test the routine
    sub start() {
        str buffer = iso:"The Quick Brown Fox Jumps Over The Lazy Dog. 1234567890 abcdefghijklmnopqrstuvwxyz !@#$%^&*()_+=-[]{}|;':,./<>? Mary had a little lamb. It's fleece was white as snow. Qoth the raven, 'It's wedding day,"
        str buffer2 = iso:"Qoth the raven, nevermore. Qoth the raven, nevermore. The raven is not the raven, but the nightingale."
        str buffer3 = iso:"99 bottles of beer on the wall, 99 bottles of beer. Take one down, pass it around, 98 bottles of beer on the wall."

        txt.print_uwhex(cx16.memory_crc(buffer, len(buffer)), true)
        txt.spc()
        txt.print_uwhex(math.crc16(buffer, len(buffer), $ffff, 0), true)
        txt.spc()
        txt.print_uwhex(math.crc16(buffer,len(buffer), 0, 0), true)
        txt.nl()
        txt.print_uwhex(cx16.memory_crc(buffer2, len(buffer2)), true)
        txt.spc()
        txt.print_uwhex(math.crc16(buffer2,len(buffer2), $ffff, 0), true)
        txt.spc()
        txt.print_uwhex(math.crc16(buffer2,len(buffer2), 0, 0), true)
        txt.nl()
        txt.print_uwhex(cx16.memory_crc(buffer3, len(buffer3)), true)
        txt.spc()
        txt.print_uwhex(math.crc16(buffer3,len(buffer3), $ffff, 0), true)
        txt.spc()
        txt.print_uwhex(math.crc16(buffer3,len(buffer3), 0, 0), true)
        txt.nl()
    }
}

