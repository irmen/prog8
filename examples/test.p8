main {
    sub start() {
        str zzz
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
