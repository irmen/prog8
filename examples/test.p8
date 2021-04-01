%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        ubyte thing = otherblock.othersub()
        txt.print_ub(thing)     ; should print 21!

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

block3 {
    ubyte returnvalue=10

    sub thing()->ubyte {
        return returnvalue
    }
}

otherblock {

    ubyte othervar=20
    ubyte calcparam=10

    sub calc(ubyte zz) -> ubyte {
        return zz+1+block3.thing()
    }

    inline sub othersub() -> ubyte {
        return calc(calcparam)
    }
}
