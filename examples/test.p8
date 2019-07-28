%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        carry(1)
        carry(0)
;        ubyte bb = @($d020)+4
;        ubyte bb2 = @($d020+A)+4
;
;        subje(55)
;        subje(@($d020+bb))
;        subje(A)
;        subje(bb)
;        subje(bb+43)
    }

    sub carry(ubyte cc) {
        A=cc
        if A!=0
            c64scr.print("carry set\n")
        else
            c64scr.print("carry clear\n")
    }

    sub subje(ubyte arg) {
        A=arg
    }
}
