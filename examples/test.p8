%import textio
%import diskio
;%import floats
;%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    sub start () {
        ubyte qq
        void c64.CHKIN(3)
        qq++
        qq=c64.CHKIN(3)
        qq=c64.OPEN()           ; TODO DO NOT REMOVE SECOND ASSIGNMENT IF ITS NOT A SIMPLE VALUE
        test_stack.test()
    }
}
