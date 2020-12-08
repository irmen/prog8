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

        ; TODO fix compiler crash:
        ; str[max_files] names
    }
}

main {
    sub start() {
        errors.tofix()
        test_stack.test()
    }
}
