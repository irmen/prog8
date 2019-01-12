%import c64utils
%import c64flt

~ main {

    sub start()  {

        ubyte i=101

        A=4
        A=5
        A=6
        A=i
        A=99        ; folded ok!

        i=4
        i=5
        i=6
        i=A
        i=99            ; folded ok

        @($d020) = 4
        @($d020) = 5
        @($d020) = 6
        @($d020) = 7
        @($d020) = 8        ; @todo should not be folded

        c64.EXTCOL = 4
        c64.EXTCOL = 5
        c64.EXTCOL = 6
        c64.EXTCOL = 7
        c64.EXTCOL = 8      ; @todo not fold

    }
}

