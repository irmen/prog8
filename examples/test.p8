%import textio
%zeropage basicsafe

main {
    sub start() {
        if true {
            txt.print("true")
        } else {
            txt.print("false")
        }
    }
}
