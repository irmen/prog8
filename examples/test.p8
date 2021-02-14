%import textio
%zeropage basicsafe

main {

    sub start() {

        str text = "44"
        uword textptr = &text

        ubyte nlen = conv.any2uword(textptr)
        ubyte lastchr = text[nlen]
        ubyte valid_operand

        valid_operand = nlen>0 and lastchr==0
        txt.print("\nvalidoper? ")
        txt.print_ub(valid_operand)
        txt.nl()

        valid_operand = nlen>0
        valid_operand = valid_operand and lastchr==0

        txt.print("\nvalidoper? ")
        txt.print_ub(valid_operand)
        txt.nl()


        valid_operand = nlen and lastchr==0
        txt.print("\nvalidoper? ")
        txt.print_ub(valid_operand)
        txt.nl()
    }
}
