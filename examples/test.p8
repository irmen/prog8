%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword wv = %01000010_01000010

        ; expected:  $8484  $4242
        sys.set_carry()
        rol(wv)
        txt.print_uwhex(wv, true)
        sys.set_carry()
        ror(wv)
        txt.print_uwhex(wv, true)
        txt.nl()

        wv = %01000010_01000010
        sys.set_carry()
        rol2(wv)
        txt.print_uwhex(wv, true)
        sys.set_carry()
        ror2(wv)
        txt.print_uwhex(wv, true)
        txt.nl()

        uword[] @nosplit arr = [ %01000010_01000010 , %01000010_01000010, %01000010_01000010 ]
        uword[] @split arrsplit = [ %01000010_01000010 , %01000010_01000010, %01000010_01000010 ]

        sys.set_carry()
        rol(arr[2])
        txt.print_uwhex(arr[2], true)
        sys.set_carry()
        ror(arr[2])
        txt.print_uwhex(arr[2], true)
        txt.nl()
        sys.set_carry()
        rol2(arr[1])
        txt.print_uwhex(arr[1], true)
        sys.set_carry()
        ror2(arr[1])
        txt.print_uwhex(arr[1], true)
        txt.nl()

        sys.set_carry()
        rol(arrsplit[2])
        txt.print_uwhex(arrsplit[2], true)
        sys.set_carry()
        ror(arrsplit[2])
        txt.print_uwhex(arrsplit[2], true)
        txt.nl()
        sys.set_carry()
        rol2(arrsplit[1])
        txt.print_uwhex(arrsplit[1], true)
        sys.set_carry()
        ror2(arrsplit[1])
        txt.print_uwhex(arrsplit[1], true)
        txt.nl()


        ; expected $2468  $1234
        arr[2] = $1234
        arr[2] <<= 1
        txt.print_uwhex(arr[2], true)
        arr[2] >>= 1
        txt.print_uwhex(arr[2], true)
        txt.nl()
        arrsplit[2] = $1234
        arrsplit[2] <<= 1
        txt.print_uwhex(arrsplit[2], true)
        arrsplit[2] >>= 1
        txt.print_uwhex(arrsplit[2], true)
        txt.nl()
    }
}
