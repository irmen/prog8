%import graphics

main {
    sub start() {
        graphics.enable_bitmap_mode()

        uword radius
        for radius in 1 to 10 {
            graphics.circle(30*radius, 100, lsb(radius))
            graphics.disc(30*radius, 130, lsb(radius))
            mydisc(30*radius, 160, lsb(radius))
        }

        repeat {}
    }

    sub mydisc(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        ; Midpoint algorithm.
        if radius==0
            return
        ubyte @zp ploty
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius

        while radius>=yy {
            graphics.horizontal_line(xcenter-radius, ycenter+yy, radius*2+1)
            graphics.horizontal_line(xcenter-radius, ycenter-yy, radius*2+1)
            graphics.horizontal_line(xcenter-yy, ycenter+radius, yy*2+1)
            graphics.horizontal_line(xcenter-yy, ycenter-radius, yy*2+1)

            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
    }

}
