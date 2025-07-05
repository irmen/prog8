%option enable_floats

main {

    sub start() {
;        struct Node {
;            ^^uword s
;        }
;
;        ^^Node l1

        ;l1^^.s[0] = 4242        ; TODO fix parse error

        other.foo()
        cx16.r1L = other.foo.ptrarray[3]^^
        cx16.r1bL = other.foo.bptrarray[3]^^
        ;other.foo.ptrarray[3]^^ = 99       ; TODO fix syntax error and rewrite  poke(ptrarray[3], 99)
        ;other.foo.bptrarray[3]^^ = true      ; TODO fix syntax error and rewrite  poke(ptrarray[3], true)

        ;other.foo2()
    }
}


other {
    sub foo() {
        ^^word[10] wptrarray
        ^^float[10] fptrarray
        ^^ubyte[10] ptrarray
        ^^bool[10] bptrarray
        float @shared f1 = fptrarray[3]^^
        cx16.r1s = wptrarray[3]^^
        cx16.r1L = ptrarray[3]^^
        cx16.r1bL = bptrarray[3]^^
        ; ptrarray[3]^^ = 99              ; TODO fix syntax error and rewrite  poke(ptrarray[3], 99)
        ; bptrarray[3]^^ = true           ; TODO fix syntax error and rewrite  poke(bptrarray[3], 99)
    }

;    sub foo2() {
;        struct List {
;            bool b
;            uword value
;        }
;
;        ^^List[10] listarray
;        listarray[3].value = cx16.r0      ; TODO fix parse error
;        listarray[3]^^.value = cx16.r0      ; TODO fix syntax error
;    }
}
