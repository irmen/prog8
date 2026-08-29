%import custom
%import textio

main {

    /** struct one */
    struct Foo {
        bool z
    }

    /** alias one */
    alias QQ = Foo

    /** thingymabob */
    const ubyte thing = 33

    /**
    hello1
    */
    sub start() {
        ^^QQ @shared wot = []

        custom.grab_system()

        /**
        hello2
        */
        bool pal = custom.isPAL

        ; ntsc
        bool ntsc = not pal

        custom.restore_system()

        txt.print("pal? ")
        txt.print_bool(pal)
        txt.nl()
    }
}
