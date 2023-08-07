%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte a = 2
        ubyte var1 = 3
        ubyte var2 = 1

        if a==2 and a>=1 {
            a++
        }
        if a==var1 and a>=var2 {
            a++
        }
        txt.print_ub(a)  ; 4

        byte sa = 2
        byte svar1 = 3
        byte svar2 = 1

        if sa==2 and sa>=1 {
            sa++
        }
        if sa==svar1 and sa>=svar2 {
            sa++
        }
        txt.print_b(sa)  ; 4        code 287
    }
}
