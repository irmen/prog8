%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str s1 = "irmen@razorvine.net"

        ubyte qq = '@'

        if qq in s1
            txt.print("good1\n")
        else
            txt.print("error1\n")

        if 'r' in s1
            txt.print("good2\n")
        else
            txt.print("error2\n")

        if qq in "irmen22@razorvine.netirmen22@razorvine.netirmen22@razorvine.netirmen22@razorvine.net"
            txt.print("good3\n")
        else
            txt.print("error3\n")

        qq = 2
        if qq in [1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2]
            txt.print("good4\n")
        else
            txt.print("error4\n")

        qq='z'
        if qq in "irm@razo"
            txt.print("good5\n")
        else
            txt.print("error5\n")

        uword zz = 2222
        if zz in [1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2222,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2]
            txt.print("good6\n")
        else
            txt.print("error6\n")

        ; str s1, ubyte ff,  txt.print_uwhex(s1+ff, true)        ; TODO fix compiler crash on s1+ff.   why no crash when using 1-argument functioncall?
    }
}
