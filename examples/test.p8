%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str s = "?"
        s[0] = 's'
        txt.print(s)
        txt.nl()

        if 's' in s {
            txt.print("ok1\n")
        } else {
            txt.print("fail1\n")
        }

        void string.find(s, 's')
        if_cs {
            txt.print("ok2\n")
        } else {
            txt.print("fail2\n")
        }

        if string.contains(s, 's') {
            txt.print("ok3\n")
        } else {
            txt.print("fail3\n")
        }

        if 'q' in s {
            txt.print("ok1\n")
        } else {
            txt.print("fail1\n")
        }

        void string.find(s, 'q')
        if_cs {
            txt.print("ok2\n")
        } else {
            txt.print("fail2\n")
        }

        if string.contains(s, 'q') {
            txt.print("ok3\n")
        } else {
            txt.print("fail3\n")
        }

        str buffer="?" * 20
        str name = "irmen de jong"
        string.left(name, 5, buffer)
        txt.print(buffer)
        txt.nl()
        string.right(name, 4, buffer)
        txt.print(buffer)
        txt.nl()

    }
}
