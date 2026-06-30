%import graphics
%import math
%zeropage basicsafe
%option no_sysinit

main {
    uword terrain = memory("terrain", 320, 0)

    sub start() {
        graphics.enable_bitmap_mode()

        ; sky
        graphics.clear_screen(7,6)

        ; sun and clouds
        graphics.disc(240, 50, 34)
        graphics.colors(15,1)
        graphics.filled_oval(60, 50, 40, 16)
        graphics.colors(14,14)
        graphics.filled_oval(100, 60, 55, 22)
        graphics.colors(1,1)
        graphics.filled_oval(140, 40, 50, 20)
        graphics.filled_oval(180, 55, 60, 12)

        ; cliffs
        start_terrain((math.rnd() % 100) + 50, (math.rnd() % 100) + 50)
        ubyte smoothness = 8
        ubyte extremes = 56
        recursive_midpoint(0, 319, extremes)
        draw(15)

        ; mountains
        start_terrain((math.rnd() % 60) + 100, (math.rnd() % 60) + 100)
        smoothness = 6
        extremes = 50
        recursive_midpoint(0, 319, extremes)
        draw(12)

        ; woods
        start_terrain((math.rnd() % 40) + 160, (math.rnd() % 40) + 160)
        smoothness = 5
        extremes = 35
        recursive_midpoint(0, 319, extremes)
        draw(9)

        ; grasslands
        start_terrain((math.rnd() % 30) + 200, (math.rnd() % 30) + 200)
        smoothness = 4
        extremes = 21
        recursive_midpoint(0, 319, extremes)
        draw(5)
        ; end.

        sub start_terrain(ubyte startheight, ubyte endheight) {
            terrain[0] = startheight
            terrain[319] = endheight
            interpolate(0, 319)
        }

        sub recursive_midpoint(uword s, uword e, ubyte displacement) {
            if displacement==0 or displacement>extremes
                return
            uword @zp half = s + (e-s) / 2
            if half!=s and half!=e {
                ; displace the terrain
                uword t = terrain[half]
                ubyte d = math.rnd() % displacement
                if math.rnd() & 1 == 1
                    t += d
                else
                    t -= d
                terrain[half] = clamp(lsb(t), 10, 230)
                interpolate(s, half)
                interpolate(half, e)

                ; recurse
                pushw(e)
                pushw(half)
                push(displacement)
                recursive_midpoint(s, half, displacement-smoothness)
                displacement = pop()
                half = popw()
                e = popw()
                recursive_midpoint(half, e, displacement-smoothness)
            }
        }

    }

    sub interpolate(uword start, uword end) {
        ; linear interpolate the terrain heights between positions start and end.
        word ts = terrain[start]
        word te = terrain[end]
        alias istep = cx16.r0s
        alias ivalue = cx16.r1s
        istep = (te-ts) * 128 / (end-start)
        ivalue = ts * 128

        while start<=end {
            terrain[start] = msb(ivalue<<1)
            start++
            ivalue += istep
        }
    }

    sub draw(ubyte color) {
        ; draw the terrain in the given color.
        graphics.colors(color, color)
        for cx16.r0 in 0 to 319 {
            graphics.line(cx16.r0, terrain[cx16.r0], cx16.r0, 239)
        }
    }
}
