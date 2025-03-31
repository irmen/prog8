%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {

    sub start()  {
        str name = "irmen"      ; there a re no memory-mepped strings.
        ubyte[] array = [11,22,33,44]
        &ubyte[20] marray = $2000
        uword @shared pointer = $4000

        sys.memset($2000, 20, 55)

        txt.print(name)
        txt.spc()
        txt.print_ub(array[1])
        txt.spc()
        txt.print_ub(marray[1])
        txt.nl()

        name[2] = '!'
        marray[1] = '!'     ; TODO should be allowed because memory mapped to non-rom area
        array[1], marray[1] = multi()

        cx16.r0L=1
        pointer[cx16.r0L] = 99

        txt.print(name)
        txt.spc()
        txt.print_ub(array[1])
        txt.spc()
        txt.print_ub(marray[1])
        txt.nl()
    }

    sub multi() -> ubyte, ubyte {
        cx16.r0++
        return 65,66
    }
}
