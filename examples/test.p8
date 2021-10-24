%import string
%import textio
%zeropage basicsafe

main {
    sub start() {
        str text = "variable"

        @($2000) = 'a'
        @($2001) = 'b'
        @($2002) = 'c'
        @($2003) = 0

        asmfunc("text")
        asmfunc(text)
        asmfunc($2000)
        func("text")
        func(text)
        func($2000)
    }

    asmsub asmfunc(str thing @AY) {
        %asm {{
            sta  func.thing
            sty  func.thing+1
            jmp  func
        }}
    }

    ; TODO fix asmgen when using 'str' type
    sub func(uword thing) {
        uword t2 = thing as uword
        ubyte length = string.length(thing)
        txt.print_ub(length)
        txt.nl()
        txt.print(thing)
        txt.nl()
    }
}
