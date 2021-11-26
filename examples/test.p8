
main {

    sub start() {
        ubyte @shared xx
        main.routine.r1arg = 20
        ; main.routine2.r2arg = 20      ; TODO asmgen

        xx = main.routine.r1arg
        xx++
        ;xx = main.routine2.r2arg           ; TODO asmgen
        ;xx++

        printstuff("hello")
        repeat {
        }
    }

    sub printstuff(str addr) {

    }
    sub routine(ubyte r1arg) {
        r1arg++
    }

    asmsub routine2(ubyte r2arg @ A) {
        %asm {{
            rts
        }}
    }

}
