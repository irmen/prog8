%import textio
%zeropage basicsafe
%address $2000

; hello world test for Atari 8-bit

main {
	sub start() {
        txt.print("Hello, World!")
		txt.nl()
		txt.waitkey()
    }
}