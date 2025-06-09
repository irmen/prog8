%import floats
%import textio

main {
    struct Node {
        bool bb
        float f
        word w
        ^^Node next
    }

    sub start() {
        ^^Node n1 = Node(false, 1.1, 1111, 0)

        ^^Node[5] node_array
        node_array [0] = node_array[1] = node_array[2] = node_array[3] = node_array[4] = n1
        bool[5] bool_array = [true, true, true, false, false]
        word[5] @nosplit word_array = [-1111,-2222,3333,4444,5555]
        float[5] float_array = [111.111,222.222,333.333,444.444,555.555]

        txt.print_bool(bool_array[2])
        txt.spc()
        txt.print_w(word_array[2])
        txt.spc()
        txt.print_f(float_array[2])
        txt.nl()

        modifyb(bool_array, 2)
        modifyw(word_array, 2)
        modifyf(float_array, 2)

        txt.print_bool(bool_array[2])
        txt.spc()
        txt.print_w(word_array[2])
        txt.spc()
        txt.print_f(float_array[2])
        txt.nl()
    }

    sub modifyb(^^bool array, ubyte index) {
        array[index] = false
    }

    sub modifyw(^^word array, ubyte index) {
        array[index] = 9999
    }

    sub modifyf(^^float array, ubyte index) {
        array[index] = 9999.999
    }
}
