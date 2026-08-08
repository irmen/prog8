; Amiga custom screen example
; requires kickstart 2.0+ (A600, A1200, etc)

%import intuition
%import graphics
%import utility

main {
    sub start() {

        long[] screentags = [
            intuition.SA_Left,      0,
            intuition.SA_Top,       300,
            intuition.SA_Width,     320,
            intuition.SA_Height,    256,
            intuition.SA_Depth,     5,  ;  5 bitplanes=32 colors
            intuition.SA_DisplayID, graphics.DEFAULT_MONITOR_ID | graphics.LORES_KEY,
            intuition.SA_Title,     "My 32-Color Screen",
            intuition.SA_Type,      intuition.CUSTOMSCREEN,
            intuition.SA_ShowTitle, false,
            ;;intuition.SA_Draggable, false,
            ;;intuition.SA_Exclusive, true,
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

        uword[32] palette = [
            $0112, $0334, $0778, $0FFF,
            $0422, $0843, $0C75, $0EA8,
            $0531, $0A72, $0EA3, $0FE7,
            $0132, $0263, $04A5, $08E7,
            $0134, $0278, $04AB, $09EE,
            $0124, $0348, $057C, $09BF,
            $0314, $0627, $0A4C, $0EF6,
            $0412, $0813, $0D34, $0F77
        ]

        ; open custom screen
        ^^intuition.Screen myScreen = intuition.OpenScreenTagList(0, screentags)
        if myScreen!=0 {
            graphics.LoadRGB4(&myScreen.emb_ViewPort, palette, len(palette))

            ; now open a window to be able to set mouse cursor, handle mouse clicks, keyboard presses, etc.
            windowtags[1] = myScreen
            ^^intuition.Window myWindow = intuition.OpenWindowTagList(0, windowtags)

            if myWindow!=0 {
                ^^graphics.RastPort rp = &myScreen.emb_RastPort

                ; draw graphics
                graphics.SetRast(rp, 1)

                ubyte index
                for index in 0 to 31 {
                    word radius = index *3 + 10
                    graphics.SetAPen(rp, index)
                    graphics.DrawEllipse(rp, 160, 128, radius, radius)
                }

                graphics.SetDrMd(rp, graphics.RP_JAM1)
                graphics.SetAPen(rp, 2)
                graphics.Move(rp, 20, 10)
                str message = "Graphics screen using Prog8 !!"
                void graphics.Text(rp, message, len(message))

                ; slide screen up and down again
                repeat 150  intuition.MoveScreen(myScreen, 0, -2)
                sys.wait(100)
                repeat 150  intuition.MoveScreen(myScreen, 0, 2)

                intuition.CloseWindow(myWindow)
            }
            intuition.CloseScreen(myScreen)
        }
    }
}
