%import textio
%import floats
%option no_sysinit

main {
    sub start() {
        boing64()
    }

    sub boing64() {
        txt.color(4)
        txt.cls()

        c64.EXTCOL = 12
        c64.BGCOL0 = 12

        const ubyte size = 24
        const ubyte center = size / 2
        const uword radius = size / 2 - 1
        const float radius_squared = radius*radius as float

        ubyte y, x

        ; --- draw the purple grid ---
        for y in 0 to 24 step 4 {
            for x in 0 to 39 {
                txt.setchr(x, y, 160)
            }
        }
        for x in 0 to 39 step 4 {
            for y in 0 to 24 {
                txt.setchr(x, y, 160)
            }
        }
        for y in 0 to 24
            txt.setcc(39, y, 160, 4)

        const ubyte lon_divs = 14
        const ubyte lat_divs = 7
        const float tilt_rads = -0.3

        const float cos_a = floats.cos(tilt_rads)
        const float sin_a = floats.sin(tilt_rads)

        const float half_π = floats.π / 2.0


        for y in 0 to size-1 {
            for x in 0 to size-1 {
                byte dx = (x - center) as byte
                byte dy = (y - center) as byte
                float rx = dx * cos_a - dy * sin_a
                float ry = dx * sin_a + dy * cos_a
                float dist_sq = rx*rx + ry*ry

                if dist_sq <= radius_squared {
                    float rz = sqrt(radius_squared - dist_sq)
                    float a = ry/radius
                    float phi = floats.atan(a / sqrt(1 - a * a))   ;  asin(ry/radius)
                    float theta = floats.atan2(rx, rz)

                    ubyte lon_idx = ((theta + floats.π) / floats.TWOPI * lon_divs) as ubyte
                    ubyte lat_idx = ((phi + half_π) / floats.π * lat_divs + 0.5) as ubyte

                    ; ubyte color = if (lon_idx + lat_idx) % 2 == 0 then 1 else 2
                    txt.setcc(x + 8, y, 160, 1 + ((lon_idx + lat_idx) & 1))
                }
            }
        }

        txt.color(0)
        txt.plot(1,1)
        txt.print("boing!")

        repeat { }
    }
}
