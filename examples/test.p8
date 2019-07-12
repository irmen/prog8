%import c64utils
%zeropage basicsafe

~ main {

    Color blocklevelcolor

    sub start() {

        uword derp =44
        ubyte[] v = [22,33,44]

        Color foreground    = [1,2,3]
        c64scr.print_ub(foreground.red)
        c64.CHROUT(':')
        c64scr.print_ub(foreground.green)
        c64.CHROUT(':')
        c64scr.print_ub(foreground.blue)
        c64.CHROUT('\n')


        Color background
        Color cursor = [255,255,255]

        foreground.red=99
        background.blue=foreground.red

        cursor = [1,2,3]    ; assign all members at once
        cursor = v
        cursor = foreground   ; @todo memberwise assignment

        c64scr.print_ub(foreground.red)
        c64.CHROUT(':')
        c64scr.print_ub(foreground.green)
        c64.CHROUT(':')
        c64scr.print_ub(foreground.blue)
        c64.CHROUT('\n')
        c64scr.print_ub(background.red)
        c64.CHROUT(':')
        c64scr.print_ub(background.green)
        c64.CHROUT(':')
        c64scr.print_ub(background.blue)
        c64.CHROUT('\n')
        c64scr.print_ub(cursor.red)
        c64.CHROUT(':')
        c64scr.print_ub(cursor.green)
        c64.CHROUT(':')
        c64scr.print_ub(cursor.blue)
        c64.CHROUT('\n')

        foo()
        foo()
        foo()
        foo()
        foo()
        foo()
        foo()


        return
    }

    sub foo() {
        Color localcolor
        localcolor.red++
        c64scr.print_ub(localcolor.red)
        c64.CHROUT(':')
        c64scr.print_ub(localcolor.green)
        c64.CHROUT(':')
        c64scr.print_ub(localcolor.blue)
        c64.CHROUT('\n')
    }

    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

    ; @todo structs as sub args. After strings and arrays as sub-args.
;    sub foo(Color arg) -> ubyte {
;        return arg.red+arg.green+arg.blue
;    }

}
