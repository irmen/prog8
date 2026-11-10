%import dos
%import exec
%import graphics
%import intuition
%import textio
%import utility
%import compression

; load and display a zx0-compressed image.
; you can create those with the special converter script that comes with prog8

main {
    ; screen tags; size and depth are set from the image before opening
    long[] @shared screentags = [
        intuition.SA_Left,      0,
        intuition.SA_Top,       0,
        intuition.SA_Width,     320,
        intuition.SA_Height,    256,
        intuition.SA_Depth,     5,
        intuition.SA_DisplayID, graphics.DEFAULT_MONITOR_ID | graphics.LORES_KEY,
        intuition.SA_Type,      intuition.CUSTOMSCREEN,
        intuition.SA_ShowTitle, false,
        utility.TAG_DONE,     0
    ]

    sub start() {

        str @shared imagefile = "?" * 60
        txt.print("name of izx0 file to load: ")
        void txt.input_chars(imagefile)

        if not izx0.readHeader(imagefile) {
            txt.print("error: could not read izx0 image header\n")
            return
        }
        if izx0.numPlanes==0 or izx0.numPlanes>8 {
            txt.print("error: unsupported plane count (1-8)\n")
            return
        }
        if izx0.imageWidth==0 or izx0.imageHeight==0 {
            txt.print("error: image has zero size\n")
            return
        }

        ; fixed 320x256 screen; the decoder clips the image to it so
        ; nothing is ever written outside the screen edges.
        screentags[9] = izx0.numPlanes as long
        long[] @shared windowtags = [
            intuition.WA_CustomScreen, 0,       ; set later
            intuition.WA_Left,         0 ,
            intuition.WA_Top,          0 ,
            intuition.WA_Width,        320 ,
            intuition.WA_Height,       256 ,
            intuition.WA_Backdrop,     true,
            intuition.WA_Borderless,   true,
            intuition.WA_Activate,     true,
            intuition.WA_RMBTrap,      true,
            intuition.WA_IDCMP,        intuition.IDCMP_MOUSEBUTTONS | intuition.IDCMP_RAWKEY,
            utility.TAG_DONE,        0
        ]

        ; open custom screen
        ^^intuition.Screen myScreen = intuition.OpenScreenTagList(0, screentags)
        if myScreen!=0 {
            izx0.clearPalette()
            ^^graphics.ViewPort vp = intuition.GetScreenViewPort(myScreen)
            ; AGA palettes are stored packed and need LoadRGB32 (via load_palette);
            ; ECS palettes are $0RGB words for LoadRGB4.
            graphics.LoadRGB4(vp, izx0.palette, 32)

            ; now open a window to be able to set mouse cursor, handle mouse clicks, keyboard presses, etc.
            windowtags[1] = myScreen
            ^^intuition.Window myWindow = intuition.OpenWindowTagList(0, windowtags)

            if myWindow!=0 {

                ^^graphics.BitMap bm = intuition.GetScreenBitMap(myScreen)
                if izx0.readPlanes(imagefile, &bm.Planes) {
                    if izx0.isAga != 0
                        graphics.load_palette(vp, izx0.palette as ^^long, izx0.numColors)
                    else
                        graphics.LoadRGB4(vp, izx0.palette, izx0.numColors as word)
                    sys.wait(200)
                } else {
                    txt.print("error: could not load izx0 image\n")
                }

                intuition.CloseWindow(myWindow)
            }
            void intuition.CloseScreen(myScreen)
        }
    }
}


