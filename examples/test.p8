%import textio
%zeropage basicsafe

main {
    sub start() {
        cx16.r0 = 0
        repeat 255 {
            cx16.r0++
        }
        txt.print_uw(255)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()

        repeat 256 {
            cx16.r0++
        }
        txt.print_uw(255+256)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        repeat 257 {
            cx16.r0++
        }
        txt.print_uw(255+256+257)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        repeat 1023 {
            cx16.r0++
        }
        txt.print_uw(255+256+257+1023)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        repeat 1024 {
            cx16.r0++
        }
        txt.print_uw(255+256+257+1023+1024)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        repeat 1025 {
            cx16.r0++
        }
        txt.print_uw(255+256+257+1023+1024+1025)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
    }
}
