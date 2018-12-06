%import c64utils
%import mathlib
%option enable_floats

~ main {

    sub start()  {

        math.randseed($2ae4)

        ubyte loop
        c64scr.print_string("random bytes:\n")
        for loop in 1 to 5 {
            rsave()     ; @todo automatic based on clobbers declaration
            c64scr.print_byte_decimal(loop)
            c64.CHROUT(':')
            c64.CHROUT(' ')
            ubyte ubr = rnd()
            c64scr.print_byte_decimal(ubr)
            rrestore()
            c64.CHROUT('\n')
        }
        c64scr.print_string("\nrandom words:\n")
        for loop in 1 to 5 {
            rsave()     ; @todo automatic based on clobbers declaration
            c64scr.print_byte_decimal(loop)
            c64.CHROUT(':')
            c64.CHROUT(' ')
            uword uwr = rndw()
            c64scr.print_word_decimal(uwr)
            rrestore()
            c64.CHROUT('\n')
        }
        c64scr.print_string("\nrandom floats:\n")
        for loop in 1 to 5 {
            c64scr.print_byte_decimal(loop)
            c64.CHROUT(':')
            c64.CHROUT(' ')
            float f = rndf()
            c64flt.print_float_ln(f)
        }
    }
}
