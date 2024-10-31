%import textio
%import diskio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        txt.print_ub(exists_byte("test.prg"))
        txt.spc()
        txt.print_ub(exists_byte("doesntexist.xxx"))
        txt.nl()
        txt.print_bool(exists_bool("test.prg"))
        txt.spc()
        txt.print_bool(exists_bool("doesntexist.xxx"))
        txt.nl()
        txt.print_bool(exists1("test.prg"))
        txt.spc()
        txt.print_bool(exists1("doesntexist.xxx"))
        txt.nl()
        txt.print_bool(exists2("test.prg"))
        txt.spc()
        txt.print_bool(exists2("doesntexist.xxx"))
        txt.nl()
    }

    sub exists1(str filename) -> bool {
        ; -- returns true if the given file exists on the disk, otherwise false
        if exists_byte(filename)!=0 {
            return true
        }
        return false
    }

    sub exists2(str filename) -> bool {
        ; -- returns true if the given file exists on the disk, otherwise false
        if exists_bool(filename) {
            return true
        }
        return false
    }


    sub exists_bool(str name) -> bool {
        %ir {{
            loadm.w r65535,main.exists_bool.name
            syscall 52 (r65535.w): r0.b
            returnr.b r0
        }}
    }

    sub exists_byte(str name) -> ubyte {
        %ir {{
            loadm.w r65535,main.exists_byte.name
            syscall 52 (r65535.w): r0.b
            returnr.b r0
        }}
    }
}

