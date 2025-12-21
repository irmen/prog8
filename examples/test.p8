%import textio
%option no_sysinit

; C64:  160, 220, 164
; C128: 167, 229, 171

main {
    sub start() {
        ubyte char=0
        ubyte x,y
        str name = sc:"irmen"
        long setcc_time, setchr_time, setclr_time

        for x in 0 to 4
            txt.setcc(x+20, 19, name[x], x)

        txt.print("9 0 18 1 13 2 5 3 14 4\n")
        for x in 0 to 4 {
            txt.print_ub(txt.getchr(x+20, 19))
            txt.spc()
            txt.print_ub(txt.getclr(x+20, 19) & 15)
            txt.spc()
        }

        sys.wait(200)

        cbm.SETTIML(0)
        setchr()
        setchr_time = cbm.RDTIML()
        sys.wait(60)
        txt.cls()

        char = 0
        cbm.SETTIML(0)
        setcc()
        setcc_time = cbm.RDTIML()
        sys.wait(60)

        cbm.SETTIM(0,0,0)
        setclr()
        setclr_time = cbm.RDTIML()
        sys.wait(60)
        txt.cls()

        txt.print("setchr: ")
        txt.print_l(setchr_time)
        txt.print("\n setcc: ")
        txt.print_l(setcc_time)
        txt.print("\nsetclr: ")
        txt.print_l(setclr_time)
        txt.nl()
        repeat {}

        sub setchr() {
            repeat 30 {
                ubyte left = 0
                ubyte top = 0
                ubyte right = txt.width()-1
                ubyte bottom = txt.height()-1

                while top<bottom {
                    for x in left to right
                        txt.setchr(x,top,char)
                    for y in top to bottom
                        txt.setchr(right,y,char)
                    for x in right downto left
                        txt.setchr(x,bottom,char)
                    for y in bottom downto top
                        txt.setchr(left,y,char)
                    char++
                    left++
                    top++
                    right--
                    bottom--
                }
            }
        }

        sub setcc() {
            repeat 30 {
                ubyte left = 0
                ubyte top = 0
                ubyte right = txt.width()-1
                ubyte bottom = txt.height()-1

                while top<bottom {
                    for x in left to right
                        txt.setcc(x,top,char,5)
                    for y in top to bottom
                        txt.setcc(right,y,char,3)
                    for x in right downto left
                        txt.setcc(x,bottom,char,7)
                    for y in bottom downto top
                        txt.setcc(left,y,char,4)
                    char++
                    left++
                    top++
                    right--
                    bottom--
                }
            }
        }

        sub setclr() {
            repeat 30 {
                ubyte left = 0
                ubyte top = 0
                ubyte right = txt.width()-1
                ubyte bottom = txt.height()-1

                while top<bottom {
                    for x in left to right
                        txt.setclr(x,top,char)
                    for y in top to bottom
                        txt.setclr(right,y,char)
                    for x in right downto left
                        txt.setclr(x,bottom,char)
                    for y in bottom downto top
                        txt.setclr(left,y,char)
                    char++
                    left++
                    top++
                    right--
                    bottom--
                }
            }
        }
    }
}
