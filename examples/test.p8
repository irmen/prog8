%option enable_floats

main {
    ^^bool g_bp
    ^^word g_bw
    ^^float g_floats

    sub start() {
        ^^bool l_bp
        ^^word l_bw
        ^^float l_floats

        assign_pointers()
        assign_values()
        assign_inplace()
        assign_deref()
        assign_uwords()
        assign_same_ptrs()
        assign_different_ptrs()

        sub assign_pointers() {
            cx16.r0 = l_bp
            cx16.r1 = l_bw
            cx16.r2 = l_floats
            cx16.r0 = g_bp
            cx16.r1 = g_bw
            cx16.r2 = g_floats
            cx16.r0 = other.g_bp
            cx16.r1 = other.g_bw
            cx16.r2 = other.g_floats
            cx16.r0 = other.func.l_bp
            cx16.r1 = other.func.l_bw
            cx16.r2 = other.func.l_floats
        }

        sub assign_deref() {
            float f
            bool b
            word w
            b = l_bp^^
            w = l_bw^^
            f = l_floats^^
            b = g_bp^^
            w = g_bw^^
            f = g_floats^^
            b = other.g_bp^^
            w = other.g_bw^^
            f = other.g_floats^^
            b = other.func.l_bp^^
            w = other.func.l_bw^^
            f = other.func.l_floats^^
        }

        sub assign_values() {
            l_bp^^ = true
            l_bw^^ = -1234
            l_floats^^ = 5.678
            g_bp^^ = true
            g_bw^^ = -1234
            g_floats^^ = 5.678
            other.g_bp^^ = true
            other.g_bw^^ = -1234
            other.g_floats^^ = 5.678
            other.func.l_bp^^ = true
            other.func.l_bw^^ = -1234
            other.func.l_floats^^ = 5.678
        }

        sub assign_same_ptrs() {
            l_bp = g_bp
            l_bw = g_bw
            l_floats = g_floats
            g_bp = other.g_bp
            g_bw = other.g_bw
            g_floats = other.g_floats
            other.g_bp = other.func.l_bp
            other.g_bw = other.func.l_bw
            other.g_floats = other.func.l_floats
        }

        sub assign_different_ptrs() {
            l_bp = g_floats as ^^bool
            l_bw = g_floats as ^^word
            l_floats = g_bp as ^^float
            other.g_bp = l_floats as ^^bool
            other.g_bw = l_floats as ^^word
            other.g_floats = l_bp as ^^float
        }

        sub assign_inplace() {
            bool b
            l_bp^^ = l_bp^^ xor b
            l_bw^^ += -1234
            l_floats^^ += 5.678
            g_bp^^ = g_bp^^ xor b
            g_bw^^ += -1234
            g_floats^^ += 5.678
            other.g_bp^^ = other.g_bp^^ xor b
            other.g_bw^^ += -1234
            other.g_floats^^ += 5.678
            other.func.l_bp^^ = other.func.l_bp^^ xor b
            other.func.l_bw^^ += -1234
            other.func.l_floats^^ += 5.678

            l_bw^^ /= 3
            l_floats^^ /= 3.0
            g_bw^^ /= 3
            g_floats^^ /= 3.0
            other.g_bw^^ /= 3
            other.g_floats^^ /= 3.0
            other.func.l_bw^^ /= 3
            other.func.l_floats^^ /= 3.0
        }

        sub assign_uwords() {
            l_bp = $9000
            l_bw = $9000
            l_floats = $9000
            g_bp = $9000
            g_bw = $9000
            g_floats = $9000
            other.g_bp = $9000
            other.g_bw = $9000
            other.g_floats = $9000
            other.func.l_bp = $9000
            other.func.l_bw = $9000
            other.func.l_floats = $9000
        }
    }
}

other {
    ^^bool g_bp
    ^^word g_bw
    ^^float g_floats

    sub func() {
        ^^bool l_bp
        ^^word l_bw
        ^^float l_floats
    }
}
