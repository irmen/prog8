%option no_sysinit
%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {
        str filename = "doesnotexist.xyz"
        ubyte status


        txt.print("read open...\n")
        if diskio.f_open(filename) {
            status = cbm.READST()
            txt.print("open ok - wrong! status=")
        } else {
            status = cbm.READST()
            txt.print("open failed - good! status=")
        }
        txt.print_ub(status)
        txt.nl()
        txt.print(diskio.status())
        txt.nl()

        txt.print("\nwriting the file\n")
        if diskio.f_open_w(filename) {
            cx16.r0 = diskio.status()
            if @(cx16.r0)=='0' {
                diskio.f_write("test", 4)
                diskio.f_close_w()
                status = cbm.READST()
                txt.print("write ok! good! status=\n")
            } else {
                txt.print("write open error, status=")
                txt.print(cx16.r0)
                txt.nl()
            }
        } else {
            status = cbm.READST()
            txt.print("write open failed! wrong! status=\n")
        }
        txt.print_ub(status)
        txt.nl()
        txt.print(diskio.status())
        txt.nl()

        txt.print("\nread open...\n")
        if diskio.f_open(filename) {
            status = cbm.READST()
            txt.print("open ok - good! status=")
        } else {
            status = cbm.READST()
            txt.print("open failed - wrong! status=")
        }
        txt.print_ub(status)
        txt.nl()
        txt.print(diskio.status())
        txt.nl()
    }
}

main33 {
    sub print_buffer() {
        uword ptr = $4000
        uword firstAA = ptr
        repeat 320*6 {
            txt.print_ubhex(@(ptr), false)
            if @(ptr)==$aa {
                firstAA = ptr
                break
            }
            ptr++
        }
        txt.nl()
        txt.print("first $aa is at ")
        txt.print_uwhex(firstAA, true)
        txt.print(" = offset ")
        txt.print_uw(firstAA-$4000)
        txt.nl()
    }

    sub start() {
        uword buffer = $4000
        sys.memset(buffer, 2000, $aa)

        print_buffer()

        void cx16.screen_mode($80, false)       ; 320*240 * 256C
        cx16.FB_cursor_position(0, 0)
        cx16.FB_get_pixels($4000, 256)
        void cx16.screen_mode(0, false)       ; 320*240 * 256C

        print_buffer()
        repeat { }


        void cx16.screen_mode($80, false)       ; 320*240 * 256C
        ;; cx16.mouse_config2(1)
        cx16.FB_cursor_position(0, 0)

        cx16.FB_set_pixels(0, 320*4)
        ; expected result: exactly 12 rows of 320 pixels written
        ; actual result: almost 13 rows of pixels written.

        repeat {}

;        sys.wait(9999)
;
;        ; this works:
;        ubyte y
;        for y in 0 to 239 step 24 {
;            cx16.FB_cursor_position(0, y)
;            cx16.FB_set_pixels(0, 320*24)
;        }
;        sys.wait(120)
;
;        ; this works too:
;        cx16.FB_cursor_position(0,0)
;        repeat 240 cx16.FB_set_pixels(0, 320)
;        sys.wait(120)
;
;        ; this kills the mouse pointer because it writes past the bitmap screen data
;        ; expected is to write 10 times 24 rows = 240 rows of pixels, exactly 1 screen...
;        cx16.FB_cursor_position(0,0)
;        unroll 10 cx16.FB_set_pixels(0, 320*24)
;
;        repeat {}
    }
}
