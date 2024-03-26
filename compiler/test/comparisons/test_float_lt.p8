
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
    float @shared x
        success = 0
    x=0.0
    ; direct jump
        if x<0.0
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(1)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<0.0
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(2)
skip1b:
        ; no else
        if x<0.0
            fail_float(3)

        ; with else
        if x<0.0
            fail_float(4)
        else
            success++

    ; direct jump
        if x<1234.56
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<1234.56
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<1234.56
            success++

        ; with else
        if x<1234.56
            success++
        else
            cx16.r0L++

    x=1234.56
    ; direct jump
        if x<0.0
            goto lbl3a
        goto skip3a
lbl3a:   fail_float(5)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<0.0
            goto cx16.r3
        goto skip3b
lbl3b:   fail_float(6)
skip3b:
        ; no else
        if x<0.0
            fail_float(7)

        ; with else
        if x<0.0
            fail_float(8)
        else
            success++

    ; direct jump
        if x<1234.56
            goto lbl4a
        goto skip4a
lbl4a:   fail_float(9)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<1234.56
            goto cx16.r3
        goto skip4b
lbl4b:   fail_float(10)
skip4b:
        ; no else
        if x<1234.56
            fail_float(11)

        ; with else
        if x<1234.56
            fail_float(12)
        else
            success++

    verify_success(7)
}
    sub test_cmp_var() {
    float @shared x, value
        success = 0
    x=0.0
    value=0.0
    ; direct jump
        if x<value
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(13)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<value
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(14)
skip1b:
        ; no else
        if x<value
            fail_float(15)

        ; with else
        if x<value
            fail_float(16)
        else
            success++

    value=1234.56
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

    x=1234.56
    value=0.0
    ; direct jump
        if x<value
            goto lbl3a
        goto skip3a
lbl3a:   fail_float(17)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<value
            goto cx16.r3
        goto skip3b
lbl3b:   fail_float(18)
skip3b:
        ; no else
        if x<value
            fail_float(19)

        ; with else
        if x<value
            fail_float(20)
        else
            success++

    value=1234.56
    ; direct jump
        if x<value
            goto lbl4a
        goto skip4a
lbl4a:   fail_float(21)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<value
            goto cx16.r3
        goto skip4b
lbl4b:   fail_float(22)
skip4b:
        ; no else
        if x<value
            fail_float(23)

        ; with else
        if x<value
            fail_float(24)
        else
            success++

    verify_success(7)
}
    sub test_cmp_array() {
    float @shared x
        float[] values = [0, 0]
        success = 0
    x=0.0
    values[1]=0.0
    ; direct jump
        if x<values[1]
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(25)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(26)
skip1b:
        ; no else
        if x<values[1]
            fail_float(27)

        ; with else
        if x<values[1]
            fail_float(28)
        else
            success++

    values[1]=1234.56
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

    x=1234.56
    values[1]=0.0
    ; direct jump
        if x<values[1]
            goto lbl3a
        goto skip3a
lbl3a:   fail_float(29)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   fail_float(30)
skip3b:
        ; no else
        if x<values[1]
            fail_float(31)

        ; with else
        if x<values[1]
            fail_float(32)
        else
            success++

    values[1]=1234.56
    ; direct jump
        if x<values[1]
            goto lbl4a
        goto skip4a
lbl4a:   fail_float(33)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   fail_float(34)
skip4b:
        ; no else
        if x<values[1]
            fail_float(35)

        ; with else
        if x<values[1]
            fail_float(36)
        else
            success++

    verify_success(7)
}
    sub test_cmp_expr() {
    float @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0.0
    ; direct jump
        if x<f4+0.0-f5
            goto lbl1a
        goto skip1a
lbl1a:   fail_float(37)
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<f4+0.0-f5
            goto cx16.r3
        goto skip1b
lbl1b:   fail_float(38)
skip1b:
        ; no else
        if x<f4+0.0-f5
            fail_float(39)

        ; with else
        if x<f4+0.0-f5
            fail_float(40)
        else
            success++

    ; direct jump
        if x<f4+1234.56-f5
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<f4+1234.56-f5
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<f4+1234.56-f5
            success++

        ; with else
        if x<f4+1234.56-f5
            success++
        else
            cx16.r0L++

    x=1234.56
    ; direct jump
        if x<f4+0.0-f5
            goto lbl3a
        goto skip3a
lbl3a:   fail_float(41)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<f4+0.0-f5
            goto cx16.r3
        goto skip3b
lbl3b:   fail_float(42)
skip3b:
        ; no else
        if x<f4+0.0-f5
            fail_float(43)

        ; with else
        if x<f4+0.0-f5
            fail_float(44)
        else
            success++

    ; direct jump
        if x<f4+1234.56-f5
            goto lbl4a
        goto skip4a
lbl4a:   fail_float(45)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<f4+1234.56-f5
            goto cx16.r3
        goto skip4b
lbl4b:   fail_float(46)
skip4b:
        ; no else
        if x<f4+1234.56-f5
            fail_float(47)

        ; with else
        if x<f4+1234.56-f5
            fail_float(48)
        else
            success++

    verify_success(7)
}

}

