%import math
%import monogfx

main {
    sub start() {
        byte @shared cos_a = math.cos8(angle)
        byte @shared sin_a = math.sin8(angle)

        monogfx.lores()
        ubyte angle
        for angle in 0 to 255 step 10 {
            monogfx.text(math.sin8u(angle), math.cos8u(angle)/2,true, iso:"Hello!")
        }

        repeat {
        }
    }
}
