%zeropage basicsafe

; @todo fix this loop labeling problem (issue #11 on github): it generates invalid asm due to improper label names


~ main {

label2:

    sub start() {

label3:
        byte var1

label4:

        sub subsub() {

label3:
label4:
            byte var1
        }

        sub subsub2() {
label3:
label4:
            byte var1
            byte var2
            byte var3

            label5ss2:

                    if A>10 {

            label6ss2:
                        A=44
                        while true {
            label7ss2:
                            ;derp
                        }
                    } else {

                        gameoverss2:
                            goto gameoverss2
                    }
            label8ss2:

        }

label5:

        if A>10 {

label6:
            A=44
            while true {
label7:
                ;derp
            }
        } else {

            gameover:
                goto gameover
        }
label8:

    }

}
