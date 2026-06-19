%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared a = 10
        ubyte @shared b = add_one(a)
        ubyte @shared c = add_one(20)
        say_hello()
    }

    sub say_hello() {
        txt.print("hello")
    }

    sub add_one(ubyte x) -> ubyte {
        return x + 1
    }
}
