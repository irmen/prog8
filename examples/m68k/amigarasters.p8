%import custom
%import textio

main {
    sub start() {
        custom.grab_system()

        ubyte offset = 0
        ubyte green_offset = 0
        ubyte blue_offset = 0
        ubyte frame = 0
        while not custom.left_button() {
            ; wait until the vertical beam reaches raster line 80
            while msb(custom.VHPOSR) != 80 {
                ; busy wait
            }

            ; draw raster bars from line 80 downwards, cycling colors
            ubyte line = 40
            ubyte cg = green_offset          ; green bar phase (moves at half speed)
            ubyte cr = (32 - offset) & 31    ; red bar phase   (start shifts down each frame)
            ubyte cb = blue_offset           ; blue bar phase  (moves down at 1/4 speed)
            while line < 250 {
                ; wait until the beam has reached (or passed) this line;
                ; using >= avoids stalling a whole frame when a line is overshot
                while msb(custom.VHPOSR) < line {
                    ; busy wait
                }

                ; build a triangle-wave gradient for each color bar
                ubyte green = cg
                if green >= 16
                    green = 31 - green        ; ramp up then down for a smooth bar
                ubyte red = cr
                if red >= 16
                    red = 31 - red
                ubyte blue = cb
                if blue >= 16
                    blue = 31 - blue

                ; or the red, green and blue bars together into one $0RGB value
                custom.COLOR0 = mkword(red, (green << 4) | blue)

                line += 4               ; amiga 500 is too slow for <4 at this time
                cg = (cg + 1) & 31
                cr = (cr + 1) & 31
                cb = (cb + 1) & 31
            }

            custom.COLOR0 = 0
            offset = (offset + 1) & 31        ; scroll the red bars each frame
            frame++
            if frame & 1 == 0
                green_offset = (green_offset + 1) & 31   ; green advances every other frame
            if frame & 3 == 0
                blue_offset = (blue_offset + 1) & 31     ; blue advances every 4th frame
        }

        custom.return_system()
        txt.print("Thank you for watching this amazing Prog8 raster bars demo.\n")
    }
}
