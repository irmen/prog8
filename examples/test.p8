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

        asmfunc("text12345")
        asmfunc(text)
        asmfunc($2000)
        txt.nl()
        func("text12345")
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

    sub func(str thing) {
        uword t2 = thing as uword
        ubyte length = string.length(thing)
        txt.print_uwhex(thing, true)
        txt.nl()
        txt.print_ub(length)
        txt.nl()
        txt.print(thing)
        txt.nl()
    }
}
