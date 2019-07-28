%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        str  s1 = "irmen"
        str_s  s2 = "hello"

        byte[4] barray
        ubyte[4] ubarray
        word[4] warray
        uword[4] uwarray
        float[4] flarray

        byte bb
        ubyte ub
        word ww
        uword uw
        float fl

        A=s1[2]
        ub=s1[2]
        ub=s2[2]
        bb=barray[2]
        ub=ubarray[2]
        ww=warray[2]
        uw=uwarray[2]
        fl=flarray[2]

        A=s1[Y]
        ub=s1[A]
        ub=s2[A]
        bb=barray[A]
        ub=ubarray[A]
        ww=warray[A]
        uw=uwarray[A]
        fl=flarray[A]

        A=s1[bb]
        ub=s1[bb]
        ub=s2[bb]
        bb=barray[bb]
        ub=ubarray[bb]
        ww=warray[bb]
        uw=uwarray[bb]
        fl=flarray[bb]

        A=s1[bb*3]
        ub=s1[bb*3]
        ub=s2[bb*3]
        bb=barray[bb*3]
        ub=ubarray[bb*3]
        ww=warray[bb*3]
        uw=uwarray[bb*3]
        fl=flarray[bb*3]

;        float f1 =  1.1
;        float f2 = 2.2
;
;        @(1024) = f1==f2
;
;        c64.CHROUT('\n')
;        c64scr.print_ub(f1==f2)
;        c64.CHROUT('\n')
;
;        if f1 ==0.0
;            c64scr.print("error\n")
;        else
;            c64scr.print("ok\n")

;        str  s1 = "hello"
;        str  s2 = "hello"
;        str  s3 = "hello"
;        str  s4 = "hello"
;
;        if true {
;            ubyte ub1 = 33
;            A=ub1
;            c64scr.print("irmen")
;        }
;
;        if true {
;            ubyte ub1 = 33
;            A=ub1
;            c64scr.print("irmen")
;        }

    }
}
