%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        ubyte zz

        carry(0, 0)
        carry(1, 1)

        A=0
        carry(2, A)
        A=1
        carry(3, A)

        zz=0
        carry(4, zz)
        zz=122
        carry(5, zz)

        carry(6, zz-122)
        carry(7, zz+34)

        ubyte endX = X
        if endX == 255
            c64scr.print("\n\nstack x ok!\n")
        else
            c64scr.print("\n\nerror: stack x != 255 !\n")

;        ubyte bb = @($d020)+4
;        ubyte bb2 = @($d020+A)+4
;
;        subje(55)
;        subje(@($d020+bb))
;        subje(A)
;        subje(bb)
;        subje(bb+43)
    }

    asmsub carry(byte offset @Y, ubyte cc @Pc) {
        %asm {{
        bcc  +
        lda  #1
        sta  $0400,y
        rts
+
        lda  #0
        sta  $0400,y
        rts
        }}
    }

    sub subje(ubyte arg) {
        A=arg
    }
}
