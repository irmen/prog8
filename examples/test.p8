%zeropage basicsafe

; @todo fix this loop labeling problem (issue #11 on github): it generates invalid asm due to improper label names

~ main {

    sub start() {

        byte var1
        byte var1


        sub subsub() {
            byte var1
            byte var1
        }

        sub subsub() {
            byte var1
            byte var2
            byte var3
            byte var2
        }

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
