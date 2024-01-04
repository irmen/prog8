%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool @shared a1 = true
        bool @shared a2 = false

        txt.print_ub(not a1)                 ; a1 = a1==0       "0"
        txt.nl()
        txt.print_ub(not not a1)             ; a1 = a1          "1"
        txt.nl()
        txt.print_ub(not not not a1)         ; a1 = a1==0       "0"
        txt.nl()
        txt.print_ub(not a1 or not a2)       ; a1 = a1==0 or a2==0    "1"
        txt.nl()
        txt.print_ub(not a1 and not a2)      ; a1 = a1==0 and a2==0   "0"
        txt.nl()
    }
}
