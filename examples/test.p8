%import textio
%import diskio
%import string
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {
        ubyte[] sarray = [11,22,33]
        ubyte[] tarray = [0,0,0]

        uword target = &tarray
        uword source = &sarray
        ubyte bb
        @(target) = @(source)
        target++
        source++
        @(target) = @(source)
        target++
        source++
        @(target) = @(source)
        target++
        source++

        for bb in tarray {
            txt.print_ub(bb)
            txt.chrout('\n')
        }
    }

}
