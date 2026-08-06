%import textio

main {
    ubyte @shared counter
    bool @shared flag

    sub start() {
        if flag {
            ubyte a,b = multi()     ; compiler error when inside if block, OK if in subroutine directly
            txt.print_ub(a)
            txt.spc()
            txt.print_ub(b)
            txt.nl()
        }
    }

    sub multi() -> ubyte, ubyte {
        counter++
        return 11,22
    }
}
