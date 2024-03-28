
%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {
    ubyte success = 0
    str datatype = "float"
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
        float @shared x
        success = 0

        x=0.0
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
            
        x = 1234.56
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
        float @shared x
        success = 0

        x=1234.56
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
            
        x = 0.0
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
    float @shared x
        success = 0
    x=0.0
    ; direct jump
        if x==1234.56
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(1,0.0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==1234.56
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(2,0.0)
skip1b:
        ; no else
        if x==1234.56
            fail_float(3,0.0)

        ; with else
        if x==1234.56
            fail_float(4,0.0)
        else
            cx16.r0L++

    x=1234.56
    ; direct jump
        if x==1234.56
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==1234.56
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x==1234.56
            success++

        ; with else
        if x==1234.56
            success++
        else
            cx16.r0L++

    verify_success(4)
}
    sub test_not_number() {
    float @shared x
        success = 0
    x=0.0
    ; direct jump
        if x!=1234.56
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=1234.56
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x!=1234.56
            success++

        ; with else
        if x!=1234.56
            success++
        else
            cx16.r0L++

    x=1234.56
    ; direct jump
        if x!=1234.56
            goto lbl2a
        goto skip2a
lbl2a:   fail_float(5,1234.56)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=1234.56
            goto cx16.r3
        goto skip2b
lbl2b:   fail_float(6,1234.56)
skip2b:
        ; no else
        if x!=1234.56
            fail_float(7,1234.56)

        ; with else
        if x!=1234.56
            fail_float(8,1234.56)
        else
            cx16.r0L++

    verify_success(4)
}
    sub test_is_var() {
    float @shared x, value
        success = 0
    x=0.0
    value=1234.56
    ; direct jump
        if x==value
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(9,0.0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==value
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(10,0.0)
skip1b:
        ; no else
        if x==value
            fail_float(11,0.0)

        ; with else
        if x==value
            fail_float(12,0.0)
        else
            cx16.r0L++

    x=1234.56
    value=1234.56
    ; direct jump
        if x==value
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==value
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x==value
            success++

        ; with else
        if x==value
            success++
        else
            cx16.r0L++

    verify_success(4)
}
    sub test_not_var() {
    float @shared x, value
        success = 0
    x=0.0
    value=1234.56
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

    x=1234.56
    value=1234.56
    ; direct jump
        if x!=value
            goto lbl2a
        goto skip2a
lbl2a:   fail_float(13,1234.56)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_float(14,1234.56)
skip2b:
        ; no else
        if x!=value
            fail_float(15,1234.56)

        ; with else
        if x!=value
            fail_float(16,1234.56)
        else
            cx16.r0L++

    verify_success(4)
}
    sub test_is_expr() {
    float @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0.0
    ; direct jump
        if x==f4+1234.56-f5
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(17,0.0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==f4+1234.56-f5
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(18,0.0)
skip1b:
        ; no else
        if x==f4+1234.56-f5
            fail_float(19,0.0)

        ; with else
        if x==f4+1234.56-f5
            fail_float(20,0.0)
        else
            cx16.r0L++

    x=1234.56
    ; direct jump
        if x==f4+1234.56-f5
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==f4+1234.56-f5
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x==f4+1234.56-f5
            success++

        ; with else
        if x==f4+1234.56-f5
            success++
        else
            cx16.r0L++

    verify_success(4)
}
    sub test_not_expr() {
    float @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0.0
    ; direct jump
        if x!=f4+1234.56-f5
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x!=f4+1234.56-f5
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x!=f4+1234.56-f5
            success++

        ; with else
        if x!=f4+1234.56-f5
            success++
        else
            cx16.r0L++

    x=1234.56
    ; direct jump
        if x!=f4+1234.56-f5
            goto lbl2a
        goto skip2a
lbl2a:   fail_float(21,1234.56)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=f4+1234.56-f5
            goto cx16.r3
        goto skip2b
lbl2b:   fail_float(22,1234.56)
skip2b:
        ; no else
        if x!=f4+1234.56-f5
            fail_float(23,1234.56)

        ; with else
        if x!=f4+1234.56-f5
            fail_float(24,1234.56)
        else
            cx16.r0L++

    verify_success(4)
}
    sub test_is_array() {
    float @shared x
        float[] values = [0, 0]
        float[] sources = [0, 0]
        success = 0
    x=0.0
    sources[1]=0.0
    values[1]=1234.56
    ; direct jump
        if x==values[1]
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(25,0.0)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x==values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(26,0.0)
skip1b:
        ; no else
        if x==values[1]
            fail_float(27,0.0)

        ; with else
        if x==values[1]
            fail_float(28,0.0)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]==values[1]
            goto lbl1c
        goto skip1c
lbl1c:   fail_float(29,0.0)
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   fail_float(30,0.0)
skip1d:
        ; no else
        if sources[1]==values[1]
            fail_float(31,0.0)

        ; with else
        if sources[1]==values[1]
            fail_float(32,0.0)
        else
            cx16.r0L++

    x=1234.56
    sources[1]=1234.56
    values[1]=1234.56
    ; direct jump
        if x==values[1]
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x==values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
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
            goto lbl2c
        goto skip2c
lbl2c:   success++
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]==values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   success++
skip2d:
        ; no else
        if sources[1]==values[1]
            success++

        ; with else
        if sources[1]==values[1]
            success++
        else
            cx16.r0L++

    verify_success(8)
}
    sub test_not_array() {
    float @shared x
        float[] values = [0, 0]
        float[] sources = [0, 0]
        success = 0
    x=0.0
    sources[1]=0.0
    values[1]=1234.56
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

    x=1234.56
    sources[1]=1234.56
    values[1]=1234.56
    ; direct jump
        if x!=values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_float(33,1234.56)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x!=values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_float(34,1234.56)
skip2b:
        ; no else
        if x!=values[1]
            fail_float(35,1234.56)

        ; with else
        if x!=values[1]
            fail_float(36,1234.56)
        else
            cx16.r0L++

    ; direct jump
        if sources[1]!=values[1]
            goto lbl2c
        goto skip2c
lbl2c:   fail_float(37,1234.56)
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]!=values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   fail_float(38,1234.56)
skip2d:
        ; no else
        if sources[1]!=values[1]
            fail_float(39,1234.56)

        ; with else
        if sources[1]!=values[1]
            fail_float(40,1234.56)
        else
            cx16.r0L++

    verify_success(8)
}

}

