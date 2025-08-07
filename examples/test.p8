main {
    uword[5] a

    sub start() {
        pokebool(cx16.r0, false)
        pokebool(a[2], false)
        pokebool(cx16.r0+cx16.r1, false)


        cx16.r0bL = peekbool(cx16.r0)
        cx16.r0bL = peekbool(a[2])
        cx16.r0bL = peekbool(cx16.r0+cx16.r1)
    }
}
