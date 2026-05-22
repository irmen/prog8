%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte v1, v2

        cx16.r0L = 99

        v1 = inline_paramless_single()
        txt.print_ub(v1)
        txt.nl()
        v1, v2 = inline_paramless_multi()
        txt.print_ub(v1)
        txt.spc()
        txt.print_ub(v2)
        txt.nl()

        v1 = inline_single(99)          ; TODO not inlined, why?
        txt.print_ub(v1)
        txt.nl()
        v1, v2 = inline_multi(99)       ; TODO not inlined, why?
        txt.print_ub(v1)
        txt.spc()
        txt.print_ub(v2)
        txt.nl()
    }

    inline sub inline_paramless_single() -> ubyte {
        return cx16.r0L+100
    }

    inline sub inline_paramless_multi() -> ubyte, ubyte {
        return cx16.r0L+10, cx16.r0L+20
    }

    inline sub inline_single(ubyte v) -> ubyte {
        return v+100
    }

    inline sub inline_multi(ubyte v) -> ubyte, ubyte {
        return v+10, v+20
    }
}
