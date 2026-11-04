; Draws 256 horizontal lines (line Y gets color Y) and then 256 vertical lines
; (line X gets color X) on a 256-color lores screen, using the blitter's
; hardware line mode, and prints how long the drawing took.
;
; AGA only: an 8-bitplane screen needs the amiga1200 target (an A500 cannot do it).
; The system clock ticks at 50 Hz, so the printed time is a multiple of 20 ms.

%import graphics
%import intuition
%import utility
%import blitter
%import timer
%import textio

main {
    const uword SCREEN_WIDTH = 320
    const uword SCREEN_HEIGHT = 256
    const ubyte SCREEN_DEPTH = 8

    long[256] palette
    long @shared screen_error

    long[] @shared screentags = [
        intuition.SA_Left,      0,
        intuition.SA_Top,       0,
        intuition.SA_Width,     SCREEN_WIDTH,
        intuition.SA_Height,    SCREEN_HEIGHT,
        intuition.SA_Depth,     SCREEN_DEPTH,
        intuition.SA_DisplayID, graphics.PAL_MONITOR_ID | graphics.LORES_KEY,  ;  PAL lores, not the default monitor
        intuition.SA_Type,      intuition.CUSTOMSCREEN,
        intuition.SA_ShowTitle, false,
        intuition.SA_ErrorCode, &screen_error,
        utility.TAG_DONE,       0
    ]

    sub start() {
        ; black to full red gradient: pen 0 = black, pen 255 = full red ($FF0000)
        for i in 0 to 255 {
            ubyte r = i as ubyte
            palette[i] = (r as long) << 16
        }

        ^^intuition.Screen screen = intuition.OpenScreenTagList(0, screentags)
        if screen == 0 {
            txt.print("OpenScreenTagList failed, SA_ErrorCode=")
            txt.print_l(screen_error)
            if screen_error == intuition.OSERR_NOMONITOR
                txt.print(" (PAL monitor mode unavailable)")
            if screen_error == intuition.OSERR_NOCHIPS
                txt.print(" (display mode requires newer chipset)")
            if screen_error == intuition.OSERR_NOMEM
                txt.print(" (not enough memory)")
            if screen_error == intuition.OSERR_NOCHIPMEM
                txt.print(" (not enough chip memory for the screen)")
            if screen_error == intuition.OSERR_UNKNOWNMODE
                txt.print(" (unknown display mode)")
            if screen_error == intuition.OSERR_TOODEEP
                txt.print(" (screen depth unsupported by display hardware)")
            txt.print("\n")
            return
        }
        defer void intuition.CloseScreen(screen)
        graphics.load_palette(intuition.GetScreenViewPort(screen), &palette, 256)

        if not timer.opendevice() {
            txt.print("cannot open timer.device\n")
            return
        }
        defer timer.closedevice()

        ^^graphics.BitMap bitmap = intuition.GetScreenBitMap(screen)
        uword pitch = bitmap.BytesPerRow
        for plane in 0 to SCREEN_DEPTH-1
            blitter.clear_plane(bitmap.Planes[plane], pitch/2, SCREEN_HEIGHT)
        blitter.wait()

        long start_s, start_us = timer.getsystime()
        draw_lines(bitmap, pitch)
        long end_s, end_us = timer.getsystime()

        long secs = end_s - start_s
        long micros = end_us - start_us
        if micros < 0 {
            micros += 1000000
            secs -= 1
        }
        txt.print("512 lines (256 horizontal + 256 vertical) took ")
        txt.print_l(secs * 1000 + micros / 1000)
        txt.print(" ms\n")

        sys.wait(300)
    }

    sub draw_lines(^^graphics.BitMap bitmap, uword pitch) {
        ; mask, single pixel, solid line pattern, plane pitch
        blitter.line_init($ffff, $8000, $ffff, pitch)
        draw_horizontals(bitmap)
        draw_verticals(bitmap)
        blitter.wait()      ; the last line is still being drawn
    }

    sub draw_horizontals(^^graphics.BitMap bitmap) {
        ; horizontal: y = color, spanning the full width
        for y in 0 to SCREEN_HEIGHT-1
            blitter.line_color(0, y, SCREEN_WIDTH-1, y, y as ubyte, bitmap.Depth, &bitmap.Planes as ^^pointer)
    }

    sub draw_verticals(^^graphics.BitMap bitmap) {
        ; vertical: x = color, spanning the full height
        for x in 0 to 255
            blitter.line_color(x, 0, x, SCREEN_HEIGHT-1, x as ubyte, bitmap.Depth, &bitmap.Planes as ^^pointer)
    }
}
