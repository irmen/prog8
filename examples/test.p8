%option enable_floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        bool @shared b
        float @shared f
        word @shared w
        const long ll = 9999999
        struct List {
            bool b
            word w
            float f
            ^^List next
        }

        ^^List @shared l1
        ^^float @shared fptr

        txt.print_ub(sys.SIZEOF_BOOL)
        txt.spc()
        txt.print_ub(sys.SIZEOF_WORD)
        txt.spc()
        txt.print_ub(sys.SIZEOF_LONG)
        txt.spc()
        txt.print_ub(sys.SIZEOF_FLOAT)
        txt.nl()

        txt.print_ub(sizeof(true))
        txt.spc()
        txt.print_ub(sizeof(1234))
        txt.spc()
        txt.print_ub(sizeof(12345678))
        txt.spc()
        txt.print_ub(sizeof(9.999))
        txt.nl()

        txt.print_ub(sizeof(b))
        txt.spc()
        txt.print_ub(sizeof(w))
        txt.spc()
        txt.print_ub(sizeof(ll))
        txt.spc()
        txt.print_ub(sizeof(f))
        txt.spc()
        txt.print_ub(sizeof(l1))
        txt.spc()
        txt.print_ub(sizeof(fptr^^))
        txt.spc()
        txt.print_ub(sizeof(l1^^))
        txt.nl()

        txt.print_ub(sizeof(bool))
        txt.spc()
        txt.print_ub(sizeof(word))
        txt.spc()
        txt.print_ub(sizeof(long))
        txt.spc()
        txt.print_ub(sizeof(float))
        txt.spc()
;        txt.print_ub(sizeof(^^float))       ; TODO parse this
;        txt.spc()
;        txt.print_ub(sizeof(^^List))       ; TODO parse this
;        txt.spc()
        txt.print_ub(sizeof(List))
        txt.nl()
    }
}
