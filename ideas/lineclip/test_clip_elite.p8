%import textio
%import lineclip
%zeropage basicsafe

main {

    sub start() {

        ; Clip window
        word cx1 = 400
        word cy1 = 300
        word cx2 = 1600
        word cy2 = 1400
        lineclip.set_cliprect(cx1, cy1, cx2, cy2)

        ; Test case names
        str[] names = [
            "T1 inside", "T2 near TL", "T3 near BR", "T4 point inside",
            "T5 far left", "T6 far right", "T7 far above", "T8 far below",
            "T9 far TL", "T10 far BR", "T11 far BL", "T12 far TR",
            "T13 enter left", "T14 enter right", "T15 enter top", "T16 enter bottom",
            "T17 diag TL-BR", "T18 diag BL-TR", "T19 diag TR-BL", "T20 diag BR-TL",
            "T21 horiz world", "T22 vert world", "T23 full world diag", "T24 full world diag2",
            "T25 shallow up", "T26 shallow down", "T27 steep right", "T28 steep left",
            "T29 above parallel", "T30 below parallel", "T31 left parallel", "T32 right parallel",
            "T33 x=0 tiny", "T34 x=2000 tiny", "T35 y=0 tiny", "T36 y=2000 tiny",
            "T37 graze top edge", "T38 graze bottom", "T39 graze left edge", "T40 graze right edge",
            "T41 pt TL corner", "T42 pt TR corner", "T43 pt BL corner", "T44 pt BR corner",
            "T45 origin to far BR", "T46 far TL to far BR",
            "T47 near left edge", "T48 near right edge", "T49 near top edge", "T50 near bottom edge"
        ]

        ; Expected visibility (true = should be visible / clipped)
        bool[] expected_visible = [
            true, true, true, true,
            false, false, false, false,
            false, false, false, false,
            true, true, true, true,
            true, true, true, true,
            true, true, true, true,
            true, true, true, true,
            false, false, false, false,
            false, false, false, false,
            true, true, true, true,
            true, true, true, true,
            true, true,
            false, false, false, false
        ]

        ; Input coordinates (x1, y1, x2, y2)
        word[] tx1 = [
            800, 420, 1580, 1000, -500, 2200, 800, 1000, -300, 1800, -400, 1800,
            200, 1400, 1000, 1000, 100, 100, 1800, 1800,
            0, 1000, 0, 2000, 200, 200, 1000, 990, 200, 200, 100, 1800,
            0, 2000, 1000, 1000, 200, 200, 400, 1600,
            400, 1600, 400, 1600, 0, 2000, 390, 1605, 1000, 1000
        ]

        word[] ty1 = [
            600, 320, 1380, 850, -200, 1500, -500, 1700, -300, 1600, 1500, -300,
            800, 800, 100, 1200, 200, 1700, 200, 1700,
            1000, 0, 0, 0, 800, 900, 100, 100, 100, 1550, 800, 800,
            1000, 1000, 0, 2000, 300, 1400, 100, 100,
            300, 300, 1400, 1400, 0, 0, 800, 800, 295, 1405
        ]

        word[] tx2 = [
            1200, 500, 1600, 1000, -300, 2500, 1000, 1200, 200, 2200, 100, 2000,
            600, 1800, 1000, 1000, 1800, 1800, 100, 100,
            2000, 1000, 2000, 0, 1800, 1800, 1010, 1000, 350, 350, 100, 1800,
            0, 2000, 1001, 1001, 1800, 1800, 400, 1600,
            400, 1600, 400, 1600, 2000, 0, 395, 1610, 1005, 1005
        ]

        word[] ty2 = [
            1000, 400, 1400, 850, 100, 1800, -200, 2000, 100, 1700, 1800, 100,
            800, 800, 500, 1600, 1700, 200, 1700, 200,
            1000, 2000, 2000, 2000, 810, 890, 1900, 1900, 100, 1550, 1100, 1100,
            1001, 1001, 0, 2000, 300, 1400, 1600, 1600,
            300, 300, 1400, 1400, 2000, 2000, 800, 800, 295, 1405
        ]

        ; Header
        txt.print("Line clipping algorithm tests")
        txt.nl()
        txt.print("Clip window: (")
        txt.print_w(cx1)
        txt.print(",")
        txt.print_w(cy1)
        txt.print(")-(")
        txt.print_w(cx2)
        txt.print(",")
        txt.print_w(cy2)
        txt.print(")")
        txt.nl()
        txt.nl()

        ubyte passed = 0
        ubyte failed = 0
        ubyte i

        for i in 0 to 49 {
            word x1 = tx1[i]
            word y1 = ty1[i]
            word x2 = tx2[i]
            word y2 = ty2[i]

            bool visible
            word ax
            word ay
            word bx
            word by
            visible, ax, ay, bx, by = lineclip.clip(x1, y1, x2, y2)

            byte fail_reason = 0

            ; Check 1: visibility must match expected
            bool expected = expected_visible[i]
            if visible != expected {
                fail_reason = 1
            }

            ; Check 2: if visible, clipped coords must be inside clip window
            if visible {
                if ax < cx1 {
                    fail_reason = 2
                }
                if ax > cx2 {
                    fail_reason = 2
                }
                if ay < cy1 {
                    fail_reason = 2
                }
                if ay > cy2 {
                    fail_reason = 2
                }
                if bx < cx1 {
                    fail_reason = 2
                }
                if bx > cx2 {
                    fail_reason = 2
                }
                if by < cy1 {
                    fail_reason = 2
                }
                if by > cy2 {
                    fail_reason = 2
                }
            }

            ; Print test result
            txt.print(names[i])
            txt.print(": ")
            if fail_reason == 0 {
                txt.print("PASS")
                passed++
            } else {
                txt.print("FAIL")
                failed++
            }
            txt.nl()

            ; Debug output with clipped coordinates
            txt.print("  clipped: (")
            txt.print_w(ax)
            txt.print(",")
            txt.print_w(ay)
            txt.print(")-(")
            txt.print_w(bx)
            txt.print(",")
            txt.print_w(by)
            txt.print(")  visible=")
            if visible {
                txt.print("true")
            } else {
                txt.print("false")
            }
            if fail_reason != 0 {
                txt.print("  FAIL_REASON=")
                txt.print_b(fail_reason)
            }
            txt.nl()
        }

        ; Summary
        txt.nl()
        txt.print_uw(passed)
        txt.print(" passed, ")
        txt.print_uw(failed)
        txt.print(" failed out of 50 tests")
        txt.nl()
    }
}
