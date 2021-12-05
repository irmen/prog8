%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str text = "hello"
        txt.print(text)
        txt.print("hello2\u0032")
        ubyte chr1 = '\x33'
        txt.chrout(chr1)
        %asm {{
            nop
        }}
    }
}
