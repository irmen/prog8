%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared v = 1234567890
        long @shared a = v/77777

        ;txt.iso()
        txt.print_l(a)      ; expected: 15873
        txt.nl()
        txt.print_l(v/88888)    ; expected: 13889
        txt.nl()
        txt.print_l(v/-5555)    ; expected: -222244
        txt.nl()
        v = -999999999
        txt.print_l(v/-77777)   ; expected: 12857
        txt.nl()

        ;sys.poweroff_system()
    }
}
