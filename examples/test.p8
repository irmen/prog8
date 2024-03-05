%import math
%import monogfx

main {
    sub start() {
        monogfx.hires()
        monogfx.drawmode(monogfx.MODE_INVERT)

        ubyte tt

        repeat {
            ubyte tts=tt
            word x1 = math.sin8(tts)
            word y1 = math.cos8(tts)
            tts += 256/3
            word x2 = math.sin8(tts)
            word y2 = math.cos8(tts)
            tts += 256/3
            word x3 = math.sin8(tts)
            word y3 = math.cos8(tts)
            monogfx.line(320+x1*2 as uword, 240+y1 as uword, 320+x2 as uword, 240+y2 as uword, true)
            monogfx.line(320+x2*2 as uword, 240+y2 as uword, 320+x3 as uword, 240+y3 as uword, true)
            monogfx.line(320+x3*2 as uword, 240+y3 as uword, 320+x1 as uword, 240+y1 as uword, true)
            tt++
        }
    }
}
