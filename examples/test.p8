%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte a = 1
        ubyte var1 = 1
        ubyte var2 = 4

        if a==var1 and a<var2 {
            a++
        }
        txt.print_ub(a)  ; 2        code 1e8

;        if a>4 or a<2 {
;            a++
;        }
;
;        if a>=2 and a<4 {
;            a++
;        }

    }
}
