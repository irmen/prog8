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

        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        c64scr.print("    X=")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

    }

}

~ irq {

sub irq() {
    memory ubyte[256] screenarray = $0400
    memory ubyte firstscreenchar = $0400

    screenarray[0]++        ; @todo incorrect code generated?
    firstscreenchar++       ; ... this is okay
    c64.EXTCOL++
}

}
