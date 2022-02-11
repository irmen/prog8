%zeropage basicsafe

main {
    sub start() {
        uword zzz = memory("sdfasdf", 100, 0)
        str @shared foobar = "zsdfzsdf"
        str @shared foobar2 = sc:"zsdfzsdf"
    }
}
