%import c64utils

~ main {

    ubyte b1 = 42
    word w = -999

    sub start() {
        vm_write_num(b1)
        vm_write_char('\n')
        vm_write_num(w)
        vm_write_char('\n')

;        c64scr.print_ub(b1)
;        c64.CHROUT('\n')
;        c64scr.print_w(w)
;        c64.CHROUT('\n')

        b1=0
        w=0

        vm_write_num(b1)
        vm_write_char('\n')
        vm_write_num(w)
        vm_write_char('\n')

;        c64scr.print_ub(b1)
;        c64.CHROUT('\n')
;        c64scr.print_w(w)
;        c64.CHROUT('\n')
        derp.derp()

    }
}


~ derp {

    ubyte b1 = 55

    sub derp() {
        word w = -999
        vm_write_num(b1)
        vm_write_char('\n')
        vm_write_num(w)
        vm_write_char('\n')

;        c64scr.print_ub(b1)
;        c64.CHROUT('\n')
;        c64scr.print_w(w)
;        c64.CHROUT('\n')

        b1=0
        w=0

        vm_write_num(b1)
        vm_write_char('\n')
        vm_write_num(w)
        vm_write_char('\n')

;        c64scr.print_ub(b1)
;        c64.CHROUT('\n')
;        c64scr.print_w(w)
;        c64.CHROUT('\n')

    }

}


;~ main {
;
;    sub start() {
;
;        ubyte @zp ub
;        byte  @zp b
;        word @zp w
;        uword @zp uw
;
;
;        byte nonzp1
;        byte nonzp2
;        byte nonzp3
;        foo.bar()
;    }
;
;}
;
;~ foo {
;
;sub bar() {
;        ubyte @zp ub
;        byte  @zp b
;        word @zp w
;        uword @zp uw
;
;    word nonzp1
;    word nonzp2
;    word nonzp3
;    A=55
;}
;}
