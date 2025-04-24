%import textio
%import floats
%import math
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        const uword biterations=2000
        const uword witerations=20000

        cx16.set_screen_mode($80)

        float sumb1, sumb2
        float sumw1, sumw2
        repeat biterations {
            cx16.r0L = math.randrange(100)
            sumb1 += cx16.r0L
            cx16.vpoke(0, 10*256+cx16.r0L, 255)
        }
        repeat biterations {
            cx16.r0L = math.randrange_rom(100)
            sumb2 += cx16.r0L
            cx16.vpoke(0, 20*256+cx16.r0L, 255)
        }
        repeat witerations {
            cx16.r0 = math.randrangew(10000)
            sumw1 += cx16.r0
            cx16.vpoke(0, 30*256+cx16.r0, 255)
        }
        repeat witerations {
            cx16.r0 = math.randrangew_rom(10000)
            sumw2 += cx16.r0
            cx16.vpoke(0, 80*256+cx16.r0, 255)
        }

        repeat 15 txt.nl()
        txt.print("means of randrange: ")
        txt.print_f(sumb1/biterations)
        txt.spc()
        txt.print_f(sumb2/biterations)
        txt.nl()
        txt.print("means of randrangew: ")
        txt.print_f(sumw1/witerations)
        txt.spc()
        txt.print_f(sumw2/witerations)
        txt.nl()
    }
}

