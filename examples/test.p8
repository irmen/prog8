%import math
%import monogfx

main {
    sub start() {
        monogfx.lores()
        monogfx.drawmode(monogfx.MODE_INVERT)

        ubyte tt

        repeat {
            ubyte tts=tt
            word x1 = math.sin8(tts) / 2
            byte y1 = math.cos8(tts) / 2
            tts += 256/3
            word x2 = math.sin8(tts) / 2
            byte y2 = math.cos8(tts) / 2
            tts += 256/3
            word x3 = math.sin8(tts) / 2
            byte y3 = math.cos8(tts) / 2
            monogfx.line(160+x1 as uword, 120+y1 as ubyte, 160+x2 as uword, 120+y2 as ubyte, true)
            monogfx.line(160+x2 as uword, 120+y2 as ubyte, 160+x3 as uword, 120+y3 as ubyte, true)
            monogfx.line(160+x3 as uword, 120+y3 as ubyte, 160+x1 as uword, 120+y1 as ubyte, true)
            sys.waitvsync()
            tt++
        }
    }
}
