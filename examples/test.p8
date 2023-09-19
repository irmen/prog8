%import textio

main {
    uword[] pages1 = [ &page_credits.chars_1]
    uword foo2 = &page_credits.chars_1

    sub start() {
        txt.print_uw(foo2)
        uword @shared foo = pages1[0]    ; TODO fix IR compiler error no chunk with label 'page_credits.chars_1'  (caused by optimizer)
    }

}

page_credits {
  ubyte[] chars_1 = [11]
}
