
; @todo fix this (issue #11 on github): it generates invalid asm due to improper label names

~ main {

    sub start() {

        if A>10 {
            A=44
            while true {
                ;derp
            }
        } else {

            gameover:
                goto gameover
        }
    }

}
