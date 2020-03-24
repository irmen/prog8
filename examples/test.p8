%import c64utils
;%import c64flt
;%option enable_floats
%zeropage dontuse

main {

    sub start() {
        byte v1
        byte v2

        bla()
        exit(4)
        v1 = 100
        v2 = 127
        A=5
        return

        sub bla () {
        A=99
        }


    }
}
