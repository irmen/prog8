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

        sub bla () {
            A=99
            bla2()


            sub bla2 () {
                A=100
                foo.ding()
                foo.ding2()
            }

        }


    }
}

foo {
    ubyte derp=99
    sub ding() {
        A=derp
    }
    sub ding2() {
        A=derp
    }
}
