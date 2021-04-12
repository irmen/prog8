%import textio
%zeropage basicsafe

main {
    sub start()  {
        word total

        repeat 10 {
            word wcosa
            word zz=1
            total += zz
        }

        txt.print_w(total)
    }
}
