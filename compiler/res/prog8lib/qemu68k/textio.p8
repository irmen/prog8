%import conv
%option ignore_unused

txt {
    sub chrout(ubyte char) {
        %asm {{
            move.b  4(sp),d0
            move.l  d0,qemu.TTY_PUT_CHAR
        }}
    }

    sub nl() {
        chrout('\n')
    }

    sub print(str text) {
        uword ii
        while text[ii] != 0 {
            chrout(text[ii])
            ii++
        }
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
    }

    sub iso_off() {
    }

    sub lowercase() {
    }

    sub uppercase() {
    }

    sub cls() {
    }

    sub clear_screen() {
    }

    sub home() {
    }

    sub rvs_on() {
    }

    sub rvs_off() {
    }
}
