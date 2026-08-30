%import dos
%import conv
%import strings
%option ignore_unused

txt {
    asmsub chrout(ubyte char @D0) clobbers(D0,D1,D2,D3,A0,A6) {
        %asm {{
            move.l  sys.DOSBase,a6
            move.b  d0,-(sp)          ; 1. Push character onto the stack. A7 now points to it!
            move.l  4.w,a0
            cmp.w   #36,20(a0)      ; KS 2.0 = V36
            bcc.s   .use_writechars

            jsr     -60(a6)           ; Output()
            move.l  d0, d1
            move.l  sp, d2             ; D2 = Pointer to our character (Current Stack Pointer)
            moveq   #1, d3             ; D3 = We only want to write exactly 1 byte
            jsr     -48(a6)           ; Write()
            addq.l  #2, sp
            rts

.use_writechars:
            move.l  sp, d1
            moveq   #1, d2
            jsr     -942(a6)        ; WriteChars (ks 2.0+)
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

    sub tab() {
        chrout('\t')
    }

    asmsub flush() {
        %asm {{
            move.l  4.w,a6
            cmp.w   #36,20(a6)      ; KS 2.0 = V36
            bcc.s   .do_flush
            rts
.do_flush:
            move.l  sys.DOSBase,a6
            jsr     -60(a6)
            move.l  d0,d1
            jmp     -360(a6)       ; Flush (ks 2.0+)
        }}
    }

    asmsub print(str text @A0) clobbers(D0,D1,D2,D3,A0,A6) {
        %asm {{
            move.l  4.w,a6
            cmp.w   #36,20(a6)      ; KS 2.0 = V36
            bcc.s   .use_putstr
            move.l  a0,d2
            jsr  strings.length
            move.l  d0,d3
            move.l  sys.DOSBase,a6
            jsr     -60(a6)           ; Output()
            move.l  d0,d1
            jmp     -48(a6)           ; Write()
.use_putstr:
            move.l  sys.DOSBase,a6
            move.l  a0,d1
            jsr     -948(a6)          ; PutStr()  (KS 2.0+)
            jsr     -60(a6)         ; Output()
            move.l  d0,d1
            jmp     -360(a6)       ; Flush (ks 2.0+)
        }}
    }

    private long @shared fmt_value_l
    private uword @shared fmt_value_w

    sub print_ub(ubyte value) {
        fmt_value_w = value as uword
        void dos.VPrintf("%d", &fmt_value_w)
    }

    sub print_ub0(ubyte value) {
        fmt_value_w = value as uword
        void dos.VPrintf("%03d", &fmt_value_w)
    }

    sub print_b(byte value) {
        fmt_value_w = value as uword
        void dos.VPrintf("%d", &fmt_value_w)
    }

    sub print_uw(uword value) {
        fmt_value_l = value as long
        void dos.VPrintf("%lu", &fmt_value_l)
    }

    sub print_uw0(uword value) {
        fmt_value_l = value as long
        void dos.VPrintf("%05lu", &fmt_value_l)
    }

    sub print_w(word value) {
        void dos.VPrintf("%d", &value)
    }

    sub print_l(long value) {
        void dos.VPrintf("%ld", &value)
    }

    sub print_ulhex(long value, bool prefix) {
        void dos.VPrintf(if prefix then "$%08lx" else "%08lx", &value)
    }

    sub print_uwhex(uword value, bool prefix) {
        void dos.VPrintf(if prefix then "$%04x" else "%04x", &value)
    }

    sub print_ubhex(ubyte value, bool prefix) {
        fmt_value_w = value as uword
        void dos.VPrintf(if prefix then "$%02x" else "%02x", &fmt_value_w)
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

    sub input_chars(str buffer) -> ubyte {
        ; Input a string (max. 80 chars) from the keyboard. Returns length of input.
        ; User entered EOL is trimmed, and the string is terminated with a 0 byte.
        ; Uses line-buffered input (stdin stays in its default line mode).
        pointer fh = dos.Input()
        if fh == 0
            return 0
        long actual = dos.Read(fh, buffer, 80)
        if actual <= 0 {
            buffer[0] = 0
            return 0
        }
        ubyte count = actual as ubyte
        if count > 0 and (buffer[count-1] == '\n' or buffer[count-1] == '\r')
            count--
        buffer[count] = 0
        return count
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
        print("\x1b[H\x1B[J")
    }

    sub home() {
        print("\x1b[H")
    }

    sub bold() {
        print("\x1b[1m")
    }

    sub dim() {
        print("\x1b[2m")
    }

    sub italic() {
        print("\x1b[3m")
    }

    sub underline() {
        print("\x1b[4m")
    }

    sub rvs() {
        print("\x1b[7m")
    }

    sub normal() {
        print("\x1b[0m")
    }

    sub cursor_off() {
        txt.print("\x1b[0 p")
    }

    sub cursor_on() {
        txt.print("\x1b[1 p")
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
        print("\x1b[")
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
