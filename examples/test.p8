%import c64lib
%import c64utils
%zeropage basicsafe


main {
    sub start() {
        uword addr = $c000
        &ubyte addr2 = $c100

        uword border = $d020
        &ubyte borderptr = $d020
;        @($d020) = 0
;        @(border) = 1
;        borderptr=2

;        @($d020)+=9
;        @($d020)-=9
        @(border)+=9        ; TODO fix wrong assembly code
        @(border)-=9        ; TODO fix wrong assembly code
;        borderptr+=9
;        borderptr-=9

        @($c000) |= 8       ; ok?
        addr2 |= 8          ; ok?
        @(addr) |= 8        ; ok?



        ; TODO Verify memory augmented assignment operations.
        addr2 =  0
        addr2 |= 8
        addr2 |= 2
        addr2 |= 2

        c64scr.print_ub(addr2)
        c64.CHROUT('\n')
        if(addr2 != 10) {
            c64scr.print("error1\n")
        }
        addr2 += 1
        addr2 *=10
        c64scr.print_ub(addr2)
        c64.CHROUT('\n')
        if(addr2 != 110) {
            c64scr.print("error2\n")
        }

        @($c000) = 0
        @($c000) |= 8      ; TODO FIX result of memory-OR/XOR and probably AND as well
        @($c000) |= 2      ; TODO FIX result of memory-OR/XOR and probably AND as well
        @($c000) |= 2      ; TODO FIX result of memory-OR/XOR and probably AND as well
        c64scr.print_ub( @($c000) )
        c64.CHROUT('\n')
        if(@($c000) != 10) {
            c64scr.print("error3\n")
        }

        @($c000) += 1      ; TODO fix result of memory += 1
        @($c000) *=10
        c64scr.print_ub( @($c000) )
        c64.CHROUT('\n')
        if(@($c000) != 110) {
            c64scr.print("error4\n")
        }


        @(addr) = 0
        @(addr) |= 8      ; TODO FIX result of memory-OR/XOR and probably AND as well
        @(addr) |= 2      ; TODO FIX result of memory-OR/XOR and probably AND as well
        @(addr) |= 2      ; TODO FIX result of memory-OR/XOR and probably AND as well
        c64scr.print_ub( @(addr) )
        c64.CHROUT('\n')
        if(@(addr) != 10) {
            c64scr.print("error5\n")
        }

        @(addr) += 1      ; TODO fix result of memory += 1
        @(addr) *= 10
        c64scr.print_ub( @(addr) )
        c64.CHROUT('\n')
        if(@(addr) != 110) {
            c64scr.print("error6\n")
        }

    }
}


