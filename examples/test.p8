%import textio
%zeropage basicsafe

main {
    sub start() {
        uword module        ; TODO shadow warning
        module++
        module.test++       ; TODO compiler error
    }
}

module {
    ubyte @shared test
}
