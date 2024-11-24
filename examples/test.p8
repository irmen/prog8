main {
    sub start() {
        foo(42)
        bar(9999,55)
;        faulty1(false)
        faulty2(42)
        faulty3(9999,55)
    }

    sub foo(ubyte arg @R2) {
        arg++
    }

    sub bar(uword arg @R0, ubyte arg2 @R1) {
        arg += arg2
    }

;    sub faulty1(bool flag @Pc) {
;        cx16.r0++
;    }

    sub faulty2(byte arg @Y) {
        arg++
    }

    sub faulty3(uword arg @R1, ubyte arg2 @R1) {
        arg += arg2
    }
}
