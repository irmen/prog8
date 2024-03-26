
%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {
    ubyte success = 0
    str datatype = "uword"
    uword @shared comparison

    sub start() {
        txt.print("\nless-than tests for: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("\n<number: ")
        test_cmp_number()
        txt.print("\n<var: ")
        test_cmp_var()
        txt.print("\n<array[]: ")
        test_cmp_array()
        txt.print("\n<expr: ")
        test_cmp_expr()
        test_stack.test()
    }
    
    sub verify_success(ubyte expected) {
        if success==expected {
            txt.print("ok")
        } else {
            txt.print(" **failed** ")
            txt.print_ub(success)
            txt.print(" success, expected ")
            txt.print_ub(expected)
        }
    }
    
    sub fail_byte(uword idx) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.print(" **")
    }

    sub fail_ubyte(uword idx) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.print(" **")
    }
    
    sub fail_word(uword idx) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.print(" **")
    }

    sub fail_uword(uword idx) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.print(" **")
    }
    
    sub fail_float(uword idx) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.print(" **")
    }    


    sub test_cmp_number() {
    uword @shared x
        success = 0
    x=0
    ; direct jump
        if x<0
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(1)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<0
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(2)
skip1b:
        ; no else
        if x<0
            fail_uword(3)

        ; with else
        if x<0
            fail_uword(4)
        else
            success++

    ; direct jump
        if x<1
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<1
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<1
            success++

        ; with else
        if x<1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<30464
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<30464
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<30464
            success++

        ; with else
        if x<30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<65535
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<65535
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x<65535
            success++

        ; with else
        if x<65535
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x<0
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(5)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<0
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(6)
skip5b:
        ; no else
        if x<0
            fail_uword(7)

        ; with else
        if x<0
            fail_uword(8)
        else
            success++

    ; direct jump
        if x<1
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(9)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<1
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(10)
skip6b:
        ; no else
        if x<1
            fail_uword(11)

        ; with else
        if x<1
            fail_uword(12)
        else
            success++

    ; direct jump
        if x<30464
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<30464
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x<30464
            success++

        ; with else
        if x<30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<65535
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<65535
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x<65535
            success++

        ; with else
        if x<65535
            success++
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x<0
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(13)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<0
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(14)
skip9b:
        ; no else
        if x<0
            fail_uword(15)

        ; with else
        if x<0
            fail_uword(16)
        else
            success++

    ; direct jump
        if x<1
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(17)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x<1
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(18)
skip10b:
        ; no else
        if x<1
            fail_uword(19)

        ; with else
        if x<1
            fail_uword(20)
        else
            success++

    ; direct jump
        if x<30464
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(21)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x<30464
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(22)
skip11b:
        ; no else
        if x<30464
            fail_uword(23)

        ; with else
        if x<30464
            fail_uword(24)
        else
            success++

    ; direct jump
        if x<65535
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x<65535
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x<65535
            success++

        ; with else
        if x<65535
            success++
        else
            cx16.r0L++

    x=65535
    ; direct jump
        if x<0
            goto lbl13a
        goto skip13a
lbl13a:   fail_uword(25)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x<0
            goto cx16.r3
        goto skip13b
lbl13b:   fail_uword(26)
skip13b:
        ; no else
        if x<0
            fail_uword(27)

        ; with else
        if x<0
            fail_uword(28)
        else
            success++

    ; direct jump
        if x<1
            goto lbl14a
        goto skip14a
lbl14a:   fail_uword(29)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x<1
            goto cx16.r3
        goto skip14b
lbl14b:   fail_uword(30)
skip14b:
        ; no else
        if x<1
            fail_uword(31)

        ; with else
        if x<1
            fail_uword(32)
        else
            success++

    ; direct jump
        if x<30464
            goto lbl15a
        goto skip15a
lbl15a:   fail_uword(33)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x<30464
            goto cx16.r3
        goto skip15b
lbl15b:   fail_uword(34)
skip15b:
        ; no else
        if x<30464
            fail_uword(35)

        ; with else
        if x<30464
            fail_uword(36)
        else
            success++

    ; direct jump
        if x<65535
            goto lbl16a
        goto skip16a
lbl16a:   fail_uword(37)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x<65535
            goto cx16.r3
        goto skip16b
lbl16b:   fail_uword(38)
skip16b:
        ; no else
        if x<65535
            fail_uword(39)

        ; with else
        if x<65535
            fail_uword(40)
        else
            success++

    verify_success(34)
}
    sub test_cmp_var() {
    uword @shared x, value
        success = 0
    x=0
    value=0
    ; direct jump
        if x<value
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(41)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<value
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(42)
skip1b:
        ; no else
        if x<value
            fail_uword(43)

        ; with else
        if x<value
            fail_uword(44)
        else
            success++

    value=1
    ; direct jump
        if x<value
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<value
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<value
            success++

        ; with else
        if x<value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x<value
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<value
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<value
            success++

        ; with else
        if x<value
            success++
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x<value
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<value
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x<value
            success++

        ; with else
        if x<value
            success++
        else
            cx16.r0L++

    x=1
    value=0
    ; direct jump
        if x<value
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(45)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<value
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(46)
skip5b:
        ; no else
        if x<value
            fail_uword(47)

        ; with else
        if x<value
            fail_uword(48)
        else
            success++

    value=1
    ; direct jump
        if x<value
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(49)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<value
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(50)
skip6b:
        ; no else
        if x<value
            fail_uword(51)

        ; with else
        if x<value
            fail_uword(52)
        else
            success++

    value=30464
    ; direct jump
        if x<value
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<value
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x<value
            success++

        ; with else
        if x<value
            success++
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x<value
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<value
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x<value
            success++

        ; with else
        if x<value
            success++
        else
            cx16.r0L++

    x=30464
    value=0
    ; direct jump
        if x<value
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(53)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<value
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(54)
skip9b:
        ; no else
        if x<value
            fail_uword(55)

        ; with else
        if x<value
            fail_uword(56)
        else
            success++

    value=1
    ; direct jump
        if x<value
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(57)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x<value
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(58)
skip10b:
        ; no else
        if x<value
            fail_uword(59)

        ; with else
        if x<value
            fail_uword(60)
        else
            success++

    value=30464
    ; direct jump
        if x<value
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(61)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x<value
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(62)
skip11b:
        ; no else
        if x<value
            fail_uword(63)

        ; with else
        if x<value
            fail_uword(64)
        else
            success++

    value=65535
    ; direct jump
        if x<value
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x<value
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x<value
            success++

        ; with else
        if x<value
            success++
        else
            cx16.r0L++

    x=65535
    value=0
    ; direct jump
        if x<value
            goto lbl13a
        goto skip13a
lbl13a:   fail_uword(65)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x<value
            goto cx16.r3
        goto skip13b
lbl13b:   fail_uword(66)
skip13b:
        ; no else
        if x<value
            fail_uword(67)

        ; with else
        if x<value
            fail_uword(68)
        else
            success++

    value=1
    ; direct jump
        if x<value
            goto lbl14a
        goto skip14a
lbl14a:   fail_uword(69)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x<value
            goto cx16.r3
        goto skip14b
lbl14b:   fail_uword(70)
skip14b:
        ; no else
        if x<value
            fail_uword(71)

        ; with else
        if x<value
            fail_uword(72)
        else
            success++

    value=30464
    ; direct jump
        if x<value
            goto lbl15a
        goto skip15a
lbl15a:   fail_uword(73)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x<value
            goto cx16.r3
        goto skip15b
lbl15b:   fail_uword(74)
skip15b:
        ; no else
        if x<value
            fail_uword(75)

        ; with else
        if x<value
            fail_uword(76)
        else
            success++

    value=65535
    ; direct jump
        if x<value
            goto lbl16a
        goto skip16a
lbl16a:   fail_uword(77)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x<value
            goto cx16.r3
        goto skip16b
lbl16b:   fail_uword(78)
skip16b:
        ; no else
        if x<value
            fail_uword(79)

        ; with else
        if x<value
            fail_uword(80)
        else
            success++

    verify_success(34)
}
    sub test_cmp_array() {
    uword @shared x
        uword[] values = [0, 0]
        success = 0
    x=0
    values[1]=0
    ; direct jump
        if x<values[1]
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(81)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(82)
skip1b:
        ; no else
        if x<values[1]
            fail_uword(83)

        ; with else
        if x<values[1]
            fail_uword(84)
        else
            success++

    values[1]=1
    ; direct jump
        if x<values[1]
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<values[1]
            success++

        ; with else
        if x<values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x<values[1]
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<values[1]
            success++

        ; with else
        if x<values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x<values[1]
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x<values[1]
            success++

        ; with else
        if x<values[1]
            success++
        else
            cx16.r0L++

    x=1
    values[1]=0
    ; direct jump
        if x<values[1]
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(85)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(86)
skip5b:
        ; no else
        if x<values[1]
            fail_uword(87)

        ; with else
        if x<values[1]
            fail_uword(88)
        else
            success++

    values[1]=1
    ; direct jump
        if x<values[1]
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(89)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(90)
skip6b:
        ; no else
        if x<values[1]
            fail_uword(91)

        ; with else
        if x<values[1]
            fail_uword(92)
        else
            success++

    values[1]=30464
    ; direct jump
        if x<values[1]
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x<values[1]
            success++

        ; with else
        if x<values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x<values[1]
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x<values[1]
            success++

        ; with else
        if x<values[1]
            success++
        else
            cx16.r0L++

    x=30464
    values[1]=0
    ; direct jump
        if x<values[1]
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(93)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<values[1]
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(94)
skip9b:
        ; no else
        if x<values[1]
            fail_uword(95)

        ; with else
        if x<values[1]
            fail_uword(96)
        else
            success++

    values[1]=1
    ; direct jump
        if x<values[1]
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(97)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x<values[1]
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(98)
skip10b:
        ; no else
        if x<values[1]
            fail_uword(99)

        ; with else
        if x<values[1]
            fail_uword(100)
        else
            success++

    values[1]=30464
    ; direct jump
        if x<values[1]
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(101)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x<values[1]
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(102)
skip11b:
        ; no else
        if x<values[1]
            fail_uword(103)

        ; with else
        if x<values[1]
            fail_uword(104)
        else
            success++

    values[1]=65535
    ; direct jump
        if x<values[1]
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x<values[1]
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x<values[1]
            success++

        ; with else
        if x<values[1]
            success++
        else
            cx16.r0L++

    x=65535
    values[1]=0
    ; direct jump
        if x<values[1]
            goto lbl13a
        goto skip13a
lbl13a:   fail_uword(105)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x<values[1]
            goto cx16.r3
        goto skip13b
lbl13b:   fail_uword(106)
skip13b:
        ; no else
        if x<values[1]
            fail_uword(107)

        ; with else
        if x<values[1]
            fail_uword(108)
        else
            success++

    values[1]=1
    ; direct jump
        if x<values[1]
            goto lbl14a
        goto skip14a
lbl14a:   fail_uword(109)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x<values[1]
            goto cx16.r3
        goto skip14b
lbl14b:   fail_uword(110)
skip14b:
        ; no else
        if x<values[1]
            fail_uword(111)

        ; with else
        if x<values[1]
            fail_uword(112)
        else
            success++

    values[1]=30464
    ; direct jump
        if x<values[1]
            goto lbl15a
        goto skip15a
lbl15a:   fail_uword(113)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x<values[1]
            goto cx16.r3
        goto skip15b
lbl15b:   fail_uword(114)
skip15b:
        ; no else
        if x<values[1]
            fail_uword(115)

        ; with else
        if x<values[1]
            fail_uword(116)
        else
            success++

    values[1]=65535
    ; direct jump
        if x<values[1]
            goto lbl16a
        goto skip16a
lbl16a:   fail_uword(117)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x<values[1]
            goto cx16.r3
        goto skip16b
lbl16b:   fail_uword(118)
skip16b:
        ; no else
        if x<values[1]
            fail_uword(119)

        ; with else
        if x<values[1]
            fail_uword(120)
        else
            success++

    verify_success(34)
}
    sub test_cmp_expr() {
    uword @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0
    ; direct jump
        if x<cx16.r4+0-cx16.r5
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(121)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<cx16.r4+0-cx16.r5
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(122)
skip1b:
        ; no else
        if x<cx16.r4+0-cx16.r5
            fail_uword(123)

        ; with else
        if x<cx16.r4+0-cx16.r5
            fail_uword(124)
        else
            success++

    ; direct jump
        if x<cx16.r4+1-cx16.r5
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<cx16.r4+1-cx16.r5
            success++

        ; with else
        if x<cx16.r4+1-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<cx16.r4+30464-cx16.r5
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<cx16.r4+30464-cx16.r5
            success++

        ; with else
        if x<cx16.r4+30464-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<cx16.r4+65535-cx16.r5
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x<cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x<cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x<cx16.r4+0-cx16.r5
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(125)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<cx16.r4+0-cx16.r5
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(126)
skip5b:
        ; no else
        if x<cx16.r4+0-cx16.r5
            fail_uword(127)

        ; with else
        if x<cx16.r4+0-cx16.r5
            fail_uword(128)
        else
            success++

    ; direct jump
        if x<cx16.r4+1-cx16.r5
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(129)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(130)
skip6b:
        ; no else
        if x<cx16.r4+1-cx16.r5
            fail_uword(131)

        ; with else
        if x<cx16.r4+1-cx16.r5
            fail_uword(132)
        else
            success++

    ; direct jump
        if x<cx16.r4+30464-cx16.r5
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x<cx16.r4+30464-cx16.r5
            success++

        ; with else
        if x<cx16.r4+30464-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<cx16.r4+65535-cx16.r5
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x<cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x<cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x<cx16.r4+0-cx16.r5
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(133)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<cx16.r4+0-cx16.r5
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(134)
skip9b:
        ; no else
        if x<cx16.r4+0-cx16.r5
            fail_uword(135)

        ; with else
        if x<cx16.r4+0-cx16.r5
            fail_uword(136)
        else
            success++

    ; direct jump
        if x<cx16.r4+1-cx16.r5
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(137)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x<cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(138)
skip10b:
        ; no else
        if x<cx16.r4+1-cx16.r5
            fail_uword(139)

        ; with else
        if x<cx16.r4+1-cx16.r5
            fail_uword(140)
        else
            success++

    ; direct jump
        if x<cx16.r4+30464-cx16.r5
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(141)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x<cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(142)
skip11b:
        ; no else
        if x<cx16.r4+30464-cx16.r5
            fail_uword(143)

        ; with else
        if x<cx16.r4+30464-cx16.r5
            fail_uword(144)
        else
            success++

    ; direct jump
        if x<cx16.r4+65535-cx16.r5
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x<cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x<cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x<cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    x=65535
    ; direct jump
        if x<cx16.r4+0-cx16.r5
            goto lbl13a
        goto skip13a
lbl13a:   fail_uword(145)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x<cx16.r4+0-cx16.r5
            goto cx16.r3
        goto skip13b
lbl13b:   fail_uword(146)
skip13b:
        ; no else
        if x<cx16.r4+0-cx16.r5
            fail_uword(147)

        ; with else
        if x<cx16.r4+0-cx16.r5
            fail_uword(148)
        else
            success++

    ; direct jump
        if x<cx16.r4+1-cx16.r5
            goto lbl14a
        goto skip14a
lbl14a:   fail_uword(149)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x<cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip14b
lbl14b:   fail_uword(150)
skip14b:
        ; no else
        if x<cx16.r4+1-cx16.r5
            fail_uword(151)

        ; with else
        if x<cx16.r4+1-cx16.r5
            fail_uword(152)
        else
            success++

    ; direct jump
        if x<cx16.r4+30464-cx16.r5
            goto lbl15a
        goto skip15a
lbl15a:   fail_uword(153)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x<cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip15b
lbl15b:   fail_uword(154)
skip15b:
        ; no else
        if x<cx16.r4+30464-cx16.r5
            fail_uword(155)

        ; with else
        if x<cx16.r4+30464-cx16.r5
            fail_uword(156)
        else
            success++

    ; direct jump
        if x<cx16.r4+65535-cx16.r5
            goto lbl16a
        goto skip16a
lbl16a:   fail_uword(157)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x<cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip16b
lbl16b:   fail_uword(158)
skip16b:
        ; no else
        if x<cx16.r4+65535-cx16.r5
            fail_uword(159)

        ; with else
        if x<cx16.r4+65535-cx16.r5
            fail_uword(160)
        else
            success++

    verify_success(34)
}

}

