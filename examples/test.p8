%import textio
%zeropage basicsafe

; (127 instructions in 15 chunks, 47 registers)
; 679 steps


main {

sub start() {
    uword i
    uword n

    repeat 10 {
        txt.chrout('.')
    }
    txt.nl()
    
    n=10
    for i in 0 to n step 3 {
        txt.print_uw(i)
        txt.nl()
    }
    txt.nl()

    n=0
    for i in 10 downto n step -3 {
        txt.print_uw(i)
        txt.nl()
    }
}
}
