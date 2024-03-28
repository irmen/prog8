
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
        txt.print("\n(in)equality tests for datatype: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("==0: ")
        test_is_zero()
        txt.print("\n!=0: ")
        test_not_zero()
        txt.print("\n==number: ")
        test_is_number()
        txt.print("\n!=number: ")
        test_not_number()
        txt.print("\n==var: ")
        test_is_var()
        txt.print("\n!=var: ")
        test_not_var()
        txt.print("\n==array[]: ")
        test_is_array()
        txt.print("\n!=array[]: ")
        test_not_array()
        txt.print("\n==expr: ")
        test_is_expr()
        txt.print("\n!=expr: ")
        test_not_expr()
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
    
    sub fail_byte(uword idx, byte v1) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_b(v1)
        txt.print(" **")
    }

    sub fail_ubyte(uword idx, ubyte v1) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_ub(v1)
        txt.print(" **")
    }
    
    sub fail_word(uword idx, word v1) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_w(v1)
        txt.print(" **")
    }

    sub fail_uword(uword idx, uword v1) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_uw(v1)
        txt.print(" **")
    }
    
    sub fail_float(uword idx, float v1) {
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        floats.print(v1)
        txt.print(" **")
    }    



    sub test_is_zero() {
        uword @shared x
        success = 0

        x=0
        ; direct jump
        if x==0
            goto lbl1
        goto skip1
lbl1:   success++
skip1:
        ; indirect jump
        cx16.r3 = &lbl2
        if x==0
            goto cx16.r3
        goto skip2
lbl2:   success++
skip2:
        ; no else
        if x==0
            success++

        ; with else
        if x==0
            success++
        else
            cx16.r0L++     
            
        x = 9999
        ; direct jump
        if x==0
            goto skip3
        success++
skip3:
        ; indirect jump
        cx16.r3 = &skip4
        if x==0
            goto cx16.r3
        success++
skip4:
        ; no else
        success++
        if x==0
            success--

        ; with else
        if x==0
            cx16.r0L++                      
        else
            success++

        verify_success(8)
    }


    sub test_not_zero() {
        uword @shared x
        success = 0

        x=9999
        ; direct jump
        if x!=0
            goto lbl1
        goto skip1
lbl1:   success++
skip1:
        ; indirect jump
        cx16.r3 = &lbl2
        if x!=0
            goto cx16.r3
        goto skip2
lbl2:   success++
skip2:
        ; no else
        if x!=0
            success++

        ; with else
        if x!=0
            success++
        else
            cx16.r0L++     
            
        x = 0
        ; direct jump
        if x!=0
            goto skip3
        success++
skip3:
        ; indirect jump
        cx16.r3 = &skip4
        if x!=0
            goto cx16.r3
        success++
skip4:
        ; no else
        success++
        if x!=0
            success--

        ; with else
        if x!=0
            cx16.r0L++                      
        else
            success++

        verify_success(8)
    }

    sub test_is_number() {
    uword @shared x
        success = 0
    x=0
    ; direct jump
        if x==1
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(1,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==1
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(2,0)
skip1b:
        ; no else
        if x==1
            fail_uword(3,0)

        ; with else
        if x==1
            fail_uword(4,0)
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl2a
        goto skip2a
lbl2a:   fail_uword(5,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==30464
            goto cx16.r3
        goto skip2b
lbl2b:   fail_uword(6,0)
skip2b:
        ; no else
        if x==30464
            fail_uword(7,0)

        ; with else
        if x==30464
            fail_uword(8,0)
        else
            cx16.r0L++

    ; direct jump
        if x==65535
            goto lbl3a
        goto skip3a
lbl3a:   fail_uword(9,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==65535
            goto cx16.r3
        goto skip3b
lbl3b:   fail_uword(10,0)
skip3b:
        ; no else
        if x==65535
            fail_uword(11,0)

        ; with else
        if x==65535
            fail_uword(12,0)
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x==1
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==1
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x==1
            success++

        ; with else
        if x==1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(13,1)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==30464
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(14,1)
skip5b:
        ; no else
        if x==30464
            fail_uword(15,1)

        ; with else
        if x==30464
            fail_uword(16,1)
        else
            cx16.r0L++

    ; direct jump
        if x==65535
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(17,1)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==65535
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(18,1)
skip6b:
        ; no else
        if x==65535
            fail_uword(19,1)

        ; with else
        if x==65535
            fail_uword(20,1)
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x==1
            goto lbl7a
        goto skip7a
lbl7a:   fail_uword(21,30464)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==1
            goto cx16.r3
        goto skip7b
lbl7b:   fail_uword(22,30464)
skip7b:
        ; no else
        if x==1
            fail_uword(23,30464)

        ; with else
        if x==1
            fail_uword(24,30464)
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==30464
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x==30464
            success++

        ; with else
        if x==30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==65535
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(25,30464)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==65535
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(26,30464)
skip9b:
        ; no else
        if x==65535
            fail_uword(27,30464)

        ; with else
        if x==65535
            fail_uword(28,30464)
        else
            cx16.r0L++

    x=65535
    ; direct jump
        if x==1
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(29,65535)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==1
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(30,65535)
skip10b:
        ; no else
        if x==1
            fail_uword(31,65535)

        ; with else
        if x==1
            fail_uword(32,65535)
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(33,65535)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==30464
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(34,65535)
skip11b:
        ; no else
        if x==30464
            fail_uword(35,65535)

        ; with else
        if x==30464
            fail_uword(36,65535)
        else
            cx16.r0L++

    ; direct jump
        if x==65535
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==65535
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x==65535
            success++

        ; with else
        if x==65535
            success++
        else
            cx16.r0L++

    verify_success(12)
}
    sub test_not_number() {
    uword @shared x
        success = 0
    x=0
    ; direct jump
        if x!=1
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=1
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x!=1
            success++

        ; with else
        if x!=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=30464
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=30464
            success++

        ; with else
        if x!=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=65535
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=65535
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=65535
            success++

        ; with else
        if x!=65535
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x!=1
            goto lbl4a
        goto skip4a
lbl4a:   fail_uword(37,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=1
            goto cx16.r3
        goto skip4b
lbl4b:   fail_uword(38,1)
skip4b:
        ; no else
        if x!=1
            fail_uword(39,1)

        ; with else
        if x!=1
            fail_uword(40,1)
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=30464
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=30464
            success++

        ; with else
        if x!=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=65535
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=65535
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x!=65535
            success++

        ; with else
        if x!=65535
            success++
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x!=1
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x!=1
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x!=1
            success++

        ; with else
        if x!=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl8a
        goto skip8a
lbl8a:   fail_uword(41,30464)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=30464
            goto cx16.r3
        goto skip8b
lbl8b:   fail_uword(42,30464)
skip8b:
        ; no else
        if x!=30464
            fail_uword(43,30464)

        ; with else
        if x!=30464
            fail_uword(44,30464)
        else
            cx16.r0L++

    ; direct jump
        if x!=65535
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x!=65535
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x!=65535
            success++

        ; with else
        if x!=65535
            success++
        else
            cx16.r0L++

    x=65535
    ; direct jump
        if x!=1
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=1
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
        ; no else
        if x!=1
            success++

        ; with else
        if x!=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl11a
        goto skip11a
lbl11a:   success++
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x!=30464
            goto cx16.r3
        goto skip11b
lbl11b:   success++
skip11b:
        ; no else
        if x!=30464
            success++

        ; with else
        if x!=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=65535
            goto lbl12a
        goto skip12a
lbl12a:   fail_uword(45,65535)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=65535
            goto cx16.r3
        goto skip12b
lbl12b:   fail_uword(46,65535)
skip12b:
        ; no else
        if x!=65535
            fail_uword(47,65535)

        ; with else
        if x!=65535
            fail_uword(48,65535)
        else
            cx16.r0L++

    verify_success(36)
}
    sub test_is_var() {
    uword @shared x, value
        success = 0
    x=0
    value=1
    ; direct jump
        if x==value
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(49,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==value
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(50,0)
skip1b:
        ; no else
        if x==value
            fail_uword(51,0)

        ; with else
        if x==value
            fail_uword(52,0)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl2a
        goto skip2a
lbl2a:   fail_uword(53,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_uword(54,0)
skip2b:
        ; no else
        if x==value
            fail_uword(55,0)

        ; with else
        if x==value
            fail_uword(56,0)
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x==value
            goto lbl3a
        goto skip3a
lbl3a:   fail_uword(57,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==value
            goto cx16.r3
        goto skip3b
lbl3b:   fail_uword(58,0)
skip3b:
        ; no else
        if x==value
            fail_uword(59,0)

        ; with else
        if x==value
            fail_uword(60,0)
        else
            cx16.r0L++

    x=1
    value=1
    ; direct jump
        if x==value
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==value
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(61,1)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==value
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(62,1)
skip5b:
        ; no else
        if x==value
            fail_uword(63,1)

        ; with else
        if x==value
            fail_uword(64,1)
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x==value
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(65,1)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==value
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(66,1)
skip6b:
        ; no else
        if x==value
            fail_uword(67,1)

        ; with else
        if x==value
            fail_uword(68,1)
        else
            cx16.r0L++

    x=30464
    value=1
    ; direct jump
        if x==value
            goto lbl7a
        goto skip7a
lbl7a:   fail_uword(69,30464)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==value
            goto cx16.r3
        goto skip7b
lbl7b:   fail_uword(70,30464)
skip7b:
        ; no else
        if x==value
            fail_uword(71,30464)

        ; with else
        if x==value
            fail_uword(72,30464)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==value
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x==value
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(73,30464)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==value
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(74,30464)
skip9b:
        ; no else
        if x==value
            fail_uword(75,30464)

        ; with else
        if x==value
            fail_uword(76,30464)
        else
            cx16.r0L++

    x=65535
    value=1
    ; direct jump
        if x==value
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(77,65535)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==value
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(78,65535)
skip10b:
        ; no else
        if x==value
            fail_uword(79,65535)

        ; with else
        if x==value
            fail_uword(80,65535)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(81,65535)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==value
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(82,65535)
skip11b:
        ; no else
        if x==value
            fail_uword(83,65535)

        ; with else
        if x==value
            fail_uword(84,65535)
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x==value
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==value
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    verify_success(12)
}
    sub test_not_var() {
    uword @shared x, value
        success = 0
    x=0
    value=1
    ; direct jump
        if x!=value
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=value
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x!=value
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=value
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x!=value
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=value
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    x=1
    value=1
    ; direct jump
        if x!=value
            goto lbl4a
        goto skip4a
lbl4a:   fail_uword(85,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=value
            goto cx16.r3
        goto skip4b
lbl4b:   fail_uword(86,1)
skip4b:
        ; no else
        if x!=value
            fail_uword(87,1)

        ; with else
        if x!=value
            fail_uword(88,1)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x!=value
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=value
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x!=value
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=value
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    x=30464
    value=1
    ; direct jump
        if x!=value
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x!=value
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x!=value
            goto lbl8a
        goto skip8a
lbl8a:   fail_uword(89,30464)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=value
            goto cx16.r3
        goto skip8b
lbl8b:   fail_uword(90,30464)
skip8b:
        ; no else
        if x!=value
            fail_uword(91,30464)

        ; with else
        if x!=value
            fail_uword(92,30464)
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x!=value
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x!=value
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    x=65535
    value=1
    ; direct jump
        if x!=value
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=value
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x!=value
            goto lbl11a
        goto skip11a
lbl11a:   success++
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x!=value
            goto cx16.r3
        goto skip11b
lbl11b:   success++
skip11b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=65535
    ; direct jump
        if x!=value
            goto lbl12a
        goto skip12a
lbl12a:   fail_uword(93,65535)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=value
            goto cx16.r3
        goto skip12b
lbl12b:   fail_uword(94,65535)
skip12b:
        ; no else
        if x!=value
            fail_uword(95,65535)

        ; with else
        if x!=value
            fail_uword(96,65535)
        else
            cx16.r0L++

    verify_success(36)
}
    sub test_is_expr() {
    uword @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0
    ; direct jump
        if x==cx16.r4+1-cx16.r5
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(97,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(98,0)
skip1b:
        ; no else
        if x==cx16.r4+1-cx16.r5
            fail_uword(99,0)

        ; with else
        if x==cx16.r4+1-cx16.r5
            fail_uword(100,0)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+30464-cx16.r5
            goto lbl2a
        goto skip2a
lbl2a:   fail_uword(101,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip2b
lbl2b:   fail_uword(102,0)
skip2b:
        ; no else
        if x==cx16.r4+30464-cx16.r5
            fail_uword(103,0)

        ; with else
        if x==cx16.r4+30464-cx16.r5
            fail_uword(104,0)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+65535-cx16.r5
            goto lbl3a
        goto skip3a
lbl3a:   fail_uword(105,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip3b
lbl3b:   fail_uword(106,0)
skip3b:
        ; no else
        if x==cx16.r4+65535-cx16.r5
            fail_uword(107,0)

        ; with else
        if x==cx16.r4+65535-cx16.r5
            fail_uword(108,0)
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x==cx16.r4+1-cx16.r5
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x==cx16.r4+1-cx16.r5
            success++

        ; with else
        if x==cx16.r4+1-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+30464-cx16.r5
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(109,1)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(110,1)
skip5b:
        ; no else
        if x==cx16.r4+30464-cx16.r5
            fail_uword(111,1)

        ; with else
        if x==cx16.r4+30464-cx16.r5
            fail_uword(112,1)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+65535-cx16.r5
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(113,1)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(114,1)
skip6b:
        ; no else
        if x==cx16.r4+65535-cx16.r5
            fail_uword(115,1)

        ; with else
        if x==cx16.r4+65535-cx16.r5
            fail_uword(116,1)
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x==cx16.r4+1-cx16.r5
            goto lbl7a
        goto skip7a
lbl7a:   fail_uword(117,30464)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip7b
lbl7b:   fail_uword(118,30464)
skip7b:
        ; no else
        if x==cx16.r4+1-cx16.r5
            fail_uword(119,30464)

        ; with else
        if x==cx16.r4+1-cx16.r5
            fail_uword(120,30464)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+30464-cx16.r5
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x==cx16.r4+30464-cx16.r5
            success++

        ; with else
        if x==cx16.r4+30464-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+65535-cx16.r5
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(121,30464)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(122,30464)
skip9b:
        ; no else
        if x==cx16.r4+65535-cx16.r5
            fail_uword(123,30464)

        ; with else
        if x==cx16.r4+65535-cx16.r5
            fail_uword(124,30464)
        else
            cx16.r0L++

    x=65535
    ; direct jump
        if x==cx16.r4+1-cx16.r5
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(125,65535)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(126,65535)
skip10b:
        ; no else
        if x==cx16.r4+1-cx16.r5
            fail_uword(127,65535)

        ; with else
        if x==cx16.r4+1-cx16.r5
            fail_uword(128,65535)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+30464-cx16.r5
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(129,65535)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(130,65535)
skip11b:
        ; no else
        if x==cx16.r4+30464-cx16.r5
            fail_uword(131,65535)

        ; with else
        if x==cx16.r4+30464-cx16.r5
            fail_uword(132,65535)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4+65535-cx16.r5
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x==cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x==cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    verify_success(12)
}
    sub test_not_expr() {
    uword @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0
    ; direct jump
        if x!=cx16.r4+1-cx16.r5
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x!=cx16.r4+1-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+1-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+30464-cx16.r5
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=cx16.r4+30464-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+30464-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+65535-cx16.r5
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x!=cx16.r4+1-cx16.r5
            goto lbl4a
        goto skip4a
lbl4a:   fail_uword(133,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip4b
lbl4b:   fail_uword(134,1)
skip4b:
        ; no else
        if x!=cx16.r4+1-cx16.r5
            fail_uword(135,1)

        ; with else
        if x!=cx16.r4+1-cx16.r5
            fail_uword(136,1)
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+30464-cx16.r5
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=cx16.r4+30464-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+30464-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+65535-cx16.r5
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x!=cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x!=cx16.r4+1-cx16.r5
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x!=cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x!=cx16.r4+1-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+1-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+30464-cx16.r5
            goto lbl8a
        goto skip8a
lbl8a:   fail_uword(137,30464)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip8b
lbl8b:   fail_uword(138,30464)
skip8b:
        ; no else
        if x!=cx16.r4+30464-cx16.r5
            fail_uword(139,30464)

        ; with else
        if x!=cx16.r4+30464-cx16.r5
            fail_uword(140,30464)
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+65535-cx16.r5
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x!=cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x!=cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    x=65535
    ; direct jump
        if x!=cx16.r4+1-cx16.r5
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=cx16.r4+1-cx16.r5
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
        ; no else
        if x!=cx16.r4+1-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+1-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+30464-cx16.r5
            goto lbl11a
        goto skip11a
lbl11a:   success++
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x!=cx16.r4+30464-cx16.r5
            goto cx16.r3
        goto skip11b
lbl11b:   success++
skip11b:
        ; no else
        if x!=cx16.r4+30464-cx16.r5
            success++

        ; with else
        if x!=cx16.r4+30464-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4+65535-cx16.r5
            goto lbl12a
        goto skip12a
lbl12a:   fail_uword(141,65535)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip12b
lbl12b:   fail_uword(142,65535)
skip12b:
        ; no else
        if x!=cx16.r4+65535-cx16.r5
            fail_uword(143,65535)

        ; with else
        if x!=cx16.r4+65535-cx16.r5
            fail_uword(144,65535)
        else
            cx16.r0L++

    verify_success(36)
}
    sub test_is_array() {
    uword @shared x
        uword[] values = [0, 0]
        uword[] sources = [0, 0]
        success = 0
    x=0
    sources[1]=0
    values[1]=1
    ; direct jump
        if x==values[1]
            goto lbl1a
        goto skip1a
lbl1a:   fail_uword(145,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   fail_uword(146,0)
skip1b:
        ; no else
        if x==values[1]
            fail_uword(147,0)

        ; with else
        if x==values[1]
            fail_uword(148,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl1c
        goto skip1c
lbl1c:   fail_uword(149,0)
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   fail_uword(150,0)
skip1d:
        ; no else
        if sources[1]==values[1]
            fail_uword(151,0)

        ; with else
        if sources[1]==values[1]
            fail_uword(152,0)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_uword(153,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_uword(154,0)
skip2b:
        ; no else
        if x==values[1]
            fail_uword(155,0)

        ; with else
        if x==values[1]
            fail_uword(156,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl2c
        goto skip2c
lbl2c:   fail_uword(157,0)
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   fail_uword(158,0)
skip2d:
        ; no else
        if sources[1]==values[1]
            fail_uword(159,0)

        ; with else
        if sources[1]==values[1]
            fail_uword(160,0)
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x==values[1]
            goto lbl3a
        goto skip3a
lbl3a:   fail_uword(161,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   fail_uword(162,0)
skip3b:
        ; no else
        if x==values[1]
            fail_uword(163,0)

        ; with else
        if x==values[1]
            fail_uword(164,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl3c
        goto skip3c
lbl3c:   fail_uword(165,0)
skip3c:
        ; indirect jump
        cx16.r3 = &lbl3d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip3d
lbl3d:   fail_uword(166,0)
skip3d:
        ; no else
        if sources[1]==values[1]
            fail_uword(167,0)

        ; with else
        if sources[1]==values[1]
            fail_uword(168,0)
        else
            cx16.r0L++

    x=1
    sources[1]=1
    values[1]=1
    ; direct jump
        if x==values[1]
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x==values[1]
            success++

        ; with else
        if x==values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl4c
        goto skip4c
lbl4c:   success++
skip4c:
        ; indirect jump
        cx16.r3 = &lbl4d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip4d
lbl4d:   success++
skip4d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(169,1)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(170,1)
skip5b:
        ; no else
        if x==values[1]
            fail_uword(171,1)

        ; with else
        if x==values[1]
            fail_uword(172,1)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl5c
        goto skip5c
lbl5c:   fail_uword(173,1)
skip5c:
        ; indirect jump
        cx16.r3 = &lbl5d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip5d
lbl5d:   fail_uword(174,1)
skip5d:
        ; no else
        if sources[1]==values[1]
            fail_uword(175,1)

        ; with else
        if sources[1]==values[1]
            fail_uword(176,1)
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x==values[1]
            goto lbl6a
        goto skip6a
lbl6a:   fail_uword(177,1)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   fail_uword(178,1)
skip6b:
        ; no else
        if x==values[1]
            fail_uword(179,1)

        ; with else
        if x==values[1]
            fail_uword(180,1)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl6c
        goto skip6c
lbl6c:   fail_uword(181,1)
skip6c:
        ; indirect jump
        cx16.r3 = &lbl6d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip6d
lbl6d:   fail_uword(182,1)
skip6d:
        ; no else
        if sources[1]==values[1]
            fail_uword(183,1)

        ; with else
        if sources[1]==values[1]
            fail_uword(184,1)
        else
            cx16.r0L++

    x=30464
    sources[1]=30464
    values[1]=1
    ; direct jump
        if x==values[1]
            goto lbl7a
        goto skip7a
lbl7a:   fail_uword(185,30464)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   fail_uword(186,30464)
skip7b:
        ; no else
        if x==values[1]
            fail_uword(187,30464)

        ; with else
        if x==values[1]
            fail_uword(188,30464)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl7c
        goto skip7c
lbl7c:   fail_uword(189,30464)
skip7c:
        ; indirect jump
        cx16.r3 = &lbl7d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip7d
lbl7d:   fail_uword(190,30464)
skip7d:
        ; no else
        if sources[1]==values[1]
            fail_uword(191,30464)

        ; with else
        if sources[1]==values[1]
            fail_uword(192,30464)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x==values[1]
            success++

        ; with else
        if x==values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl8c
        goto skip8c
lbl8c:   success++
skip8c:
        ; indirect jump
        cx16.r3 = &lbl8d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip8d
lbl8d:   success++
skip8d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x==values[1]
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(193,30464)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==values[1]
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(194,30464)
skip9b:
        ; no else
        if x==values[1]
            fail_uword(195,30464)

        ; with else
        if x==values[1]
            fail_uword(196,30464)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl9c
        goto skip9c
lbl9c:   fail_uword(197,30464)
skip9c:
        ; indirect jump
        cx16.r3 = &lbl9d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip9d
lbl9d:   fail_uword(198,30464)
skip9d:
        ; no else
        if sources[1]==values[1]
            fail_uword(199,30464)

        ; with else
        if sources[1]==values[1]
            fail_uword(200,30464)
        else
            cx16.r0L++

    x=65535
    sources[1]=65535
    values[1]=1
    ; direct jump
        if x==values[1]
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(201,65535)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==values[1]
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(202,65535)
skip10b:
        ; no else
        if x==values[1]
            fail_uword(203,65535)

        ; with else
        if x==values[1]
            fail_uword(204,65535)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl10c
        goto skip10c
lbl10c:   fail_uword(205,65535)
skip10c:
        ; indirect jump
        cx16.r3 = &lbl10d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip10d
lbl10d:   fail_uword(206,65535)
skip10d:
        ; no else
        if sources[1]==values[1]
            fail_uword(207,65535)

        ; with else
        if sources[1]==values[1]
            fail_uword(208,65535)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl11a
        goto skip11a
lbl11a:   fail_uword(209,65535)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==values[1]
            goto cx16.r3
        goto skip11b
lbl11b:   fail_uword(210,65535)
skip11b:
        ; no else
        if x==values[1]
            fail_uword(211,65535)

        ; with else
        if x==values[1]
            fail_uword(212,65535)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl11c
        goto skip11c
lbl11c:   fail_uword(213,65535)
skip11c:
        ; indirect jump
        cx16.r3 = &lbl11d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip11d
lbl11d:   fail_uword(214,65535)
skip11d:
        ; no else
        if sources[1]==values[1]
            fail_uword(215,65535)

        ; with else
        if sources[1]==values[1]
            fail_uword(216,65535)
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x==values[1]
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==values[1]
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x==values[1]
            success++

        ; with else
        if x==values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl12c
        goto skip12c
lbl12c:   success++
skip12c:
        ; indirect jump
        cx16.r3 = &lbl12d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip12d
lbl12d:   success++
skip12d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    verify_success(24)
}
    sub test_not_array() {
    uword @shared x
        uword[] values = [0, 0]
        uword[] sources = [0, 0]
        success = 0
    x=0
    sources[1]=0
    values[1]=1
    ; direct jump
        if x!=values[1]
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl1c
        goto skip1c
lbl1c:   success++
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   success++
skip1d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x!=values[1]
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl2c
        goto skip2c
lbl2c:   success++
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   success++
skip2d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x!=values[1]
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl3c
        goto skip3c
lbl3c:   success++
skip3c:
        ; indirect jump
        cx16.r3 = &lbl3d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip3d
lbl3d:   success++
skip3d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    x=1
    sources[1]=1
    values[1]=1
    ; direct jump
        if x!=values[1]
            goto lbl4a
        goto skip4a
lbl4a:   fail_uword(217,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   fail_uword(218,1)
skip4b:
        ; no else
        if x!=values[1]
            fail_uword(219,1)

        ; with else
        if x!=values[1]
            fail_uword(220,1)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl4c
        goto skip4c
lbl4c:   fail_uword(221,1)
skip4c:
        ; indirect jump
        cx16.r3 = &lbl4d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip4d
lbl4d:   fail_uword(222,1)
skip4d:
        ; no else
        if sources[1]!=values[1]
            fail_uword(223,1)

        ; with else
        if sources[1]!=values[1]
            fail_uword(224,1)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x!=values[1]
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl5c
        goto skip5c
lbl5c:   success++
skip5c:
        ; indirect jump
        cx16.r3 = &lbl5d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip5d
lbl5d:   success++
skip5d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x!=values[1]
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl6c
        goto skip6c
lbl6c:   success++
skip6c:
        ; indirect jump
        cx16.r3 = &lbl6d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip6d
lbl6d:   success++
skip6d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    x=30464
    sources[1]=30464
    values[1]=1
    ; direct jump
        if x!=values[1]
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x!=values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl7c
        goto skip7c
lbl7c:   success++
skip7c:
        ; indirect jump
        cx16.r3 = &lbl7d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip7d
lbl7d:   success++
skip7d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x!=values[1]
            goto lbl8a
        goto skip8a
lbl8a:   fail_uword(225,30464)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   fail_uword(226,30464)
skip8b:
        ; no else
        if x!=values[1]
            fail_uword(227,30464)

        ; with else
        if x!=values[1]
            fail_uword(228,30464)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl8c
        goto skip8c
lbl8c:   fail_uword(229,30464)
skip8c:
        ; indirect jump
        cx16.r3 = &lbl8d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip8d
lbl8d:   fail_uword(230,30464)
skip8d:
        ; no else
        if sources[1]!=values[1]
            fail_uword(231,30464)

        ; with else
        if sources[1]!=values[1]
            fail_uword(232,30464)
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x!=values[1]
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x!=values[1]
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl9c
        goto skip9c
lbl9c:   success++
skip9c:
        ; indirect jump
        cx16.r3 = &lbl9d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip9d
lbl9d:   success++
skip9d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    x=65535
    sources[1]=65535
    values[1]=1
    ; direct jump
        if x!=values[1]
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=values[1]
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl10c
        goto skip10c
lbl10c:   success++
skip10c:
        ; indirect jump
        cx16.r3 = &lbl10d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip10d
lbl10d:   success++
skip10d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x!=values[1]
            goto lbl11a
        goto skip11a
lbl11a:   success++
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x!=values[1]
            goto cx16.r3
        goto skip11b
lbl11b:   success++
skip11b:
        ; no else
        if x!=values[1]
            success++

        ; with else
        if x!=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl11c
        goto skip11c
lbl11c:   success++
skip11c:
        ; indirect jump
        cx16.r3 = &lbl11d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip11d
lbl11d:   success++
skip11d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x!=values[1]
            goto lbl12a
        goto skip12a
lbl12a:   fail_uword(233,65535)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=values[1]
            goto cx16.r3
        goto skip12b
lbl12b:   fail_uword(234,65535)
skip12b:
        ; no else
        if x!=values[1]
            fail_uword(235,65535)

        ; with else
        if x!=values[1]
            fail_uword(236,65535)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl12c
        goto skip12c
lbl12c:   fail_uword(237,65535)
skip12c:
        ; indirect jump
        cx16.r3 = &lbl12d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip12d
lbl12d:   fail_uword(238,65535)
skip12d:
        ; no else
        if sources[1]!=values[1]
            fail_uword(239,65535)

        ; with else
        if sources[1]!=values[1]
            fail_uword(240,65535)
        else
            cx16.r0L++

    verify_success(72)
}

}

