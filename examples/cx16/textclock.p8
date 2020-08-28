; CommanderX16 text clock example!
; make sure to compile with the cx16 compiler target.

%import cx16textio
%zeropage basicsafe

main {

    sub start() {

;        %asm {{
;            lda  #$80
;            jsr  cx16.screen_set_mode
;        }}
;        cx16.r0=0
;        cx16.GRAPH_init()
;        %asm {{
;            lda  #4
;            ldy  #0
;            ldx  #1
;            jsr  cx16.GRAPH_set_colors
;        }}
;        cx16.GRAPH_clear()
;        cx16.r0=10
;        cx16.r1=10
;        cx16.r2=100
;        cx16.r3=150
;        cx16.GRAPH_draw_line()
;
;        repeat {
;        }

        cx16.r0 = mkword(8, 2020 - 1900)
        cx16.r1 = mkword(19, 27)
        cx16.r2 = mkword(0, 16)
        cx16.r3 = 0
        cx16.clock_set_date_time()
        cx16.screen_set_charset(3, 0)  ; lowercase charset

        repeat {
            c64.CHROUT(19)      ; HOME
            txt.print("\n yyyy-mm-dd HH:MM:SS.jj\n\n")
            cx16.clock_get_date_time()
            c64.CHROUT(' ')
            print_date()
            c64.CHROUT(' ')
            print_time()
	    }
    }

    sub print_date() {
        txt.print_uw(1900 + lsb(cx16.r0))
        c64.CHROUT('-')
        if msb(cx16.r0) < 10
            c64.CHROUT('0')
        txt.print_ub(msb(cx16.r0))
        c64.CHROUT('-')
        if lsb(cx16.r1) < 10
            c64.CHROUT('0')
        txt.print_ub(lsb(cx16.r1))
    }

    sub print_time() {
        if msb(cx16.r1) < 10
           c64.CHROUT('0')
        txt.print_ub(msb(cx16.r1))
        c64.CHROUT(':')
        if lsb(cx16.r2) < 10
            c64.CHROUT('0')
        txt.print_ub(lsb(cx16.r2))
        c64.CHROUT(':')
        if msb(cx16.r2) < 10
            c64.CHROUT('0')
        txt.print_ub(msb(cx16.r2))
        c64.CHROUT('.')
        if lsb(cx16.r3) < 10
            c64.CHROUT('0')
        txt.print_ub(lsb(cx16.r3))
    }
}

