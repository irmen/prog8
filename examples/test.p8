%import c64utils
%import c64flt

~ main {

    sub start()  {


        inlinecall(1,2,3)
        ubyte r = inlinesub(3,4,5)
        c64scr.print_ub(r)
        c64.CHROUT('\n')
    }

    sub inlinecall(byte b1, byte b2, byte b3) {
        float f=3.1415
        c64scr.print("this is inlinecall!\n")
        c64flt.print_f(f)
        f*=2.0
        c64flt.print_f(f)
        c64.CHROUT('\n')
        c64scr.print("end of inlinecall!\n")
    }

    sub inlinesub(ubyte b1, ubyte b2, ubyte b3) -> ubyte {
        c64scr.print("this is inlinesub!\n")
        ubyte qq = b1+b2
        qq += b3
        c64scr.print("end of inlinesub!\n")
        return qq
    }
}

