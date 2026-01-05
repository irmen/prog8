%import strings
%import textio
%zeropage basicsafe

main {
    sub start() {
        word zz

        zz = 10
        txt.print_w(zz)
        txt.nl()
        compares()
        sgns()
        txt.nl()

        zz= -10
        txt.print_w(zz)
        txt.nl()
        compares()
        sgns()
        txt.nl()

        zz=0
        txt.print_w(zz)
        txt.nl()
        compares()
        sgns()
        txt.nl()

        sub compares() {
            if zz>0
                txt.print(">0\n")
            if zz>=0
                txt.print(">=0\n")
            if zz<0
                txt.print("<0\n")
            if zz<=0
                txt.print("<=0\n")
            if zz==0
                txt.print("==0\n")
            if zz!=0
                txt.print("!=0\n")
        }

        sub sgns() {
            if sgn(zz)>0
                txt.print(">0\n")
            if sgn(zz)>=0
                txt.print(">=0\n")
            if sgn(zz)<0
                txt.print("<0\n")
            if sgn(zz)<=0
                txt.print("<=0\n")
            if sgn(zz)==0
                txt.print("==0\n")
            if sgn(zz)!=0
                txt.print("!=0\n")
        }
    }
}
