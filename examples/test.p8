%import c64utils

~ main {

    sub start()  {

        ubyte i = 10
        ubyte ub2
        byte j = 5
        byte b2
        uword uw = 1000
        uword uw2
        word w = 1000
        word w2

;        i=10
;        ub2=i*1
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*2
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*3
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*4
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*5
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*6
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*7
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*8
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*9
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*10
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*11
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*12
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*13
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*14
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*15
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*16
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*17
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*18
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*19
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*20
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*21
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*22
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*23
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*24
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;        ub2=i*25
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;
;        i=5
;        ub2=i*40
;        c64scr.print_ub(ub2)
;        c64.CHROUT('\n')
;
;        c64.CHROUT('\n')
;
;
;        j=5
;        b2=j*1
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*2
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*3
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*4
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*5
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*6
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*7
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*8
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*9
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*10
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*11
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*12
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*13
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*14
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*15
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*16
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*17
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*18
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*19
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*20
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*21
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*22
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*23
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*24
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*25
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;
;        j=3
;        b2=j*40
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;
;        c64.CHROUT('\n')
;
;
;        ; multiplication by negative values
;        j=5
;        b2=j*-1
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-2
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-3
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-4
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-5
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-6
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-7
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-8
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-9
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-10
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-11
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-12
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-13
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-14
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-15
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-16
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-17
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-18
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-19
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-20
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-21
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-22
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-23
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-24
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;        b2=j*-25
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;
;        j=3
;        b2=j*-40
;        c64scr.print_b(b2)
;        c64.CHROUT('\n')
;
;        c64.CHROUT('\n')


        ;@todo the same, for uword and word

        uw=1000
        uw2=uw*1
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*2
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*3
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*4
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*5
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*6
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*7
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*8
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*9
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*10
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*11
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*12
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*13
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*14
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*15
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*16
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*17
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*18
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*19
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*20
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*21
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*22
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*23
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*24
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        uw2=uw*25
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')

        uw=500
        uw2=uw*40
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')

        c64.CHROUT('\n')


        w=500
        w2=w*1
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*2
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*3
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*4
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*5
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*6
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*7
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*8
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*9
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*10
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*11
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*12
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*13
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*14
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*15
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*16
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*17
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*18
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*19
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*20
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*21
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*22
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*23
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*24
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*25
        c64scr.print_w(w2)
        c64.CHROUT('\n')

        w=500
        w2=w*40
        c64scr.print_w(w2)
        c64.CHROUT('\n')

        c64.CHROUT('\n')


        ; multuwpluwcatuwon by negatuwve values
        w=500
        w2=w*-1
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-2
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-3
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-4
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-5
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-6
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-7
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-8
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-9
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-10
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-11
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-12
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-13
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-14
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-15
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-16
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-17
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-18
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-19
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-20
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-21
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-22
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-23
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-24
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        w2=w*-25
        c64scr.print_w(w2)
        c64.CHROUT('\n')

        w=500
        w2=w*-40
        c64scr.print_w(w2)
        c64.CHROUT('\n')

        c64.CHROUT('\n')


    }
}
