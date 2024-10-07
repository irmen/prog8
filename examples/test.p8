%import textio
%zeropage basicsafe

main {
    sub foo() { foo() }
    sub bar() -> ubyte { return bar() }
    sub start() { }
}
