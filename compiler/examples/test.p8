%import c64utils

~ main {

    sub start()  {

        for ubyte j in 0 to 7 step 2 {      ; @todo wrong bytecode generated!!
            vm_write_num(j)
            vm_write_char('\n')
            ;c64scr.print_ub(j)
            ;c64.CHROUT('\n')
        }

        for ubyte j in 10 to 3 step -2 {      ; @todo wrong bytecode generated!!
            vm_write_num(j)
            vm_write_char('\n')
            ;c64scr.print_ub(j)
            ;c64.CHROUT('\n')
        }
    }
}
