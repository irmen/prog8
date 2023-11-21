%import palette
%import textio
%import syslib
%zeropage basicsafe

main {
    sub start() {
        sys.set_rasterirq(&handler, 200)
        txt.print("installed\n")
    }

    sub handler() -> bool {
        palette.set_color(0, $f00)
        repeat 1000 {
            cx16.r0++
        }
        palette.set_color(0, $000)

        return true
    }
}
