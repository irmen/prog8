
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
        txt.print("\ngreater-equal tests for: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("\n>=number: ")
        test_cmp_number()
        txt.print("\n>=var: ")
        test_cmp_var()
        txt.print("\n>=array[]: ")
        test_cmp_array()
        txt.print("\n>=expr: ")
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
    byte @shared x
        success = 0
    x=-1
    ; direct jump
        if x>=-1
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x>=-1
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x>=-1
            success++

        ; with else
        if x>=-1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=0
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(1)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=0
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(2)
skip2b:
        ; no else
        if x>=0
            fail_byte(3)

        ; with else
        if x>=0
            fail_byte(4)
        else
            success++

    ; direct jump
        if x>=1
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(5)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=1
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(6)
skip3b:
        ; no else
        if x>=1
            fail_byte(7)

        ; with else
        if x>=1
            fail_byte(8)
        else
            success++

    x=0
    ; direct jump
        if x>=-1
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=-1
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x>=-1
            success++

        ; with else
        if x>=-1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=0
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=0
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x>=0
            success++

        ; with else
        if x>=0
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=1
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(9)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=1
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(10)
skip6b:
        ; no else
        if x>=1
            fail_byte(11)

        ; with else
        if x>=1
            fail_byte(12)
        else
            success++

    x=1
    ; direct jump
        if x>=-1
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=-1
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x>=-1
            success++

        ; with else
        if x>=-1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=0
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x>=0
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x>=0
            success++

        ; with else
        if x>=0
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=1
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x>=1
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x>=1
            success++

        ; with else
        if x>=1
            success++
        else
            cx16.r0L++

    verify_success(27)
}
    sub test_cmp_var() {
    byte @shared x, value
        success = 0
    x=-1
    value=-1
    ; direct jump
        if x>=value
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x>=value
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=0
    ; direct jump
        if x>=value
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(13)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(14)
skip2b:
        ; no else
        if x>=value
            fail_byte(15)

        ; with else
        if x>=value
            fail_byte(16)
        else
            success++

    value=1
    ; direct jump
        if x>=value
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(17)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=value
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(18)
skip3b:
        ; no else
        if x>=value
            fail_byte(19)

        ; with else
        if x>=value
            fail_byte(20)
        else
            success++

    x=0
    value=-1
    ; direct jump
        if x>=value
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=value
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=0
    ; direct jump
        if x>=value
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=value
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=1
    ; direct jump
        if x>=value
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(21)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=value
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(22)
skip6b:
        ; no else
        if x>=value
            fail_byte(23)

        ; with else
        if x>=value
            fail_byte(24)
        else
            success++

    x=1
    value=-1
    ; direct jump
        if x>=value
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=value
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=0
    ; direct jump
        if x>=value
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x>=value
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=1
    ; direct jump
        if x>=value
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x>=value
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    verify_success(27)
}
    sub test_cmp_array() {
    byte @shared x
        byte[] values = [0, 0]
        success = 0
    x=-1
    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x>=values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=0
    ; direct jump
        if x>=values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(25)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(26)
skip2b:
        ; no else
        if x>=values[1]
            fail_byte(27)

        ; with else
        if x>=values[1]
            fail_byte(28)
        else
            success++

    values[1]=1
    ; direct jump
        if x>=values[1]
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(29)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(30)
skip3b:
        ; no else
        if x>=values[1]
            fail_byte(31)

        ; with else
        if x>=values[1]
            fail_byte(32)
        else
            success++

    x=0
    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=0
    ; direct jump
        if x>=values[1]
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=1
    ; direct jump
        if x>=values[1]
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(33)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(34)
skip6b:
        ; no else
        if x>=values[1]
            fail_byte(35)

        ; with else
        if x>=values[1]
            fail_byte(36)
        else
            success++

    x=1
    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=0
    ; direct jump
        if x>=values[1]
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x>=values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=1
    ; direct jump
        if x>=values[1]
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x>=values[1]
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    verify_success(27)
}
    sub test_cmp_expr() {
    byte @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=-1
    ; direct jump
        if x>=cx16.r4sL+-1-cx16.r5sL
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x>=cx16.r4sL+-1-cx16.r5sL
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x>=cx16.r4sL+-1-cx16.r5sL
            success++

        ; with else
        if x>=cx16.r4sL+-1-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4sL+0-cx16.r5sL
            goto lbl2a
        goto skip2a
lbl2a:   fail_byte(37)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=cx16.r4sL+0-cx16.r5sL
            goto cx16.r3
        goto skip2b
lbl2b:   fail_byte(38)
skip2b:
        ; no else
        if x>=cx16.r4sL+0-cx16.r5sL
            fail_byte(39)

        ; with else
        if x>=cx16.r4sL+0-cx16.r5sL
            fail_byte(40)
        else
            success++

    ; direct jump
        if x>=cx16.r4sL+1-cx16.r5sL
            goto lbl3a
        goto skip3a
lbl3a:   fail_byte(41)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=cx16.r4sL+1-cx16.r5sL
            goto cx16.r3
        goto skip3b
lbl3b:   fail_byte(42)
skip3b:
        ; no else
        if x>=cx16.r4sL+1-cx16.r5sL
            fail_byte(43)

        ; with else
        if x>=cx16.r4sL+1-cx16.r5sL
            fail_byte(44)
        else
            success++

    x=0
    ; direct jump
        if x>=cx16.r4sL+-1-cx16.r5sL
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=cx16.r4sL+-1-cx16.r5sL
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x>=cx16.r4sL+-1-cx16.r5sL
            success++

        ; with else
        if x>=cx16.r4sL+-1-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4sL+0-cx16.r5sL
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=cx16.r4sL+0-cx16.r5sL
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x>=cx16.r4sL+0-cx16.r5sL
            success++

        ; with else
        if x>=cx16.r4sL+0-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4sL+1-cx16.r5sL
            goto lbl6a
        goto skip6a
lbl6a:   fail_byte(45)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=cx16.r4sL+1-cx16.r5sL
            goto cx16.r3
        goto skip6b
lbl6b:   fail_byte(46)
skip6b:
        ; no else
        if x>=cx16.r4sL+1-cx16.r5sL
            fail_byte(47)

        ; with else
        if x>=cx16.r4sL+1-cx16.r5sL
            fail_byte(48)
        else
            success++

    x=1
    ; direct jump
        if x>=cx16.r4sL+-1-cx16.r5sL
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=cx16.r4sL+-1-cx16.r5sL
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x>=cx16.r4sL+-1-cx16.r5sL
            success++

        ; with else
        if x>=cx16.r4sL+-1-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4sL+0-cx16.r5sL
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x>=cx16.r4sL+0-cx16.r5sL
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x>=cx16.r4sL+0-cx16.r5sL
            success++

        ; with else
        if x>=cx16.r4sL+0-cx16.r5sL
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4sL+1-cx16.r5sL
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x>=cx16.r4sL+1-cx16.r5sL
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x>=cx16.r4sL+1-cx16.r5sL
            success++

        ; with else
        if x>=cx16.r4sL+1-cx16.r5sL
            success++
        else
            cx16.r0L++

    verify_success(27)
}

}

