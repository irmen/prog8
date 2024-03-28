
%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {
    uword success = 0
    str datatype = "uword"
    uword @shared comparison

    sub start() {
        txt.print("\nless-equal split words array tests for: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("\n<=array[]: ")
        test_cmp_array()
        test_stack.test()
    }
    
    sub verify_success(uword expected) {
        if success==expected {
            txt.print("ok")
        } else {
            txt.print(" **failed** ")
            txt.print_uw(success)
            txt.print(" success, expected ")
            txt.print_uw(expected)
        }
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
    

    sub test_cmp_array() {
    uword @shared x
        uword[] @split values = [0, 0]
        uword[] @split sources = [0, 0]
        success = 0
    x=0
    sources[1]=0
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

    ; direct jump
        if sources[1]<=values[1]
            goto lbl1c
        goto skip1c
lbl1c:   success++
skip1c:
        ; indirect jump
        cx16.r3 = &lbl1d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip1d
lbl1d:   success++
skip1d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
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

    ; direct jump
        if sources[1]<=values[1]
            goto lbl2c
        goto skip2c
lbl2c:   success++
skip2c:
        ; indirect jump
        cx16.r3 = &lbl2d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip2d
lbl2d:   success++
skip2d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
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

    ; direct jump
        if sources[1]<=values[1]
            goto lbl3c
        goto skip3c
lbl3c:   success++
skip3c:
        ; indirect jump
        cx16.r3 = &lbl3d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip3d
lbl3d:   success++
skip3d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x<=values[1]
            goto lbl4a
        goto skip4a
lbl4a:   success++
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x<=values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   success++
skip4b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl4c
        goto skip4c
lbl4c:   success++
skip4c:
        ; indirect jump
        cx16.r3 = &lbl4d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip4d
lbl4d:   success++
skip4d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    x=1
    sources[1]=1
    values[1]=0
    ; direct jump
        if x<=values[1]
            goto lbl5a
        goto skip5a
lbl5a:   fail_uword(1)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x<=values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   fail_uword(2)
skip5b:
        ; no else
        if x<=values[1]
            fail_uword(3)

        ; with else
        if x<=values[1]
            fail_uword(4)
        else
            success++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl5c
        goto skip5c
lbl5c:   fail_uword(5)
skip5c:
        ; indirect jump
        cx16.r3 = &lbl5d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip5d
lbl5d:   fail_uword(6)
skip5d:
        ; no else
        if sources[1]<=values[1]
            fail_uword(7)

        ; with else
        if sources[1]<=values[1]
            fail_uword(8)
        else
            success++

    values[1]=1
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

    ; direct jump
        if sources[1]<=values[1]
            goto lbl6c
        goto skip6c
lbl6c:   success++
skip6c:
        ; indirect jump
        cx16.r3 = &lbl6d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip6d
lbl6d:   success++
skip6d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x<=values[1]
            goto lbl7a
        goto skip7a
lbl7a:   success++
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x<=values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   success++
skip7b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl7c
        goto skip7c
lbl7c:   success++
skip7c:
        ; indirect jump
        cx16.r3 = &lbl7d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip7d
lbl7d:   success++
skip7d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x<=values[1]
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x<=values[1]
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl8c
        goto skip8c
lbl8c:   success++
skip8c:
        ; indirect jump
        cx16.r3 = &lbl8d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip8d
lbl8d:   success++
skip8d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    x=30464
    sources[1]=30464
    values[1]=0
    ; direct jump
        if x<=values[1]
            goto lbl9a
        goto skip9a
lbl9a:   fail_uword(9)
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x<=values[1]
            goto cx16.r3
        goto skip9b
lbl9b:   fail_uword(10)
skip9b:
        ; no else
        if x<=values[1]
            fail_uword(11)

        ; with else
        if x<=values[1]
            fail_uword(12)
        else
            success++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl9c
        goto skip9c
lbl9c:   fail_uword(13)
skip9c:
        ; indirect jump
        cx16.r3 = &lbl9d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip9d
lbl9d:   fail_uword(14)
skip9d:
        ; no else
        if sources[1]<=values[1]
            fail_uword(15)

        ; with else
        if sources[1]<=values[1]
            fail_uword(16)
        else
            success++

    values[1]=1
    ; direct jump
        if x<=values[1]
            goto lbl10a
        goto skip10a
lbl10a:   fail_uword(17)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x<=values[1]
            goto cx16.r3
        goto skip10b
lbl10b:   fail_uword(18)
skip10b:
        ; no else
        if x<=values[1]
            fail_uword(19)

        ; with else
        if x<=values[1]
            fail_uword(20)
        else
            success++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl10c
        goto skip10c
lbl10c:   fail_uword(21)
skip10c:
        ; indirect jump
        cx16.r3 = &lbl10d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip10d
lbl10d:   fail_uword(22)
skip10d:
        ; no else
        if sources[1]<=values[1]
            fail_uword(23)

        ; with else
        if sources[1]<=values[1]
            fail_uword(24)
        else
            success++

    values[1]=30464
    ; direct jump
        if x<=values[1]
            goto lbl11a
        goto skip11a
lbl11a:   success++
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x<=values[1]
            goto cx16.r3
        goto skip11b
lbl11b:   success++
skip11b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl11c
        goto skip11c
lbl11c:   success++
skip11c:
        ; indirect jump
        cx16.r3 = &lbl11d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip11d
lbl11d:   success++
skip11d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    values[1]=65535
    ; direct jump
        if x<=values[1]
            goto lbl12a
        goto skip12a
lbl12a:   success++
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x<=values[1]
            goto cx16.r3
        goto skip12b
lbl12b:   success++
skip12b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl12c
        goto skip12c
lbl12c:   success++
skip12c:
        ; indirect jump
        cx16.r3 = &lbl12d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip12d
lbl12d:   success++
skip12d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    x=65535
    sources[1]=65535
    values[1]=0
    ; direct jump
        if x<=values[1]
            goto lbl13a
        goto skip13a
lbl13a:   fail_uword(25)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x<=values[1]
            goto cx16.r3
        goto skip13b
lbl13b:   fail_uword(26)
skip13b:
        ; no else
        if x<=values[1]
            fail_uword(27)

        ; with else
        if x<=values[1]
            fail_uword(28)
        else
            success++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl13c
        goto skip13c
lbl13c:   fail_uword(29)
skip13c:
        ; indirect jump
        cx16.r3 = &lbl13d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip13d
lbl13d:   fail_uword(30)
skip13d:
        ; no else
        if sources[1]<=values[1]
            fail_uword(31)

        ; with else
        if sources[1]<=values[1]
            fail_uword(32)
        else
            success++

    values[1]=1
    ; direct jump
        if x<=values[1]
            goto lbl14a
        goto skip14a
lbl14a:   fail_uword(33)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x<=values[1]
            goto cx16.r3
        goto skip14b
lbl14b:   fail_uword(34)
skip14b:
        ; no else
        if x<=values[1]
            fail_uword(35)

        ; with else
        if x<=values[1]
            fail_uword(36)
        else
            success++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl14c
        goto skip14c
lbl14c:   fail_uword(37)
skip14c:
        ; indirect jump
        cx16.r3 = &lbl14d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip14d
lbl14d:   fail_uword(38)
skip14d:
        ; no else
        if sources[1]<=values[1]
            fail_uword(39)

        ; with else
        if sources[1]<=values[1]
            fail_uword(40)
        else
            success++

    values[1]=30464
    ; direct jump
        if x<=values[1]
            goto lbl15a
        goto skip15a
lbl15a:   fail_uword(41)
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x<=values[1]
            goto cx16.r3
        goto skip15b
lbl15b:   fail_uword(42)
skip15b:
        ; no else
        if x<=values[1]
            fail_uword(43)

        ; with else
        if x<=values[1]
            fail_uword(44)
        else
            success++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl15c
        goto skip15c
lbl15c:   fail_uword(45)
skip15c:
        ; indirect jump
        cx16.r3 = &lbl15d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip15d
lbl15d:   fail_uword(46)
skip15d:
        ; no else
        if sources[1]<=values[1]
            fail_uword(47)

        ; with else
        if sources[1]<=values[1]
            fail_uword(48)
        else
            success++

    values[1]=65535
    ; direct jump
        if x<=values[1]
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x<=values[1]
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
        ; no else
        if x<=values[1]
            success++

        ; with else
        if x<=values[1]
            success++
        else
            cx16.r0L++

    ; direct jump
        if sources[1]<=values[1]
            goto lbl16c
        goto skip16c
lbl16c:   success++
skip16c:
        ; indirect jump
        cx16.r3 = &lbl16d
        if sources[1]<=values[1]
            goto cx16.r3
        goto skip16d
lbl16d:   success++
skip16d:
        ; no else
        if sources[1]<=values[1]
            success++

        ; with else
        if sources[1]<=values[1]
            success++
        else
            cx16.r0L++

    verify_success(92)
}

}

