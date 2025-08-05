%option enable_floats

main {
    ^^float g_floats

    sub start() {
        ^^float l_floats

        f_add()
        f_sub()
        f_mul()
        f_div()

        sub f_add() {
            l_floats^^ += 3.0
            g_floats^^ += 3.0
            other.g_floats^^ += 3.0
            other.func.l_floats^^ += 3.0
        }

        sub f_sub() {
            l_floats^^ -= 3.0
            g_floats^^ -= 3.0
            other.g_floats^^ -= 3.0
            other.func.l_floats^^ -= 3.0
        }

        sub f_mul() {
            l_floats^^ *= 3.0
            g_floats^^ *= 3.0
            other.g_floats^^ *= 3.0
            other.func.l_floats^^ *= 3.0
        }

        sub f_div() {
            l_floats^^ /= 3.0
            g_floats^^ /= 3.0
            other.g_floats^^ /= 3.0
            other.func.l_floats^^ /= 3.0
        }
    }
}

other {
    %option force_output

    ^^float g_floats

    sub func() {
        ^^float l_floats
    }
}
