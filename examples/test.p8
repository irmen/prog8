%import c64utils
%zeropage basicsafe

main {

    sub start() {

        while true {
            A=99
        }

        repeat {
            A=44
        } until false


        c64scr.print("spstart:")
        print_stackpointer()
        sub1()
        c64scr.print("spend:")
        print_stackpointer()
    }

    sub sub1() {
        c64scr.print("sp1:")
        print_stackpointer()
        sub2()
    }

    sub sub2() {
        c64scr.print("sp2:")
        print_stackpointer()
        exit(33)
        sub3()      ; TODO warning about unreachable code
        sub3()      ; TODO remove statements after a return/exit
        c64scr.print("sp2:")
        c64scr.print("sp2:")
        sub3()

        sub3()
        sub3()
        sub3()
        sub3()
        sub3()
        sub3()
        sub3()
        sub3()
        sub3()
    }

    sub sub3() {
        c64scr.print("sp3:")
        print_stackpointer()
    }

    sub print_stackpointer() {
        c64scr.print_ub(X)      ; prints stack pointer
        c64.CHROUT('\n')
    }
 }
