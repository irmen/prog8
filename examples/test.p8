%import textio
%zeropage basicsafe

main {
    sub start() {
        uword count = 255
        cx16.r0 = 0
        repeat count {
            cx16.r0++
        }
        txt.print_uw(255)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()

        count=256
        repeat count {
            cx16.r0++
        }
        txt.print_uw(255+256)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        count = 257
        repeat count {
            cx16.r0++
        }
        txt.print_uw(255+256+257)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        count=1023
        repeat count {
            cx16.r0++
        }
        txt.print_uw(255+256+257+1023)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        count=1024
        repeat count {
            cx16.r0++
        }
        txt.print_uw(255+256+257+1023+1024)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        count = 1025
        repeat count {
            cx16.r0++
        }
        txt.print_uw(255+256+257+1023+1024+1025)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        count = 65534
        repeat count {
            cx16.r0++
        }
        txt.print_uw(3838)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
        count = 65535
        repeat count {
            cx16.r0++
        }
        count=0
        repeat count {
            cx16.r0++
        }
        repeat 0 {
            cx16.r0++
        }
        txt.print_uw(3837)
        txt.spc()
        txt.print_uw(cx16.r0)
        txt.nl()
    }
}
