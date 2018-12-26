%import c64utils
%option enable_floats


~ main {

    ;@todo implement the various byte/word division routines.

     ;c64scr.PLOT(screenx(x), screeny(y))    ; @todo fix argument calculation of parameters ???!!!


    const uword width = 320
    const uword height = 200


    sub screenx(float x) -> word {
        ;return ((x/4.1* (width as float)) + 160.0) as word ;width // 2       ; @todo fix calculation
        float wf = width
        return (x/4.1* wf + wf / 2.0)   as word
    }


    sub start()  {

;        c64scr.print("    X=")
;        c64scr.print_ub(X)
;        c64.CHROUT('\n')

    ubyte[256] screenarray
    ubyte index = 2
    screenarray[1]--
    screenarray[index]--


;        c64scr.print("    X=")
;        c64scr.print_ub(X)
;        c64.CHROUT('\n')

    }

}
