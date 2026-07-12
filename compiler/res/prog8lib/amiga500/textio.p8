%import dos
%import conv
%import strings
%option ignore_unused

txt {
    asmsub chrout(ubyte char @D0) {
        %asm {{
            move.b  d0,-(sp)          ; 1. Push character onto the stack. A7 now points to it!
            move.l  sys.DOSBase,a6
            jsr     -60(a6)           ; Output()
            move.l  d0, d1
            move.l  sp, d2             ; D2 = Pointer to our character (Current Stack Pointer)
            moveq   #1, d3             ; D3 = We only want to write exactly 1 byte
            jsr     -48(a6)           ; Write()
            addq.l  #2, sp
            rts
        }}
    }

    sub nl() {
        chrout('\n')
    }

    sub spc() {
        chrout(' ')
    }

    asmsub print(str text @A0) {
        %asm {{
            move.l  a0,d2
            jsr  strings.length
            move.l  d0,d3
            move.l  sys.DOSBase,a6
            jsr     -60(a6)           ; Output()
            move.l  d0,d1
            jmp     -48(a6)           ; Write()
        }}
    }

    sub print_ub(ubyte value) {
        print(conv.str_ub(value))
    }

    sub print_ub0(ubyte value) {
        print(conv.str_ub0(value))
    }

    sub print_b(byte value) {
        print(conv.str_b(value))
    }

    sub print_uw(uword value) {
        print(conv.str_uw(value))
    }

    sub print_uw0(uword value) {
        print(conv.str_uw0(value))
    }

    sub print_w(word value) {
        print(conv.str_w(value))
    }

    sub print_l(long value) {
        print(conv.str_l(value))
    }

    sub print_ulhex(long value, bool prefix) {
        if prefix
            chrout('$')
        print(conv.str_ulhex(value))
    }

    sub print_uwhex(uword value, bool prefix) {
        if prefix
            chrout('$')
        print(conv.str_uwhex(value))
    }

    sub print_ubhex(ubyte value, bool prefix) {
        if prefix
            chrout('$')
        print(conv.str_ubhex(value))
    }

    sub print_ubbin(ubyte value, bool prefix) {
        if prefix
            chrout('%')
        print(conv.str_ubbin(value))
    }

    sub print_uwbin(uword value, bool prefix) {
        if prefix
            chrout('%')
        print(conv.str_uwbin(value))
    }

    sub print_bool(bool value) {
        if value
            print("true")
        else
            print("false")
    }

    sub iso() {
        ; is the default
    }

    sub lowercase() {
        ; is the default
    }

    sub cls() {
        clear_screen()
    }

    sub clear_screen() {
        print("\x1b[2J\x1B[H")
    }

    sub home() {
        print("\x1b[H")
    }

    sub column(ubyte col) {
        chrout(27)
        chrout('[')
        print_ub(col+1)
        chrout(';')
        chrout('G')
    }

    sub rvs_on() {
        print("\x1b[7m")
    }

    sub rvs_off() {
        print("\x1b[0m")
    }

    sub color (ubyte txtcol) {
        print("\x1b[3")
        chrout('0' + txtcol)
        chrout('m')
    }

    sub bell() {
        chrout(7)
    }

    sub width() -> ubyte {
        ; TODO use console Query WIndow Size escape sequence
        return 80
    }

    sub height() -> ubyte {
        ; TODO use console Query WIndow Size escape sequence
        return 25
    }

    sub size() -> ubyte, ubyte {
        ; TODO use console Query WIndow Size escape sequence
        return width(), height()
    }

    sub plot(ubyte col, ubyte row) {
        ; use ANSI escape sequence to position the cursor
        chrout(27)
        chrout('[')
        print_ub(row)
        chrout(';')
        print_ub(col)
        chrout('H')
    }

    sub setchr(ubyte col, ubyte row, ubyte char) {
        plot(col, row)
        chrout(char)
    }
}
