; Prog8 definitions for the Text I/O console routines for the Virtual Machine
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%import conv

txt {

sub  clear_screen() {
    void syscall1(3, "\x1b[2J\x1B[H")
}

sub nl() {
    txt.chrout('\n')
}

sub spc() {
    txt.chrout(' ')
}

sub lowercase() {
    ; not supported
}

sub uppercase() {
    ; not supported
}

sub chrout(ubyte char) {
    void syscall1(2, char)
}

sub  print (str text) {
    void syscall1(3, text)
}

sub  print_ub0  (ubyte value) {
    ; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
    conv.str_ub0(value)
    print(conv.string_out)
}

sub  print_ub  (ubyte value)  {
    ; ---- print the ubyte in decimal form, without left padding 0s
    conv.str_ub(value)
    print(conv.string_out)
}

sub  print_b  (byte value)   {
    ; ---- print the byte in decimal form, without left padding 0s
    conv.str_b(value)
    print(conv.string_out)
}

sub  print_ubhex  (ubyte value, ubyte prefix)  {
    ; ---- print the ubyte in hex form
    if prefix
        chrout('$')
    conv.str_ubhex(value)
    print(conv.string_out)
}

sub  print_ubbin  (ubyte value, ubyte prefix) {
    ; ---- print the ubyte in binary form
    if prefix
        chrout('%')
    conv.str_ubbin(value)
    print(conv.string_out)
}

sub  print_uwbin  (uword value, ubyte prefix)  {
    ; ---- print the uword in binary form
    if prefix
        chrout('%')
    conv.str_uwbin(value)
    print(conv.string_out)
}

sub  print_uwhex  (uword value, ubyte prefix) {
    ; ---- print the uword in hexadecimal form (4 digits)
    if prefix
        chrout('$')
    conv.str_uwhex(value)
    print(conv.string_out)
}

sub  print_uw0  (uword value)  {
    ; ---- print the uword value in decimal form, with left padding 0s (5 positions total)
    conv.str_uw0(value)
    print(conv.string_out)
}

sub  print_uw  (uword value)  {
    ; ---- print the uword in decimal form, without left padding 0s
    conv.str_uw(value)
    print(conv.string_out)
}

sub  print_w  (word value) {
    ; ---- print the (signed) word in decimal form, without left padding 0's
    conv.str_w(value)
    print(conv.string_out)
}

sub  input_chars  (uword buffer) -> ubyte  {
    ; ---- Input a string (max. 80 chars) from the keyboard. Returns length of input. (string is terminated with a 0 byte as well)
    ;      It assumes the keyboard is selected as I/O channel!
    return syscall1(6, buffer)
}

}
