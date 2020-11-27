%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        ubyte[] ubarray = [100,200]

        ubyte index = 0
        ubarray[index+1] += 13
        ubarray[index+1] += 13
        ubarray[index+1] += 13
        ubarray[index+2] += 13

        txt.print_ub(ubarray[1])
        txt.chrout('\n')
    }

;    sub start222() {
;
;        ubyte[] ubarray = [100,200]
;        uword[] uwarray = [1000,2000]
;        float[] flarray = [100.1, 200.2]
;
;        ubyte index = 1
;
;        ubarray[1] += 3
;        txt.print_ub(ubarray[1])
;        txt.chrout('\n')
;        ubarray[index] += 3
;        txt.print_ub(ubarray[1])
;        txt.chrout('\n')
;        index = 0
;        ubarray[index*99+1] += 3
;        txt.print_ub(ubarray[1])
;        txt.chrout('\n')
;        txt.chrout('\n')
;
;        index = 1
;        uwarray[1] += 3
;        txt.print_uw(uwarray[1])
;        txt.chrout('\n')
;        uwarray[index] += 3
;        txt.print_uw(uwarray[1])
;        txt.chrout('\n')
;        index = 0
;        uwarray[index*99+1] += 3
;        txt.print_uw(uwarray[1])
;        txt.chrout('\n')
;        txt.chrout('\n')
;
;        index=1
;        flarray[1] += 3.0
;        floats.print_f(flarray[1])
;        txt.chrout('\n')
;        flarray[index] += 3.0
;        floats.print_f(flarray[1])
;        txt.chrout('\n')
;        index = 0
;        flarray[index*99+1] += 3.0
;        floats.print_f(flarray[1])
;        txt.chrout('\n')
;
;        test_stack.test()
;
;    }
;
;    sub name() -> str {
;        return "irmen"
;    }
}
