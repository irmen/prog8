%import c64utils
%import c64flt

~ main {

    sub start()  {

        while(true)
            A++


        repeat A++ until(false)


        for ubyte i in 0 to 10
            A++


;        c64scr.print_ub(c64utils.str2ubyte("1"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("12"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("123"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("1234"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("12xyz"))
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        c64scr.print_ub(c64utils.str2ubyte("19"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("199"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("29"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("99xyz"))
;        c64.CHROUT('\n')
;        c64scr.print_ub(c64utils.str2ubyte("199xyz"))
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')

        c64scr.print_b(c64utils.str2byte("1"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("12"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("123"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("1234"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2ubyte("12xyz"))
        c64.CHROUT('\n')
        c64.CHROUT('\n')

                c64scr.print_b(c64utils.str2byte("19"))
                c64.CHROUT('\n')
                c64scr.print_b(c64utils.str2byte("29"))
                c64.CHROUT('\n')
                c64scr.print_b(c64utils.str2byte("199"))
                c64.CHROUT('\n')
                c64scr.print_b(c64utils.str2byte("299"))
                c64.CHROUT('\n')
                c64scr.print_b(c64utils.str2ubyte("99zzxyz"))
                c64.CHROUT('\n')
                c64.CHROUT('\n')

        c64scr.print_b(c64utils.str2byte("-9"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-99"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-199"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-1111"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-12xyz"))
        c64.CHROUT('\n')

    }

    sub foo(ubyte param1, ubyte param2) {
        ubyte local1
    }

}

