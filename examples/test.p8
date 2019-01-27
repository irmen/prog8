%import c64utils
%import c64flt
%option enable_floats

~ main {

    sub start() {

        ubyte ub
        byte b
        word w
        uword uw
        float f1
        float f2
        float f3
        float f4
        float f5
        float f6

        f1=sqrt(A)

        f1=A**0.5
        f2=ub**0.5
        f3=b**0.5
        f4=w**0.5
        f5=uw**0.5
        f6=f1**0.5

;        A=A**5
;        ub=ub**5
;        b=b**5
;        w=w**5
;        uw=uw**5
;        f=f**5
;
;        A=A**Y
;        ub=ub**Y
;        b=b**Y
;        w=w**Y
;        uw=uw**Y
;        f=f**Y

    }


    ; @todo code for pow()

}
