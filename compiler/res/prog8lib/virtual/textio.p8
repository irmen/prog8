; Prog8 definitions for the Text I/O and Screen routines for the Virtual Machine
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0

%import syslib


txt {

const ubyte DEFAULT_WIDTH = 40
const ubyte DEFAULT_HEIGHT = 24


sub  clear_screen() {
    txt.chrout(125)
}

sub nl() {
    txt.chrout('\n')
}

sub spc() {
    txt.chrout(' ')
}

sub  fill_screen (ubyte char) {
    ; ---- fill the character screen with the given fill character.
    ;      TODO
}

sub  clear_screenchars (ubyte char) {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	; TODO
}

sub chrout(ubyte char) {
    ; TODO
}

sub  print (str text) {
	; ---- print null terminated string from A/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to CHROUT of that single char.
	; TODO
}

sub  print_ub0  (ubyte value) {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	; TODO
}

sub  print_ub  (ubyte value)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	; TODO
}

sub  print_b  (byte value)   {
	; ---- print the byte in A in decimal form, without left padding 0s
	; TODO
}

sub  print_ubhex  (ubyte value, ubyte prefix)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	; TODO
}

sub  print_ubbin  (ubyte value, ubyte prefix) {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	; TODO
}

sub  print_uwbin  (uword value, ubyte prefix)  {
	; ---- print the uword in A/Y in binary form (if Carry is set, a radix prefix '%' is printed as well)
	; TODO
}

sub  print_uwhex  (uword value, ubyte prefix) {
	; ---- print the uword in A/Y in hexadecimal form (4 digits)
	;      (if Carry is set, a radix prefix '$' is printed as well)
	; TODO
}

sub  print_uw0  (uword value)  {
	; ---- print the uword in A/Y in decimal form, with left padding 0s (5 positions total)
	; TODO
}

sub  print_uw  (uword value)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	; TODO
}

sub  print_w  (word value) {
	; ---- print the (signed) word in A/Y in decimal form, without left padding 0's
	; TODO
}

sub  input_chars  (uword buffer) -> ubyte  {
	; ---- Input a string (max. 80 chars) from the keyboard. Returns length in Y. (string is terminated with a 0 byte as well)
	;      It assumes the keyboard is selected as I/O channel!
	;  TODO
	return 0
}

}
