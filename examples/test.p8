%import textio
%zeropage basicsafe

main {
    sub start() {
        byte[] foo = [ 1, 2, ; this comment is ok

; but after this comment there's a syntax error


               3 ]

        byte bb
        for bb in foo {
            txt.print_b(bb)
            txt.nl()
        }
    }
}
