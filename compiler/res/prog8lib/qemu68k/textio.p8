%import conv
%option ignore_unused

txt {
    asmsub chrout(ubyte char @D0) {
        %asm {{
            bra  qemu.chrout
        }}
    }

    alias chrin = qemu.chrin
    alias keypressed = qemu.keypressed

    sub nl() {
        chrout('\n')
    }

    sub spc() {
        chrout(' ')
    }

    asmsub print(str text @A0) {
        %asm {{
.loop:
            move.b   (a0)+,d0
            beq.s    .done
            move.l   d0,qemu.TTY_PUT_CHAR
            bra.s    .loop
.done:
            rts
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

    sub  input_chars  (str buffer) -> ubyte  {
        ; Input a string (max. 80 chars) from the keyboard. Returns length of input.
        ; User entered EOL is trimmed, and the string is terminated with a 0 byte.
        ubyte count
        while count < 80 {
            ubyte ch = qemu.chrin()
            if ch == '\r' or ch == '\n' {
                buffer[count] = 0
                return count
            }
            if ch == 8 or ch == 127 {
                if count > 0 {
                    count--
                    chrout(8)       ; move cursor back
                    chrout(' ')     ; overwrite with space
                    chrout(8)       ; move cursor back again
                }
            } else {
                chrout(ch)
                buffer[count] = ch
                count++
            }
        }
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
        return 80
    }

    sub height() -> ubyte {
        return 25
    }

    sub size() -> ubyte, ubyte {
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
