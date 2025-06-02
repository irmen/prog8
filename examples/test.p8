%import textio
%zeropage basicsafe

main {
    sub start() {
        bool @shared bb, bb2

        bb=true
        bb2=false

        if bb and test() and testasm()
            txt.print("yes1 ")
        else
            txt.print("error1 ")

        if bb2 or test2() or testasm2()
            txt.print("error2 ")
        else
            txt.print("yes2 ")
        txt.nl()
    }

    sub test() -> bool {
        cx16.r0++
        return true
    }

    sub test2() -> bool {
        cx16.r0++
        return false
    }

    asmsub testasm() -> bool @A {
        %asm {{
            lda  #1
            ldy  #0
            rts
        }}
    }

    asmsub testasm2() -> bool @A {
        %asm {{
            lda  #0
            ldy  #1
            rts
        }}
    }
}
