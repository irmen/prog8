%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv = 99887766
        lv = func(lv)
        txt.print_l(lv)
    }

    sub func(long arg) -> long {
         arg -= 1234567
         txt.print("func: ")
         txt.print_l(arg)
         txt.nl()
         return arg
    }

    ; TODO multi-value returns
}
