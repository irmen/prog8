%import textio

main {
    sub start() {
        cx16.r0 = foo()
    }

    extsub $f000 = foo() clobbers(X) -> uword @AY
}