izx0 {
    ; IZX0 header, palette, then compressed planes.
    const HEADER_SIZE = 60
    const TOTAL_COMPRESSED_SIZE_OFFSET = 20
    const MAX_PALETTE_SIZE = 256 * 4

    uword imageWidth
    uword imageHeight
    word originX
    word originY
    uword numPlanes          ; color planes (mask, if any, is a separate stream)
    ubyte isAga
    ubyte hasMask            ; plane_sizes[8] != 0: mask stream follows the color planes
    uword numColors          ; palette entries for the color planes (2^numPlanes, or 0)

    pointer header = memory("izx0_header", HEADER_SIZE, 0)
    pointer palette = memory("izx0_palette", MAX_PALETTE_SIZE, 0)

    sub clearPalette() {
        sys.memset(palette, MAX_PALETTE_SIZE, 0)
    }

    sub readHeader(str filename) -> bool {
        ; parse just the 60-byte header into the module variables above
        bool success = false
        pointer file = dos.Open(filename, dos.MODE_OLDFILE)
        if file != 0 {
            if dos.Read(file, header, HEADER_SIZE) == HEADER_SIZE {
                if peekl(header) == $495A5830 {         ; 'IZX0'
                    imageWidth = peekw(header + 4)
                    imageHeight = peekw(header + 6)
                    originX = peekw(header + 8) as word
                    originY = peekw(header + 10) as word
                    numPlanes = peekw(header + 12)
                    isAga = @(header + 14)
                    numColors = peekw(header + 16)
                    if peekl(header + 24 + 32) != 0
                        hasMask = 1
                    else
                        hasMask = 0
                    success = true
                }
            }
            void dos.Close(file)
        }
        return success
    }

    sub readPlanes(str filename, ^^long bitplanepointers) -> bool {
        ; Decode into the given screen planes, clipping the image to the
        ; 320x256 screen so nothing is ever written outside the screen edges.
        ; bitplanepointers must have room for numPlanes screen planes
        ; (320/8 = 40 bytes per row).
        const uword SCREEN_WIDTH = 320
        const uword SCREEN_HEIGHT = 256
        const uword SCREEN_STRIDE = 40
        bool success = false
        pointer file = dos.Open(filename, dos.MODE_OLDFILE)
        if file != 0 {
            long bytesRead = dos.Read(file, header, HEADER_SIZE)
            if bytesRead == HEADER_SIZE {
                if peekl(header) == $495A5830 {         ; 'IZX0'
                    imageWidth = peekw(header + 4)
                    imageHeight = peekw(header + 6)
                    originX = peekw(header + 8) as word
                    originY = peekw(header + 10) as word
                    numPlanes = peekw(header + 12)
                    isAga = @(header + 14)
                    numColors = peekw(header + 16)
                    uword paletteSize = peekw(header + 18)

                    if paletteSize>MAX_PALETTE_SIZE
                        return false
                    if numPlanes==0 or numPlanes>8
                        return false
                    if imageWidth==0 or imageHeight==0
                        return false

                    ; clip to the screen edges
                    uword srcStride = (imageWidth + 15) / 16 * 2
                    uword copyRows = imageHeight
                    if copyRows > SCREEN_HEIGHT
                        copyRows = SCREEN_HEIGHT
                    uword copyBytes = srcStride
                    if copyBytes > SCREEN_STRIDE
                        copyBytes = SCREEN_STRIDE

                    ; palette follows the header
                    clearPalette()
                    bytesRead = dos.Read(file, palette, paletteSize)
                    if bytesRead == paletteSize {
                        ; buffer all compressed planes before decoding
                        long compressedSize = peekl(header + TOTAL_COMPRESSED_SIZE_OFFSET)
                        if compressedSize > 0 and bitplanepointers!=0 {
                            pointer compressedBuffer = exec.AllocVec(compressedSize, exec.MEMF_PUBLIC)
                            pointer tempPlane = exec.AllocVec(srcStride as long * imageHeight as long, exec.MEMF_PUBLIC)
                            if compressedBuffer != 0 and tempPlane != 0 {
                                bytesRead = dos.Read(file, compressedBuffer, compressedSize)
                                if bytesRead == compressedSize {
                                    pointer compressedPlane = compressedBuffer
                                    for plane in 0 to numPlanes - 1 {
                                        long streamSize = peekl(header + 24 + plane * 4)
                                        long bitplane = peekp(bitplanepointers)
                                        ; decode one plane into the temp buffer,
                                        ; then copy the visible rows clipped to 40 bytes
                                        if bitplane!=0 {
                                            compression.unZX0(compressedPlane, tempPlane)
                                            pointer src = tempPlane
                                            long dest = bitplane
                                            for row in 0 to copyRows-1 {
                                                sys.memcopy(src as long, dest, copyBytes as long)
                                                src += srcStride
                                                dest += SCREEN_STRIDE
                                            }
                                        }
                                        compressedPlane += streamSize
                                        bitplanepointers++
                                    }
                                    ; a mask stream after the color planes is not
                                    ; image data: skip it here (use it as the
                                    ; blitter cookie-cut mask instead).
                                    success = true
                                }
                                exec.FreeVec(compressedBuffer)
                                exec.FreeVec(tempPlane)
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
