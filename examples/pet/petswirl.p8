%import math
%import petgfx

; Note: this program can be compiled for multiple target systems.

main {

    sub start()  {
        uword anglex, angley
        ubyte x,y

        repeat {
            x = (math.sin8u(msb(anglex)) * 256 / 820) as ubyte      ; range 0-79
            y = (math.cos8u(msb(angley)) * 256 / 1310) as ubyte     ; range 0-49
            petgfx.plot(x,y)
            anglex+=375
            angley+=291
        }
    }
}
