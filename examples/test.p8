%import c64utils
%import c64flt

~ main {

    sub start()  {


        c64scr.print_ub(c64utils.str2ubyte("1"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2ubyte("12"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2ubyte("123"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2ubyte("1234"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2ubyte("12xyz"))
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print_ub(c64utils.str2byte("1"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2byte("12"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2byte("123"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2byte("1234"))
        c64.CHROUT('\n')
        c64scr.print_ub(c64utils.str2ubyte("12xyz"))
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print_b(c64utils.str2byte("-1"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-12"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-123"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-1111"))
        c64.CHROUT('\n')
        c64scr.print_b(c64utils.str2byte("-12xyz"))
        c64.CHROUT('\n')

    }
}

