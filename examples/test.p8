%import textio

main {
    sub start() {
        cx16.r0 = &txt.print    ; IR: this is ok
        uword[] @shared vectors = [ &txt.print ] ; IR: ERROR no chunk with label txt.print
    }
}
