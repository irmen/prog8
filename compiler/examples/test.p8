%option enable_floats



~ main {

sub start() {

    ; word [20] prime_numbers = [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71]   ; todo array_w!!!
    word [20] fibonacci_numbers = [0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181]

    for word fibnr in fibonacci_numbers {
        _vm_write_num(fibnr)
        _vm_write_char('\n')
    }
}

}
