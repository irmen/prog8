%import textio
%import diskio
%import floats
%zeropage basicsafe
%import test_stack
%option no_sysinit

errors {
    sub tofix() {


;        ; TODO fix undefined symbol:
;        repeat {
;            ubyte char = c64.CHRIN()
;            ; ...
;        }
;        do {
;            char = c64.CHRIN()      ; TODO fix undefined symbol error, should refer to 'char' above in the subroutine's scope
;        } until char==0

        str[4] names1 = ["one", "two", "three", "four"]
        str[4] names2

        names2[0] = "four"
        names2[1] = "three"
        names2[2] = "two"
        names2[3] = "one"

        uword xx
        for xx in names1 {
            txt.print(xx)
            txt.chrout(',')
        }
        txt.chrout('\n')
        for xx in names2 {
            txt.print(xx)
            txt.chrout(',')
        }
        txt.chrout('\n')
    }
}

main {
    sub start() {
        errors.tofix()
        test_stack.test()
    }
}
