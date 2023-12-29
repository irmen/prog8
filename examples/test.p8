%import math
%import textio
%zeropage dontuse

main {
    sub start() {
        str poem_data = iso:"Once upon a midnight dreary, while I pondered, weak and weary,"+
                        iso:"Over many a quaint and curious volume of forgotten lore-"+
                        iso:"While I nodded, nearly napping, suddenly there came a tapping,"+
                        iso:"As of some one gently rapping, rapping at my chamber door. ..."
        uword size = len(poem_data)

        cbm.SETTIM(0,0,0)
        repeat 20 {
            cx16.r9 = math.crc16(poem_data, size)
        }
        txt.print_uwhex(cx16.r9, true)
        txt.spc()
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        cbm.SETTIM(0,0,0)
        repeat 20 {
            cx16.r9 = cx16.memory_crc(poem_data, size)      ; faster but I can't figure out the flavour of crc algorithm it uses, it's not any on https://crccalc.com/
        }
        txt.print_uwhex(cx16.r9, true)
        txt.spc()
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        cbm.SETTIM(0,0,0)
        repeat 20 {
            math.crc32(poem_data, size)
        }
        txt.print_uwhex(cx16.r1, true)
        txt.print_uwhex(cx16.r0, false)
        txt.spc()
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
    }
}
