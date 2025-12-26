; Prog8 definitions for the Text I/O console routines for the Virtual Machine

%import conv
%option ignore_unused

txt {

sub width() -> ubyte {
    %ir {{
        syscall 46 (): r99000.w
        lsigb.w r99100,r99000
        returnr.b r99100
    }}
}

sub height() -> ubyte {
    %ir {{
        syscall 46 (): r99000.w
        msigb.w r99100,r99000
        returnr.b r99100
    }}
}

sub size() -> ubyte, ubyte {
    ; -- returns the text screen width and height (number of columns and rows)
    return width(), height()
}

sub  clear_screen() {
    str @shared sequence = "\x1b[2J\x1B[H"
    %ir {{
        load.w r99000,txt.clear_screen.sequence
        syscall 3 (r99000.w)
    }}
}

sub cls() {
    clear_screen()
}

sub nl() {
    chrout('\n')
}

sub spc() {
    chrout(' ')
}

sub home() {
    print("\x1b[H")
}

sub lowercase() {
    ; not supported
}

sub uppercase() {
    ; not supported
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


sub chrout(ubyte char) {
    %ir {{
        loadm.b r99100,txt.chrout.char
        syscall 2 (r99100.b)
    }}
}

sub bell() {
    chrout(7)
}

sub  print (str text) {
    %ir {{
        loadm.w r99000,txt.print.text
        syscall 3 (r99000.w)
    }}
}

sub  print_ub0  (ubyte value) {
    ; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
    print(conv.str_ub0(value))
}

sub  print_ub  (ubyte value)  {
    ; ---- print the ubyte in decimal form, without left padding 0s
    print(conv.str_ub(value))
}

sub  print_b  (byte value)   {
    ; ---- print the byte in decimal form, without left padding 0s
    print(conv.str_b(value))
}

sub print_bool(bool value) {
    if value
        print("true")
    else
        print("false")
}

sub  print_ubhex  (ubyte value, bool prefix)  {
    ; ---- print the ubyte in hex form
    if prefix
        chrout('$')
    print(conv.str_ubhex(value))
}

sub  print_ubbin  (ubyte value, bool prefix) {
    ; ---- print the ubyte in binary form
    if prefix
        chrout('%')
    print(conv.str_ubbin(value))
}

sub  print_uwbin  (uword value, bool prefix)  {
    ; ---- print the uword in binary form
    if prefix
        chrout('%')
    print(conv.str_uwbin(value))
}

sub  print_uwhex  (uword value, bool prefix) {
    ; ---- print the uword in hexadecimal form (4 digits)
    if prefix
        chrout('$')
    print(conv.str_uwhex(value))
}

sub  print_ulhex  (long value, bool prefix) {
    ; ---- print the ulong in hexadecimal form (4 digits)
    if prefix
        chrout('$')
    print(conv.str_ulhex(value))
}

sub  print_uw0  (uword value)  {
    ; ---- print the uword value in decimal form, with left padding 0s (5 positions total)
    print(conv.str_uw0(value))
}

sub  print_uw  (uword value)  {
    ; ---- print the uword in decimal form, without left padding 0s
    print(conv.str_uw(value))
}

sub  print_w  (word value) {
    ; ---- print the (signed) word in decimal form, without left padding 0's
    print(conv.str_w(value))
}

sub  print_l  (long value) {
    ; ---- print the (signed) long in decimal form, without left padding 0's
    %ir {{
        loadm.l r99200,txt.print_l.value
        syscall 59 (r99200.l)
    }}
}

sub  input_chars  (str buffer) -> ubyte  {
    ; ---- Input a string (max. 80 chars) from the keyboard. Returns length of input. (string is terminated with a 0 byte as well)
    ;      It assumes the keyboard is selected as I/O channel!
    %ir {{
        loadm.w r99000,txt.input_chars.buffer
        load.b r99100,#80
        syscall 6 (r99000.w, r99100.b): r99100.b
        returnr.b r99100
    }}
}

sub column(ubyte col) {
    txt.chrout(27)
    txt.chrout('[')
    txt.print_ub(col+1)
    txt.chrout(';')
    txt.chrout('G')
}

sub  plot  (ubyte col, ubyte row) {
    ; use ANSI escape sequence to position the cursor
    txt.chrout(27)
    txt.chrout('[')
    txt.print_ub(row)
    txt.chrout(';')
    txt.print_ub(col)
    txt.chrout('H')
}

sub setchr (ubyte col, ubyte row, ubyte char) {
    plot(col, row)
    txt.chrout(char)
}

sub petscii2scr(ubyte petscii_char) -> ubyte {
    ; -- convert petscii character to screencode
    ubyte[8] offsets = [128, 0, 64, 32, 64, 192, 128, 128]
    return petscii_char ^ offsets[petscii_char>>5]
}

sub petscii2scr_str(str petscii_string) {
    ; -- convert petscii string to screencodes string
    cx16.r0 = petscii_string
    while @(cx16.r0)!=0 {
        @(cx16.r0) = petscii2scr(@(cx16.r0))
        cx16.r0++
    }
}

    sub iso2petscii(ubyte iso_char) -> ubyte {
        ; --converts iso 8859-15 character to petscii character (lowercase)
        if iso_char & $7f <= $20
        	return petscii:' '  ; whitspace
        if iso_char <= $3f
            return iso_char  ; numbers and symbols
        if iso_char < $80
            return translate40to7F[iso_char-$40]
        return translateA0toFF[iso_char-$a0]

        ubyte[$40] translate40to7F = [
            $40, $c1, $c2, $c3, $c4, $c5, $c6, $c7, $c8, $c9, $ca, $cb, $cc, $cd, $ce, $cf,
            $d0, $d1, $d2, $d3, $d4, $d5, $d6, $d7, $d8, $d9, $da, $5b, $3f, $5d, $5e, $e4,
            $27, $41, $42, $43, $44, $45, $46, $47, $48, $49, $4a, $4b, $4c, $4d, $4e, $4f,
            $50, $51, $52, $53, $54, $55, $56, $57, $58, $59, $5a, $f3, $7d, $eb, $3f, $3f,
        ]
        ubyte[$60] translateA0toFF = [
            $20, $21, $20, $5c, $c5, $d9, $3f, $3f, $3f, $c3, $3f, $28, $3f, $3f, $d2, $e3,
            $3f, $3f, $32, $33, $3f, $3f, $ff, $3f, $3f, $31, $3f, $3e, $3f, $3f, $d9, $3f,
            $c1, $c1, $c1, $c1, $c1, $c1, $c1, $c3, $c5, $c5, $c5, $c5, $c9, $c9, $c9, $c9,
            $c4, $ce, $cf, $cf, $cf, $cf, $cf, $58, $cf, $d5, $d5, $d5, $d5, $d9, $3f, $53,
            $41, $41, $41, $41, $41, $41, $41, $43, $45, $45, $45, $45, $49, $49, $49, $49,
            $4f, $4e, $4f, $4f, $4f, $4f, $4f, $3f, $4f, $55, $55, $55, $55, $59, $3f, $59,
        ]
    }

    sub iso2petscii_str(str iso_string) {
        ; -- convert iso string to petscii (lowercase) string
        cx16.r0 = iso_string
        while @(cx16.r0)!=0 {
            @(cx16.r0) = iso2petscii(@(cx16.r0))
            cx16.r0++
        }
    }
}
