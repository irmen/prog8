%import string
%import textio
%zeropage basicsafe

main {
    sub start() {
        uword[] values = [1111,2222,3333,4444]

        @($2000) = 'a'
        @($2001) = 'b'
        @($2002) = 'c'
        @($2003) = 0

        asmfunc([999,888,777])
        asmfunc(values)
        asmfunc($2000)
        txt.nl()
        func([999,888,777])
        func(values)
        func($2000)
    }

    asmsub asmfunc(uword[] thing @AY) {
        %asm {{
            sta  func.thing
            sty  func.thing+1
            jmp  func
        }}
    }

    sub func(uword[] thing) {
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
