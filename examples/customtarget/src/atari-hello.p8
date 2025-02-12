%import textio
%zeropage basicsafe
%launcher none
%output xex

; hello world test for Atari 8-bit

main {
    sub start() {
        txt.print("Hello, World!")
        txt.nl()
        void txt.waitkey()
    }
}
