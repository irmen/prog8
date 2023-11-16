%zeropage basicsafe
%import textio

main {
    sub start() {
        str name="irmen"
        if name=="." {
            cx16.r0++
        }
        txt.nl()
    }
}
