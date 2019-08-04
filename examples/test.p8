%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    sub start() {
        memset(c64.Screen, 40, 1)
        memset(c64.Screen+40, 80, 2)
    }
}
