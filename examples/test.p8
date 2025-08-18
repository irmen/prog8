%import textio
%import floats

%option no_sysinit
%zeropage basicsafe

main {
    struct Node {
        ^^Node left
        ^^Node right
        uword value
    }

    sub start() {
        ^^Node n1 = 30000
        ^^Node n2 = 30200

        dump(n1)
        dump(n2)
        n1.left = 1111
        n1.right = 2222
        n1.value = 3333
        n2.left=4444
        n2.right=5555
        n2.value=6666
        dump(n1)
        dump(n2)

        n1.right = n2.left
        n1.left = n2.right
        dump(n1)

    }

    sub dump(^^Node n) {
        txt.print_uw(n.left)
        txt.spc()
        txt.print_uw(n.right)
        txt.spc()
        txt.print_uw(n.value)
        txt.nl()
    }
}


;main {
;    sub start() {
;        floatprob()
;        ;basicpointers()
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
