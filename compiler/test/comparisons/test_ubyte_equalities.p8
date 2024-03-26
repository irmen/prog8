
%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {
    ubyte success = 0
    str datatype = "ubyte"
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
        ubyte @shared x
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
            
        x = 100
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
        ubyte @shared x
        success = 0

        x=100
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
    ubyte @shared x
        success = 0
    x=0
    ; direct jump
        if x==1
            goto lbl1a
        goto skip1a
lbl1a:   fail_ubyte(1,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==1
            goto cx16.r3
        goto skip1b
lbl1b:   fail_ubyte(2,0)
skip1b:
        ; no else
        if x==1
            fail_ubyte(3,0)

        ; with else
        if x==1
            fail_ubyte(4,0)
        else
            cx16.r0L++

    ; direct jump
        if x==255
            goto lbl2a
        goto skip2a
lbl2a:   fail_ubyte(5,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==255
            goto cx16.r3
        goto skip2b
lbl2b:   fail_ubyte(6,0)
skip2b:
        ; no else
        if x==255
            fail_ubyte(7,0)

        ; with else
        if x==255
            fail_ubyte(8,0)
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x==1
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==1
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x==1
            success++

        ; with else
        if x==1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==255
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(9,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==255
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(10,1)
skip4b:
        ; no else
        if x==255
            fail_ubyte(11,1)

        ; with else
        if x==255
            fail_ubyte(12,1)
        else
            cx16.r0L++

    x=255
    ; direct jump
        if x==1
            goto lbl5a
        goto skip5a
lbl5a:   fail_ubyte(13,255)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==1
            goto cx16.r3
        goto skip5b
lbl5b:   fail_ubyte(14,255)
skip5b:
        ; no else
        if x==1
            fail_ubyte(15,255)

        ; with else
        if x==1
            fail_ubyte(16,255)
        else
            cx16.r0L++

    ; direct jump
        if x==255
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==255
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x==255
            success++

        ; with else
        if x==255
            success++
        else
            cx16.r0L++

    verify_success(8)
}
    sub test_not_number() {
    ubyte @shared x
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
        if x!=255
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=255
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=255
            success++

        ; with else
        if x!=255
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x!=1
            goto lbl3a
        goto skip3a
lbl3a:   fail_ubyte(17,1)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=1
            goto cx16.r3
        goto skip3b
lbl3b:   fail_ubyte(18,1)
skip3b:
        ; no else
        if x!=1
            fail_ubyte(19,1)

        ; with else
        if x!=1
            fail_ubyte(20,1)
        else
            cx16.r0L++

    ; direct jump
        if x!=255
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=255
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x!=255
            success++

        ; with else
        if x!=255
            success++
        else
            cx16.r0L++

    x=255
    ; direct jump
        if x!=1
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=1
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=1
            success++

        ; with else
        if x!=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=255
            goto lbl6a
        goto skip6a
lbl6a:   fail_ubyte(21,255)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=255
            goto cx16.r3
        goto skip6b
lbl6b:   fail_ubyte(22,255)
skip6b:
        ; no else
        if x!=255
            fail_ubyte(23,255)

        ; with else
        if x!=255
            fail_ubyte(24,255)
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_is_var() {
    ubyte @shared x, value
        success = 0
    x=0
    value=1
    ; direct jump
        if x==value
            goto lbl1a
        goto skip1a
lbl1a:   fail_ubyte(25,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==value
            goto cx16.r3
        goto skip1b
lbl1b:   fail_ubyte(26,0)
skip1b:
        ; no else
        if x==value
            fail_ubyte(27,0)

        ; with else
        if x==value
            fail_ubyte(28,0)
        else
            cx16.r0L++

    value=255
    ; direct jump
        if x==value
            goto lbl2a
        goto skip2a
lbl2a:   fail_ubyte(29,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_ubyte(30,0)
skip2b:
        ; no else
        if x==value
            fail_ubyte(31,0)

        ; with else
        if x==value
            fail_ubyte(32,0)
        else
            cx16.r0L++

    x=1
    value=1
    ; direct jump
        if x==value
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==value
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    value=255
    ; direct jump
        if x==value
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(33,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==value
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(34,1)
skip4b:
        ; no else
        if x==value
            fail_ubyte(35,1)

        ; with else
        if x==value
            fail_ubyte(36,1)
        else
            cx16.r0L++

    x=255
    value=1
    ; direct jump
        if x==value
            goto lbl5a
        goto skip5a
lbl5a:   fail_ubyte(37,255)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==value
            goto cx16.r3
        goto skip5b
lbl5b:   fail_ubyte(38,255)
skip5b:
        ; no else
        if x==value
            fail_ubyte(39,255)

        ; with else
        if x==value
            fail_ubyte(40,255)
        else
            cx16.r0L++

    value=255
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
    ubyte @shared x, value
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

    value=255
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

    x=1
    value=1
    ; direct jump
        if x!=value
            goto lbl3a
        goto skip3a
lbl3a:   fail_ubyte(41,1)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=value
            goto cx16.r3
        goto skip3b
lbl3b:   fail_ubyte(42,1)
skip3b:
        ; no else
        if x!=value
            fail_ubyte(43,1)

        ; with else
        if x!=value
            fail_ubyte(44,1)
        else
            cx16.r0L++

    value=255
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

    x=255
    value=1
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

    value=255
    ; direct jump
        if x!=value
            goto lbl6a
        goto skip6a
lbl6a:   fail_ubyte(45,255)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=value
            goto cx16.r3
        goto skip6b
lbl6b:   fail_ubyte(46,255)
skip6b:
        ; no else
        if x!=value
            fail_ubyte(47,255)

        ; with else
        if x!=value
            fail_ubyte(48,255)
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_is_expr() {
    ubyte @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0
    ; direct jump
        if x==cx16.r4L+1-cx16.r5L
            goto lbl1a
        goto skip1a
lbl1a:   fail_ubyte(49,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip1b
lbl1b:   fail_ubyte(50,0)
skip1b:
        ; no else
        if x==cx16.r4L+1-cx16.r5L
            fail_ubyte(51,0)

        ; with else
        if x==cx16.r4L+1-cx16.r5L
            fail_ubyte(52,0)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4L+255-cx16.r5L
            goto lbl2a
        goto skip2a
lbl2a:   fail_ubyte(53,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip2b
lbl2b:   fail_ubyte(54,0)
skip2b:
        ; no else
        if x==cx16.r4L+255-cx16.r5L
            fail_ubyte(55,0)

        ; with else
        if x==cx16.r4L+255-cx16.r5L
            fail_ubyte(56,0)
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x==cx16.r4L+1-cx16.r5L
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x==cx16.r4L+1-cx16.r5L
            success++

        ; with else
        if x==cx16.r4L+1-cx16.r5L
            success++
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4L+255-cx16.r5L
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(57,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(58,1)
skip4b:
        ; no else
        if x==cx16.r4L+255-cx16.r5L
            fail_ubyte(59,1)

        ; with else
        if x==cx16.r4L+255-cx16.r5L
            fail_ubyte(60,1)
        else
            cx16.r0L++

    x=255
    ; direct jump
        if x==cx16.r4L+1-cx16.r5L
            goto lbl5a
        goto skip5a
lbl5a:   fail_ubyte(61,255)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip5b
lbl5b:   fail_ubyte(62,255)
skip5b:
        ; no else
        if x==cx16.r4L+1-cx16.r5L
            fail_ubyte(63,255)

        ; with else
        if x==cx16.r4L+1-cx16.r5L
            fail_ubyte(64,255)
        else
            cx16.r0L++

    ; direct jump
        if x==cx16.r4L+255-cx16.r5L
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x==cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x==cx16.r4L+255-cx16.r5L
            success++

        ; with else
        if x==cx16.r4L+255-cx16.r5L
            success++
        else
            cx16.r0L++

    verify_success(8)
}
    sub test_not_expr() {
    ubyte @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0
    ; direct jump
        if x!=cx16.r4L+1-cx16.r5L
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x!=cx16.r4L+1-cx16.r5L
            success++

        ; with else
        if x!=cx16.r4L+1-cx16.r5L
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4L+255-cx16.r5L
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x!=cx16.r4L+255-cx16.r5L
            success++

        ; with else
        if x!=cx16.r4L+255-cx16.r5L
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x!=cx16.r4L+1-cx16.r5L
            goto lbl3a
        goto skip3a
lbl3a:   fail_ubyte(65,1)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip3b
lbl3b:   fail_ubyte(66,1)
skip3b:
        ; no else
        if x!=cx16.r4L+1-cx16.r5L
            fail_ubyte(67,1)

        ; with else
        if x!=cx16.r4L+1-cx16.r5L
            fail_ubyte(68,1)
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4L+255-cx16.r5L
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x!=cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x!=cx16.r4L+255-cx16.r5L
            success++

        ; with else
        if x!=cx16.r4L+255-cx16.r5L
            success++
        else
            cx16.r0L++

    x=255
    ; direct jump
        if x!=cx16.r4L+1-cx16.r5L
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x!=cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x!=cx16.r4L+1-cx16.r5L
            success++

        ; with else
        if x!=cx16.r4L+1-cx16.r5L
            success++
        else
            cx16.r0L++

    ; direct jump
        if x!=cx16.r4L+255-cx16.r5L
            goto lbl6a
        goto skip6a
lbl6a:   fail_ubyte(69,255)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip6b
lbl6b:   fail_ubyte(70,255)
skip6b:
        ; no else
        if x!=cx16.r4L+255-cx16.r5L
            fail_ubyte(71,255)

        ; with else
        if x!=cx16.r4L+255-cx16.r5L
            fail_ubyte(72,255)
        else
            cx16.r0L++

    verify_success(16)
}
    sub test_is_array() {
    ubyte @shared x
        ubyte[] values = [0, 0]
        ubyte[] sources = [0, 0]
        success = 0
    x=0
    sources[1]=0
    values[1]=1
    ; direct jump
        if x==values[1]
            goto lbl1a
        goto skip1a
lbl1a:   fail_ubyte(73,0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   fail_ubyte(74,0)
skip1b:
        ; no else
        if x==values[1]
            fail_ubyte(75,0)

        ; with else
        if x==values[1]
            fail_ubyte(76,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl1c
        goto skip1c
lbl1c:   fail_ubyte(77,0)
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   fail_ubyte(78,0)
skip1d:
        ; no else
        if sources[1]==values[1]
            fail_ubyte(79,0)

        ; with else
        if sources[1]==values[1]
            fail_ubyte(80,0)
        else
            cx16.r0L++

    values[1]=255
    ; direct jump
        if x==values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_ubyte(81,0)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_ubyte(82,0)
skip2b:
        ; no else
        if x==values[1]
            fail_ubyte(83,0)

        ; with else
        if x==values[1]
            fail_ubyte(84,0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl2c
        goto skip2c
lbl2c:   fail_ubyte(85,0)
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   fail_ubyte(86,0)
skip2d:
        ; no else
        if sources[1]==values[1]
            fail_ubyte(87,0)

        ; with else
        if sources[1]==values[1]
            fail_ubyte(88,0)
        else
            cx16.r0L++

    x=1
    sources[1]=1
    values[1]=1
    ; direct jump
        if x==values[1]
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x==values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
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
            goto lbl3c
        goto skip3c
lbl3c:   success++
skip3c:
        ; indirect jump
        cx16.r3 = &lbl3d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip3d
lbl3d:   success++
skip3d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    values[1]=255
    ; direct jump
        if x==values[1]
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(89,1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x==values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(90,1)
skip4b:
        ; no else
        if x==values[1]
            fail_ubyte(91,1)

        ; with else
        if x==values[1]
            fail_ubyte(92,1)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl4c
        goto skip4c
lbl4c:   fail_ubyte(93,1)
skip4c:
        ; indirect jump
        cx16.r3 = &lbl4d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip4d
lbl4d:   fail_ubyte(94,1)
skip4d:
        ; no else
        if sources[1]==values[1]
            fail_ubyte(95,1)

        ; with else
        if sources[1]==values[1]
            fail_ubyte(96,1)
        else
            cx16.r0L++

    x=255
    sources[1]=255
    values[1]=1
    ; direct jump
        if x==values[1]
            goto lbl5a
        goto skip5a
lbl5a:   fail_ubyte(97,255)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x==values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   fail_ubyte(98,255)
skip5b:
        ; no else
        if x==values[1]
            fail_ubyte(99,255)

        ; with else
        if x==values[1]
            fail_ubyte(100,255)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl5c
        goto skip5c
lbl5c:   fail_ubyte(101,255)
skip5c:
        ; indirect jump
        cx16.r3 = &lbl5d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip5d
lbl5d:   fail_ubyte(102,255)
skip5d:
        ; no else
        if sources[1]==values[1]
            fail_ubyte(103,255)

        ; with else
        if sources[1]==values[1]
            fail_ubyte(104,255)
        else
            cx16.r0L++

    values[1]=255
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
    ubyte @shared x
        ubyte[] values = [0, 0]
        ubyte[] sources = [0, 0]
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

    values[1]=255
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

    x=1
    sources[1]=1
    values[1]=1
    ; direct jump
        if x!=values[1]
            goto lbl3a
        goto skip3a
lbl3a:   fail_ubyte(105,1)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x!=values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   fail_ubyte(106,1)
skip3b:
        ; no else
        if x!=values[1]
            fail_ubyte(107,1)

        ; with else
        if x!=values[1]
            fail_ubyte(108,1)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl3c
        goto skip3c
lbl3c:   fail_ubyte(109,1)
skip3c:
        ; indirect jump
        cx16.r3 = &lbl3d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip3d
lbl3d:   fail_ubyte(110,1)
skip3d:
        ; no else
        if sources[1]!=values[1]
            fail_ubyte(111,1)

        ; with else
        if sources[1]!=values[1]
            fail_ubyte(112,1)
        else
            cx16.r0L++

    values[1]=255
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

    x=255
    sources[1]=255
    values[1]=1
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

    values[1]=255
    ; direct jump
        if x!=values[1]
            goto lbl6a
        goto skip6a
lbl6a:   fail_ubyte(113,255)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x!=values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   fail_ubyte(114,255)
skip6b:
        ; no else
        if x!=values[1]
            fail_ubyte(115,255)

        ; with else
        if x!=values[1]
            fail_ubyte(116,255)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl6c
        goto skip6c
lbl6c:   fail_ubyte(117,255)
skip6c:
        ; indirect jump
        cx16.r3 = &lbl6d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip6d
lbl6d:   fail_ubyte(118,255)
skip6d:
        ; no else
        if sources[1]!=values[1]
            fail_ubyte(119,255)

        ; with else
        if sources[1]!=values[1]
            fail_ubyte(120,255)
        else
            cx16.r0L++

    verify_success(32)
}

}

