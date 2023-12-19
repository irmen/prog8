%import textio
%zeropage basicsafe

main {
    sub start() {
        uword module
        module++
        module.test++
    }
}

module {
    ubyte @shared test
}
