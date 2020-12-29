%import test_stack
%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        uword cnt
        ubyte ub


        ; TODO differences between:

        repeat cnt as ubyte {           ; TODO this goes via stack
            ub++
        }

        repeat lsb(cnt) {           ; TODO this doesnt
            ub++
        }


        ; TODO stack based evaluation for this function call even when it's inlined:
        next_pixel((cnt as ubyte) + 30)


        test_stack.test()

    }


    inline asmsub next_pixel(ubyte color @A) {
        %asm {{
            nop
        }}
    }


}
