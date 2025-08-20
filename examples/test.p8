%import textio
%import floats

%option no_sysinit
%zeropage basicsafe


main {

    struct List {
        ^^uword s
        ubyte n
        ^^List next
    }

    sub start() {
        ubyte[10] array
        uword @shared wordptr
        ^^bool @shared boolptr
        ^^float @shared floatptr
        ^^byte @shared byteptr
        ^^ubyte @shared ubyteptr
        ^^List @shared listptr
        ^^List @shared listptr2

        bool @shared zz
        float @shared fl
        byte @shared bb

        zz = boolptr[999]
        fl = floatptr[999]
        bb = byteptr[999]
        cx16.r0L = ubyteptr[999]
        cx16.r1L = wordptr[999]
        cx16.r2L = array[9]
    }
}

;main {
;    sub start() {
;        floatprob()
;        basicpointers()
;
;        ; TODO test address-of with array indexed as well
;    }
;
;    sub floatprob() {
;        uword @shared dummy
;        ^^float[10] floatptrs
;        ^^float fptr1 = 20000 + 3*sizeof(float)
;        ^^float fptr2 = 20000
;        txt.print_uw(&floatptrs)
;        txt.spc()
;        txt.print_uw(&floatptrs[3])   ; NOTE: will be 3 because the pointer array is split-words .  This codegen is OK!
;        txt.nl()
;
;        txt.print_uw(fptr1)
;        txt.spc()
;        txt.print_uw(fptr2)
;        txt.spc()
;        txt.print_uw(& fptr2[3])
;        txt.spc()
;        txt.print_uw(&& fptr2[3])
;        txt.spc()
;        cx16.r0L = 3
;        txt.print_uw(& fptr2[cx16.r0L])
;        txt.spc()
;        txt.print_uw(&& fptr2[cx16.r0L])
;        txt.nl()
;        cx16.r0L = 2
;        txt.print_uw(& fptr2[cx16.r0L+1])
;        txt.spc()
;        txt.print_uw(&& fptr2[cx16.r0L+1])
;        txt.nl()
;
;        pokef(20000+3*sizeof(float), 3.1415927)
;        txt.print_f(fptr1^^)
;        txt.spc()
;        txt.print_f(fptr2[3])
;        txt.nl()
;    }
;
;    sub basicpointers() {
;        ^^bool bptr = 20000
;        ^^float fptr = 20100
;        ^^word wptr = 20200
;        ^^ubyte ubptr = 20300
;
;        txt.print("direct deref:\n")
;        txt.print_bool(bptr^^)
;        txt.spc()
;        txt.print_f(fptr^^)
;        txt.spc()
;        txt.print_w(wptr^^)
;        txt.spc()
;        txt.print_ub(ubptr^^)
;        txt.nl()
;
;        @(20000) = 1
;        pokef(20100, 3.1415927)
;        pokew(20200, -22222 as uword)
;        @(20300) = 123
;
;        txt.print_bool(bptr^^)
;        txt.spc()
;        txt.print_f(fptr^^)
;        txt.spc()
;        txt.print_w(wptr^^)
;        txt.spc()
;        txt.print_ub(ubptr^^)
;        txt.nl()
;
;
;        txt.print("indexed deref (const):\n")
;        @(20003) = 1
;        pokef(20100+3*sizeof(float), 9.876543)
;        pokew(20200+3*2, -11111 as uword)
;        @(20300+3) = 42
;
;        txt.print_bool(bptr[3])
;        txt.spc()
;        txt.print_f(fptr[3])
;        txt.spc()
;        txt.print_w(wptr[3])
;        txt.spc()
;        txt.print_ub(ubptr[3])
;        txt.nl()
;
;
;        txt.print("indexed deref (var):\n")
;        cx16.r0L = 3
;        txt.print_bool(bptr[cx16.r0L])
;        txt.spc()
;        txt.print_f(fptr[cx16.r0L])
;        txt.spc()
;        txt.print_w(wptr[cx16.r0L])
;        txt.spc()
;        txt.print_ub(ubptr[cx16.r0L])
;        txt.nl()
;
;        txt.print("indexed deref (expr):\n")
;        cx16.r0L = 2
;        txt.print_bool(bptr[cx16.r0L+1])
;        txt.spc()
;        txt.print_f(fptr[cx16.r0L+1])
;        txt.spc()
;        txt.print_w(wptr[cx16.r0L+1])
;        txt.spc()
;        txt.print_ub(ubptr[cx16.r0L+1])
;        txt.nl()
;    }
;}
;
;
;;        ^^Node[5] nodesarray
;;
;;        cx16.r0L = nodesarray[2].weight
;;        cx16.r0L = nodes[2].weight
;
;
;;main {
;;    sub start() {
;;        struct List {
;;            bool b
;;            uword value
;;        }
;;
;;        ^^List[10] listarray
;;        cx16.r0 = listarray[2].value
;;        cx16.r1 = listarray[3]^^.value
;;    }
;;}
