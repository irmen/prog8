main {
    str s1 = "hello"
    ^^ubyte @shared ubyteptr

    sub start() {
        cx16.r0bL = s1=="wob"
        cx16.r1bL = "wob"=="s1"
        cx16.r2bL = "wob"==cx16.r0
        cx16.r3bL = cx16.r0=="wob"
        cx16.r4bL = "wob"==ubyteptr
        cx16.r5bL = ubyteptr=="wob"
        void compare1("wob")
        void compare2("wob")
    }

    sub compare1(str s2) -> bool {
        return s1==s2
    }

    sub compare2(str s2) -> bool {
        return s2==s1
    }
}
