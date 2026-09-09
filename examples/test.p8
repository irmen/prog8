main {
    sub start() {
        cx16.r1 = private.foo()
        cx16.r2 = mkword.foo()
    }
}

private {
    sub foo() -> uword {
        cx16.r0++
        return cx16.r0
    }
}

mkword {
    sub foo() -> uword {
        cx16.r0++
        return cx16.r0
    }
}
