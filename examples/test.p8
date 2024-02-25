
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
        txt.print("\ngreater-equal tests for: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("\n>=number: ")
        test_cmp_number()
/*
        txt.print("\n>=var: ")
        test_cmp_var()
        txt.print("\n>=array[]: ")
        test_cmp_array()
        txt.print("\n>=expr: ")
        test_cmp_expr()
*/
        test_stack.test()
    }

    sub verify_success(ubyte expected) {
        if success==expected {
            txt.print_ub(success)
            txt.print(" successes ok")
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
    uword @shared x = 65500
        if x>=65500
            goto lbl4a
        goto skip4a
lbl4a:   txt.print("should see this!\n")
skip4a:
}

/*
    sub test_cmp_var() {
    uword @shared x, value
        success = 0
    x=0
    value=0
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

    value=65535
    ; direct jump
        if x>=value
            goto lbl2a
        goto skip2a
lbl2a:   fail_uword(5)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_uword(6)
skip2b:
        ; no else
        if x>=value
            fail_uword(7)

        ; with else
        if x>=value
            fail_uword(8)
        else
            success++

    x=65535
    value=0
    ; direct jump
        if x>=value
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=value
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=65535
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

    verify_success(13)
}
    sub test_cmp_array() {
    uword @shared x
        uword[] values = [0, 0]
        success = 0
    x=0
    values[1]=0
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

    values[1]=65535
    ; direct jump
        if x>=values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_uword(9)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_uword(10)
skip2b:
        ; no else
        if x>=values[1]
            fail_uword(11)

        ; with else
        if x>=values[1]
            fail_uword(12)
        else
            success++

    x=65535
    values[1]=0
    ; direct jump
        if x>=values[1]
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
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

    verify_success(13)
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
        if x>=cx16.r4+0-cx16.r5
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x>=cx16.r4+0-cx16.r5
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x>=cx16.r4+0-cx16.r5
            success++

        ; with else
        if x>=cx16.r4+0-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4+65535-cx16.r5
            goto lbl2a
        goto skip2a
lbl2a:   fail_uword(13)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip2b
lbl2b:   fail_uword(14)
skip2b:
        ; no else
        if x>=cx16.r4+65535-cx16.r5
            fail_uword(15)

        ; with else
        if x>=cx16.r4+65535-cx16.r5
            fail_uword(16)
        else
            success++

    x=65535
    ; direct jump
        if x>=cx16.r4+0-cx16.r5
            goto lbl3a
        goto skip3a
lbl3a:   success++
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=cx16.r4+0-cx16.r5
            goto cx16.r3
        goto skip3b
lbl3b:   success++
skip3b:
        ; no else
        if x>=cx16.r4+0-cx16.r5
            success++

        ; with else
        if x>=cx16.r4+0-cx16.r5
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4+65535-cx16.r5
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=cx16.r4+65535-cx16.r5
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x>=cx16.r4+65535-cx16.r5
            success++

        ; with else
        if x>=cx16.r4+65535-cx16.r5
            success++
        else
            cx16.r0L++

    verify_success(13)
}
*/

}

