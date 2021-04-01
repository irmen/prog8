%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {

        ubyte color=0
        ubyte xx
        uword ptr = $0400

        @($02) = 0

        repeat {
            sys.waitvsync()
            %asm {{
                ldy  $02
                lda  #'*'
                sta  $0400,y
                inc  $02
            }}
        }
        txt.print("hello")

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

