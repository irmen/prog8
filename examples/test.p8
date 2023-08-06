%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte a = 1
        ubyte var1 = 2
        ubyte var2 = 4

        if a==1 and a<4 {
            a++
        }
        if a==var1 and a<var2 {
            a++
        }
        txt.print_ub(a)  ; 3

        byte sa = 1
        byte svar1 = 2
        byte svar2 = 4

        if sa==1 and sa<4 {
            sa++
        }
        if sa==svar1 and sa<svar2 {
            sa++
        }
        txt.print_b(sa)  ; 3        code 287
    }
}
