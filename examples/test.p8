%import textio
%import floats
%import math

main {
    sub start() {
        bool bb1 = true
        bool bb2 = false
        ubyte ub1 = 11
        ubyte ub2 = 22
        uword uw1 = 1111
        uword uw2 = 2222
        long l1 = 1111111
        long l2 = 2222222
        float f1 = 1.111
        float f2 = 2.222
        ^^long ptr1 = 1111
        ^^long ptr2 = 2222
        word[2] words = [-1111,-2222]
        word[2] @nosplit words2 = [-1111,-2222]
        cx16.r6 = 1111
        cx16.r7 = 2222
        cx16.r4L = 11
        cx16.r4H = 22

        ^^uword uwptr1 = &cx16.r6
        ^^uword uwptr2 = &cx16.r7
        ^^byte bptr1 = &cx16.r4L
        ^^byte bptr2 = &cx16.r4H

        swap(bb1, bb2)
        swap(ub1, ub2)
        swap(uw1, uw2)
        swap(l1, l2)
        swap(f1, f2)
        swap(ptr1, ptr2)
        swap(bptr1^^, bptr2^^)
        swap(uwptr1^^, uwptr2^^)
        cx16.r9L = 0
        cx16.r10 = 1
        ;TODO implement array element swap: swap(words[cx16.r9L], words[cx16.r10L])
        ;TODO implement array element swap: swap(words2[cx16.r9L], words2[cx16.r10L])

        txt.print_bool(bb1)
        txt.spc()
        txt.print_bool(bb2)
        txt.nl()

        txt.print_ub(ub1)
        txt.spc()
        txt.print_ub(ub2)
        txt.nl()

        txt.print_ub(cx16.r4L)
        txt.spc()
        txt.print_ub(cx16.r4H)
        txt.nl()

        txt.print_uw(uw1)
        txt.spc()
        txt.print_uw(uw2)
        txt.nl()

        txt.print_uw(ptr1)
        txt.spc()
        txt.print_uw(ptr2)
        txt.nl()

        txt.print_uw(cx16.r6)
        txt.spc()
        txt.print_uw(cx16.r7)
        txt.nl()

; TODO implement array swap
;        txt.print_w(words[0])
;        txt.spc()
;        txt.print_w(words[1])
;        txt.nl()
;
;        txt.print_w(words2[0])
;        txt.spc()
;        txt.print_w(words2[1])
;        txt.nl()

        txt.print_l(l1)
        txt.spc()
        txt.print_l(l2)
        txt.nl()

        txt.print_f(f1)
        txt.spc()
        txt.print_f(f2)
        txt.nl()


        repeat {
        }
    }
}
