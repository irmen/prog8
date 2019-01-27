%import c64utils

~ main {

    ubyte xx=99
    word yy=12345

    sub start() {
        c64scr.print_ub(xx)
        c64.CHROUT('\n')
        c64scr.print_w(yy)
        c64.CHROUT('\n')

        foo.derp()
        foo2.derp()
    }


    ; @todo code for pow()

    ; @todo optimize code generation for "if blah ..." and "if not blah ..."

    ; @todo optimize vm
    ;        push_byte  ub:01
    ;        jnz  _prog8stmt_7_loop
}


~ foo {

    ubyte woo=2

    sub derp() {
        A=woo
    }
}

~ foo2 {

    sub derp() {
        ubyte woo=3
        A=99
    }
}
