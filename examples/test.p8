main {
    sub start() {

        ^^uword @shared ptr
        uword @shared ww

        one()
        two()
        three()
        four()

        sub one() { ; TODO horrific code bloat
            cx16.r0 = ptr + cx16.r0L
            cx16.r1 = peekw(ptr + cx16.r0L)
            pokew(ptr + cx16.r0L, 9999)
        }

        sub two() {
            ; efficient code, no calls to peekw / pokew
            cx16.r0 = ww + cx16.r0L * 2
            cx16.r1 = peekw(ww + cx16.r0L * 2)
            pokew(ww + cx16.r0L * 2, 9999)
        }

        sub three() {
            ; TODO semi-efficient code,  calls peekw/pokew
            cx16.r0 = ptr - cx16.r0L
            cx16.r1 = peekw(ptr - cx16.r0L)
            pokew(ptr - cx16.r0L, 9999)
        }

        sub four() {        ; TODO funnily enough, horrific code bloat on regular uword
            cx16.r0 = (ww - cx16.r0L * 2) as uword
            cx16.r1 = peekw((ww - cx16.r0L * 2) as uword)
            pokew((ww - cx16.r0L * 2) as uword, 9999)
        }
    }
}
