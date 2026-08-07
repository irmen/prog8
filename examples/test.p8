;%import intuition
;%import graphics
;%import utility
%import textio

main {
    sub start() {
        uword[4000] array

        array[3] = 1111
        array[33] = 2222
        array[333] = 3333
        array[3333] = 4444

        txt.print_uw(array[3])
        txt.nl()
        txt.print_uw(array[33])
        txt.nl()
        txt.print_uw(array[333])
        txt.nl()
        txt.print_uw(array[3333])
        txt.nl()

;        long[] screentags = [
;            intuition.SA_Left,      0,
;            intuition.SA_Top,       0,
;            intuition.SA_Width,     320,
;            intuition.SA_Height,    200,
;            intuition.SA_Depth,     5,  ;  5 bitplanes=32 colors
;            intuition.SA_DisplayID, graphics.NTSC_MONITOR_ID | graphics.LORES_KEY,
;            intuition.SA_Title,     "My 32-Color NTSC Screen" as long,
;            intuition.SA_Type,      intuition.CUSTOMSCREEN as long,
;            intuition.SA_ShowTitle, true as long,
;            utility.TAG_DONE,     0
;        ]
;
;        txt.print_bool(sizeof(screentags))
;        txt.nl()
;        txt.print_uw(len(screentags))
;        txt.nl()
    }
}
