
%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {
    ubyte success = 0
    str datatype = "word"
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
        word @shared x
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
            
        x = -9999
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
        word @shared x
        success = 0

        x=-9999
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
    word @shared x
        success = 0
    x=-21829
    ; direct jump
        if x==-21829
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==-21829
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x==-21829
            success++

        ; with else
        if x==-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==170
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(1,-21829)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==170
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(2,-21829)
skip2b:
        ; no else
        if x==170
            fail_word(3,-21829)

        ; with else
        if x==170
            fail_word(4,-21829)
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(5,-21829)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==30464
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(6,-21829)
skip3b:
        ; no else
        if x==30464
            fail_word(7,-21829)

        ; with else
        if x==30464
            fail_word(8,-21829)
        else
            cx16.r0L++

    ; direct jump
        if x==32767
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(9,-21829)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==32767
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(10,-21829)
skip4b:
        ; no else
        if x==32767
            fail_word(11,-21829)

        ; with else
        if x==32767
            fail_word(12,-21829)
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x==-21829
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(13,0)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==-21829
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(14,0)
skip5b:
        ; no else
        if x==-21829
            fail_word(15,0)

        ; with else
        if x==-21829
            fail_word(16,0)
        else
            cx16.r0L++

    ; direct jump
        if x==170
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(17,0)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==170
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(18,0)
skip6b:
        ; no else
        if x==170
            fail_word(19,0)

        ; with else
        if x==170
            fail_word(20,0)
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(21,0)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==30464
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(22,0)
skip7b:
        ; no else
        if x==30464
            fail_word(23,0)

        ; with else
        if x==30464
            fail_word(24,0)
        else
            cx16.r0L++

    ; direct jump
        if x==32767
            goto lbl8a
        goto skip8a
lbl8a:   fail_word(25,0)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==32767
            goto cx16.r3
        goto skip8b
lbl8b:   fail_word(26,0)
skip8b:
        ; no else
        if x==32767
            fail_word(27,0)

        ; with else
        if x==32767
            fail_word(28,0)
        else
            cx16.r0L++

    x=170
    ; direct jump
        if x==-21829
            goto lbl9a
        goto skip9a
lbl9a:   fail_word(29,170)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==-21829
            goto cx16.r3
        goto skip9b
lbl9b:   fail_word(30,170)
skip9b:
        ; no else
        if x==-21829
            fail_word(31,170)

        ; with else
        if x==-21829
            fail_word(32,170)
        else
            cx16.r0L++

    ; direct jump
        if x==170
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==170
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
        ; no else
        if x==170
            success++

        ; with else
        if x==170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(33,170)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==30464
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(34,170)
skip11b:
        ; no else
        if x==30464
            fail_word(35,170)

        ; with else
        if x==30464
            fail_word(36,170)
        else
            cx16.r0L++

    ; direct jump
        if x==32767
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(37,170)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==32767
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(38,170)
skip12b:
        ; no else
        if x==32767
            fail_word(39,170)

        ; with else
        if x==32767
            fail_word(40,170)
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x==-21829
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(41,30464)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x==-21829
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(42,30464)
skip13b:
        ; no else
        if x==-21829
            fail_word(43,30464)

        ; with else
        if x==-21829
            fail_word(44,30464)
        else
            cx16.r0L++

    ; direct jump
        if x==170
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(45,30464)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x==170
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(46,30464)
skip14b:
        ; no else
        if x==170
            fail_word(47,30464)

        ; with else
        if x==170
            fail_word(48,30464)
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x==30464
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
        ; no else
        if x==30464
            success++

        ; with else
        if x==30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==32767
            goto lbl16a
        goto skip16a
lbl16a:   fail_word(49,30464)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x==32767
            goto cx16.r3
        goto skip16b
lbl16b:   fail_word(50,30464)
skip16b:
        ; no else
        if x==32767
            fail_word(51,30464)

        ; with else
        if x==32767
            fail_word(52,30464)
        else
            cx16.r0L++

    x=32767
    ; direct jump
        if x==-21829
            goto lbl17a
        goto skip17a
lbl17a:   fail_word(53,32767)
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x==-21829
            goto cx16.r3
        goto skip17b
lbl17b:   fail_word(54,32767)
skip17b:
        ; no else
        if x==-21829
            fail_word(55,32767)

        ; with else
        if x==-21829
            fail_word(56,32767)
        else
            cx16.r0L++

    ; direct jump
        if x==170
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(57,32767)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x==170
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(58,32767)
skip18b:
        ; no else
        if x==170
            fail_word(59,32767)

        ; with else
        if x==170
            fail_word(60,32767)
        else
            cx16.r0L++

    ; direct jump
        if x==30464
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(61,32767)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x==30464
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(62,32767)
skip19b:
        ; no else
        if x==30464
            fail_word(63,32767)

        ; with else
        if x==30464
            fail_word(64,32767)
        else
            cx16.r0L++

    ; direct jump
        if x==32767
            goto lbl20a
        goto skip20a
lbl20a:   success++
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x==32767
            goto cx16.r3
        goto skip20b
lbl20b:   success++
skip20b:
        ; no else
        if x==32767
            success++

        ; with else
        if x==32767
            success++
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_not_number() {
    word @shared x
        success = 0
    x=-21829
    ; direct jump
        if x!=-21829
            goto lbl1a
        goto skip1a
lbl1a:   fail_word(65,-21829)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=-21829
            goto cx16.r3
        goto skip1b
lbl1b:   fail_word(66,-21829)
skip1b:
        ; no else
        if x!=-21829
            fail_word(67,-21829)

        ; with else
        if x!=-21829
            fail_word(68,-21829)
        else
            cx16.r0L++

    ; direct jump
        if x!=170
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=170
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=170
            success++

        ; with else
        if x!=170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=30464
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=30464
            success++

        ; with else
        if x!=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=32767
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=32767
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x!=32767
            success++

        ; with else
        if x!=32767
            success++
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x!=-21829
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=-21829
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=-21829
            success++

        ; with else
        if x!=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=170
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=170
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x!=170
            success++

        ; with else
        if x!=170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x!=30464
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x!=30464
            success++

        ; with else
        if x!=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=32767
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=32767
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x!=32767
            success++

        ; with else
        if x!=32767
            success++
        else
            cx16.r0L++

    x=170
    ; direct jump
        if x!=-21829
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x!=-21829
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x!=-21829
            success++

        ; with else
        if x!=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=170
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(69,170)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=170
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(70,170)
skip10b:
        ; no else
        if x!=170
            fail_word(71,170)

        ; with else
        if x!=170
            fail_word(72,170)
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
        if x!=32767
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=32767
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x!=32767
            success++

        ; with else
        if x!=32767
            success++
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x!=-21829
            goto lbl13a
        goto skip13a
lbl13a:   success++
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x!=-21829
            goto cx16.r3
        goto skip13b
lbl13b:   success++
skip13b:
        ; no else
        if x!=-21829
            success++

        ; with else
        if x!=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=170
            goto lbl14a
        goto skip14a
lbl14a:   success++
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x!=170
            goto cx16.r3
        goto skip14b
lbl14b:   success++
skip14b:
        ; no else
        if x!=170
            success++

        ; with else
        if x!=170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl15a
        goto skip15a
lbl15a:   fail_word(73,30464)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x!=30464
            goto cx16.r3
        goto skip15b
lbl15b:   fail_word(74,30464)
skip15b:
        ; no else
        if x!=30464
            fail_word(75,30464)

        ; with else
        if x!=30464
            fail_word(76,30464)
        else
            cx16.r0L++

    ; direct jump
        if x!=32767
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x!=32767
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
        ; no else
        if x!=32767
            success++

        ; with else
        if x!=32767
            success++
        else
            cx16.r0L++

    x=32767
    ; direct jump
        if x!=-21829
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x!=-21829
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
        ; no else
        if x!=-21829
            success++

        ; with else
        if x!=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=170
            goto lbl18a
        goto skip18a
lbl18a:   success++
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x!=170
            goto cx16.r3
        goto skip18b
lbl18b:   success++
skip18b:
        ; no else
        if x!=170
            success++

        ; with else
        if x!=170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=30464
            goto lbl19a
        goto skip19a
lbl19a:   success++
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x!=30464
            goto cx16.r3
        goto skip19b
lbl19b:   success++
skip19b:
        ; no else
        if x!=30464
            success++

        ; with else
        if x!=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=32767
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(77,32767)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x!=32767
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(78,32767)
skip20b:
        ; no else
        if x!=32767
            fail_word(79,32767)

        ; with else
        if x!=32767
            fail_word(80,32767)
        else
            cx16.r0L++

    verify_success(64)
}
    sub test_is_var() {
    word @shared x, value
        success = 0
    x=-21829
    value=-21829
    ; direct jump
        if x==value
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==value
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x==value
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(81,-21829)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(82,-21829)
skip2b:
        ; no else
        if x==value
            fail_word(83,-21829)

        ; with else
        if x==value
            fail_word(84,-21829)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(85,-21829)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==value
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(86,-21829)
skip3b:
        ; no else
        if x==value
            fail_word(87,-21829)

        ; with else
        if x==value
            fail_word(88,-21829)
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x==value
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(89,-21829)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==value
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(90,-21829)
skip4b:
        ; no else
        if x==value
            fail_word(91,-21829)

        ; with else
        if x==value
            fail_word(92,-21829)
        else
            cx16.r0L++

    x=0
    value=-21829
    ; direct jump
        if x==value
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(93,0)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==value
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(94,0)
skip5b:
        ; no else
        if x==value
            fail_word(95,0)

        ; with else
        if x==value
            fail_word(96,0)
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x==value
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(97,0)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==value
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(98,0)
skip6b:
        ; no else
        if x==value
            fail_word(99,0)

        ; with else
        if x==value
            fail_word(100,0)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(101,0)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==value
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(102,0)
skip7b:
        ; no else
        if x==value
            fail_word(103,0)

        ; with else
        if x==value
            fail_word(104,0)
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x==value
            goto lbl8a
        goto skip8a
lbl8a:   fail_word(105,0)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==value
            goto cx16.r3
        goto skip8b
lbl8b:   fail_word(106,0)
skip8b:
        ; no else
        if x==value
            fail_word(107,0)

        ; with else
        if x==value
            fail_word(108,0)
        else
            cx16.r0L++

    x=170
    value=-21829
    ; direct jump
        if x==value
            goto lbl9a
        goto skip9a
lbl9a:   fail_word(109,170)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==value
            goto cx16.r3
        goto skip9b
lbl9b:   fail_word(110,170)
skip9b:
        ; no else
        if x==value
            fail_word(111,170)

        ; with else
        if x==value
            fail_word(112,170)
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x==value
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==value
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
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
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(113,170)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==value
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(114,170)
skip11b:
        ; no else
        if x==value
            fail_word(115,170)

        ; with else
        if x==value
            fail_word(116,170)
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x==value
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(117,170)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==value
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(118,170)
skip12b:
        ; no else
        if x==value
            fail_word(119,170)

        ; with else
        if x==value
            fail_word(120,170)
        else
            cx16.r0L++

    x=30464
    value=-21829
    ; direct jump
        if x==value
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(121,30464)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x==value
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(122,30464)
skip13b:
        ; no else
        if x==value
            fail_word(123,30464)

        ; with else
        if x==value
            fail_word(124,30464)
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x==value
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(125,30464)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x==value
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(126,30464)
skip14b:
        ; no else
        if x==value
            fail_word(127,30464)

        ; with else
        if x==value
            fail_word(128,30464)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x==value
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x==value
            goto lbl16a
        goto skip16a
lbl16a:   fail_word(129,30464)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x==value
            goto cx16.r3
        goto skip16b
lbl16b:   fail_word(130,30464)
skip16b:
        ; no else
        if x==value
            fail_word(131,30464)

        ; with else
        if x==value
            fail_word(132,30464)
        else
            cx16.r0L++

    x=32767
    value=-21829
    ; direct jump
        if x==value
            goto lbl17a
        goto skip17a
lbl17a:   fail_word(133,32767)
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x==value
            goto cx16.r3
        goto skip17b
lbl17b:   fail_word(134,32767)
skip17b:
        ; no else
        if x==value
            fail_word(135,32767)

        ; with else
        if x==value
            fail_word(136,32767)
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x==value
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(137,32767)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x==value
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(138,32767)
skip18b:
        ; no else
        if x==value
            fail_word(139,32767)

        ; with else
        if x==value
            fail_word(140,32767)
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x==value
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(141,32767)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x==value
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(142,32767)
skip19b:
        ; no else
        if x==value
            fail_word(143,32767)

        ; with else
        if x==value
            fail_word(144,32767)
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x==value
            goto lbl20a
        goto skip20a
lbl20a:   success++
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x==value
            goto cx16.r3
        goto skip20b
lbl20b:   success++
skip20b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_not_var() {
    word @shared x, value
        success = 0
    x=-21829
    value=-21829
    ; direct jump
        if x!=value
            goto lbl1a
        goto skip1a
lbl1a:   fail_word(145,-21829)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=value
            goto cx16.r3
        goto skip1b
lbl1b:   fail_word(146,-21829)
skip1b:
        ; no else
        if x!=value
            fail_word(147,-21829)

        ; with else
        if x!=value
            fail_word(148,-21829)
        else
            cx16.r0L++

    value=170
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

    value=30464
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

    value=32767
    ; direct jump
        if x!=value
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=value
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    x=0
    value=-21829
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

    value=170
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

    value=30464
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

    value=32767
    ; direct jump
        if x!=value
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=value
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    x=170
    value=-21829
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

    value=170
    ; direct jump
        if x!=value
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(149,170)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=value
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(150,170)
skip10b:
        ; no else
        if x!=value
            fail_word(151,170)

        ; with else
        if x!=value
            fail_word(152,170)
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

    value=32767
    ; direct jump
        if x!=value
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=value
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    x=30464
    value=-21829
    ; direct jump
        if x!=value
            goto lbl13a
        goto skip13a
lbl13a:   success++
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x!=value
            goto cx16.r3
        goto skip13b
lbl13b:   success++
skip13b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x!=value
            goto lbl14a
        goto skip14a
lbl14a:   success++
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x!=value
            goto cx16.r3
        goto skip14b
lbl14b:   success++
skip14b:
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
            goto lbl15a
        goto skip15a
lbl15a:   fail_word(153,30464)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x!=value
            goto cx16.r3
        goto skip15b
lbl15b:   fail_word(154,30464)
skip15b:
        ; no else
        if x!=value
            fail_word(155,30464)

        ; with else
        if x!=value
            fail_word(156,30464)
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x!=value
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x!=value
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    x=32767
    value=-21829
    ; direct jump
        if x!=value
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x!=value
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x!=value
            goto lbl18a
        goto skip18a
lbl18a:   success++
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x!=value
            goto cx16.r3
        goto skip18b
lbl18b:   success++
skip18b:
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
            goto lbl19a
        goto skip19a
lbl19a:   success++
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x!=value
            goto cx16.r3
        goto skip19b
lbl19b:   success++
skip19b:
        ; no else
        if x!=value
            success++

        ; with else
        if x!=value
            success++
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x!=value
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(157,32767)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x!=value
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(158,32767)
skip20b:
        ; no else
        if x!=value
            fail_word(159,32767)

        ; with else
        if x!=value
            fail_word(160,32767)
        else
            cx16.r0L++

    verify_success(64)
}
    sub test_is_expr() {
    word @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=-21829
    ; direct jump
        if x==cx16.r4s+-21829-cx16.r5s
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x==cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x==cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+170-cx16.r5s
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(161,-21829)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(162,-21829)
skip2b:
        ; no else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(163,-21829)

        ; with else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(164,-21829)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+30464-cx16.r5s
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(165,-21829)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(166,-21829)
skip3b:
        ; no else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(167,-21829)

        ; with else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(168,-21829)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+32767-cx16.r5s
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(169,-21829)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(170,-21829)
skip4b:
        ; no else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(171,-21829)

        ; with else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(172,-21829)
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x==cx16.r4s+-21829-cx16.r5s
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(173,0)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(174,0)
skip5b:
        ; no else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(175,0)

        ; with else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(176,0)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+170-cx16.r5s
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(177,0)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(178,0)
skip6b:
        ; no else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(179,0)

        ; with else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(180,0)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+30464-cx16.r5s
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(181,0)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(182,0)
skip7b:
        ; no else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(183,0)

        ; with else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(184,0)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+32767-cx16.r5s
            goto lbl8a
        goto skip8a
lbl8a:   fail_word(185,0)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip8b
lbl8b:   fail_word(186,0)
skip8b:
        ; no else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(187,0)

        ; with else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(188,0)
        else
            cx16.r0L++

    x=170
    ; direct jump
        if x==cx16.r4s+-21829-cx16.r5s
            goto lbl9a
        goto skip9a
lbl9a:   fail_word(189,170)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip9b
lbl9b:   fail_word(190,170)
skip9b:
        ; no else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(191,170)

        ; with else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(192,170)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+170-cx16.r5s
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
        ; no else
        if x==cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x==cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+30464-cx16.r5s
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(193,170)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(194,170)
skip11b:
        ; no else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(195,170)

        ; with else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(196,170)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+32767-cx16.r5s
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(197,170)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(198,170)
skip12b:
        ; no else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(199,170)

        ; with else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(200,170)
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x==cx16.r4s+-21829-cx16.r5s
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(201,30464)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x==cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(202,30464)
skip13b:
        ; no else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(203,30464)

        ; with else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(204,30464)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+170-cx16.r5s
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(205,30464)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x==cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(206,30464)
skip14b:
        ; no else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(207,30464)

        ; with else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(208,30464)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+30464-cx16.r5s
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x==cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
        ; no else
        if x==cx16.r4s+30464-cx16.r5s
            success++

        ; with else
        if x==cx16.r4s+30464-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+32767-cx16.r5s
            goto lbl16a
        goto skip16a
lbl16a:   fail_word(209,30464)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x==cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip16b
lbl16b:   fail_word(210,30464)
skip16b:
        ; no else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(211,30464)

        ; with else
        if x==cx16.r4s+32767-cx16.r5s
            fail_word(212,30464)
        else
            cx16.r0L++

    x=32767
    ; direct jump
        if x==cx16.r4s+-21829-cx16.r5s
            goto lbl17a
        goto skip17a
lbl17a:   fail_word(213,32767)
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x==cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip17b
lbl17b:   fail_word(214,32767)
skip17b:
        ; no else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(215,32767)

        ; with else
        if x==cx16.r4s+-21829-cx16.r5s
            fail_word(216,32767)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+170-cx16.r5s
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(217,32767)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x==cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(218,32767)
skip18b:
        ; no else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(219,32767)

        ; with else
        if x==cx16.r4s+170-cx16.r5s
            fail_word(220,32767)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+30464-cx16.r5s
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(221,32767)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x==cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(222,32767)
skip19b:
        ; no else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(223,32767)

        ; with else
        if x==cx16.r4s+30464-cx16.r5s
            fail_word(224,32767)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4s+32767-cx16.r5s
            goto lbl20a
        goto skip20a
lbl20a:   success++
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x==cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip20b
lbl20b:   success++
skip20b:
        ; no else
        if x==cx16.r4s+32767-cx16.r5s
            success++

        ; with else
        if x==cx16.r4s+32767-cx16.r5s
            success++
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_not_expr() {
    word @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=-21829
    ; direct jump
        if x!=cx16.r4s+-21829-cx16.r5s
            goto lbl1a
        goto skip1a
lbl1a:   fail_word(225,-21829)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip1b
lbl1b:   fail_word(226,-21829)
skip1b:
        ; no else
        if x!=cx16.r4s+-21829-cx16.r5s
            fail_word(227,-21829)

        ; with else
        if x!=cx16.r4s+-21829-cx16.r5s
            fail_word(228,-21829)
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+170-cx16.r5s
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+30464-cx16.r5s
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=cx16.r4s+30464-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+30464-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+32767-cx16.r5s
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x!=cx16.r4s+32767-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+32767-cx16.r5s
            success++
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x!=cx16.r4s+-21829-cx16.r5s
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+170-cx16.r5s
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x!=cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+30464-cx16.r5s
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x!=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x!=cx16.r4s+30464-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+30464-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+32767-cx16.r5s
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x!=cx16.r4s+32767-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+32767-cx16.r5s
            success++
        else
            cx16.r0L++

    x=170
    ; direct jump
        if x!=cx16.r4s+-21829-cx16.r5s
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x!=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+170-cx16.r5s
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(229,170)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(230,170)
skip10b:
        ; no else
        if x!=cx16.r4s+170-cx16.r5s
            fail_word(231,170)

        ; with else
        if x!=cx16.r4s+170-cx16.r5s
            fail_word(232,170)
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+30464-cx16.r5s
            goto lbl11a
        goto skip11a
lbl11a:   success++
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x!=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip11b
lbl11b:   success++
skip11b:
        ; no else
        if x!=cx16.r4s+30464-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+30464-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+32767-cx16.r5s
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x!=cx16.r4s+32767-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+32767-cx16.r5s
            success++
        else
            cx16.r0L++

    x=30464
    ; direct jump
        if x!=cx16.r4s+-21829-cx16.r5s
            goto lbl13a
        goto skip13a
lbl13a:   success++
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x!=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip13b
lbl13b:   success++
skip13b:
        ; no else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+170-cx16.r5s
            goto lbl14a
        goto skip14a
lbl14a:   success++
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x!=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip14b
lbl14b:   success++
skip14b:
        ; no else
        if x!=cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+30464-cx16.r5s
            goto lbl15a
        goto skip15a
lbl15a:   fail_word(233,30464)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x!=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip15b
lbl15b:   fail_word(234,30464)
skip15b:
        ; no else
        if x!=cx16.r4s+30464-cx16.r5s
            fail_word(235,30464)

        ; with else
        if x!=cx16.r4s+30464-cx16.r5s
            fail_word(236,30464)
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+32767-cx16.r5s
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x!=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
        ; no else
        if x!=cx16.r4s+32767-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+32767-cx16.r5s
            success++
        else
            cx16.r0L++

    x=32767
    ; direct jump
        if x!=cx16.r4s+-21829-cx16.r5s
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x!=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
        ; no else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+170-cx16.r5s
            goto lbl18a
        goto skip18a
lbl18a:   success++
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x!=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip18b
lbl18b:   success++
skip18b:
        ; no else
        if x!=cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+30464-cx16.r5s
            goto lbl19a
        goto skip19a
lbl19a:   success++
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x!=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip19b
lbl19b:   success++
skip19b:
        ; no else
        if x!=cx16.r4s+30464-cx16.r5s
            success++

        ; with else
        if x!=cx16.r4s+30464-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4s+32767-cx16.r5s
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(237,32767)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x!=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(238,32767)
skip20b:
        ; no else
        if x!=cx16.r4s+32767-cx16.r5s
            fail_word(239,32767)

        ; with else
        if x!=cx16.r4s+32767-cx16.r5s
            fail_word(240,32767)
        else
            cx16.r0L++

    verify_success(64)
}
    sub test_is_array() {
    word @shared x
        word[] values = [0, 0]
        word[] sources = [0, 0]
        success = 0
    x=-21829
    sources[1]=-21829
    values[1]=-21829
    ; direct jump
        if x==values[1]
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
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
            goto lbl1c
        goto skip1c
lbl1c:   success++
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   success++
skip1d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x==values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(241,-21829)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(242,-21829)
skip2b:
        ; no else
        if x==values[1]
            fail_word(243,-21829)

        ; with else
        if x==values[1]
            fail_word(244,-21829)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl2c
        goto skip2c
lbl2c:   fail_word(245,-21829)
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   fail_word(246,-21829)
skip2d:
        ; no else
        if sources[1]==values[1]
            fail_word(247,-21829)

        ; with else
        if sources[1]==values[1]
            fail_word(248,-21829)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(249,-21829)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(250,-21829)
skip3b:
        ; no else
        if x==values[1]
            fail_word(251,-21829)

        ; with else
        if x==values[1]
            fail_word(252,-21829)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl3c
        goto skip3c
lbl3c:   fail_word(253,-21829)
skip3c:
        ; indirect jump
        cx16.r3 = &lbl3d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip3d
lbl3d:   fail_word(254,-21829)
skip3d:
        ; no else
        if sources[1]==values[1]
            fail_word(255,-21829)

        ; with else
        if sources[1]==values[1]
            fail_word(256,-21829)
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x==values[1]
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(257,-21829)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(258,-21829)
skip4b:
        ; no else
        if x==values[1]
            fail_word(259,-21829)

        ; with else
        if x==values[1]
            fail_word(260,-21829)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl4c
        goto skip4c
lbl4c:   fail_word(261,-21829)
skip4c:
        ; indirect jump
        cx16.r3 = &lbl4d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip4d
lbl4d:   fail_word(262,-21829)
skip4d:
        ; no else
        if sources[1]==values[1]
            fail_word(263,-21829)

        ; with else
        if sources[1]==values[1]
            fail_word(264,-21829)
        else
            cx16.r0L++

    x=0
    sources[1]=0
    values[1]=-21829
    ; direct jump
        if x==values[1]
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(265,0)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(266,0)
skip5b:
        ; no else
        if x==values[1]
            fail_word(267,0)

        ; with else
        if x==values[1]
            fail_word(268,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl5c
        goto skip5c
lbl5c:   fail_word(269,0)
skip5c:
        ; indirect jump
        cx16.r3 = &lbl5d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip5d
lbl5d:   fail_word(270,0)
skip5d:
        ; no else
        if sources[1]==values[1]
            fail_word(271,0)

        ; with else
        if sources[1]==values[1]
            fail_word(272,0)
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x==values[1]
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(273,0)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(274,0)
skip6b:
        ; no else
        if x==values[1]
            fail_word(275,0)

        ; with else
        if x==values[1]
            fail_word(276,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl6c
        goto skip6c
lbl6c:   fail_word(277,0)
skip6c:
        ; indirect jump
        cx16.r3 = &lbl6d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip6d
lbl6d:   fail_word(278,0)
skip6d:
        ; no else
        if sources[1]==values[1]
            fail_word(279,0)

        ; with else
        if sources[1]==values[1]
            fail_word(280,0)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(281,0)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x==values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(282,0)
skip7b:
        ; no else
        if x==values[1]
            fail_word(283,0)

        ; with else
        if x==values[1]
            fail_word(284,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl7c
        goto skip7c
lbl7c:   fail_word(285,0)
skip7c:
        ; indirect jump
        cx16.r3 = &lbl7d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip7d
lbl7d:   fail_word(286,0)
skip7d:
        ; no else
        if sources[1]==values[1]
            fail_word(287,0)

        ; with else
        if sources[1]==values[1]
            fail_word(288,0)
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x==values[1]
            goto lbl8a
        goto skip8a
lbl8a:   fail_word(289,0)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x==values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   fail_word(290,0)
skip8b:
        ; no else
        if x==values[1]
            fail_word(291,0)

        ; with else
        if x==values[1]
            fail_word(292,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl8c
        goto skip8c
lbl8c:   fail_word(293,0)
skip8c:
        ; indirect jump
        cx16.r3 = &lbl8d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip8d
lbl8d:   fail_word(294,0)
skip8d:
        ; no else
        if sources[1]==values[1]
            fail_word(295,0)

        ; with else
        if sources[1]==values[1]
            fail_word(296,0)
        else
            cx16.r0L++

    x=170
    sources[1]=170
    values[1]=-21829
    ; direct jump
        if x==values[1]
            goto lbl9a
        goto skip9a
lbl9a:   fail_word(297,170)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x==values[1]
            goto cx16.r3
        goto skip9b
lbl9b:   fail_word(298,170)
skip9b:
        ; no else
        if x==values[1]
            fail_word(299,170)

        ; with else
        if x==values[1]
            fail_word(300,170)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl9c
        goto skip9c
lbl9c:   fail_word(301,170)
skip9c:
        ; indirect jump
        cx16.r3 = &lbl9d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip9d
lbl9d:   fail_word(302,170)
skip9d:
        ; no else
        if sources[1]==values[1]
            fail_word(303,170)

        ; with else
        if sources[1]==values[1]
            fail_word(304,170)
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x==values[1]
            goto lbl10a
        goto skip10a
lbl10a:   success++
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x==values[1]
            goto cx16.r3
        goto skip10b
lbl10b:   success++
skip10b:
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
            goto lbl10c
        goto skip10c
lbl10c:   success++
skip10c:
        ; indirect jump
        cx16.r3 = &lbl10d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip10d
lbl10d:   success++
skip10d:
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
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(305,170)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x==values[1]
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(306,170)
skip11b:
        ; no else
        if x==values[1]
            fail_word(307,170)

        ; with else
        if x==values[1]
            fail_word(308,170)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl11c
        goto skip11c
lbl11c:   fail_word(309,170)
skip11c:
        ; indirect jump
        cx16.r3 = &lbl11d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip11d
lbl11d:   fail_word(310,170)
skip11d:
        ; no else
        if sources[1]==values[1]
            fail_word(311,170)

        ; with else
        if sources[1]==values[1]
            fail_word(312,170)
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x==values[1]
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(313,170)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x==values[1]
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(314,170)
skip12b:
        ; no else
        if x==values[1]
            fail_word(315,170)

        ; with else
        if x==values[1]
            fail_word(316,170)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl12c
        goto skip12c
lbl12c:   fail_word(317,170)
skip12c:
        ; indirect jump
        cx16.r3 = &lbl12d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip12d
lbl12d:   fail_word(318,170)
skip12d:
        ; no else
        if sources[1]==values[1]
            fail_word(319,170)

        ; with else
        if sources[1]==values[1]
            fail_word(320,170)
        else
            cx16.r0L++

    x=30464
    sources[1]=30464
    values[1]=-21829
    ; direct jump
        if x==values[1]
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(321,30464)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x==values[1]
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(322,30464)
skip13b:
        ; no else
        if x==values[1]
            fail_word(323,30464)

        ; with else
        if x==values[1]
            fail_word(324,30464)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl13c
        goto skip13c
lbl13c:   fail_word(325,30464)
skip13c:
        ; indirect jump
        cx16.r3 = &lbl13d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip13d
lbl13d:   fail_word(326,30464)
skip13d:
        ; no else
        if sources[1]==values[1]
            fail_word(327,30464)

        ; with else
        if sources[1]==values[1]
            fail_word(328,30464)
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x==values[1]
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(329,30464)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x==values[1]
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(330,30464)
skip14b:
        ; no else
        if x==values[1]
            fail_word(331,30464)

        ; with else
        if x==values[1]
            fail_word(332,30464)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl14c
        goto skip14c
lbl14c:   fail_word(333,30464)
skip14c:
        ; indirect jump
        cx16.r3 = &lbl14d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip14d
lbl14d:   fail_word(334,30464)
skip14d:
        ; no else
        if sources[1]==values[1]
            fail_word(335,30464)

        ; with else
        if sources[1]==values[1]
            fail_word(336,30464)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x==values[1]
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
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
            goto lbl15c
        goto skip15c
lbl15c:   success++
skip15c:
        ; indirect jump
        cx16.r3 = &lbl15d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip15d
lbl15d:   success++
skip15d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x==values[1]
            goto lbl16a
        goto skip16a
lbl16a:   fail_word(337,30464)
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x==values[1]
            goto cx16.r3
        goto skip16b
lbl16b:   fail_word(338,30464)
skip16b:
        ; no else
        if x==values[1]
            fail_word(339,30464)

        ; with else
        if x==values[1]
            fail_word(340,30464)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl16c
        goto skip16c
lbl16c:   fail_word(341,30464)
skip16c:
        ; indirect jump
        cx16.r3 = &lbl16d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip16d
lbl16d:   fail_word(342,30464)
skip16d:
        ; no else
        if sources[1]==values[1]
            fail_word(343,30464)

        ; with else
        if sources[1]==values[1]
            fail_word(344,30464)
        else
            cx16.r0L++

    x=32767
    sources[1]=32767
    values[1]=-21829
    ; direct jump
        if x==values[1]
            goto lbl17a
        goto skip17a
lbl17a:   fail_word(345,32767)
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x==values[1]
            goto cx16.r3
        goto skip17b
lbl17b:   fail_word(346,32767)
skip17b:
        ; no else
        if x==values[1]
            fail_word(347,32767)

        ; with else
        if x==values[1]
            fail_word(348,32767)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl17c
        goto skip17c
lbl17c:   fail_word(349,32767)
skip17c:
        ; indirect jump
        cx16.r3 = &lbl17d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip17d
lbl17d:   fail_word(350,32767)
skip17d:
        ; no else
        if sources[1]==values[1]
            fail_word(351,32767)

        ; with else
        if sources[1]==values[1]
            fail_word(352,32767)
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x==values[1]
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(353,32767)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x==values[1]
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(354,32767)
skip18b:
        ; no else
        if x==values[1]
            fail_word(355,32767)

        ; with else
        if x==values[1]
            fail_word(356,32767)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl18c
        goto skip18c
lbl18c:   fail_word(357,32767)
skip18c:
        ; indirect jump
        cx16.r3 = &lbl18d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip18d
lbl18d:   fail_word(358,32767)
skip18d:
        ; no else
        if sources[1]==values[1]
            fail_word(359,32767)

        ; with else
        if sources[1]==values[1]
            fail_word(360,32767)
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x==values[1]
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(361,32767)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x==values[1]
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(362,32767)
skip19b:
        ; no else
        if x==values[1]
            fail_word(363,32767)

        ; with else
        if x==values[1]
            fail_word(364,32767)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl19c
        goto skip19c
lbl19c:   fail_word(365,32767)
skip19c:
        ; indirect jump
        cx16.r3 = &lbl19d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip19d
lbl19d:   fail_word(366,32767)
skip19d:
        ; no else
        if sources[1]==values[1]
            fail_word(367,32767)

        ; with else
        if sources[1]==values[1]
            fail_word(368,32767)
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x==values[1]
            goto lbl20a
        goto skip20a
lbl20a:   success++
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x==values[1]
            goto cx16.r3
        goto skip20b
lbl20b:   success++
skip20b:
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
            goto lbl20c
        goto skip20c
lbl20c:   success++
skip20c:
        ; indirect jump
        cx16.r3 = &lbl20d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip20d
lbl20d:   success++
skip20d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    verify_success(32)
}
    sub test_not_array() {
    word @shared x
        word[] values = [0, 0]
        word[] sources = [0, 0]
        success = 0
    x=-21829
    sources[1]=-21829
    values[1]=-21829
    ; direct jump
        if x!=values[1]
            goto lbl1a
        goto skip1a
lbl1a:   fail_word(369,-21829)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   fail_word(370,-21829)
skip1b:
        ; no else
        if x!=values[1]
            fail_word(371,-21829)

        ; with else
        if x!=values[1]
            fail_word(372,-21829)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl1c
        goto skip1c
lbl1c:   fail_word(373,-21829)
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   fail_word(374,-21829)
skip1d:
        ; no else
        if sources[1]!=values[1]
            fail_word(375,-21829)

        ; with else
        if sources[1]!=values[1]
            fail_word(376,-21829)
        else
            cx16.r0L++

    values[1]=170
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

    values[1]=30464
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

    values[1]=32767
    ; direct jump
        if x!=values[1]
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
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
            goto lbl4c
        goto skip4c
lbl4c:   success++
skip4c:
        ; indirect jump
        cx16.r3 = &lbl4d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip4d
lbl4d:   success++
skip4d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    x=0
    sources[1]=0
    values[1]=-21829
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

    values[1]=170
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

    values[1]=30464
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

    values[1]=32767
    ; direct jump
        if x!=values[1]
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x!=values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
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
            goto lbl8c
        goto skip8c
lbl8c:   success++
skip8c:
        ; indirect jump
        cx16.r3 = &lbl8d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip8d
lbl8d:   success++
skip8d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    x=170
    sources[1]=170
    values[1]=-21829
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

    values[1]=170
    ; direct jump
        if x!=values[1]
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(377,170)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x!=values[1]
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(378,170)
skip10b:
        ; no else
        if x!=values[1]
            fail_word(379,170)

        ; with else
        if x!=values[1]
            fail_word(380,170)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl10c
        goto skip10c
lbl10c:   fail_word(381,170)
skip10c:
        ; indirect jump
        cx16.r3 = &lbl10d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip10d
lbl10d:   fail_word(382,170)
skip10d:
        ; no else
        if sources[1]!=values[1]
            fail_word(383,170)

        ; with else
        if sources[1]!=values[1]
            fail_word(384,170)
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

    values[1]=32767
    ; direct jump
        if x!=values[1]
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x!=values[1]
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
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
            goto lbl12c
        goto skip12c
lbl12c:   success++
skip12c:
        ; indirect jump
        cx16.r3 = &lbl12d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip12d
lbl12d:   success++
skip12d:
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
    values[1]=-21829
    ; direct jump
        if x!=values[1]
            goto lbl13a
        goto skip13a
lbl13a:   success++
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x!=values[1]
            goto cx16.r3
        goto skip13b
lbl13b:   success++
skip13b:
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
            goto lbl13c
        goto skip13c
lbl13c:   success++
skip13c:
        ; indirect jump
        cx16.r3 = &lbl13d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip13d
lbl13d:   success++
skip13d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x!=values[1]
            goto lbl14a
        goto skip14a
lbl14a:   success++
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x!=values[1]
            goto cx16.r3
        goto skip14b
lbl14b:   success++
skip14b:
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
            goto lbl14c
        goto skip14c
lbl14c:   success++
skip14c:
        ; indirect jump
        cx16.r3 = &lbl14d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip14d
lbl14d:   success++
skip14d:
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
            goto lbl15a
        goto skip15a
lbl15a:   fail_word(385,30464)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x!=values[1]
            goto cx16.r3
        goto skip15b
lbl15b:   fail_word(386,30464)
skip15b:
        ; no else
        if x!=values[1]
            fail_word(387,30464)

        ; with else
        if x!=values[1]
            fail_word(388,30464)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl15c
        goto skip15c
lbl15c:   fail_word(389,30464)
skip15c:
        ; indirect jump
        cx16.r3 = &lbl15d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip15d
lbl15d:   fail_word(390,30464)
skip15d:
        ; no else
        if sources[1]!=values[1]
            fail_word(391,30464)

        ; with else
        if sources[1]!=values[1]
            fail_word(392,30464)
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x!=values[1]
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x!=values[1]
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
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
            goto lbl16c
        goto skip16c
lbl16c:   success++
skip16c:
        ; indirect jump
        cx16.r3 = &lbl16d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip16d
lbl16d:   success++
skip16d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    x=32767
    sources[1]=32767
    values[1]=-21829
    ; direct jump
        if x!=values[1]
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x!=values[1]
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
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
            goto lbl17c
        goto skip17c
lbl17c:   success++
skip17c:
        ; indirect jump
        cx16.r3 = &lbl17d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip17d
lbl17d:   success++
skip17d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x!=values[1]
            goto lbl18a
        goto skip18a
lbl18a:   success++
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x!=values[1]
            goto cx16.r3
        goto skip18b
lbl18b:   success++
skip18b:
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
            goto lbl18c
        goto skip18c
lbl18c:   success++
skip18c:
        ; indirect jump
        cx16.r3 = &lbl18d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip18d
lbl18d:   success++
skip18d:
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
            goto lbl19a
        goto skip19a
lbl19a:   success++
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x!=values[1]
            goto cx16.r3
        goto skip19b
lbl19b:   success++
skip19b:
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
            goto lbl19c
        goto skip19c
lbl19c:   success++
skip19c:
        ; indirect jump
        cx16.r3 = &lbl19d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip19d
lbl19d:   success++
skip19d:
        ; no else
        if sources[1]!=values[1]
            success++

        ; with else
        if sources[1]!=values[1]
            success++
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x!=values[1]
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(393,32767)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x!=values[1]
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(394,32767)
skip20b:
        ; no else
        if x!=values[1]
            fail_word(395,32767)

        ; with else
        if x!=values[1]
            fail_word(396,32767)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl20c
        goto skip20c
lbl20c:   fail_word(397,32767)
skip20c:
        ; indirect jump
        cx16.r3 = &lbl20d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip20d
lbl20d:   fail_word(398,32767)
skip20d:
        ; no else
        if sources[1]!=values[1]
            fail_word(399,32767)

        ; with else
        if sources[1]!=values[1]
            fail_word(400,32767)
        else
            cx16.r0L++

    verify_success(128)
}

}

