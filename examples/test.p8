%option enable_floats

main {

    sub start() {
        ^^ubyte[4] array1 = [1000, 1100, 1200, 1300]
        ^^byte[4] array2 = [1000, 1100, 1200, 1300]
        ^^bool[4] array3 = [1000, 1100, 1200, 1300]
        ^^word[4] array4 = [1000, 1100, 1200, 1300]
        ^^uword[4] array5 = [1000, 1100, 1200, 1300]
        ^^float[4] array6 = [1000, 1100, 1200, 1300]
        ^^long[4] array7 = [1000, 1100, 1200, 1300]
        ^^str[4] array8 = [1000, 1100, 1200, 1300]

        cx16.r0 = array1[2]
        cx16.r1 = array2[2]
        cx16.r2 = array3[2]
        cx16.r3 = array4[2]
        cx16.r4 = array5[2]
        cx16.r5 = array6[2]
        cx16.r6 = array7[2]
        cx16.r7 = array8[2]

;        txt.print_uw(array[1])
;        txt.print_uw(array[2])
;        txt.print_uw(array[3])
;        txt.print_uw(array[4])
;        txt.print_uw(array[5])
;        txt.print_uw(array[6])
;        txt.print_uw(array[7])
;
;        func(farray)
    }

;    sub func(^^float zzz) {
;        cx16.r0++
;    }
}
