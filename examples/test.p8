%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        str  s1 = "hello"
        str  s2 = "hello"
        str  s3 = "hello"
        str  s4 = "hello"

        if true {
            c64scr.print("irmen")
            c64scr.print("hello")
            c64scr.print("hello2")
        }

        if true {
            c64scr.print("irmen")
            c64scr.print("hello")
            c64scr.print("hello2")
        }

    }
}
