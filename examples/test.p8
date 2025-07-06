%import textio
%option enable_floats

main {

    sub start() {
        ^^ubyte[4] array1
        ^^byte[4] array2
        ^^bool[4] array3
        ^^word[4] array4
        ^^uword[4] array5
        ^^float[4] array6
        ^^long[4] array7
        ^^str[4] array8

        error1(array1)
        error2(array2)
        error3(array3)
        error4(array4)
        error5(array5)
        error6(array6)
        error7(array7)
        error8(array8)

        ok1(array1)
        ok2(array2)
        ok3(array3)
        ok4(array4)
        ok5(array5)
        ok6(array6)
        ok7(array7)
        ok8(array8)
    }

    sub error1(^^ubyte ptr) {
        cx16.r0++
    }
    sub error2(^^byte ptr) {
        cx16.r0++
    }
    sub error3(^^bool ptr) {
        cx16.r0++
    }
    sub error4(^^word ptr) {
        cx16.r0++
    }
    sub error5(^^uword ptr) {
        cx16.r0++
    }
    sub error6(^^float ptr) {
        cx16.r0++
    }
    sub error7(^^long ptr) {
        cx16.r0++
    }
    sub error8(^^str ptr) {
        cx16.r0++
    }

    sub ok1(^^ubyte[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
    sub ok2(^^byte[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
    sub ok3(^^bool[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
    sub ok4(^^word[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
    sub ok5(^^uword[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
    sub ok6(^^float[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
    sub ok7(^^long[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
    sub ok8(^^str[] ptr) {
        txt.print_uw(ptr)
        txt.nl()
    }
}
