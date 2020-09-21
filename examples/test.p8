%import syslib
; %import graphics
%import textio
%zeropage basicsafe


main {

    sub start()  {

        ; 40 x 25
        ; 80 x 60
        txt.fill_screen('*', 8)

        txt.clear_screen()

        txt.print_ub(txt.width())
        txt.chrout('\n')
        txt.print_ub(txt.height())
        txt.chrout('\n')

        ubyte i
        repeat {
            txt.clear_screencolors(i)
            i++
        }
    }
}
