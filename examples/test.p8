%import textio
%zeropage basicsafe
%option no_sysinit, romable

main $9000 {
    sub start() {
        ubyte[] filled_array = [11,22,33,44]
        ubyte[10] empty_array

        uword block = memory("block", 100, 0)
        sys.memset(block, 100, 0)
        str name = "irmen"
        ubyte @shared number
        txt.print(name)
        txt.spc()
        number++
        txt.print_ub(number)
        txt.spc()
        number++
        txt.print_ub(number)
        txt.nl()
        txt.print_ub(block[10])
        txt.spc()
        block[10]++
        txt.print_ub(block[10])
        txt.nl()

        txt.print_ub(filled_array[2])
        txt.spc()
        ;;empty_array[2]=0        ; TODO should not give error!
        txt.print_ub(empty_array[2])
        txt.spc()
        ;;empty_array[2]++        ; TODO should not give error!
        txt.print_ub(empty_array[2])
        txt.nl()

    }
}
