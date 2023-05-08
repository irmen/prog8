%import textio
%import string
%zeropage basicsafe

main {

    sub start() {
        str output_filename = "12345678\x00abcdefghij"
        void myprint2("hallo", &output_filename+string.length(output_filename))
;        ubyte length = string.length(output_filename)
;        txt.print_ub(length)
;        txt.nl()
;        txt.print_uw(&output_filename)
;        txt.nl()
;        output_filename[2]='!'
;        txt.print(output_filename)
;        txt.nl()
;
;        void string_copy(".prg", &output_filename + string.length(output_filename))
;        txt.print(output_filename)
    }

    sub myprint2(str source1, str source2) {
        txt.print(source1)
        txt.nl()
        txt.print(source2)
        txt.nl()
    }

    sub string_copy(str source, str target) -> ubyte {
        ; Copy a string to another, overwriting that one.
        ; Returns the length of the string that was copied.
        ; Often you don’t have to call this explicitly and can just write string1 = string2
        ; but this function is useful if you’re dealing with addresses for instance.
        txt.print("src=")
        txt.print(source)
        txt.nl()
        txt.print("target=")
        txt.print(target)
        txt.nl()
        ubyte ix
        repeat 5 {
            ubyte qq=source[ix]
            txt.print_ub(qq)
            target[ix]=qq
            txt.spc()
            if qq==0 {
                txt.nl()
                return ix
            }
            ix++
        }
    }
}

