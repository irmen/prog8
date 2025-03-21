%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {

    sub start()  {
        ubyte[] @shared array = [1,2,3,4,5,6,7]
        uword @shared @nozp ptr = $4000

        @(ptr) = %11110001
        txt.print_ub(@(ptr))
        txt.nl()
        @(ptr)++
        txt.print_ub(@(ptr))
        txt.nl()
        @(ptr)--
        txt.print_ub(@(ptr))
        txt.nl()

;        cx16.r0L = @(ptr)
;
;        @(ptr)++
;
;        @(ptr)+=10
    }
}
