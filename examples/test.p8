%import textio ; txt.*
%zeropage basicsafe
main {

    sub start() {

        ubyte @shared xx=99
        uword @shared asmvar
        %asm {{
            inc  xx
            lda  asmvar
            ldy  asmvar+1
        }}
        txt.nl()

        str string1 = "stringvalue\n"
        str string2 = "stringvalue\n"
        str string3 = "stringvalue\n"
        str string4 = "a"
        str string5 = "bb"

        txt.print(string1)
        txt.print(string2)
        txt.print(string3)
        string1[1]='?'
        string2[2] = '?'
        string3[3] = '?'
        txt.print(string1)
        txt.print(string2)
        txt.print(string3)

        txt.print("a")
        txt.print("a")
        txt.print(string4)
        txt.print("bb")
        txt.print("bb")
        txt.print(string5)
        txt.print("\n")
        txt.print("\n\n")
        txt.print("hello\n")
        txt.print("hello\n")
        txt.print("hello\n")
        txt.print("bye\n")
        txt.print("bye\n")
        txt.print("bye\n")

    }
}
