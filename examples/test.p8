%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {

    sub start()  {
        ubyte x,y
        c128.set80()
        c128.fast()

        repeat {
            txt.chrout(205 + math.randrange(2))
        }
    }
}
