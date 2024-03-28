
%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {
    ubyte success = 0
    str datatype = "byte"
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
        byte @shared x
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
            
        x = -100
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
        byte @shared x
        success = 0

        x=-100
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
    byte @shared x
        success = 0
    x=-100
    ; direct jump
        if x==-100
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==-100
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x==-100
            success++

        ; with else
        if x==-100
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==100
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(1,-100)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==100
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(2,-100)
skip2b:
        ; no else
        if x==100
            fail_byte(3,-100)

        ; with else
        if x==100
            fail_byte(4,-100)
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x==-100
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(5,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==-100
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(6,0)
skip3b:
        ; no else
        if x==-100
            fail_byte(7,0)

        ; with else
        if x==-100
            fail_byte(8,0)
        else
            cx16.r0L++

    ; direct jump
        if x==100
            goto lbl4a
        goto skip4a
lbl4a:   fail_byte(9,0)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==100
            goto cx16.r3
        goto skip4b
lbl4b:   fail_byte(10,0)
skip4b:
        ; no else
        if x==100
            fail_byte(11,0)

        ; with else
        if x==100
            fail_byte(12,0)
        else
            cx16.r0L++

    x=100
    ; direct jump
        if x==-100
            goto lbl5a
        goto skip5a
lbl5a:   fail_byte(13,100)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==-100
            goto cx16.r3
        goto skip5b
lbl5b:   fail_byte(14,100)
skip5b:
        ; no else
        if x==-100
            fail_byte(15,100)

        ; with else
        if x==-100
            fail_byte(16,100)
        else
            cx16.r0L++

    ; direct jump
        if x==100
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==100
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x==100
            success++

        ; with else
        if x==100
            success++
        else
            cx16.r0L++

    verify_success(8)
}
    sub test_not_number() {
    byte @shared x
        success = 0
    x=-100
    ; direct jump
        if x!=-100
            goto lbl1a
        goto skip1a
lbl1a:   fail_byte(17,-100)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=-100
            goto cx16.r3
        goto skip1b
lbl1b:   fail_byte(18,-100)
skip1b:
        ; no else
        if x!=-100
            fail_byte(19,-100)

        ; with else
        if x!=-100
            fail_byte(20,-100)
        else
            cx16.r0L++

    ; direct jump
        if x!=100
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=100
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=100
            success++

        ; with else
        if x!=100
            success++
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x!=-100
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=-100
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=-100
            success++

        ; with else
        if x!=-100
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=100
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=100
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x!=100
            success++

        ; with else
        if x!=100
            success++
        else
            cx16.r0L++

    x=100
    ; direct jump
        if x!=-100
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=-100
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=-100
            success++

        ; with else
        if x!=-100
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=100
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(21,100)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=100
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(22,100)
skip6b:
        ; no else
        if x!=100
            fail_byte(23,100)

        ; with else
        if x!=100
            fail_byte(24,100)
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_is_var() {
    byte @shared x, value
        success = 0
    x=-100
    value=-100
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

    value=100
    ; direct jump
        if x==value
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(25,-100)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(26,-100)
skip2b:
        ; no else
        if x==value
            fail_byte(27,-100)

        ; with else
        if x==value
            fail_byte(28,-100)
        else
            cx16.r0L++

    x=0
    value=-100
    ; direct jump
        if x==value
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(29,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==value
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(30,0)
skip3b:
        ; no else
        if x==value
            fail_byte(31,0)

        ; with else
        if x==value
            fail_byte(32,0)
        else
            cx16.r0L++

    value=100
    ; direct jump
        if x==value
            goto lbl4a
        goto skip4a
lbl4a:   fail_byte(33,0)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==value
            goto cx16.r3
        goto skip4b
lbl4b:   fail_byte(34,0)
skip4b:
        ; no else
        if x==value
            fail_byte(35,0)

        ; with else
        if x==value
            fail_byte(36,0)
        else
            cx16.r0L++

    x=100
    value=-100
    ; direct jump
        if x==value
            goto lbl5a
        goto skip5a
lbl5a:   fail_byte(37,100)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==value
            goto cx16.r3
        goto skip5b
lbl5b:   fail_byte(38,100)
skip5b:
        ; no else
        if x==value
            fail_byte(39,100)

        ; with else
        if x==value
            fail_byte(40,100)
        else
            cx16.r0L++

    value=100
    ; direct jump
        if x==value
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==value
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    verify_success(8)
}
    sub test_not_var() {
    byte @shared x, value
        success = 0
    x=-100
    value=-100
    ; direct jump
        if x!=value
            goto lbl1a
        goto skip1a
lbl1a:   fail_byte(41,-100)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=value
            goto cx16.r3
        goto skip1b
lbl1b:   fail_byte(42,-100)
skip1b:
        ; no else
        if x!=value
            fail_byte(43,-100)

        ; with else
        if x!=value
            fail_byte(44,-100)
        else
            cx16.r0L++

    value=100
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

    x=0
    value=-100
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

    value=100
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

    x=100
    value=-100
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

    value=100
    ; direct jump
        if x!=value
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(45,100)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=value
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(46,100)
skip6b:
        ; no else
        if x!=value
            fail_byte(47,100)

        ; with else
        if x!=value
            fail_byte(48,100)
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_is_expr() {
    byte @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=-100
    ; direct jump
        if x==cx16.r4sL+-100-cx16.r5sL
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==cx16.r4sL+-100-cx16.r5sL
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x==cx16.r4sL+-100-cx16.r5sL
            success++

        ; with else
        if x==cx16.r4sL+-100-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4sL+100-cx16.r5sL
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(49,-100)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==cx16.r4sL+100-cx16.r5sL
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(50,-100)
skip2b:
        ; no else
        if x==cx16.r4sL+100-cx16.r5sL
            fail_byte(51,-100)

        ; with else
        if x==cx16.r4sL+100-cx16.r5sL
            fail_byte(52,-100)
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x==cx16.r4sL+-100-cx16.r5sL
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(53,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==cx16.r4sL+-100-cx16.r5sL
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(54,0)
skip3b:
        ; no else
        if x==cx16.r4sL+-100-cx16.r5sL
            fail_byte(55,0)

        ; with else
        if x==cx16.r4sL+-100-cx16.r5sL
            fail_byte(56,0)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4sL+100-cx16.r5sL
            goto lbl4a
        goto skip4a
lbl4a:   fail_byte(57,0)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==cx16.r4sL+100-cx16.r5sL
            goto cx16.r3
        goto skip4b
lbl4b:   fail_byte(58,0)
skip4b:
        ; no else
        if x==cx16.r4sL+100-cx16.r5sL
            fail_byte(59,0)

        ; with else
        if x==cx16.r4sL+100-cx16.r5sL
            fail_byte(60,0)
        else
            cx16.r0L++

    x=100
    ; direct jump
        if x==cx16.r4sL+-100-cx16.r5sL
            goto lbl5a
        goto skip5a
lbl5a:   fail_byte(61,100)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==cx16.r4sL+-100-cx16.r5sL
            goto cx16.r3
        goto skip5b
lbl5b:   fail_byte(62,100)
skip5b:
        ; no else
        if x==cx16.r4sL+-100-cx16.r5sL
            fail_byte(63,100)

        ; with else
        if x==cx16.r4sL+-100-cx16.r5sL
            fail_byte(64,100)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4sL+100-cx16.r5sL
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==cx16.r4sL+100-cx16.r5sL
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x==cx16.r4sL+100-cx16.r5sL
            success++

        ; with else
        if x==cx16.r4sL+100-cx16.r5sL
            success++
        else
            cx16.r0L++

    verify_success(8)
}
    sub test_not_expr() {
    byte @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=-100
    ; direct jump
        if x!=cx16.r4sL+-100-cx16.r5sL
            goto lbl1a
        goto skip1a
lbl1a:   fail_byte(65,-100)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=cx16.r4sL+-100-cx16.r5sL
            goto cx16.r3
        goto skip1b
lbl1b:   fail_byte(66,-100)
skip1b:
        ; no else
        if x!=cx16.r4sL+-100-cx16.r5sL
            fail_byte(67,-100)

        ; with else
        if x!=cx16.r4sL+-100-cx16.r5sL
            fail_byte(68,-100)
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4sL+100-cx16.r5sL
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=cx16.r4sL+100-cx16.r5sL
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=cx16.r4sL+100-cx16.r5sL
            success++

        ; with else
        if x!=cx16.r4sL+100-cx16.r5sL
            success++
        else
            cx16.r0L++

    x=0
    ; direct jump
        if x!=cx16.r4sL+-100-cx16.r5sL
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=cx16.r4sL+-100-cx16.r5sL
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x!=cx16.r4sL+-100-cx16.r5sL
            success++

        ; with else
        if x!=cx16.r4sL+-100-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4sL+100-cx16.r5sL
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=cx16.r4sL+100-cx16.r5sL
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x!=cx16.r4sL+100-cx16.r5sL
            success++

        ; with else
        if x!=cx16.r4sL+100-cx16.r5sL
            success++
        else
            cx16.r0L++

    x=100
    ; direct jump
        if x!=cx16.r4sL+-100-cx16.r5sL
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=cx16.r4sL+-100-cx16.r5sL
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=cx16.r4sL+-100-cx16.r5sL
            success++

        ; with else
        if x!=cx16.r4sL+-100-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4sL+100-cx16.r5sL
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(69,100)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=cx16.r4sL+100-cx16.r5sL
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(70,100)
skip6b:
        ; no else
        if x!=cx16.r4sL+100-cx16.r5sL
            fail_byte(71,100)

        ; with else
        if x!=cx16.r4sL+100-cx16.r5sL
            fail_byte(72,100)
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_is_array() {
    byte @shared x
        byte[] values = [0, 0]
        byte[] sources = [0, 0]
        success = 0
    x=-100
    sources[1]=-100
    values[1]=-100
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

    values[1]=100
    ; direct jump
        if x==values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(73,-100)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(74,-100)
skip2b:
        ; no else
        if x==values[1]
            fail_byte(75,-100)

        ; with else
        if x==values[1]
            fail_byte(76,-100)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl2c
        goto skip2c
lbl2c:   fail_byte(77,-100)
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   fail_byte(78,-100)
skip2d:
        ; no else
        if sources[1]==values[1]
            fail_byte(79,-100)

        ; with else
        if sources[1]==values[1]
            fail_byte(80,-100)
        else
            cx16.r0L++

    x=0
    sources[1]=0
    values[1]=-100
    ; direct jump
        if x==values[1]
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(81,0)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(82,0)
skip3b:
        ; no else
        if x==values[1]
            fail_byte(83,0)

        ; with else
        if x==values[1]
            fail_byte(84,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl3c
        goto skip3c
lbl3c:   fail_byte(85,0)
skip3c:
        ; indirect jump
        cx16.r3 = &lbl3d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip3d
lbl3d:   fail_byte(86,0)
skip3d:
        ; no else
        if sources[1]==values[1]
            fail_byte(87,0)

        ; with else
        if sources[1]==values[1]
            fail_byte(88,0)
        else
            cx16.r0L++

    values[1]=100
    ; direct jump
        if x==values[1]
            goto lbl4a
        goto skip4a
lbl4a:   fail_byte(89,0)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   fail_byte(90,0)
skip4b:
        ; no else
        if x==values[1]
            fail_byte(91,0)

        ; with else
        if x==values[1]
            fail_byte(92,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl4c
        goto skip4c
lbl4c:   fail_byte(93,0)
skip4c:
        ; indirect jump
        cx16.r3 = &lbl4d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip4d
lbl4d:   fail_byte(94,0)
skip4d:
        ; no else
        if sources[1]==values[1]
            fail_byte(95,0)

        ; with else
        if sources[1]==values[1]
            fail_byte(96,0)
        else
            cx16.r0L++

    x=100
    sources[1]=100
    values[1]=-100
    ; direct jump
        if x==values[1]
            goto lbl5a
        goto skip5a
lbl5a:   fail_byte(97,100)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   fail_byte(98,100)
skip5b:
        ; no else
        if x==values[1]
            fail_byte(99,100)

        ; with else
        if x==values[1]
            fail_byte(100,100)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl5c
        goto skip5c
lbl5c:   fail_byte(101,100)
skip5c:
        ; indirect jump
        cx16.r3 = &lbl5d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip5d
lbl5d:   fail_byte(102,100)
skip5d:
        ; no else
        if sources[1]==values[1]
            fail_byte(103,100)

        ; with else
        if sources[1]==values[1]
            fail_byte(104,100)
        else
            cx16.r0L++

    values[1]=100
    ; direct jump
        if x==values[1]
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
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
            goto lbl6c
        goto skip6c
lbl6c:   success++
skip6c:
        ; indirect jump
        cx16.r3 = &lbl6d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip6d
lbl6d:   success++
skip6d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_not_array() {
    byte @shared x
        byte[] values = [0, 0]
        byte[] sources = [0, 0]
        success = 0
    x=-100
    sources[1]=-100
    values[1]=-100
    ; direct jump
        if x!=values[1]
            goto lbl1a
        goto skip1a
lbl1a:   fail_byte(105,-100)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   fail_byte(106,-100)
skip1b:
        ; no else
        if x!=values[1]
            fail_byte(107,-100)

        ; with else
        if x!=values[1]
            fail_byte(108,-100)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl1c
        goto skip1c
lbl1c:   fail_byte(109,-100)
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   fail_byte(110,-100)
skip1d:
        ; no else
        if sources[1]!=values[1]
            fail_byte(111,-100)

        ; with else
        if sources[1]!=values[1]
            fail_byte(112,-100)
        else
            cx16.r0L++

    values[1]=100
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

    x=0
    sources[1]=0
    values[1]=-100
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

    values[1]=100
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

    x=100
    sources[1]=100
    values[1]=-100
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

    values[1]=100
    ; direct jump
        if x!=values[1]
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(113,100)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(114,100)
skip6b:
        ; no else
        if x!=values[1]
            fail_byte(115,100)

        ; with else
        if x!=values[1]
            fail_byte(116,100)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl6c
        goto skip6c
lbl6c:   fail_byte(117,100)
skip6c:
        ; indirect jump
        cx16.r3 = &lbl6d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip6d
lbl6d:   fail_byte(118,100)
skip6d:
        ; no else
        if sources[1]!=values[1]
            fail_byte(119,100)

        ; with else
        if sources[1]!=values[1]
            fail_byte(120,100)
        else
            cx16.r0L++

    verify_success(32)
}

}

