main {
    sub start() {
        cx16.r1 = memory.foo()
        cx16.r2 = mkword.foo()
    }
}

memory {
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
