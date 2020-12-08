%import textio
%import diskio
%import floats
%zeropage basicsafe
%import test_stack
%option no_sysinit

errors {
    sub tofix() {

        repeat {
            ubyte char3 = c64.CHRIN()
            if_cc
                break

            if_z
                char3 ++
            else
                break

            char3--
        }
labeltje:


        while c64.CHRIN() {
            ; TODO: the loop condition isn't properly tested because a ldx is in the way before the beq
        }

        repeat {
            ubyte char2 = c64.CHRIN()
            if char2==0         ; TODO condition not properly tested after optimizing because there's only a sta char2 before it (works without optimizing)
                break
        }

        ; TODO fix undefined symbol:
        repeat {
            ubyte char = c64.CHRIN()
            ; ...
        }
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
