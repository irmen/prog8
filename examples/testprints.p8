%zeropage basicsafe

main {

    sub start() {

        c64scr.print("print uw0:")
        c64scr.print_uw0(53204)
        c64.CHROUT(' ')
        c64scr.print_uw0(3204)
        c64.CHROUT(' ')
        c64scr.print_uw0(204)
        c64.CHROUT(' ')
        c64scr.print_uw0(14)
        c64.CHROUT(' ')
        c64scr.print_uw0(4)
        c64.CHROUT(' ')
        c64scr.print_uw0(0)
        c64.CHROUT('\n')

        c64scr.print("print uw:")
        c64scr.print_uw(53204)
        c64.CHROUT(' ')
        c64scr.print_uw(3204)
        c64.CHROUT(' ')
        c64scr.print_uw(204)
        c64.CHROUT(' ')
        c64scr.print_uw(14)
        c64.CHROUT(' ')
        c64scr.print_uw(4)
        c64.CHROUT(' ')
        c64scr.print_uw(0)
        c64.CHROUT('\n')

        c64scr.print("print w:")
        c64scr.print_w(23204)
        c64.CHROUT(' ')
        c64scr.print_w(3204)
        c64.CHROUT(' ')
        c64scr.print_w(204)
        c64.CHROUT(' ')
        c64scr.print_w(14)
        c64.CHROUT(' ')
        c64scr.print_w(4)
        c64.CHROUT(' ')
        c64scr.print_w(0)
        c64.CHROUT('\n')
        c64scr.print_w(-23204)
        c64.CHROUT(' ')
        c64scr.print_w(-3204)
        c64.CHROUT(' ')
        c64scr.print_w(-204)
        c64.CHROUT(' ')
        c64scr.print_w(-14)
        c64.CHROUT(' ')
        c64scr.print_w(-4)
        c64.CHROUT(' ')
        c64scr.print_w(-0)
        c64.CHROUT('\n')

        check_eval_stack()          ; TODO fix stack error

    }


    sub check_eval_stack() {
        if X!=255 {
            c64scr.print("stack x=")
            c64scr.print_ub(X)
            c64scr.print(" error!\n")
        }
    }
}
