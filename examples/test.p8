%import textio
%zeropage basicsafe

main {
    sub start() {
        foo("hello")
    }

    sub foo(str s) {
        cx16.r0L = len(s)
        cx16.r1L = len(false)
    }
}
