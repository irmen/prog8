%import textio

main {
    sub start() {
        ^^uword ptr = 2000
        pokew(2000, 1111)

        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ += 5
        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ -= 9
        txt.print_uw(ptr^^)
        txt.nl()
;        ptr^^ *= 5
;        txt.print_uw(ptr^^)
;        txt.nl()

;        const uword buffer = $2000
;        uword @shared addr = &buffer[2]
;
;        const ubyte width = 100
;        ubyte @shared i
;        ubyte @shared j
;        uword @shared addr2 = &buffer[i * width + j]
;        txt.print_uw(addr)
    }
}

/*
main {
    sub start() {
        readbyte(&thing.name)          ; ok
        readbyte(&thing.name[1])       ; ok
        readbyte(&thing.array)         ; ok
        cx16.r0 = &thing.array[1]       ; TODO with typed &: fix error, register multiple types
        readbyte(&thing.array[1])      ; TODO with typed &: fix error, register multiple types
    }

    sub readbyte(uword @requirezp ptr) {
        thing.printpointer()
        txt.spc()
        txt.print_uw(ptr)
        txt.nl()
        ptr=0
    }
}

thing {
    str name = "error"
    ubyte[10] array

    sub printpointer() {
        txt.print("&name=")
        txt.print_uw(&name)
        txt.print(" &array=")
        txt.print_uw(&array)
        txt.nl()
    }
}
*/
