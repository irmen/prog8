%import textio
%zeropage basicsafe

main {

    sub start() {

        str text = "44"
        uword textptr = &text

        ubyte nlen = conv.any2uword(textptr)
        ubyte valid_operand = (nlen>0) and (@(textptr+nlen)==0)

        txt.print("\nvalidoper? ")
        txt.print_ub(valid_operand)
        txt.print("\nnlen=")
        txt.print_ub(nlen)
        txt.print("\nr15=")
        txt.print_uwhex(cx16.r15, true)
        txt.nl()
    }
}
