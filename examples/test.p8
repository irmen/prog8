%import c64flt
%zeropage basicsafe

main {

    sub start() {
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
        exit(65)
        sub3()          ; TODO unreachable code compiler warning + remove the code in optimizer
        sub3()          ; TODO unreachable code compiler warning + remove the code in optimizer
        sub3()          ; TODO unreachable code compiler warning + remove the code in optimizer
    }

    sub sub3() {
        c64scr.print("sp3:")
        print_stackpointer()
    }

    sub print_stackpointer() {
        %asm {{
            tsx
        }}
        c64scr.print_ub(X)      ; prints stack pointer
        c64.CHROUT('\n')
    }
 }
