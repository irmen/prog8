; Prog8 definitions for the Text I/O console routines for the Virtual Machine

%import conv
%option ignore_unused

txt {

sub width() -> ubyte {
    %ir {{
        syscall 46 (): r99000.w
        lsig.b r99100,r99000
        returnr.b r99100
    }}
}

sub height() -> ubyte {
    %ir {{
        syscall 46 (): r99000.w
        msig.b r99100,r99000
        returnr.b r99100
    }}
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

sub  input_chars  (str buffer) -> ubyte  {
    ; ---- Input a string (max. 80 chars) from the keyboard. Returns length of input. (string is terminated with a 0 byte as well)
    ;      It assumes the keyboard is selected as I/O channel!
    %ir {{
        loadm.w r99000,txt.input_chars.buffer
        load.b r99100,80
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

}
