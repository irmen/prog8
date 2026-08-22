; Draw filled discs directly into a 5-plane Amiga bitmap.

%import graphics
%import intuition
%import textio
%import utility

main {
    const uword SCREEN_WIDTH = 320
    const uword SCREEN_HEIGHT = 256
    const ubyte SCREEN_DEPTH = 5
    const ubyte DISC_RADIUS = 80
    const uword BYTES_PER_ROW = (SCREEN_WIDTH + 15) / 16 * 2

    uword[32] palette = [
        $000, $f00, $0f0, $00f, $ff0, $f0f, $0ff, $fff,
        $800, $080, $008, $880, $808, $088, $888, $ccc,
        $400, $040, $004, $440, $404, $044, $444, $aaa,
        $200, $020, $002, $220, $202, $022, $222, $666
    ]
    ubyte[8] leftMasks = [$ff, $7f, $3f, $1f, $0f, $07, $03, $01]
    ubyte[8] rightMasks = [$80, $c0, $e0, $f0, $f8, $fc, $fe, $ff]
    ubyte[5] planeMasks = [1, 2, 4, 8, 16]

    sub start() {
        long[] screentags = [
            intuition.SA_Left,      0,
            intuition.SA_Top,       0,
            intuition.SA_Width,     SCREEN_WIDTH,
            intuition.SA_Height,    SCREEN_HEIGHT,
            intuition.SA_Depth,     SCREEN_DEPTH,
            intuition.SA_DisplayID, graphics.DEFAULT_MONITOR_ID | graphics.LORES_KEY,
            intuition.SA_Type,      intuition.CUSTOMSCREEN,
            intuition.SA_ShowTitle, false,
            utility.TAG_DONE,       0
        ]

        long[] windowtags = [
            intuition.WA_CustomScreen, 0,
            intuition.WA_Left,         0,
            intuition.WA_Top,          0,
            intuition.WA_Width,        SCREEN_WIDTH,
            intuition.WA_Height,       SCREEN_HEIGHT,
            intuition.WA_Backdrop,     true,
            intuition.WA_Borderless,   true,
            intuition.WA_Activate,     true,
            intuition.WA_IDCMP,        intuition.IDCMP_MOUSEBUTTONS | intuition.IDCMP_RAWKEY,
            utility.TAG_DONE,          0
        ]

        ^^intuition.Screen screen = intuition.OpenScreenTagList(0, screentags)
        if screen!=0 {
            graphics.LoadRGB4(intuition.GetScreenViewPort(screen), palette, 32)
            windowtags[1] = screen
            ^^intuition.Window window = intuition.OpenWindowTagList(0, windowtags)
            if window!=0 {
                drawDiscs(intuition.GetScreenBitMap(screen))
                sys.wait(300)
                intuition.CloseWindow(window)
            }
            void intuition.CloseScreen(screen)
        }
    }

    sub drawDiscs(^^graphics.BitMap bitmap) {
        for index in 0 to 99 {
            uword x = 80 + (index % 10) * 159 / 9
            uword y = 80 + (index / 10) * 95 / 9
            drawDisc(bitmap, x, y, DISC_RADIUS, (index & 31) as ubyte)
        }
    }

    sub drawDisc(^^graphics.BitMap bitmap, uword xcenter, uword ycenter, ubyte radius, ubyte color) {
        ubyte yy = 0
        word decisionOver2 = (1 as word)-radius
        ubyte pendingRadius
        ubyte pendingWidth
        bool hasPending = false

        while radius>=yy {
            drawSpan(bitmap, xcenter-radius, ycenter+yy, radius*$0002+1, color)
            drawSpan(bitmap, xcenter-radius, ycenter-yy, radius*$0002+1, color)

            if hasPending and pendingRadius != radius {
                if pendingRadius != pendingWidth {
                    drawSpan(bitmap, xcenter-pendingWidth, ycenter+pendingRadius, pendingWidth*$0002+1, color)
                    drawSpan(bitmap, xcenter-pendingWidth, ycenter-pendingRadius, pendingWidth*$0002+1, color)
                }
                hasPending = false
            }
            if not hasPending {
                pendingRadius = radius
                hasPending = true
            }
            pendingWidth = yy

            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }

        if hasPending and pendingRadius != pendingWidth {
            drawSpan(bitmap, xcenter-pendingWidth, ycenter+pendingRadius, pendingWidth*$0002+1, color)
            drawSpan(bitmap, xcenter-pendingWidth, ycenter-pendingRadius, pendingWidth*$0002+1, color)
        }
    }

    sub drawSpan(^^graphics.BitMap bitmap, uword left, uword y, uword length, ubyte color) {
        uword firstByte = left >> 3
        uword lastPixel = left + length - 1
        uword lastByte = lastPixel >> 3
        uword byteOffset = y*BYTES_PER_ROW + firstByte
        for plane in 0 to SCREEN_DEPTH-1 {
            pointer address = bitmap.Planes[plane] + byteOffset
            bool setPlane = (color & planeMasks[plane]) != 0
            if firstByte == lastByte {
                ubyte mask = leftMasks[lsb(left) & 7] & rightMasks[lsb(lastPixel) & 7]
                if setPlane
                    @(address) |= mask
                else
                    @(address) &= ~mask
            } else {
                if setPlane
                    @(address) |= leftMasks[lsb(left) & 7]
                else
                    @(address) &= ~leftMasks[lsb(left) & 7]
                address++
                uword byteIndex = firstByte + 1
                while byteIndex < lastByte {
                    if setPlane
                        @(address) = $ff
                    else
                        @(address) = 0
                    address++
                    byteIndex++
                }
                if setPlane
                    @(address) |= rightMasks[lsb(lastPixel) & 7]
                else
                    @(address) &= ~rightMasks[lsb(lastPixel) & 7]
            }
        }
    }
}
