%zeropage basicsafe
%option enable_floats

%import c64flt


~ main {

    sub start() {

    ubyte[3] array1
    ubyte[3] array2
    ubyte[3] array3

    str string1="hello"
    str string2="bye"

    uword pointer = &array1
    uword pointer2
    uword pointer3
    byte bt

    pointer2 = &array2
    pointer3 = &string1

    uword[4] pointers = [&array1, &array2, &string1, &string2]


    ptrsubasm("moet werken")
    pointersub("moet werken")
    myprintasm(string1)
    myprintasm(string2)
    myprintasm("moet werken3")
    myprintasm("moet werken3")
    myprintasm("moet werken4")

    c64scr.print("this print must work\n")
    c64.CHROUT('\n')

    ptrsubasm(&array1)
    ptrsubasm(&array2)
    ptrsubasm(&string1)
    ptrsubasm(&string2)
    pointersub(&array1)
    pointersub(&array2)
    pointersub(&string1)
    pointersub(&string2)
    c64scr.print_uwhex(1, pointers[0])
    c64.CHROUT(',')
    c64scr.print_uwhex(1, pointers[1])
    c64.CHROUT(',')
    c64scr.print_uwhex(1, pointers[2])
    c64.CHROUT(',')
    c64scr.print_uwhex(1, pointers[3])

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

~ test {

    sub testsub() {
        uword[4] pointers = [&main.start.array1, &main.start.array2, &main.start.string1, &main.start.string2]   ; @todo make it possible to initialize array with pointers

    }
}
