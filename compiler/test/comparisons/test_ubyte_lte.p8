
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
        txt.print("\nless-equal tests for: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("\n<=number: ")
        test_cmp_number()
        txt.print("\n<=var: ")
        test_cmp_var()
        txt.print("\n<=array[]: ")
        test_cmp_array()
        txt.print("\n<=expr: ")
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
    ubyte @shared x
        success = 0
    x=0
    ; direct jump
        if x<=0
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<=0
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x<=0
            success++

        ; with else
        if x<=0
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<=1
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<=1
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<=1
            success++

        ; with else
        if x<=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<=255
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<=255
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<=255
            success++

        ; with else
        if x<=255
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x<=0
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(1)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<=0
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(2)
skip4b:
        ; no else
        if x<=0
            fail_ubyte(3)

        ; with else
        if x<=0
            fail_ubyte(4)
        else
            success++

    ; direct jump
        if x<=1
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<=1
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x<=1
            success++

        ; with else
        if x<=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<=255
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<=255
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x<=255
            success++

        ; with else
        if x<=255
            success++
        else
            cx16.r0L++

    x=255
    ; direct jump
        if x<=0
            goto lbl7a
        goto skip7a
lbl7a:   fail_ubyte(5)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<=0
            goto cx16.r3
        goto skip7b
lbl7b:   fail_ubyte(6)
skip7b:
        ; no else
        if x<=0
            fail_ubyte(7)

        ; with else
        if x<=0
            fail_ubyte(8)
        else
            success++

    ; direct jump
        if x<=1
            goto lbl8a
        goto skip8a
lbl8a:   fail_ubyte(9)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<=1
            goto cx16.r3
        goto skip8b
lbl8b:   fail_ubyte(10)
skip8b:
        ; no else
        if x<=1
            fail_ubyte(11)

        ; with else
        if x<=1
            fail_ubyte(12)
        else
            success++

    ; direct jump
        if x<=255
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<=255
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x<=255
            success++

        ; with else
        if x<=255
            success++
        else
            cx16.r0L++

    verify_success(27)
}
    sub test_cmp_var() {
    ubyte @shared x, value
        success = 0
    x=0
    value=0
    ; direct jump
        if x<=value
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<=value
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x<=value
            success++

        ; with else
        if x<=value
            success++
        else
            cx16.r0L++

    value=1
    ; direct jump
        if x<=value
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<=value
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<=value
            success++

        ; with else
        if x<=value
            success++
        else
            cx16.r0L++

    value=255
    ; direct jump
        if x<=value
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<=value
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<=value
            success++

        ; with else
        if x<=value
            success++
        else
            cx16.r0L++

    x=1
    value=0
    ; direct jump
        if x<=value
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(13)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<=value
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(14)
skip4b:
        ; no else
        if x<=value
            fail_ubyte(15)

        ; with else
        if x<=value
            fail_ubyte(16)
        else
            success++

    value=1
    ; direct jump
        if x<=value
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<=value
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x<=value
            success++

        ; with else
        if x<=value
            success++
        else
            cx16.r0L++

    value=255
    ; direct jump
        if x<=value
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<=value
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x<=value
            success++

        ; with else
        if x<=value
            success++
        else
            cx16.r0L++

    x=255
    value=0
    ; direct jump
        if x<=value
            goto lbl7a
        goto skip7a
lbl7a:   fail_ubyte(17)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<=value
            goto cx16.r3
        goto skip7b
lbl7b:   fail_ubyte(18)
skip7b:
        ; no else
        if x<=value
            fail_ubyte(19)

        ; with else
        if x<=value
            fail_ubyte(20)
        else
            success++

    value=1
    ; direct jump
        if x<=value
            goto lbl8a
        goto skip8a
lbl8a:   fail_ubyte(21)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<=value
            goto cx16.r3
        goto skip8b
lbl8b:   fail_ubyte(22)
skip8b:
        ; no else
        if x<=value
            fail_ubyte(23)

        ; with else
        if x<=value
            fail_ubyte(24)
        else
            success++

    value=255
    ; direct jump
        if x<=value
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<=value
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x<=value
            success++

        ; with else
        if x<=value
            success++
        else
            cx16.r0L++

    verify_success(27)
}
    sub test_cmp_array() {
    ubyte @shared x
        ubyte[] values = [0, 0]
        success = 0
    x=0
    values[1]=0
    ; direct jump
        if x<=values[1]
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<=values[1]
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=1
    ; direct jump
        if x<=values[1]
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<=values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=255
    ; direct jump
        if x<=values[1]
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<=values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    x=1
    values[1]=0
    ; direct jump
        if x<=values[1]
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(25)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<=values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(26)
skip4b:
        ; no else
        if x<=values[1]
            fail_ubyte(27)

        ; with else
        if x<=values[1]
            fail_ubyte(28)
        else
            success++

    values[1]=1
    ; direct jump
        if x<=values[1]
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<=values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=255
    ; direct jump
        if x<=values[1]
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<=values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    x=255
    values[1]=0
    ; direct jump
        if x<=values[1]
            goto lbl7a
        goto skip7a
lbl7a:   fail_ubyte(29)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<=values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   fail_ubyte(30)
skip7b:
        ; no else
        if x<=values[1]
            fail_ubyte(31)

        ; with else
        if x<=values[1]
            fail_ubyte(32)
        else
            success++

    values[1]=1
    ; direct jump
        if x<=values[1]
            goto lbl8a
        goto skip8a
lbl8a:   fail_ubyte(33)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<=values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   fail_ubyte(34)
skip8b:
        ; no else
        if x<=values[1]
            fail_ubyte(35)

        ; with else
        if x<=values[1]
            fail_ubyte(36)
        else
            success++

    values[1]=255
    ; direct jump
        if x<=values[1]
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<=values[1]
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    verify_success(27)
}
    sub test_cmp_expr() {
    ubyte @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=0
    ; direct jump
        if x<=cx16.r4L+0-cx16.r5L
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x<=cx16.r4L+0-cx16.r5L
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x<=cx16.r4L+0-cx16.r5L
            success++

        ; with else
        if x<=cx16.r4L+0-cx16.r5L
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<=cx16.r4L+1-cx16.r5L
            goto lbl2a
        goto skip2a
lbl2a:   success++
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x<=cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip2b
lbl2b:   success++
skip2b:
        ; no else
        if x<=cx16.r4L+1-cx16.r5L
            success++

        ; with else
        if x<=cx16.r4L+1-cx16.r5L
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<=cx16.r4L+255-cx16.r5L
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x<=cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x<=cx16.r4L+255-cx16.r5L
            success++

        ; with else
        if x<=cx16.r4L+255-cx16.r5L
            success++
        else
            cx16.r0L++

    x=1
    ; direct jump
        if x<=cx16.r4L+0-cx16.r5L
            goto lbl4a
        goto skip4a
lbl4a:   fail_ubyte(37)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<=cx16.r4L+0-cx16.r5L
            goto cx16.r3
        goto skip4b
lbl4b:   fail_ubyte(38)
skip4b:
        ; no else
        if x<=cx16.r4L+0-cx16.r5L
            fail_ubyte(39)

        ; with else
        if x<=cx16.r4L+0-cx16.r5L
            fail_ubyte(40)
        else
            success++

    ; direct jump
        if x<=cx16.r4L+1-cx16.r5L
            goto lbl5a
        goto skip5a
lbl5a:   success++
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<=cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip5b
lbl5b:   success++
skip5b:
        ; no else
        if x<=cx16.r4L+1-cx16.r5L
            success++

        ; with else
        if x<=cx16.r4L+1-cx16.r5L
            success++
        else
            cx16.r0L++

    ; direct jump
        if x<=cx16.r4L+255-cx16.r5L
            goto lbl6a
        goto skip6a
lbl6a:   success++
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x<=cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip6b
lbl6b:   success++
skip6b:
        ; no else
        if x<=cx16.r4L+255-cx16.r5L
            success++

        ; with else
        if x<=cx16.r4L+255-cx16.r5L
            success++
        else
            cx16.r0L++

    x=255
    ; direct jump
        if x<=cx16.r4L+0-cx16.r5L
            goto lbl7a
        goto skip7a
lbl7a:   fail_ubyte(41)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<=cx16.r4L+0-cx16.r5L
            goto cx16.r3
        goto skip7b
lbl7b:   fail_ubyte(42)
skip7b:
        ; no else
        if x<=cx16.r4L+0-cx16.r5L
            fail_ubyte(43)

        ; with else
        if x<=cx16.r4L+0-cx16.r5L
            fail_ubyte(44)
        else
            success++

    ; direct jump
        if x<=cx16.r4L+1-cx16.r5L
            goto lbl8a
        goto skip8a
lbl8a:   fail_ubyte(45)
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<=cx16.r4L+1-cx16.r5L
            goto cx16.r3
        goto skip8b
lbl8b:   fail_ubyte(46)
skip8b:
        ; no else
        if x<=cx16.r4L+1-cx16.r5L
            fail_ubyte(47)

        ; with else
        if x<=cx16.r4L+1-cx16.r5L
            fail_ubyte(48)
        else
            success++

    ; direct jump
        if x<=cx16.r4L+255-cx16.r5L
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<=cx16.r4L+255-cx16.r5L
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x<=cx16.r4L+255-cx16.r5L
            success++

        ; with else
        if x<=cx16.r4L+255-cx16.r5L
            success++
        else
            cx16.r0L++

    verify_success(27)
}

}

