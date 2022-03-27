%import textio

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        txt.clear_screen()
        txt.print("Welcome to a prog8 pixel shader :-)\n")

        uword @shared chunk = memory("irmen", 4000, 256)
        txt.print_uwhex(chunk,true)
        txt.nl()

        ubyte bb = 4
        ubyte[] array = [1,2,3,4,5,6]
        uword[] warray = [1111,2222,3333]
        str tekst = "test"
        uword ww = 19
        bb = bb in "teststring"
        bb++
        bb = bb in [1,2,3,4,5,6]
        bb++
        bb = bb in array
        bb++
        bb = bb in tekst
        bb++
        bb = ww in warray
        bb++
        bb = 666 in warray
        bb ++
        bb = '?' in tekst
        bb++
        txt.print("bb=")
        txt.print_ub(bb)
        txt.nl()
        sys.exit(99)


        syscall1(8, 0)      ; enable lo res creen
        ubyte shifter

        shifter >>= 1

        repeat {
            uword xx
            uword yy = 0
            repeat 240 {
                xx = 0
                repeat 320 {
                    syscall3(10, xx, yy, xx*yy + shifter)   ; plot pixel
                    xx++
                }
                yy++
            }
            shifter+=4

            txt.print_ub(shifter)
            txt.nl()
        }
    }
}
