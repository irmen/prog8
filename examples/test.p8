%import textio
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        str anim = "1234"
        ubyte anim_counter = 0

        test_stack.test()

        txt.print("loading ")
        repeat 100 {
            ubyte qq = anim[anim_counter/2]
            txt.chrout(qq)
            anim_counter = (anim_counter+1) & 7
        }
        txt.print("done!\n")

        test_stack.test()
    }
}

