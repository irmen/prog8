%import textio
%zeropage basicsafe

main {

    sub start() {

        word wvar
        word wvar2
        uword uwvar
        uword uwvar2
        byte bvar
        byte bvar2
        ubyte ubvar

        uwvar result

        uwvar = 49999
        uwvar2 = 1234
        result = uwvar / uwvar2
        txt.print_uw(result)
        result = uwvar % uwvar2
        txt.print_uw(result)

 ;       uwvar %= uwvar2


;        wvar /= bvar
;        uwvar /= bvar
;        uwvar %= ubvar



;        ubyte[] array = [1,2,3,4]
;        ubyte ix
;
;        ubyte a = array[1] + array[ix]
;        a = array[ix] + array[ix]
;        a = array[ix+1] + array[ix]
;        uword multiple=0
;        a = array[lsb(multiple)] + array[ix]


;        str filename = "titlescreen.bin"
;        ubyte success = cx16.vload(filename, 8, 0, $0000)
;        if success {
;            txt.print("load ok")
;            cx16.VERA_DC_HSCALE = 64
;            cx16.VERA_DC_VSCALE = 64
;            cx16.VERA_L1_CONFIG = %00011111     ; 256c bitmap mode
;            cx16.VERA_L1_MAPBASE = 0
;            cx16.VERA_L1_TILEBASE = 0
;        } else {
;            txt.print("load fail")
;        }
    }
}

