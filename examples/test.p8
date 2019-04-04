%zeropage basicsafe

~ main {

    sub start() {

        ; TODO what about avg() on floating point array variable!


    ubyte[3] array1
    ubyte[3] array2
    ubyte[3] array3

    str string1="hello"
    str string2="bye"

    uword pointer = &array1
    byte bt

    pointer = &array2
    pointer = &string1

    uword[4] pointers = [&array1, &array2, &string1, &string2]   ; @todo make it possible to initialize array with pointers

    ;ptrsubasm("moet werken")         ; @todo rewrite ast into pointer-of expression (and remove special cases from Compiler)
    ;pointersub("moet werken")        ; @todo rewrite ast into pointer-of expression (and remove special cases from Compiler)
    ;myprintasm("moet werken3")          ; @todo rewrite ast into pointer-of expression (and remove special cases from Compiler)

    ptrsubasm(&array1)
    ptrsubasm(&string1)
    pointersub(&array1)
    pointersub(&string1)

    }

    sub pointersub(uword arg) {
        A=lsb(arg)
    }

    asmsub ptrsubasm(uword arg @ AY) -> clobbers() -> () {
        A=4
    }

    asmsub myprintasm(str arg @ AY) -> clobbers() -> () {
        A=4
    }

}
