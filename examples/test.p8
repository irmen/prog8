%zeropage basicsafe
%option enable_floats

%import c64flt


~ main {

    sub start() {
        ;aggregates()
        pointers()
    }

    sub aggregates() {

        ; @todo test this in StackVM as well!!

        byte[3] ba = [-5, 0, 20]
        ubyte[3] uba = [100, 0, 20]
        word[3] wa = [-5000, 0, 2000]
        uword[3] uwa = [10000, 0, 2000]
        float[4] fa = [5.5, 0.0, 20.22,-4.44]

        c64scr.print_ub(any(ba))
        c64.CHROUT(' ')
        c64scr.print_ub(any(uba))
        c64.CHROUT(' ')
        c64scr.print_ub(any(wa))
        c64.CHROUT(' ')
        c64scr.print_ub(any(uwa))
        c64.CHROUT(' ')
        c64scr.print_ub(any(fa))
        c64.CHROUT('\n')

        c64scr.print_ub(all(ba))
        c64.CHROUT(' ')
        c64scr.print_ub(all(uba))
        c64.CHROUT(' ')
        c64scr.print_ub(all(wa))
        c64.CHROUT(' ')
        c64scr.print_ub(all(uwa))
        c64.CHROUT(' ')
        c64scr.print_ub(all(fa))
        c64.CHROUT('\n')

        c64scr.print_b(min(ba))
        c64.CHROUT(' ')
        c64scr.print_ub(min(uba))
        c64.CHROUT(' ')
        c64scr.print_w(min(wa))
        c64.CHROUT(' ')
        c64scr.print_uw(min(uwa))
        c64.CHROUT(' ')
        c64flt.print_f(min(fa))     ; @todo fix min(floatarray)
        c64.CHROUT('\n')

        c64scr.print_b(max(ba))
        c64.CHROUT(' ')
        c64scr.print_ub(max(uba))
        c64.CHROUT(' ')
        c64scr.print_w(max(wa))
        c64.CHROUT(' ')
        c64scr.print_uw(max(uwa))
        c64.CHROUT(' ')
        c64flt.print_f(max(fa))     ; @todo fix max(floatarray)
        c64.CHROUT('\n')

        c64scr.print_uw(sum(ba))
        c64.CHROUT(' ')
        c64scr.print_uw(sum(uba))
        c64.CHROUT(' ')
        c64scr.print_w(sum(wa))
        c64.CHROUT(' ')
        c64scr.print_uw(sum(uwa))
        c64.CHROUT(' ')
        c64flt.print_f(sum(fa))
        c64.CHROUT('\n')

        c64flt.print_f(avg(ba))
        c64.CHROUT(' ')
        c64flt.print_f(avg(uba))
        c64.CHROUT(' ')
        c64flt.print_f(avg(wa))
        c64.CHROUT(' ')
        c64flt.print_f(avg(uwa))
        c64.CHROUT(' ')
        c64flt.print_f(avg(fa))
        c64.CHROUT('\n')

    }

    sub pointers() {

    ubyte[3] array1
    ubyte[3] array2
    ubyte[3] array3

    str string1="hello"
    str string2="bye"

    uword pointer = &array1
    byte bt

    pointer = &array2
    pointer = &string1

    ;uword[4] pointers = [&array1, &array2, &string1, &string2]   ; @todo make it possible to initialize array with pointers


    ;ptrsubasm("moet werken")         ; @todo rewrite ast into pointer-of expression (and remove special cases from Compiler)
    ;pointersub("moet werken")        ; @todo rewrite ast into pointer-of expression (and remove special cases from Compiler)

    myprintasm("moet werken3")
    myprintasm("moet werken3")
    myprintasm("moet werken4")

    c64.CHROUT('\n')

    ptrsubasm(&array1)
    ptrsubasm(&array2)
    ptrsubasm(&string1)
    ptrsubasm(&string2)
    pointersub(&array1)
    pointersub(&array2)
    pointersub(&string1)
    pointersub(&string2)

    }

    sub pointersub(uword arg) {
        c64scr.print_uwhex(1, arg)
        c64.CHROUT('\n')
    }

    asmsub ptrsubasm(uword arg @ AY) -> clobbers() -> () {
        %asm {{
            sec
            jsr  c64scr.print_uwhex
            lda  #13
            jmp  c64.CHROUT
        }}
    }

    asmsub myprintasm(str arg @ AY) -> clobbers() -> () {
        %asm {{
            sec
            jsr  c64scr.print_uwhex
            lda  #13
            jmp  c64.CHROUT
        }}
    }

}
