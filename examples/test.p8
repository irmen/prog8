%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        str bla = "asfasd" + "zzz"
        str bla2 = "sdfsdf" * 4

        c64scr.print(bla)
        c64.CHROUT('\n')
        c64scr.print(bla2)
        c64.CHROUT('\n')
    }

}
