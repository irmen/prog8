%import c64utils
%option enable_floats


~ main {

    sub start() {
        ubyte i=0

        vm_write_str("sin 0 ")
        i=0
        vm_write_num(sin8(0))
        vm_write_char(':')
        vm_write_num(sin8(i))
        vm_write_char('\n')

        vm_write_str("sin 50 ")
        i=50
        vm_write_num(sin8(50))
        vm_write_char(':')
        vm_write_num(sin8(i))
        vm_write_char('\n')

        vm_write_str("sin 128 ")
        i=128
        vm_write_num(sin8(128))
        vm_write_char(':')
        vm_write_num(sin8(i))
        vm_write_char('\n')

        vm_write_str("sin 140 ")
        i=140
        vm_write_num(sin8(140))
        vm_write_char(':')
        vm_write_num(sin8(i))
        vm_write_char('\n')

        vm_write_str("sin 250 ")
        i=250
        vm_write_num(sin8(250))
        vm_write_char(':')
        vm_write_num(sin8(i))
        vm_write_char('\n')

        vm_write_str("cos 0 ")
        i=0
        vm_write_num(cos8(0))
        vm_write_char(':')
        vm_write_num(cos8(i))
        vm_write_char('\n')

        vm_write_str("cos 50 ")
        i=50
        vm_write_num(cos8(50))
        vm_write_char(':')
        vm_write_num(cos8(i))
        vm_write_char('\n')

        vm_write_str("cos 128 ")
        i=128
        vm_write_num(cos8(128))
        vm_write_char(':')
        vm_write_num(cos8(i))
        vm_write_char('\n')

        vm_write_str("cos 140 ")
        i=140
        vm_write_num(cos8(140))
        vm_write_char(':')
        vm_write_num(cos8(i))
        vm_write_char('\n')

        vm_write_str("cos 250 ")
        i=250
        vm_write_num(cos8(250))
        vm_write_char(':')
        vm_write_num(cos8(i))
        vm_write_char('\n')
        vm_write_char('\n')

        vm_write_str("sin16 0 ")
        i=0
        vm_write_num(sin16(0))
        vm_write_char(':')
        vm_write_num(sin16(i))
        vm_write_char('\n')

        vm_write_str("sin16 50 ")
        i=50
        vm_write_num(sin16(50))
        vm_write_char(':')
        vm_write_num(sin16(i))
        vm_write_char('\n')

        vm_write_str("sin16 128 ")
        i=128
        vm_write_num(sin16(128))
        vm_write_char(':')
        vm_write_num(sin16(i))
        vm_write_char('\n')

        vm_write_str("sin16 140 ")
        i=140
        vm_write_num(sin16(140))
        vm_write_char(':')
        vm_write_num(sin16(i))
        vm_write_char('\n')

        vm_write_str("sin16 250 ")
        i=250
        vm_write_num(sin16(250))
        vm_write_char(':')
        vm_write_num(sin16(i))
        vm_write_char('\n')

        vm_write_str("cos16 0 ")
        i=0
        vm_write_num(cos16(0))
        vm_write_char(':')
        vm_write_num(cos16(i))
        vm_write_char('\n')

        vm_write_str("cos16 50 ")
        i=50
        vm_write_num(cos16(50))
        vm_write_char(':')
        vm_write_num(cos16(i))
        vm_write_char('\n')

        vm_write_str("cos16 128 ")
        i=128
        vm_write_num(cos16(128))
        vm_write_char(':')
        vm_write_num(cos16(i))
        vm_write_char('\n')

        vm_write_str("cos16 140 ")
        i=140
        vm_write_num(cos16(140))
        vm_write_char(':')
        vm_write_num(cos16(i))
        vm_write_char('\n')

        vm_write_str("cos16 250 ")
        i=250
        vm_write_num(cos16(250))
        vm_write_char(':')
        vm_write_num(cos16(i))
        vm_write_char('\n')

    }
}
