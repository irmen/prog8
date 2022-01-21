%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print("yo\n")
        uword jumps = $4000
        if_cc
            goto jumps

        goto jumps
    }
}

test $4000 {
    %option force_output

jumper:
    %asm {{
        rts
    }}
}
