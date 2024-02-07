%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name1 = ""
        str name2 = "hello"
        str name3 = "  \n\rhello"
        str name4 = "  \x02\x02\x02\n\r\xa0\xa0\xff\xffhello"

        txt.chrout('[')
        txt.print(string.ltrimmed(name1))
        txt.print("]\n")
        txt.chrout('[')
        txt.print(string.ltrimmed(name2))
        txt.print("]\n")
        txt.chrout('[')
        txt.print(string.ltrimmed(name3))
        txt.print("]\n")
        txt.chrout('[')
        txt.print(string.ltrimmed(name4))
        txt.print("]\n\n")

        txt.chrout('[')
        txt.print(string.lstripped(name1))
        txt.print("]\n")
        txt.chrout('[')
        txt.print(string.lstripped(name2))
        txt.print("]\n")
        txt.chrout('[')
        txt.print(string.lstripped(name3))
        txt.print("]\n")
        txt.chrout('[')
        txt.print(string.lstripped(name4))
        txt.print("]\n")

        ; foo(name2)
    }

    sub foo (str s2) {
        str s = "irmen"
        txt.print_uwhex(s, true)
        txt.nl()
        txt.print_uwhex(&s, true)
        txt.nl()
        txt.print_uwhex(&s[2], true)    ; TODO doesn't print correctly in the AST!
        txt.nl()
        txt.nl()
        txt.print_uwhex(s2, true)
        txt.nl()
        txt.print_uwhex(&s2, true)
        txt.nl()
        txt.print_uwhex(s2+2, true)
        txt.nl()
        txt.print_uwhex(&s2[2], true)   ; TODO should be the same as the previous one!  TODO doesn't print correctly in the AST!
        txt.nl()
    }
}
