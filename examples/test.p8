%import textio
%import test_stack
%import string
%zeropage dontuse

main {

    sub start() {

        uword[10] ptrs
        ubyte @shared xx
        xx = compare("zzz", ptrs[2])
    }

    asmsub compare(uword string1 @R0, uword string2 @AY) clobbers(Y) -> byte @A {
        %asm {{
        rts
        }}
    }
}
