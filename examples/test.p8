%import textio
%zeropage basicsafe
%option no_sysinit

main {

    romsub @bank %100  $f800 = routine_in_kernal_addr_space(uword arg @AY) -> uword @AY
    ;              ^-- I/O enabled, basic and kernal roms banked out
    sub start() {
        ; copy the routine into kernal area address space
        sys.memcopy(&the_invert_routine, $f800, 255)

        cx16.r0 = the_invert_routine(12345)
        txt.print("inverted (normal)=")
        txt.print_uw(cx16.r0)
        txt.nl()
        cx16.r0 = routine_in_kernal_addr_space(12345)
        txt.print("inverted (kernal space)=")
        txt.print_uw(cx16.r0)
        txt.nl()
        txt.print("inverted (callfar)=")
        cx16.r0=callfar(%100, $f800, 12345)
        txt.print_uw(cx16.r0)
        txt.nl()
        txt.print("inverted (callfar2)=")
        cx16.r0=callfar2(%100, $f800, 57, 0, 48, false)
        txt.print_uw(cx16.r0)
        txt.nl()

    }

    asmsub the_invert_routine(uword arg @AY) -> uword @AY {
        %asm {{
            eor  #$ff
            pha
            tya
            eor  #$ff
            tay
            pla
            rts
        }}
    }
}

;main {
;    sub start() {
;        basic_area.routine1()
;        hiram_area.routine2()
;
;        ; copy the kernal area routine to actual kernal address space $f800
;        sys.memcopy(&kernal_area.routine3, $f800, 255)
;
;        ; how to call the routine using manual bank switching:
;        ; c64.banks(%101)     ; bank out kernal rom
;        ; call($f800)         ; call our routine
;        ; c64.banks(%111)     ; kernal back
;
;        ; how to use prog8's automatic bank switching:
;        romsub @bank %101  $f800 = kernal_routine()
;
;        kernal_routine()
;
;        txt.print("done!\n")
;    }
;}
;
;kernal_area {
;    ; this routine is actually copied to kernal address space first
;    ; we cannot use CHROUT when the kernal is banked out so we write to the screen directly
;    asmsub routine3() {
;        %asm {{
;            lda  #<_message
;            ldy  #>_message
;            sta  $fe
;            sty  $ff
;            ldy  #0
;-           lda  ($fe),y
;            beq  +
;            sta  $0400+240,y
;            iny
;            bne  -
;+           rts
;
;_message
;        .enc 'screen'
;        .text "hello from kernal area $f800",0
;        .enc 'none'
;            ; !notreached!
;        }}
;    }
;}
;
;
;basic_area $a000 {
;    sub routine1() {
;        txt.print("hello from basic rom area ")
;        txt.print_uwhex(&routine1, true)
;        txt.nl()
;    }
;}
;
;hiram_area $ca00 {
;    sub routine2() {
;        txt.print("hello from hiram area ")
;        txt.print_uwhex(&routine2, true)
;        txt.nl()
;    }
;}
;
