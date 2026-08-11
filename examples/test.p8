%import floats
%import textio
%zeropage basicsafe

main {
    ubyte @shared fails

    sub check(str name, float actual, float expected) {
        float diff = actual - expected
        bool passed = true
        if diff < -0.0001
            passed = false
        if diff > 0.0001
            passed = false

        txt.print(name)
        txt.print(" actual=")
        txt.print_f(actual)
        txt.print(" expected=")
        txt.print_f(expected)
        if not passed {
            txt.print(" fail")
            fails++
        }
        else
            txt.print(" pass")
        txt.nl()
    }

    sub start() {
        fails = 0

        check("sin 0", floats.sin(0.0), 0.0)
        check("sin pi/2", floats.sin(floats.π / 2.0), 1.0)
        check("cos 0", floats.cos(0.0), 1.0)
        check("cos pi", floats.cos(floats.π), -1.0)
        check("tan 0", floats.tan(0.0), 0.0)
        check("atan 1", floats.atan(1.0), 0.785398)
        check("atan2 1,1", floats.atan2(1.0, 1.0), 0.785398)
        check("ln 1", floats.ln(1.0), 0.0)
        check("log2 8", floats.log2(8.0), 3.0)
        check("sqrt 9", sqrt(9.0), 3.0)
        check("pow 2^8", floats.pow(2.0, 8.0), 256.0)
        check("round 2.6", floats.round(2.6), 3.0)
        check("floor 2.9", floats.floor(2.9), 2.0)
        check("ceil 2.1", floats.ceil(2.1), 3.0)
        check("secant 0", floats.secant(0.0), 1.0)
        check("csc pi/2", floats.csc(floats.π / 2.0), 1.0)
        check("cot pi/4", floats.cot(floats.π / 4.0), 1.0)
        check("rad 180", floats.rad(180.0), floats.π)
        check("deg pi", floats.deg(floats.π), 180.0)
        check("minf", floats.minf(2.0, 3.0), 2.0)
        check("maxf", floats.maxf(2.0, 3.0), 3.0)
        check("clampf", floats.clampf(4.0, 0.0, 3.0), 3.0)
        check("mod 7/3", floats.mod(7.0, 3.0), 1.0)
        check("lerp", floats.lerp(10.0, 20.0, 0.25), 12.5)
        check("interpolate", floats.interpolate(5.0, 0.0, 10.0, 0.0, 100.0), 50.0)
        check("parse", floats.parse("12.5"), 12.5)
        check("normalize", floats.normalize(1.25), 1.25)

        txt.print("fails = ")
        txt.print_ub(fails)
        txt.nl()
        sys.exit(fails)
    }
}
