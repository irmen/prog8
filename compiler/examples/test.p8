%import c64utils
%option enable_floats

~ main {

    sub start()  {

        uword   uw1
        uword   uw2
        word    w1
        word    w2
        ubyte   ub1
        ubyte   ub2
        byte    b1
        byte    b2
        float   f1
        float   f2

        ub1 = 200
        c64scr.print_ubyte(abs(ub1))    ; ok
        c64.CHROUT('\n')
        b1 = 100
        c64scr.print_byte(abs(b1))  ; ok
        c64.CHROUT('\n')
        b1 = 0
        c64scr.print_byte(abs(b1))  ;ok
        c64.CHROUT('\n')
        b1 = -100
        c64scr.print_byte(abs(b1))  ;ok
        c64.CHROUT('\n')

        uw1 = 1000
        c64scr.print_uword(abs(uw1))    ;ok
        c64.CHROUT('\n')
        w1 = 0
        c64scr.print_word(abs(w1))  ;ok
        c64.CHROUT('\n')
        w1 = -1000
        c64scr.print_word(abs(w1))  ;ok
        c64.CHROUT('\n')

        f1 = 11.22
        c64flt.print_float(abs(f1)) ;ok
        c64.CHROUT('\n')
        f1 = 0
        c64flt.print_float(abs(f1)) ;ok
        c64.CHROUT('\n')
        f1 = -22.33
        c64flt.print_float(abs(f1)) ;@todo FAIL
        c64.CHROUT('\n')

;        float t = 0.0
;        ubyte color=0
;        while(true) {
;            ubyte x = lsb(round(sin(t)*17.0))+20
;            ubyte y = lsb(round(cos(t*1.3634567)*10.0))+12
;
;            c64scr.setchrclr(x,y,color,color)
;            color++
;;            vm_gfx_text(x, y, 1, "*")
;            ;vm_gfx_pixel(x,y,1)
;;            c64scr.print_ubyte(x)
;;            c64.CHROUT(',')
;;            c64scr.print_ubyte(y)
;;            c64.CHROUT('\n')
;            ;screen[x] = '*'
;            t+=0.1
;        }
    }
}

