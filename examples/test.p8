%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str namestring = petscii:"The Quick Brown Fox Jumps Over The Lazy Dog\n0123456789!#$%^&*()-=_+[]{};:'<>,./?\n"
        str namestring2 = petscii:"The Quick Brown Fox Jumps Over The Lazy Dog\n0123456789!#$%^&*()-=_+[]{};:'<>,./?\n"
        txt.petscii2scr_str(namestring2)
        for cx16.r0L in 0 to len(namestring) {
            txt.print_ubhex(namestring[cx16.r0L], false)
            txt.spc()
            txt.print_ubhex(namestring2[cx16.r0L], false)
            txt.nl()
        }
        txt.nl()
        sys.exit(1)

        str name1 = ""
        str name2 = "hello \r\n"
        str name3 = "  \n\rhello"
        str name4 = "  \n\r\xa0\xa0\xff\xffhello\x02\x02\x02  \n  "

        foo(name2)
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
