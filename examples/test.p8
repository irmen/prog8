%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {
    sub start() {
        cx16.r0 = $2200
        float @shared fl = 1123.56

        if fl as bool
            txt.print("yep ")
        else
            txt.print("nope ")

        bool @shared qq = fl as bool
        if qq
            txt.print("yep ")
        else
            txt.print("nope ")

        if cx16.r0 as bool
            txt.print("yep ")
        else
            txt.print("nope ")

        qq = cx16.r0 as bool
        if qq
            txt.print("yep ")
        else
            txt.print("nope ")

        if cx16.r0s as bool
            txt.print("yep ")
        else
            txt.print("nope ")

        qq = cx16.r0s as bool
        if qq
            txt.print("yep ")
        else
            txt.print("nope ")

;        test_stack.test()
;        broken_word_gt()
;        broken_word_lt()
;        broken_uword_gt()
;        broken_uword_lt()
;
;        test_bool()
;        test_float()
;        test_byte()
;        test_ubyte()
;        test_word()
;        test_uword()
;
;        test_stack.test()

        if sys.target!=255
            repeat { }
    }

    sub broken_word_gt() {
        txt.print("word >: ")
        cx16.r0s = 1111
        cx16.r1s = 2222

        if cx16.r0s > 1000
            goto oklbl
        txt.print("fail1 ")
        goto skip1
oklbl:
        txt.print("ok1 ")

skip1:
        cx16.r3 = &ok2lbl
        if cx16.r0s > 1000
            goto cx16.r3
        txt.print("fail2 ")
        goto skip2
ok2lbl:
        txt.print("ok2 ")

skip2:
        if cx16.r0s > 1000
            txt.print("ok3 ")

        if cx16.r0s > 1000
            txt.print("ok4 ")
        else
            txt.print("fail4 ")

        txt.nl()
    }

    sub broken_uword_gt() {
        txt.print("uword >: ")
        cx16.r0 = 1111
        cx16.r1 = 2222

        if cx16.r0 > 1000
            goto oklbl
        txt.print("fail1 ")
        goto skip1
oklbl:
        txt.print("ok1 ")

skip1:
        cx16.r3 = &ok2lbl
        if cx16.r0 > 1000
            goto cx16.r3
        txt.print("fail2 ")
        goto skip2
ok2lbl:
        txt.print("ok2 ")

skip2:
        if cx16.r0 > 1000
            txt.print("ok3 ")

        if cx16.r0 > 1000
            txt.print("ok4 ")
        else
            txt.print("fail4 ")

        txt.nl()
    }

    sub broken_word_lt() {
        txt.print("word <: ")
        cx16.r0s = -1999
        cx16.r1s = -1999

        if cx16.r0s < 1000
            goto oklbl
        txt.print("fail1 ")
        goto skip1
oklbl:
        txt.print("ok1 ")

skip1:
        cx16.r3 = &ok2lbl
        if cx16.r0s < 1000
            goto cx16.r3
        txt.print("fail2 ")
        goto skip2
ok2lbl:
        txt.print("ok2 ")

skip2:
        if cx16.r0s < 1000
            txt.print("ok3 ")

        if cx16.r0s < 1000
            txt.print("ok4 ")
        else
            txt.print("fail4 ")

        txt.nl()
    }

    sub broken_uword_lt() {
        txt.print("uword <: ")
        cx16.r0 = 999
        cx16.r1 = 999

        if cx16.r0 < 1000
            goto oklbl
        txt.print("fail1 ")
        goto skip1
oklbl:
        txt.print("ok1 ")

skip1:
        cx16.r3 = &ok2lbl
        if cx16.r0 < 1000
            goto cx16.r3
        txt.print("fail2 ")
        goto skip2
ok2lbl:
        txt.print("ok2 ")

skip2:
        if cx16.r0 < 1000
            txt.print("ok3 ")

        if cx16.r0 < 1000
            txt.print("ok4 ")
        else
            txt.print("fail4 ")

        txt.nl()
    }

    /*
    tests:
    - if with only a single goto, direct
    - if with only a single indirect goto
    - if without an else block
    - if with both if and else blocks
    carthesian product with:
    - comparison with const zero
    - comparison with const values
    - comparison with variable
    - comparison with array
    - comparison with expression
    */

    sub fail(uword test) {
        txt.print("failed ")
        txt.print_uw(test)
        txt.nl()
    }

    sub test_bool() {
        bool @shared var1, var2
        bool[2] barray = [false, true]
        ubyte success
        single_goto()
        single_goto_indirect()
        no_else()
        if_else()

        sub single_goto() {
            success=0
            txt.print("bool single_goto: ")
            var1 = true
            var2 = false
test1:
            if var2
                goto lbl1
            success++
            goto test2
lbl1:
            fail(1)

test2:
            if var1
                goto lbl2
            fail(2)
            goto test3
lbl2:       success++

test3:
            if var1 xor var2
                goto lbl3
            fail(3)
            goto test4
lbl3:       success++

test4:
            if barray[1]
                goto lbl4
            fail(4)
            goto test5
lbl4:       success++

test5:
            if barray[0] and var1
                goto test6
            success++
            goto test6

test6:
            if barray[1] and var1
                goto lbl6
            fail(5)
            goto test7
lbl6:       success++

test7:
            if success==6
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub single_goto_indirect() {
            success=0
            uword pointer
            txt.print("bool single_goto_indirect: ")
test1:
            var1 = false
            var2 = true

            pointer = &lbl1
            if var1
                goto pointer
            success++
            goto test2
lbl1:
            fail(10)

test2:
            pointer = &lbl2
            if var2
                goto pointer
            fail(11)
            goto test3
lbl2:       success++

test3:
            pointer = &lbl3
            if var1 xor var2
                goto pointer
            fail(12)
            goto test4
lbl3:       success++

test4:
            pointer = &lbl4
            if barray[1]
                goto pointer
            fail(13)
            goto test5
lbl4:       success++

test5:
            pointer = &test6
            if barray[0] and var1
                goto pointer
            success++

test6:
            pointer = &lbl6
            if barray[1] and var2
                goto pointer
            fail(14)
            goto test7
lbl6:       success++

test7:
            if success==6
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub no_else() {
            txt.print("bool no_else: ")
            success=0

            var1=true
            var2=false

            if var1
                success++
            if var2
                fail(20)
            if var1==var2
                fail(21)
            if var1!=var2
                success++
            if var2 or var1
                success++
            if var1 and var2
                fail(22)
            if barray[0] xor var1
                success++
            if barray[1] and var1
                success++

            if success==5
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub if_else() {
            txt.print("bool if_else: ")
            success=0

            var1=true
            var2=false

            if var1
                success++
            else
                fail(30)
            if var2
                fail(31)
            else
                success++
            if var1 and var2
                fail(32)
            else
                success++
            if barray[1]
                success++
            else
                fail(33)
            if var1 xor barray[1]
                fail(34)
            else
                success++

            if var1==var2
                fail(35)
            else
                success++

            if var1!=var2
                success++
            else
                fail(36)

            if var1 and barray[1]
                success++
            else
                fail(37)

            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }
    }

    sub test_float() {
        float @shared var1, var2
        float[3] array = [11.11, 22.22, 33.33]
        ubyte success

        single_goto()
        single_goto_indirect()
        no_else()
        if_else()

        sub single_goto() {
            success=0
            txt.print("float single_goto: ")
            var1 = 11.11
            var2 = 11.11
test1:
            if var1==0
                goto lbl1
            success++
            goto test2
lbl1:
            fail(41)

test2:
            if var1==11.11
                goto lbl2
            fail(42)
            goto test3
lbl2:       success++

test3:
            if var1!=0
                goto lbl3
            fail(43)
            goto test4
lbl3:       success++

test4:
            if var1!=99.99
                goto lbl4
            fail(44)
            goto test5
lbl4:       success++

test5:
            if var1 + var2 > 10
                goto lbl5
            fail(45)
            goto test6
lbl5:       success++

test6:
            if array[1]!=0
                goto lbl6
            fail(46)
            goto test7
lbl6:       success++

test7:
            if array[0] + var1 < 0
                goto test8
            success++

test8:
            if array[2] + var2 != 44.44
                goto test9
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub single_goto_indirect() {
            success = 0
            uword pointer
            txt.print("float single_goto_indirect: ")

            var1 = 11.11
            var2 = 11.11
test1:
            pointer = &lbl1
            if var1==0
                goto pointer
            success++
            goto test2
lbl1:
            fail(51)

test2:
            pointer = &lbl2
            if var1==11.11
                goto pointer
            fail(52)
            goto test3
lbl2:       success++

test3:
            pointer = &lbl3
            if var1!=0
                goto pointer
            fail(53)
            goto test4
lbl3:       success++

test4:
            pointer = &lbl4
            if var1!=99.99
                goto pointer
            fail(54)
            goto test5
lbl4:       success++

test5:
            pointer = &lbl5
            if var1 + var2 > 10
                goto pointer
            fail(55)
            goto test6
lbl5:       success++

test6:
            pointer = &lbl6
            if array[1]!=0
                goto pointer
            fail(56)
            goto test7
lbl6:       success++

test7:
            pointer = &test8
            if array[0] + var1 < 0
                goto pointer
            success++

test8:
            pointer = &test9
            if array[2] + var2 != 44.44
                goto pointer
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")

        }

        sub no_else() {
            success=0
            txt.print("float no_else: ")
            var1 = 11.11
            var2 = 11.11

            if var1==0
                fail(61)

            if var1!=0
                success++

            if var1==11.11
                success++

            if var1!=11.11
                fail(62)

            if var1==var2
                success++

            if var1!=var2
                fail(63)

            if var1 + var2 > 10
                success++

            if array[1]!=0
                success++

            if array[0] + var1 < 0
                fail(64)

            if array[2] + var2 !=44.44
                fail(65)

            if success==5
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub if_else() {
            success=0
            txt.print("float if_else: ")
            var1 = 11.11
            var2 = 11.11

            if var1==0
                fail(70)
            else success++

            if var1!=0
                success++
            else
                fail(71)

            if var1==11.11
                success++
            else
                fail(72)

            if var1!=11.11
                fail(73)
            else success++

            if var1==var2
                success++
            else
                fail(74)

            if var1!=var2
                fail(75)
            else
                success++

            if var1 + var2 > 10
                success++
            else fail(76)

            if array[1]!=0
                success++
            else fail(77)

            if array[0] + var1 < 0
                fail(78)
            else success++

            if array[2] + var2 != 44.44
                fail(79)
            else success++

            if success==10
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

    }

    sub test_byte() {
        byte @shared var1, var2
        byte[3] array = [11, 22, -33]
        ubyte success

        single_goto()
        single_goto_indirect()
        no_else()
        if_else()

        sub single_goto() {
            success=0
            txt.print("byte single_goto: ")
            var1 = 11
            var2 = 11
test1:
            if var1==0
                goto lbl1
            success++
            goto test2
lbl1:
            fail(81)

test2:
            if var1==11
                goto lbl2
            fail(82)
            goto test3
lbl2:       success++

test3:
            if var1!=0
                goto lbl3
            fail(83)
            goto test4
lbl3:       success++

test4:
            if var1!=99
                goto lbl4
            fail(84)
            goto test5
lbl4:       success++

test5:
            if var1 + var2 > 10
                goto lbl5
            fail(85)
            goto test6
lbl5:       success++

test6:
            if array[1]!=0
                goto lbl6
            fail(86)
            goto test7
lbl6:       success++

test7:
            if array[0] + var1 < 0
                goto test8
            success++

test8:
            if array[2] + var2 != -22
                goto test9
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub single_goto_indirect() {
            success = 0
            uword pointer
            txt.print("byte single_goto_indirect: ")

            var1 = 11
            var2 = 11
test1:
            pointer = &lbl1
            if var1==0
                goto pointer
            success++
            goto test2
lbl1:
            fail(91)

test2:
            pointer = &lbl2
            if var1==11
                goto pointer
            fail(92)
            goto test3
lbl2:       success++

test3:
            pointer = &lbl3
            if var1!=0
                goto pointer
            fail(93)
            goto test4
lbl3:       success++

test4:
            pointer = &lbl4
            if var1!=99
                goto pointer
            fail(94)
            goto test5
lbl4:       success++

test5:
            pointer = &lbl5
            if var1 + var2 > 10
                goto pointer
            fail(95)
            goto test6
lbl5:       success++

test6:
            pointer = &lbl6
            if array[1]!=0
                goto pointer
            fail(96)
            goto test7
lbl6:       success++

test7:
            pointer = &test8
            if array[0] + var1 < 0
                goto pointer
            success++

test8:
            pointer = &test9
            if array[2] + var2 != -22
                goto pointer
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")

        }

        sub no_else() {
            success=0
            txt.print("byte no_else: ")
            var1 = 11
            var2 = 11

            if var1==0
                fail(101)

            if var1!=0
                success++

            if var1==11
                success++

            if var1!=11
                fail(102)

            if var1==var2
                success++

            if var1!=var2
                fail(103)

            if var1 + var2 > 10
                success++

            if array[1]!=0
                success++

            if array[0] + var1 < 0
                fail(104)

            if array[2] + var2 != -22
                fail(105)

            if success==5
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub if_else() {
            success=0
            txt.print("byte if_else: ")
            var1 = 11
            var2 = 11

            if var1==0
                fail(110)
            else success++

            if var1!=0
                success++
            else
                fail(111)

            if var1==11
                success++
            else
                fail(112)

            if var1!=11
                fail(113)
            else success++

            if var1==var2
                success++
            else
                fail(114)

            if var1!=var2
                fail(115)
            else
                success++

            if var1 + var2 > 10
                success++
            else fail(116)

            if array[1]!=0
                success++
            else fail(117)

            if array[0] + var1 < 0
                fail(118)
            else success++

            if array[2] + var2 != -22
                fail(119)
            else success++

            if success==10
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

    }

    sub test_ubyte() {
        ubyte @shared var1, var2
        ubyte[3] array = [11, 22, 33]
        ubyte success

        single_goto()
        single_goto_indirect()
        no_else()
        if_else()

        sub single_goto() {
            success=0
            txt.print("ubyte single_goto: ")
            var1 = 11
            var2 = 11
test1:
            if var1==0
                goto lbl1
            success++
            goto test2
lbl1:
            fail(121)

test2:
            if var1==11
                goto lbl2
            fail(122)
            goto test3
lbl2:       success++

test3:
            if var1!=0
                goto lbl3
            fail(123)
            goto test4
lbl3:       success++

test4:
            if var1!=99
                goto lbl4
            fail(124)
            goto test5
lbl4:       success++

test5:
            if var1 + var2 > 10
                goto lbl5
            fail(125)
            goto test6
lbl5:       success++

test6:
            if array[1]!=0
                goto lbl6
            fail(126)
            goto test7
lbl6:       success++

test7:
            if array[0] + var1 < 10
                goto test8
            success++

test8:
            if array[2] + var2 != 44
                goto test9
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub single_goto_indirect() {
            success = 0
            uword pointer
            txt.print("ubyte single_goto_indirect: ")

            var1 = 11
            var2 = 11
test1:
            pointer = &lbl1
            if var1==0
                goto pointer
            success++
            goto test2
lbl1:
            fail(131)

test2:
            pointer = &lbl2
            if var1==11
                goto pointer
            fail(132)
            goto test3
lbl2:       success++

test3:
            pointer = &lbl3
            if var1!=0
                goto pointer
            fail(133)
            goto test4
lbl3:       success++

test4:
            pointer = &lbl4
            if var1!=99
                goto pointer
            fail(134)
            goto test5
lbl4:       success++

test5:
            pointer = &lbl5
            if var1 + var2 > 10
                goto pointer
            fail(135)
            goto test6
lbl5:       success++

test6:
            pointer = &lbl6
            if array[1]!=0
                goto pointer
            fail(136)
            goto test7
lbl6:       success++

test7:
            pointer = &test8
            if array[0] + var1 < 10
                goto pointer
            success++

test8:
            pointer = &test9
            if array[2] + var2 != 44
                goto pointer
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")

        }

        sub no_else() {
            success=0
            txt.print("ubyte no_else: ")
            var1 = 11
            var2 = 11

            if var1==0
                fail(141)

            if var1!=0
                success++

            if var1==11
                success++

            if var1!=11
                fail(142)

            if var1==var2
                success++

            if var1!=var2
                fail(143)

            if var1 + var2 > 10
                success++

            if array[1]!=0
                success++

            if array[0] + var1 < 10
                fail(144)

            if array[2] + var2 != 44
                fail(145)

            if success==5
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub if_else() {
            success=0
            txt.print("ubyte if_else: ")
            var1 = 11
            var2 = 11

            if var1==0
                fail(150)
            else success++

            if var1!=0
                success++
            else
                fail(151)

            if var1==11
                success++
            else
                fail(152)

            if var1!=11
                fail(153)
            else success++

            if var1==var2
                success++
            else
                fail(154)

            if var1!=var2
                fail(155)
            else
                success++

            if var1 + var2 > 10
                success++
            else fail(156)

            if array[1]!=0
                success++
            else fail(157)

            if array[0] + var1 < 10
                fail(158)
            else success++

            if array[2] + var2 != 44
                fail(159)
            else success++

            if success==10
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

    }

    sub test_word() {
        word @shared var1, var2
        word[3] array = [1111, 2222, -3333]
        ubyte success

        single_goto()
        single_goto_indirect()
        no_else()
        if_else()

        sub single_goto() {
            success=0
            txt.print("word single_goto: ")
            var1 = 1111
            var2 = 1111
test1:
            if var1==0
                goto lbl1
            success++
            goto test2
lbl1:
            fail(161)

test2:
            if var1==1111
                goto lbl2
            fail(162)
            goto test3
lbl2:       success++

test3:
            if var1!=0
                goto lbl3
            fail(163)
            goto test4
lbl3:       success++

test4:
            if var1!=9999
                goto lbl4
            fail(164)
            goto test5
lbl4:       success++

test5:
            if var1 + var2 > 1000
                goto lbl5
            fail(165)
            goto test6
lbl5:       success++

test6:
            if array[1]!=0
                goto lbl6
            fail(166)
            goto test7
lbl6:       success++

test7:
            if array[0] + var1 < 0
                goto test8
            success++

test8:
            if array[2] + var2 != -2222
                goto test9
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub single_goto_indirect() {
            success = 0
            uword pointer
            txt.print("word single_goto_indirect: ")

            var1 = 1111
            var2 = 1111
test1:
            pointer = &lbl1
            if var1==0
                goto pointer
            success++
            goto test2
lbl1:
            fail(171)

test2:
            pointer = &lbl2
            if var1==1111
                goto pointer
            fail(172)
            goto test3
lbl2:       success++

test3:
            pointer = &lbl3
            if var1!=0
                goto pointer
            fail(173)
            goto test4
lbl3:       success++

test4:
            pointer = &lbl4
            if var1!=9999
                goto pointer
            fail(174)
            goto test5
lbl4:       success++

test5:
            pointer = &lbl5
            if var1 + var2 > 1000
                goto pointer
            fail(175)
            goto test6
lbl5:       success++

test6:
            pointer = &lbl6
            if array[1]!=0
                goto pointer
            fail(176)
            goto test7
lbl6:       success++

test7:
            pointer = &test8
            if array[0] + var1 < 0
                goto pointer
            success++

test8:
            pointer = &test9
            if array[2] + var2 != -2222
                goto pointer
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")

        }

        sub no_else() {
            success=0
            txt.print("word no_else: ")
            var1 = 1111
            var2 = 1111

            if var1==0
                fail(181)

            if var1!=0
                success++

            if var1==1111
                success++

            if var1!=1111
                fail(182)

            if var1==var2
                success++

            if var1!=var2
                fail(183)

            if var1 + var2 > 1000
                success++

            if array[1]!=0
                success++

            if array[0] + var1 < 0
                fail(184)

            if array[2] + var2 != -2222
                fail(185)

            if success==5
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub if_else() {
            success=0
            txt.print("word if_else: ")
            var1 = 1111
            var2 = 1111

            if var1==0
                fail(190)
            else success++

            if var1!=0
                success++
            else
                fail(191)

            if var1==1111
                success++
            else
                fail(192)

            if var1!=1111
                fail(193)
            else success++

            if var1==var2
                success++
            else
                fail(194)

            if var1!=var2
                fail(195)
            else
                success++

            if var1 + var2 > 1000
                success++
            else fail(196)

            if array[1]!=0
                success++
            else fail(197)

            if array[0] + var1 < 0
                fail(198)
            else success++

            if array[2] + var2 != -2222
                fail(199)
            else success++

            if success==10
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

    }

    sub test_uword() {
        uword @shared var1, var2
        uword[3] array = [1111, 2222, 3333]
        ubyte success

        single_goto()
        single_goto_indirect()
        no_else()
        if_else()

        sub single_goto() {
            success=0
            txt.print("uword single_goto: ")
            var1 = 1111
            var2 = 1111
test1:
            if var1==0
                goto lbl1
            success++
            goto test2
lbl1:
            fail(201)

test2:
            if var1==1111
                goto lbl2
            fail(202)
            goto test3
lbl2:       success++

test3:
            if var1!=0
                goto lbl3
            fail(203)
            goto test4
lbl3:       success++

test4:
            if var1!=9999
                goto lbl4
            fail(204)
            goto test5
lbl4:       success++

test5:
            if var1 + var2 > 1000
                goto lbl5
            fail(205)
            goto test6
lbl5:       success++

test6:
            if array[1]!=0
                goto lbl6
            fail(206)
            goto test7
lbl6:       success++

test7:
            if array[0] + var1 < 1000
                goto test8
            success++

test8:
            if array[2] + var2 != 4444
                goto test9
            success++

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub single_goto_indirect() {
            success = 0
            uword pointer
            txt.print("uword single_goto_indirect: ")

            var1 = 1111
            var2 = 1111
test1:
            pointer = &lbl1
            if var1==0
                goto pointer
            success++
            goto test2
lbl1:
            fail(211)

test2:
            pointer = &lbl2
            if var1==1111
                goto pointer
            fail(212)
            goto test3
lbl2:       success++

test3:
            pointer = &lbl3
            if var1!=0
                goto pointer
            fail(213)
            goto test4
lbl3:       success++

test4:
            pointer = &lbl4
            if var1!=9999
                goto pointer
            fail(214)
            goto test5
lbl4:       success++

test5:
            pointer = &lbl5
            if var1 + var2 > 1000
                goto pointer
            fail(215)
            goto test6
lbl5:       success++

test6:
            pointer = &lbl6
            if array[1]!=0
                goto pointer
            fail(216)
            goto test7
lbl6:       success++

test7:
            pointer = &lbl7
            if array[0] + var1 < 1000
                goto pointer
            success++
            goto test8
lbl7:       fail(217)

test8:
            pointer = &lbl8
            if array[2] + var2 != 4444
                goto pointer
            success++
            goto test9
lbl8:       fail(218)

test9:
            if success==8
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")

        }

        sub no_else() {
            success=0
            txt.print("uword no_else: ")
            var1 = 1111
            var2 = 1111

            if var1==0
                fail(221)

            if var1!=0
                success++

            if var1==1111
                success++

            if var1!=1111
                fail(222)

            if var1==var2
                success++

            if var1!=var2
                fail(223)

            if var1 + var2 > 1000
                success++

            if array[1]!=0
                success++

            if array[0] + var1 < 1000
                fail(184)

            if array[2] + var2 != 4444
                fail(225)

            if success==5
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

        sub if_else() {
            success=0
            txt.print("uword if_else: ")
            var1 = 1111
            var2 = 1111

            if var1==0
                fail(230)
            else success++

            if var1!=0
                success++
            else
                fail(231)

            if var1==1111
                success++
            else
                fail(232)

            if var1!=1111
                fail(233)
            else success++

            if var1==var2
                success++
            else
                fail(234)

            if var1!=var2
                fail(235)
            else
                success++

            if var1 + var2 > 1000
                success++
            else fail(236)

            if array[1]!=0
                success++
            else fail(237)

            if array[0] + var1 < 1000
                fail(238)
            else success++

            if array[2] + var2 != 4444
                fail(239)
            else success++

            if success==10
                txt.print("  ok\n")
            else
                txt.print("  \x12 failed \x92\n")
        }

    }
}
