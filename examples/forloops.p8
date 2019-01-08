%import c64utils
%import c64flt


~ main {

    ubyte[3] ubarray = [11,55,222]
    byte[3] barray = [-11,-22,-33]
    uword[3] uwarray = [111,2222,55555]
    word[3] warray = [-111,-222,-555]
    float[3] farray = [1.11, 2.22, -3.33]
    str text = "hello\n"

    sub start()  {
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        c64scr.print("loop str\n")
        for ubyte c in text {
            c64scr.print_ub(c)
            c64.CHROUT(',')
        }

        c64scr.print("\nloop ub\n")
        for ubyte ub in ubarray{
            c64scr.print_ub(ub)
            c64.CHROUT(',')
        }

        c64scr.print("\nloop b\n")
        for byte b in barray {
            c64scr.print_b(b)
            c64.CHROUT(',')
        }

        c64scr.print("\nloop uw\n")
        for uword uw in uwarray {
            c64scr.print_uw(uw)
            c64.CHROUT(',')
        }

        c64scr.print("\nloop w\n")
        for word w in warray {
            c64scr.print_w(w)
            c64.CHROUT(',')
        }

        c64scr.print("\nloop f\n")
        for float f in farray {
            c64flt.print_f(f)
            c64.CHROUT(',')
        }


ending:
        c64scr.print("\nending\n")
        c64scr.print_ub(X)
        c64.CHROUT('\n')
    }
}
