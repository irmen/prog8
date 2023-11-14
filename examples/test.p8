%zeropage basicsafe
%import textio

txt {
    %option merge
    sub println(uword string) {
        txt.print(string)
        txt.nl()
    }
}

main {
    sub start() {
        txt.lowercase()
        txt.println("Hello, world1")
        txt.println("Hello, world2")
        txt.println("Hello, world3")
    }
}
