%import textio
%zeropage basicsafe

main {
    const ^^uword bptr = $2000
    const ^^ubyte byteptr = $2000
    const ^^float fptr = $4000
    const ^^long lptr = $3000

    sub start() {
        txt.print("ptr=")
        txt.print_uwhex(bptr, true)
        txt.nl()

        uword ptr2 = bptr + 4     ; equivalent to $2008 and is now correctly const-folded
        txt.print("ptr2=")
        txt.print_uwhex(ptr2, true)     ; should print $2008
        txt.nl()

        uword ptr3 = bptr - 2     ; equivalent to $1ffc and should be const-folded
        txt.print("ptr3=")
        txt.print_uwhex(ptr3, true)
        txt.nl()

        uword ptr4 = 10 + byteptr  ; equivalent to $200a and should be const-folded
        txt.print("ptr4=")
        txt.print_uwhex(ptr4, true)
        txt.nl()

        uword ptr5 = fptr + 2      ; equivalent to $4000 + 2 * sizeof(float) and should be const-folded
        txt.print("ptr5=")
        txt.print_uwhex(ptr5, true)
        txt.nl()

        uword ptr6 = lptr + 1      ; equivalent to $3000 + sizeof(long) and should be const-folded
        txt.print("ptr6=")
        txt.print_uwhex(ptr6, true)
        txt.nl()

        uword ptr7 = byteptr + sizeof(float)  ; equivalent to $2000 + sizeof(float) and should be const-folded
        txt.print("ptr7=")
        txt.print_uwhex(ptr7, true)
        txt.nl()

        cx16.r0 = bptr[$1000]      ; equivalent to peekw($4000) and is now correctly const-folded
        cx16.r1L = byteptr[$1000]      ; equivalent to peek($3000) and is now correctly const-folded
    }
}
