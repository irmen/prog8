%option enable_floats

main {

    sub start() {
;        struct Node {
;            ^^uword s
;        }
;
;        ^^Node l1
;
;        l1^^.s[0] = 4242        ; TODO fix parse error

        other.foo2.listarray[3]^^.value = cx16.r0       ; TODO fix syntax error
        other.foo2()
    }
}


other {
    sub foo2() {
        struct List {
            bool b
            uword value
        }

        ^^List[10] listarray
        ; listarray[3].value = cx16.r0      ; TODO fix parse error
        listarray[3]^^.value = cx16.r0      ; TODO fix syntax error
    }
}
