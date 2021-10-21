main {
    sub start() {
        asmfunc("text")
        func("text")
    }

    asmsub asmfunc(str thing @AY) {
        %asm {{
            rts
        }}
    }


    sub func(str thing) {
    }
}
