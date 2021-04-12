%import textio
%zeropage basicsafe

main {
    sub start()  {
        ubyte xx=9
        ubyte yy=9

        ; ubyte c_xx = xx+1

        if xx+1 >= 10 {
            txt.print("yes")
        } else {
            txt.print("error!")
        }
        if yy+1 >= 10 {
            txt.print("yes2")
        } else {
            txt.print("error2!")
        }

        if xx+1 >= xx-2 {
            txt.print("yes")
        } else {
            txt.print("error!")
        }
        if yy+1 >= yy-2 {
            txt.print("yes2")
        } else {
            txt.print("error2!")
        }
    }
}
