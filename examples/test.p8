%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {

    sub start()  {
        uword @shared @nozp ptr = $4000

        @(ptr) = %11110001
        txt.print_ubbin(@(ptr), true)
        txt.nl()
        sys.set_carry()
        rol(@(ptr))
        txt.print_ubbin(@(ptr), true)
        txt.nl()
        sys.clear_carry()
        ror(@(ptr))
        txt.print_ubbin(@(ptr), true)
        txt.nl()

;        cx16.r0L = @(ptr)
;
;        @(ptr)++
;
;        @(ptr)+=10
    }
}
