%import c64utils
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        float fl
        ubyte ub

        when A+Y {
            fl -> A=2
            fl+3.3 -> A=3
            ub -> A=4
            ub+2 -> A=5
            4 -> {
                Y=7
            }
            else -> A=99
            5 -> Y=5        ; @todo error; else already seen
        }
    }

}
