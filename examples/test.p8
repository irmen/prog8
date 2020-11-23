%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        float fl = 2.0
        float cf = 1.5

        if fl == cf+0.5
            txt.print(".\n")
        else
            txt.print("!\n")

        if fl != cf+0.5
            txt.print("!\n")
        else
            txt.print(".\n")

        if fl < cf+0.5
            txt.print("!\n")
        else
            txt.print(".\n")

        if fl <= cf+0.5
            txt.print(".\n")
        else
            txt.print("!\n")

        if fl > cf+0.5
            txt.print("!\n")
        else
            txt.print(".\n")

        if fl >= cf+0.5
            txt.print(".\n")
        else
            txt.print("!\n")

    }

    sub func(float fa) -> float {
        fa = fa*99.0
        return fa + 1.0
    }
}
