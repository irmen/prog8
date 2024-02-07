%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name1 = ""
        str name2 = "hello \r\n"
        str name3 = "  \n\rhello"
        str name4 = "  \n\r\xa0\xa0\xff\xffhello\x02\x02\x02  \n  "

        txt.print("strip:\n")
        string.strip(name1)
        txt.chrout('[')
        txt.print(name1)
        txt.print("]\n")
        string.strip(name2)
        txt.chrout('[')
        txt.print(name2)
        txt.print("]\n")
        string.strip(name3)
        txt.chrout('[')
        txt.print(name3)
        txt.print("]\n")
        string.strip(name4)
        txt.chrout('[')
        txt.print(name4)
        txt.print("]\n")

        str tname1 = ""
        str tname2 = "hello \r\n"
        str tname3 = "  \n\r\x09hello"
        str tname4 = "  \n\x09\x0b\r\xa0\xa0\xff\xffhello\x05\x05\x05  \n  "

        txt.print("trim:\n")
        string.trim(tname1)
        txt.chrout('[')
        txt.print(tname1)
        txt.print("]\n")
        string.trim(tname2)
        txt.chrout('[')
        txt.print(tname2)
        txt.print("]\n")
        string.trim(tname3)
        txt.chrout('[')
        txt.print(tname3)
        txt.print("]\n")
        string.trim(tname4)
        txt.chrout('[')
        txt.print(tname4)
        txt.print("]\n")
    }
}
