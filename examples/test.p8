%import textio
%import string
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    sub start() {
        ubyte xx=existing_entry(1,2)
    }

    ; TODO FIX COMPILER ERROR ABOUT MISSING RETURN

    sub existing_entry(ubyte hash, uword symbol) -> ubyte {
        if hash>10
            return 44
        else
            return false
    }
}

