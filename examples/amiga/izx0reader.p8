%import dos
%import exec
%import graphics
%import intuition
%import textio
%import utility
%import compression
%import textio

; load and display a zx0-compressed image.
; you can create those with the special converter script that comes with prog8

main {
    sub start() {

        str imagefile = "?" * 60
        txt.print("name of izx0 file to load: ")
        void txt.input_chars(imagefile)

        ; open a 320x256, 5-plane screen
        long[] screentags = [
            intuition.SA_Left,      0,
            intuition.SA_Top,       0,
            intuition.SA_Width,     320,
            intuition.SA_Height,    256,
            intuition.SA_Depth,     5,  ;  5 bitplanes=32 colors
            intuition.SA_DisplayID, graphics.DEFAULT_MONITOR_ID | graphics.LORES_KEY,
            intuition.SA_Type,      intuition.CUSTOMSCREEN,
            intuition.SA_ShowTitle, false,
            utility.TAG_DONE,     0
        ]

        long[] windowtags = [
            intuition.WA_CustomScreen, 0,       ; set later
            intuition.WA_Left,         0 ,
            intuition.WA_Top,          0 ,
            intuition.WA_Width,        320 ,
            intuition.WA_Height,       256 ,
            intuition.WA_Backdrop,     true,
            intuition.WA_Borderless,   true,
            intuition.WA_Activate,     true,
            intuition.WA_IDCMP,        intuition.IDCMP_MOUSEBUTTONS | intuition.IDCMP_RAWKEY,
            utility.TAG_DONE,        0
        ]

        ; open custom screen
        ^^intuition.Screen myScreen = intuition.OpenScreenTagList(0, screentags)
        if myScreen!=0 {
            izx0.clearPalette()
            graphics.LoadRGB4(intuition.GetScreenViewPort(myScreen), izx0.palette, 32)

            ; now open a window to be able to set mouse cursor, handle mouse clicks, keyboard presses, etc.
            windowtags[1] = myScreen
            ^^intuition.Window myWindow = intuition.OpenWindowTagList(0, windowtags)

            if myWindow!=0 {

                ^^graphics.BitMap bm = intuition.GetScreenBitMap(myScreen)
                if izx0.read(imagefile, &bm.Planes) {
                    graphics.LoadRGB4(intuition.GetScreenViewPort(myScreen), izx0.palette, izx0.numColors as word)
                    sys.wait(200)
                }

                intuition.CloseWindow(myWindow)
            }
            void intuition.CloseScreen(myScreen)
        }
    }
}


izx0 {
    ; IZX0 header, palette, then compressed planes.
    const HEADER_SIZE = 52
    const TOTAL_COMPRESSED_SIZE_OFFSET = 48
    const MAX_PALETTE_SIZE = 256 * 4

    uword imageWidth
    uword imageHeight
    uword numPlanes
    ubyte isAga
    uword numColors

    pointer header = memory("izx0_header", HEADER_SIZE, 0)
    pointer palette = memory("izx0_palette", MAX_PALETTE_SIZE, 0)

    sub clearPalette() {
        sys.memset(palette, MAX_PALETTE_SIZE, 0)
    }

    sub read(str filename, ^^long bitplanepointers) -> bool {
        bool success = false
        pointer file = dos.Open(filename, dos.MODE_OLDFILE)
        if file != 0 {
            long bytesRead = dos.Read(file, header, HEADER_SIZE)
            if bytesRead == HEADER_SIZE {
                if peekl(header) == $495A5830 {         ; 'IZX0'
                    imageWidth = peekw(header + 4)
                    imageHeight = peekw(header + 6)
                    numPlanes = peekw(header + 8)
                    isAga = @(header + 10)
                    numColors = peekw(header + 12)
                    uword paletteSize = peekw(header + 14)

                    ; palette follows the header
                    clearPalette()
                    bytesRead = dos.Read(file, palette, paletteSize)
                    if bytesRead == paletteSize {
                        ; buffer all compressed planes before decoding
                        long compressedSize = peekl(header + TOTAL_COMPRESSED_SIZE_OFFSET)
                        if compressedSize > 0 and bitplanepointers!=0 {
                            pointer compressedBuffer = exec.AllocVec(compressedSize, exec.MEMF_PUBLIC)
                            if compressedBuffer != 0 {
                                bytesRead = dos.Read(file, compressedBuffer, compressedSize)
                                if bytesRead == compressedSize {
                                    pointer compressedPlane = compressedBuffer
                                    for plane in 0 to numPlanes - 1 {
                                        long streamSize = peekl(header + 16 + plane * 4)
                                        long bitplane = peekp(bitplanepointers)
                                        ; decode one plane into CHIP RAM
                                        if bitplane!=0
                                            compression.unZX0(compressedPlane, bitplane)
                                        compressedPlane += streamSize
                                        bitplanepointers++
                                    }
                                    success = true
                                }
                                exec.FreeVec(compressedBuffer)
                            }
                        }
                    }
                }
            }
            void dos.Close(file)
        }
        return success
    }

}
