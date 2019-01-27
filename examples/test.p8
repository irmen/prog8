%import c64utils

~ main {

    sub start() {
        while true {
            A=99
        }

        while true {
            A=99
            if A>100
                break
        }

        repeat {
            A=99
        } until false

        repeat {
            A=99
            if A>100
              break
        } until false
    }


    ; @todo code for pow()

    ; @todo optimize code generation for "if blah ..." and "if not blah ..."

    ; @todo optimize vm
    ;        push_byte  ub:01
    ;        jnz  _prog8stmt_7_loop
}
