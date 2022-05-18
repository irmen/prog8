%import math

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        byte xx
        byte yy
        ubyte ubx

        xx = xx
        xx = xx*9
        xx = yy*9
        xx = xx+3*yy
        xx = xx/yy
        xx = -xx
        @($4000) = @($4000)
        @($4000) = @($4000) + 2
        xx = xx | yy
        xx = xx & yy
        xx = xx ^ yy
        xx = (not xx) as byte
        xx = (~xx) as byte
        xx++

        ubx = not ubx
        ubx = ~ubx
    }
}
