
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
    word @shared x
        success = 0
    x=-21829
    ; direct jump
        if x>=-21829
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x>=-21829
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x>=-21829
            success++

        ; with else
        if x>=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=-1
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(1)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=-1
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(2)
skip2b:
        ; no else
        if x>=-1
            fail_word(3)

        ; with else
        if x>=-1
            fail_word(4)
        else
            success++

    ; direct jump
        if x>=0
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(5)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=0
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(6)
skip3b:
        ; no else
        if x>=0
            fail_word(7)

        ; with else
        if x>=0
            fail_word(8)
        else
            success++

    ; direct jump
        if x>=1
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(9)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=1
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(10)
skip4b:
        ; no else
        if x>=1
            fail_word(11)

        ; with else
        if x>=1
            fail_word(12)
        else
            success++

    ; direct jump
        if x>=170
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(13)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=170
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(14)
skip5b:
        ; no else
        if x>=170
            fail_word(15)

        ; with else
        if x>=170
            fail_word(16)
        else
            success++

    ; direct jump
        if x>=30464
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(17)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=30464
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(18)
skip6b:
        ; no else
        if x>=30464
            fail_word(19)

        ; with else
        if x>=30464
            fail_word(20)
        else
            success++

    ; direct jump
        if x>=32767
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(21)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=32767
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(22)
skip7b:
        ; no else
        if x>=32767
            fail_word(23)

        ; with else
        if x>=32767
            fail_word(24)
        else
            success++

    x=-1
    ; direct jump
        if x>=-21829
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x>=-21829
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x>=-21829
            success++

        ; with else
        if x>=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=-1
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x>=-1
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
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
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(25)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x>=0
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(26)
skip10b:
        ; no else
        if x>=0
            fail_word(27)

        ; with else
        if x>=0
            fail_word(28)
        else
            success++

    ; direct jump
        if x>=1
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(29)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x>=1
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(30)
skip11b:
        ; no else
        if x>=1
            fail_word(31)

        ; with else
        if x>=1
            fail_word(32)
        else
            success++

    ; direct jump
        if x>=170
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(33)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x>=170
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(34)
skip12b:
        ; no else
        if x>=170
            fail_word(35)

        ; with else
        if x>=170
            fail_word(36)
        else
            success++

    ; direct jump
        if x>=30464
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(37)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x>=30464
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(38)
skip13b:
        ; no else
        if x>=30464
            fail_word(39)

        ; with else
        if x>=30464
            fail_word(40)
        else
            success++

    ; direct jump
        if x>=32767
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(41)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x>=32767
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(42)
skip14b:
        ; no else
        if x>=32767
            fail_word(43)

        ; with else
        if x>=32767
            fail_word(44)
        else
            success++

    x=0
    ; direct jump
        if x>=-21829
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x>=-21829
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
        ; no else
        if x>=-21829
            success++

        ; with else
        if x>=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=-1
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x>=-1
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
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
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x>=0
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
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
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(45)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x>=1
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(46)
skip18b:
        ; no else
        if x>=1
            fail_word(47)

        ; with else
        if x>=1
            fail_word(48)
        else
            success++

    ; direct jump
        if x>=170
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(49)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x>=170
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(50)
skip19b:
        ; no else
        if x>=170
            fail_word(51)

        ; with else
        if x>=170
            fail_word(52)
        else
            success++

    ; direct jump
        if x>=30464
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(53)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x>=30464
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(54)
skip20b:
        ; no else
        if x>=30464
            fail_word(55)

        ; with else
        if x>=30464
            fail_word(56)
        else
            success++

    ; direct jump
        if x>=32767
            goto lbl21a
        goto skip21a
lbl21a:   fail_word(57)
skip21a:
        ; indirect jump
        cx16.r3 = &lbl21b
        if x>=32767
            goto cx16.r3
        goto skip21b
lbl21b:   fail_word(58)
skip21b:
        ; no else
        if x>=32767
            fail_word(59)

        ; with else
        if x>=32767
            fail_word(60)
        else
            success++

    x=1
    ; direct jump
        if x>=-21829
            goto lbl22a
        goto skip22a
lbl22a:   success++
skip22a:
        ; indirect jump
        cx16.r3 = &lbl22b
        if x>=-21829
            goto cx16.r3
        goto skip22b
lbl22b:   success++
skip22b:
        ; no else
        if x>=-21829
            success++

        ; with else
        if x>=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=-1
            goto lbl23a
        goto skip23a
lbl23a:   success++
skip23a:
        ; indirect jump
        cx16.r3 = &lbl23b
        if x>=-1
            goto cx16.r3
        goto skip23b
lbl23b:   success++
skip23b:
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
            goto lbl24a
        goto skip24a
lbl24a:   success++
skip24a:
        ; indirect jump
        cx16.r3 = &lbl24b
        if x>=0
            goto cx16.r3
        goto skip24b
lbl24b:   success++
skip24b:
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
            goto lbl25a
        goto skip25a
lbl25a:   success++
skip25a:
        ; indirect jump
        cx16.r3 = &lbl25b
        if x>=1
            goto cx16.r3
        goto skip25b
lbl25b:   success++
skip25b:
        ; no else
        if x>=1
            success++

        ; with else
        if x>=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=170
            goto lbl26a
        goto skip26a
lbl26a:   fail_word(61)
skip26a:
        ; indirect jump
        cx16.r3 = &lbl26b
        if x>=170
            goto cx16.r3
        goto skip26b
lbl26b:   fail_word(62)
skip26b:
        ; no else
        if x>=170
            fail_word(63)

        ; with else
        if x>=170
            fail_word(64)
        else
            success++

    ; direct jump
        if x>=30464
            goto lbl27a
        goto skip27a
lbl27a:   fail_word(65)
skip27a:
        ; indirect jump
        cx16.r3 = &lbl27b
        if x>=30464
            goto cx16.r3
        goto skip27b
lbl27b:   fail_word(66)
skip27b:
        ; no else
        if x>=30464
            fail_word(67)

        ; with else
        if x>=30464
            fail_word(68)
        else
            success++

    ; direct jump
        if x>=32767
            goto lbl28a
        goto skip28a
lbl28a:   fail_word(69)
skip28a:
        ; indirect jump
        cx16.r3 = &lbl28b
        if x>=32767
            goto cx16.r3
        goto skip28b
lbl28b:   fail_word(70)
skip28b:
        ; no else
        if x>=32767
            fail_word(71)

        ; with else
        if x>=32767
            fail_word(72)
        else
            success++

    x=170
    ; direct jump
        if x>=-21829
            goto lbl29a
        goto skip29a
lbl29a:   success++
skip29a:
        ; indirect jump
        cx16.r3 = &lbl29b
        if x>=-21829
            goto cx16.r3
        goto skip29b
lbl29b:   success++
skip29b:
        ; no else
        if x>=-21829
            success++

        ; with else
        if x>=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=-1
            goto lbl30a
        goto skip30a
lbl30a:   success++
skip30a:
        ; indirect jump
        cx16.r3 = &lbl30b
        if x>=-1
            goto cx16.r3
        goto skip30b
lbl30b:   success++
skip30b:
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
            goto lbl31a
        goto skip31a
lbl31a:   success++
skip31a:
        ; indirect jump
        cx16.r3 = &lbl31b
        if x>=0
            goto cx16.r3
        goto skip31b
lbl31b:   success++
skip31b:
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
            goto lbl32a
        goto skip32a
lbl32a:   success++
skip32a:
        ; indirect jump
        cx16.r3 = &lbl32b
        if x>=1
            goto cx16.r3
        goto skip32b
lbl32b:   success++
skip32b:
        ; no else
        if x>=1
            success++

        ; with else
        if x>=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=170
            goto lbl33a
        goto skip33a
lbl33a:   success++
skip33a:
        ; indirect jump
        cx16.r3 = &lbl33b
        if x>=170
            goto cx16.r3
        goto skip33b
lbl33b:   success++
skip33b:
        ; no else
        if x>=170
            success++

        ; with else
        if x>=170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=30464
            goto lbl34a
        goto skip34a
lbl34a:   fail_word(73)
skip34a:
        ; indirect jump
        cx16.r3 = &lbl34b
        if x>=30464
            goto cx16.r3
        goto skip34b
lbl34b:   fail_word(74)
skip34b:
        ; no else
        if x>=30464
            fail_word(75)

        ; with else
        if x>=30464
            fail_word(76)
        else
            success++

    ; direct jump
        if x>=32767
            goto lbl35a
        goto skip35a
lbl35a:   fail_word(77)
skip35a:
        ; indirect jump
        cx16.r3 = &lbl35b
        if x>=32767
            goto cx16.r3
        goto skip35b
lbl35b:   fail_word(78)
skip35b:
        ; no else
        if x>=32767
            fail_word(79)

        ; with else
        if x>=32767
            fail_word(80)
        else
            success++

    x=30464
    ; direct jump
        if x>=-21829
            goto lbl36a
        goto skip36a
lbl36a:   success++
skip36a:
        ; indirect jump
        cx16.r3 = &lbl36b
        if x>=-21829
            goto cx16.r3
        goto skip36b
lbl36b:   success++
skip36b:
        ; no else
        if x>=-21829
            success++

        ; with else
        if x>=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=-1
            goto lbl37a
        goto skip37a
lbl37a:   success++
skip37a:
        ; indirect jump
        cx16.r3 = &lbl37b
        if x>=-1
            goto cx16.r3
        goto skip37b
lbl37b:   success++
skip37b:
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
            goto lbl38a
        goto skip38a
lbl38a:   success++
skip38a:
        ; indirect jump
        cx16.r3 = &lbl38b
        if x>=0
            goto cx16.r3
        goto skip38b
lbl38b:   success++
skip38b:
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
            goto lbl39a
        goto skip39a
lbl39a:   success++
skip39a:
        ; indirect jump
        cx16.r3 = &lbl39b
        if x>=1
            goto cx16.r3
        goto skip39b
lbl39b:   success++
skip39b:
        ; no else
        if x>=1
            success++

        ; with else
        if x>=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=170
            goto lbl40a
        goto skip40a
lbl40a:   success++
skip40a:
        ; indirect jump
        cx16.r3 = &lbl40b
        if x>=170
            goto cx16.r3
        goto skip40b
lbl40b:   success++
skip40b:
        ; no else
        if x>=170
            success++

        ; with else
        if x>=170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=30464
            goto lbl41a
        goto skip41a
lbl41a:   success++
skip41a:
        ; indirect jump
        cx16.r3 = &lbl41b
        if x>=30464
            goto cx16.r3
        goto skip41b
lbl41b:   success++
skip41b:
        ; no else
        if x>=30464
            success++

        ; with else
        if x>=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=32767
            goto lbl42a
        goto skip42a
lbl42a:   fail_word(81)
skip42a:
        ; indirect jump
        cx16.r3 = &lbl42b
        if x>=32767
            goto cx16.r3
        goto skip42b
lbl42b:   fail_word(82)
skip42b:
        ; no else
        if x>=32767
            fail_word(83)

        ; with else
        if x>=32767
            fail_word(84)
        else
            success++

    x=32767
    ; direct jump
        if x>=-21829
            goto lbl43a
        goto skip43a
lbl43a:   success++
skip43a:
        ; indirect jump
        cx16.r3 = &lbl43b
        if x>=-21829
            goto cx16.r3
        goto skip43b
lbl43b:   success++
skip43b:
        ; no else
        if x>=-21829
            success++

        ; with else
        if x>=-21829
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=-1
            goto lbl44a
        goto skip44a
lbl44a:   success++
skip44a:
        ; indirect jump
        cx16.r3 = &lbl44b
        if x>=-1
            goto cx16.r3
        goto skip44b
lbl44b:   success++
skip44b:
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
            goto lbl45a
        goto skip45a
lbl45a:   success++
skip45a:
        ; indirect jump
        cx16.r3 = &lbl45b
        if x>=0
            goto cx16.r3
        goto skip45b
lbl45b:   success++
skip45b:
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
            goto lbl46a
        goto skip46a
lbl46a:   success++
skip46a:
        ; indirect jump
        cx16.r3 = &lbl46b
        if x>=1
            goto cx16.r3
        goto skip46b
lbl46b:   success++
skip46b:
        ; no else
        if x>=1
            success++

        ; with else
        if x>=1
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=170
            goto lbl47a
        goto skip47a
lbl47a:   success++
skip47a:
        ; indirect jump
        cx16.r3 = &lbl47b
        if x>=170
            goto cx16.r3
        goto skip47b
lbl47b:   success++
skip47b:
        ; no else
        if x>=170
            success++

        ; with else
        if x>=170
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=30464
            goto lbl48a
        goto skip48a
lbl48a:   success++
skip48a:
        ; indirect jump
        cx16.r3 = &lbl48b
        if x>=30464
            goto cx16.r3
        goto skip48b
lbl48b:   success++
skip48b:
        ; no else
        if x>=30464
            success++

        ; with else
        if x>=30464
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=32767
            goto lbl49a
        goto skip49a
lbl49a:   success++
skip49a:
        ; indirect jump
        cx16.r3 = &lbl49b
        if x>=32767
            goto cx16.r3
        goto skip49b
lbl49b:   success++
skip49b:
        ; no else
        if x>=32767
            success++

        ; with else
        if x>=32767
            success++
        else
            cx16.r0L++

    verify_success(133)
}
    sub test_cmp_var() {
    word @shared x, value
        success = 0
    x=-21829
    value=-21829
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

    value=-1
    ; direct jump
        if x>=value
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(85)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=value
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(86)
skip2b:
        ; no else
        if x>=value
            fail_word(87)

        ; with else
        if x>=value
            fail_word(88)
        else
            success++

    value=0
    ; direct jump
        if x>=value
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(89)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=value
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(90)
skip3b:
        ; no else
        if x>=value
            fail_word(91)

        ; with else
        if x>=value
            fail_word(92)
        else
            success++

    value=1
    ; direct jump
        if x>=value
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(93)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=value
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(94)
skip4b:
        ; no else
        if x>=value
            fail_word(95)

        ; with else
        if x>=value
            fail_word(96)
        else
            success++

    value=170
    ; direct jump
        if x>=value
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(97)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=value
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(98)
skip5b:
        ; no else
        if x>=value
            fail_word(99)

        ; with else
        if x>=value
            fail_word(100)
        else
            success++

    value=30464
    ; direct jump
        if x>=value
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(101)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=value
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(102)
skip6b:
        ; no else
        if x>=value
            fail_word(103)

        ; with else
        if x>=value
            fail_word(104)
        else
            success++

    value=32767
    ; direct jump
        if x>=value
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(105)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=value
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(106)
skip7b:
        ; no else
        if x>=value
            fail_word(107)

        ; with else
        if x>=value
            fail_word(108)
        else
            success++

    x=-1
    value=-21829
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

    value=-1
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

    value=0
    ; direct jump
        if x>=value
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(109)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x>=value
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(110)
skip10b:
        ; no else
        if x>=value
            fail_word(111)

        ; with else
        if x>=value
            fail_word(112)
        else
            success++

    value=1
    ; direct jump
        if x>=value
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(113)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x>=value
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(114)
skip11b:
        ; no else
        if x>=value
            fail_word(115)

        ; with else
        if x>=value
            fail_word(116)
        else
            success++

    value=170
    ; direct jump
        if x>=value
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(117)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x>=value
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(118)
skip12b:
        ; no else
        if x>=value
            fail_word(119)

        ; with else
        if x>=value
            fail_word(120)
        else
            success++

    value=30464
    ; direct jump
        if x>=value
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(121)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x>=value
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(122)
skip13b:
        ; no else
        if x>=value
            fail_word(123)

        ; with else
        if x>=value
            fail_word(124)
        else
            success++

    value=32767
    ; direct jump
        if x>=value
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(125)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x>=value
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(126)
skip14b:
        ; no else
        if x>=value
            fail_word(127)

        ; with else
        if x>=value
            fail_word(128)
        else
            success++

    x=0
    value=-21829
    ; direct jump
        if x>=value
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x>=value
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=-1
    ; direct jump
        if x>=value
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x>=value
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
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
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x>=value
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
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
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(129)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x>=value
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(130)
skip18b:
        ; no else
        if x>=value
            fail_word(131)

        ; with else
        if x>=value
            fail_word(132)
        else
            success++

    value=170
    ; direct jump
        if x>=value
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(133)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x>=value
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(134)
skip19b:
        ; no else
        if x>=value
            fail_word(135)

        ; with else
        if x>=value
            fail_word(136)
        else
            success++

    value=30464
    ; direct jump
        if x>=value
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(137)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x>=value
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(138)
skip20b:
        ; no else
        if x>=value
            fail_word(139)

        ; with else
        if x>=value
            fail_word(140)
        else
            success++

    value=32767
    ; direct jump
        if x>=value
            goto lbl21a
        goto skip21a
lbl21a:   fail_word(141)
skip21a:
        ; indirect jump
        cx16.r3 = &lbl21b
        if x>=value
            goto cx16.r3
        goto skip21b
lbl21b:   fail_word(142)
skip21b:
        ; no else
        if x>=value
            fail_word(143)

        ; with else
        if x>=value
            fail_word(144)
        else
            success++

    x=1
    value=-21829
    ; direct jump
        if x>=value
            goto lbl22a
        goto skip22a
lbl22a:   success++
skip22a:
        ; indirect jump
        cx16.r3 = &lbl22b
        if x>=value
            goto cx16.r3
        goto skip22b
lbl22b:   success++
skip22b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=-1
    ; direct jump
        if x>=value
            goto lbl23a
        goto skip23a
lbl23a:   success++
skip23a:
        ; indirect jump
        cx16.r3 = &lbl23b
        if x>=value
            goto cx16.r3
        goto skip23b
lbl23b:   success++
skip23b:
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
            goto lbl24a
        goto skip24a
lbl24a:   success++
skip24a:
        ; indirect jump
        cx16.r3 = &lbl24b
        if x>=value
            goto cx16.r3
        goto skip24b
lbl24b:   success++
skip24b:
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
            goto lbl25a
        goto skip25a
lbl25a:   success++
skip25a:
        ; indirect jump
        cx16.r3 = &lbl25b
        if x>=value
            goto cx16.r3
        goto skip25b
lbl25b:   success++
skip25b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x>=value
            goto lbl26a
        goto skip26a
lbl26a:   fail_word(145)
skip26a:
        ; indirect jump
        cx16.r3 = &lbl26b
        if x>=value
            goto cx16.r3
        goto skip26b
lbl26b:   fail_word(146)
skip26b:
        ; no else
        if x>=value
            fail_word(147)

        ; with else
        if x>=value
            fail_word(148)
        else
            success++

    value=30464
    ; direct jump
        if x>=value
            goto lbl27a
        goto skip27a
lbl27a:   fail_word(149)
skip27a:
        ; indirect jump
        cx16.r3 = &lbl27b
        if x>=value
            goto cx16.r3
        goto skip27b
lbl27b:   fail_word(150)
skip27b:
        ; no else
        if x>=value
            fail_word(151)

        ; with else
        if x>=value
            fail_word(152)
        else
            success++

    value=32767
    ; direct jump
        if x>=value
            goto lbl28a
        goto skip28a
lbl28a:   fail_word(153)
skip28a:
        ; indirect jump
        cx16.r3 = &lbl28b
        if x>=value
            goto cx16.r3
        goto skip28b
lbl28b:   fail_word(154)
skip28b:
        ; no else
        if x>=value
            fail_word(155)

        ; with else
        if x>=value
            fail_word(156)
        else
            success++

    x=170
    value=-21829
    ; direct jump
        if x>=value
            goto lbl29a
        goto skip29a
lbl29a:   success++
skip29a:
        ; indirect jump
        cx16.r3 = &lbl29b
        if x>=value
            goto cx16.r3
        goto skip29b
lbl29b:   success++
skip29b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=-1
    ; direct jump
        if x>=value
            goto lbl30a
        goto skip30a
lbl30a:   success++
skip30a:
        ; indirect jump
        cx16.r3 = &lbl30b
        if x>=value
            goto cx16.r3
        goto skip30b
lbl30b:   success++
skip30b:
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
            goto lbl31a
        goto skip31a
lbl31a:   success++
skip31a:
        ; indirect jump
        cx16.r3 = &lbl31b
        if x>=value
            goto cx16.r3
        goto skip31b
lbl31b:   success++
skip31b:
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
            goto lbl32a
        goto skip32a
lbl32a:   success++
skip32a:
        ; indirect jump
        cx16.r3 = &lbl32b
        if x>=value
            goto cx16.r3
        goto skip32b
lbl32b:   success++
skip32b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x>=value
            goto lbl33a
        goto skip33a
lbl33a:   success++
skip33a:
        ; indirect jump
        cx16.r3 = &lbl33b
        if x>=value
            goto cx16.r3
        goto skip33b
lbl33b:   success++
skip33b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x>=value
            goto lbl34a
        goto skip34a
lbl34a:   fail_word(157)
skip34a:
        ; indirect jump
        cx16.r3 = &lbl34b
        if x>=value
            goto cx16.r3
        goto skip34b
lbl34b:   fail_word(158)
skip34b:
        ; no else
        if x>=value
            fail_word(159)

        ; with else
        if x>=value
            fail_word(160)
        else
            success++

    value=32767
    ; direct jump
        if x>=value
            goto lbl35a
        goto skip35a
lbl35a:   fail_word(161)
skip35a:
        ; indirect jump
        cx16.r3 = &lbl35b
        if x>=value
            goto cx16.r3
        goto skip35b
lbl35b:   fail_word(162)
skip35b:
        ; no else
        if x>=value
            fail_word(163)

        ; with else
        if x>=value
            fail_word(164)
        else
            success++

    x=30464
    value=-21829
    ; direct jump
        if x>=value
            goto lbl36a
        goto skip36a
lbl36a:   success++
skip36a:
        ; indirect jump
        cx16.r3 = &lbl36b
        if x>=value
            goto cx16.r3
        goto skip36b
lbl36b:   success++
skip36b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=-1
    ; direct jump
        if x>=value
            goto lbl37a
        goto skip37a
lbl37a:   success++
skip37a:
        ; indirect jump
        cx16.r3 = &lbl37b
        if x>=value
            goto cx16.r3
        goto skip37b
lbl37b:   success++
skip37b:
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
            goto lbl38a
        goto skip38a
lbl38a:   success++
skip38a:
        ; indirect jump
        cx16.r3 = &lbl38b
        if x>=value
            goto cx16.r3
        goto skip38b
lbl38b:   success++
skip38b:
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
            goto lbl39a
        goto skip39a
lbl39a:   success++
skip39a:
        ; indirect jump
        cx16.r3 = &lbl39b
        if x>=value
            goto cx16.r3
        goto skip39b
lbl39b:   success++
skip39b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x>=value
            goto lbl40a
        goto skip40a
lbl40a:   success++
skip40a:
        ; indirect jump
        cx16.r3 = &lbl40b
        if x>=value
            goto cx16.r3
        goto skip40b
lbl40b:   success++
skip40b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x>=value
            goto lbl41a
        goto skip41a
lbl41a:   success++
skip41a:
        ; indirect jump
        cx16.r3 = &lbl41b
        if x>=value
            goto cx16.r3
        goto skip41b
lbl41b:   success++
skip41b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x>=value
            goto lbl42a
        goto skip42a
lbl42a:   fail_word(165)
skip42a:
        ; indirect jump
        cx16.r3 = &lbl42b
        if x>=value
            goto cx16.r3
        goto skip42b
lbl42b:   fail_word(166)
skip42b:
        ; no else
        if x>=value
            fail_word(167)

        ; with else
        if x>=value
            fail_word(168)
        else
            success++

    x=32767
    value=-21829
    ; direct jump
        if x>=value
            goto lbl43a
        goto skip43a
lbl43a:   success++
skip43a:
        ; indirect jump
        cx16.r3 = &lbl43b
        if x>=value
            goto cx16.r3
        goto skip43b
lbl43b:   success++
skip43b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=-1
    ; direct jump
        if x>=value
            goto lbl44a
        goto skip44a
lbl44a:   success++
skip44a:
        ; indirect jump
        cx16.r3 = &lbl44b
        if x>=value
            goto cx16.r3
        goto skip44b
lbl44b:   success++
skip44b:
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
            goto lbl45a
        goto skip45a
lbl45a:   success++
skip45a:
        ; indirect jump
        cx16.r3 = &lbl45b
        if x>=value
            goto cx16.r3
        goto skip45b
lbl45b:   success++
skip45b:
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
            goto lbl46a
        goto skip46a
lbl46a:   success++
skip46a:
        ; indirect jump
        cx16.r3 = &lbl46b
        if x>=value
            goto cx16.r3
        goto skip46b
lbl46b:   success++
skip46b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=170
    ; direct jump
        if x>=value
            goto lbl47a
        goto skip47a
lbl47a:   success++
skip47a:
        ; indirect jump
        cx16.r3 = &lbl47b
        if x>=value
            goto cx16.r3
        goto skip47b
lbl47b:   success++
skip47b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=30464
    ; direct jump
        if x>=value
            goto lbl48a
        goto skip48a
lbl48a:   success++
skip48a:
        ; indirect jump
        cx16.r3 = &lbl48b
        if x>=value
            goto cx16.r3
        goto skip48b
lbl48b:   success++
skip48b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    value=32767
    ; direct jump
        if x>=value
            goto lbl49a
        goto skip49a
lbl49a:   success++
skip49a:
        ; indirect jump
        cx16.r3 = &lbl49b
        if x>=value
            goto cx16.r3
        goto skip49b
lbl49b:   success++
skip49b:
        ; no else
        if x>=value
            success++

        ; with else
        if x>=value
            success++
        else
            cx16.r0L++

    verify_success(133)
}
    sub test_cmp_array() {
    word @shared x
        word[] values = [0, 0]
        success = 0
    x=-21829
    values[1]=-21829
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

    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(169)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=values[1]
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(170)
skip2b:
        ; no else
        if x>=values[1]
            fail_word(171)

        ; with else
        if x>=values[1]
            fail_word(172)
        else
            success++

    values[1]=0
    ; direct jump
        if x>=values[1]
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(173)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=values[1]
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(174)
skip3b:
        ; no else
        if x>=values[1]
            fail_word(175)

        ; with else
        if x>=values[1]
            fail_word(176)
        else
            success++

    values[1]=1
    ; direct jump
        if x>=values[1]
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(177)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=values[1]
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(178)
skip4b:
        ; no else
        if x>=values[1]
            fail_word(179)

        ; with else
        if x>=values[1]
            fail_word(180)
        else
            success++

    values[1]=170
    ; direct jump
        if x>=values[1]
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(181)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=values[1]
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(182)
skip5b:
        ; no else
        if x>=values[1]
            fail_word(183)

        ; with else
        if x>=values[1]
            fail_word(184)
        else
            success++

    values[1]=30464
    ; direct jump
        if x>=values[1]
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(185)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=values[1]
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(186)
skip6b:
        ; no else
        if x>=values[1]
            fail_word(187)

        ; with else
        if x>=values[1]
            fail_word(188)
        else
            success++

    values[1]=32767
    ; direct jump
        if x>=values[1]
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(189)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=values[1]
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(190)
skip7b:
        ; no else
        if x>=values[1]
            fail_word(191)

        ; with else
        if x>=values[1]
            fail_word(192)
        else
            success++

    x=-1
    values[1]=-21829
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

    values[1]=-1
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

    values[1]=0
    ; direct jump
        if x>=values[1]
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(193)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x>=values[1]
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(194)
skip10b:
        ; no else
        if x>=values[1]
            fail_word(195)

        ; with else
        if x>=values[1]
            fail_word(196)
        else
            success++

    values[1]=1
    ; direct jump
        if x>=values[1]
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(197)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x>=values[1]
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(198)
skip11b:
        ; no else
        if x>=values[1]
            fail_word(199)

        ; with else
        if x>=values[1]
            fail_word(200)
        else
            success++

    values[1]=170
    ; direct jump
        if x>=values[1]
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(201)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x>=values[1]
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(202)
skip12b:
        ; no else
        if x>=values[1]
            fail_word(203)

        ; with else
        if x>=values[1]
            fail_word(204)
        else
            success++

    values[1]=30464
    ; direct jump
        if x>=values[1]
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(205)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x>=values[1]
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(206)
skip13b:
        ; no else
        if x>=values[1]
            fail_word(207)

        ; with else
        if x>=values[1]
            fail_word(208)
        else
            success++

    values[1]=32767
    ; direct jump
        if x>=values[1]
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(209)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x>=values[1]
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(210)
skip14b:
        ; no else
        if x>=values[1]
            fail_word(211)

        ; with else
        if x>=values[1]
            fail_word(212)
        else
            success++

    x=0
    values[1]=-21829
    ; direct jump
        if x>=values[1]
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x>=values[1]
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x>=values[1]
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
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
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x>=values[1]
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
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
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(213)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x>=values[1]
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(214)
skip18b:
        ; no else
        if x>=values[1]
            fail_word(215)

        ; with else
        if x>=values[1]
            fail_word(216)
        else
            success++

    values[1]=170
    ; direct jump
        if x>=values[1]
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(217)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x>=values[1]
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(218)
skip19b:
        ; no else
        if x>=values[1]
            fail_word(219)

        ; with else
        if x>=values[1]
            fail_word(220)
        else
            success++

    values[1]=30464
    ; direct jump
        if x>=values[1]
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(221)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x>=values[1]
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(222)
skip20b:
        ; no else
        if x>=values[1]
            fail_word(223)

        ; with else
        if x>=values[1]
            fail_word(224)
        else
            success++

    values[1]=32767
    ; direct jump
        if x>=values[1]
            goto lbl21a
        goto skip21a
lbl21a:   fail_word(225)
skip21a:
        ; indirect jump
        cx16.r3 = &lbl21b
        if x>=values[1]
            goto cx16.r3
        goto skip21b
lbl21b:   fail_word(226)
skip21b:
        ; no else
        if x>=values[1]
            fail_word(227)

        ; with else
        if x>=values[1]
            fail_word(228)
        else
            success++

    x=1
    values[1]=-21829
    ; direct jump
        if x>=values[1]
            goto lbl22a
        goto skip22a
lbl22a:   success++
skip22a:
        ; indirect jump
        cx16.r3 = &lbl22b
        if x>=values[1]
            goto cx16.r3
        goto skip22b
lbl22b:   success++
skip22b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl23a
        goto skip23a
lbl23a:   success++
skip23a:
        ; indirect jump
        cx16.r3 = &lbl23b
        if x>=values[1]
            goto cx16.r3
        goto skip23b
lbl23b:   success++
skip23b:
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
            goto lbl24a
        goto skip24a
lbl24a:   success++
skip24a:
        ; indirect jump
        cx16.r3 = &lbl24b
        if x>=values[1]
            goto cx16.r3
        goto skip24b
lbl24b:   success++
skip24b:
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
            goto lbl25a
        goto skip25a
lbl25a:   success++
skip25a:
        ; indirect jump
        cx16.r3 = &lbl25b
        if x>=values[1]
            goto cx16.r3
        goto skip25b
lbl25b:   success++
skip25b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x>=values[1]
            goto lbl26a
        goto skip26a
lbl26a:   fail_word(229)
skip26a:
        ; indirect jump
        cx16.r3 = &lbl26b
        if x>=values[1]
            goto cx16.r3
        goto skip26b
lbl26b:   fail_word(230)
skip26b:
        ; no else
        if x>=values[1]
            fail_word(231)

        ; with else
        if x>=values[1]
            fail_word(232)
        else
            success++

    values[1]=30464
    ; direct jump
        if x>=values[1]
            goto lbl27a
        goto skip27a
lbl27a:   fail_word(233)
skip27a:
        ; indirect jump
        cx16.r3 = &lbl27b
        if x>=values[1]
            goto cx16.r3
        goto skip27b
lbl27b:   fail_word(234)
skip27b:
        ; no else
        if x>=values[1]
            fail_word(235)

        ; with else
        if x>=values[1]
            fail_word(236)
        else
            success++

    values[1]=32767
    ; direct jump
        if x>=values[1]
            goto lbl28a
        goto skip28a
lbl28a:   fail_word(237)
skip28a:
        ; indirect jump
        cx16.r3 = &lbl28b
        if x>=values[1]
            goto cx16.r3
        goto skip28b
lbl28b:   fail_word(238)
skip28b:
        ; no else
        if x>=values[1]
            fail_word(239)

        ; with else
        if x>=values[1]
            fail_word(240)
        else
            success++

    x=170
    values[1]=-21829
    ; direct jump
        if x>=values[1]
            goto lbl29a
        goto skip29a
lbl29a:   success++
skip29a:
        ; indirect jump
        cx16.r3 = &lbl29b
        if x>=values[1]
            goto cx16.r3
        goto skip29b
lbl29b:   success++
skip29b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl30a
        goto skip30a
lbl30a:   success++
skip30a:
        ; indirect jump
        cx16.r3 = &lbl30b
        if x>=values[1]
            goto cx16.r3
        goto skip30b
lbl30b:   success++
skip30b:
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
            goto lbl31a
        goto skip31a
lbl31a:   success++
skip31a:
        ; indirect jump
        cx16.r3 = &lbl31b
        if x>=values[1]
            goto cx16.r3
        goto skip31b
lbl31b:   success++
skip31b:
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
            goto lbl32a
        goto skip32a
lbl32a:   success++
skip32a:
        ; indirect jump
        cx16.r3 = &lbl32b
        if x>=values[1]
            goto cx16.r3
        goto skip32b
lbl32b:   success++
skip32b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x>=values[1]
            goto lbl33a
        goto skip33a
lbl33a:   success++
skip33a:
        ; indirect jump
        cx16.r3 = &lbl33b
        if x>=values[1]
            goto cx16.r3
        goto skip33b
lbl33b:   success++
skip33b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x>=values[1]
            goto lbl34a
        goto skip34a
lbl34a:   fail_word(241)
skip34a:
        ; indirect jump
        cx16.r3 = &lbl34b
        if x>=values[1]
            goto cx16.r3
        goto skip34b
lbl34b:   fail_word(242)
skip34b:
        ; no else
        if x>=values[1]
            fail_word(243)

        ; with else
        if x>=values[1]
            fail_word(244)
        else
            success++

    values[1]=32767
    ; direct jump
        if x>=values[1]
            goto lbl35a
        goto skip35a
lbl35a:   fail_word(245)
skip35a:
        ; indirect jump
        cx16.r3 = &lbl35b
        if x>=values[1]
            goto cx16.r3
        goto skip35b
lbl35b:   fail_word(246)
skip35b:
        ; no else
        if x>=values[1]
            fail_word(247)

        ; with else
        if x>=values[1]
            fail_word(248)
        else
            success++

    x=30464
    values[1]=-21829
    ; direct jump
        if x>=values[1]
            goto lbl36a
        goto skip36a
lbl36a:   success++
skip36a:
        ; indirect jump
        cx16.r3 = &lbl36b
        if x>=values[1]
            goto cx16.r3
        goto skip36b
lbl36b:   success++
skip36b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl37a
        goto skip37a
lbl37a:   success++
skip37a:
        ; indirect jump
        cx16.r3 = &lbl37b
        if x>=values[1]
            goto cx16.r3
        goto skip37b
lbl37b:   success++
skip37b:
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
            goto lbl38a
        goto skip38a
lbl38a:   success++
skip38a:
        ; indirect jump
        cx16.r3 = &lbl38b
        if x>=values[1]
            goto cx16.r3
        goto skip38b
lbl38b:   success++
skip38b:
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
            goto lbl39a
        goto skip39a
lbl39a:   success++
skip39a:
        ; indirect jump
        cx16.r3 = &lbl39b
        if x>=values[1]
            goto cx16.r3
        goto skip39b
lbl39b:   success++
skip39b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x>=values[1]
            goto lbl40a
        goto skip40a
lbl40a:   success++
skip40a:
        ; indirect jump
        cx16.r3 = &lbl40b
        if x>=values[1]
            goto cx16.r3
        goto skip40b
lbl40b:   success++
skip40b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x>=values[1]
            goto lbl41a
        goto skip41a
lbl41a:   success++
skip41a:
        ; indirect jump
        cx16.r3 = &lbl41b
        if x>=values[1]
            goto cx16.r3
        goto skip41b
lbl41b:   success++
skip41b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x>=values[1]
            goto lbl42a
        goto skip42a
lbl42a:   fail_word(249)
skip42a:
        ; indirect jump
        cx16.r3 = &lbl42b
        if x>=values[1]
            goto cx16.r3
        goto skip42b
lbl42b:   fail_word(250)
skip42b:
        ; no else
        if x>=values[1]
            fail_word(251)

        ; with else
        if x>=values[1]
            fail_word(252)
        else
            success++

    x=32767
    values[1]=-21829
    ; direct jump
        if x>=values[1]
            goto lbl43a
        goto skip43a
lbl43a:   success++
skip43a:
        ; indirect jump
        cx16.r3 = &lbl43b
        if x>=values[1]
            goto cx16.r3
        goto skip43b
lbl43b:   success++
skip43b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=-1
    ; direct jump
        if x>=values[1]
            goto lbl44a
        goto skip44a
lbl44a:   success++
skip44a:
        ; indirect jump
        cx16.r3 = &lbl44b
        if x>=values[1]
            goto cx16.r3
        goto skip44b
lbl44b:   success++
skip44b:
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
            goto lbl45a
        goto skip45a
lbl45a:   success++
skip45a:
        ; indirect jump
        cx16.r3 = &lbl45b
        if x>=values[1]
            goto cx16.r3
        goto skip45b
lbl45b:   success++
skip45b:
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
            goto lbl46a
        goto skip46a
lbl46a:   success++
skip46a:
        ; indirect jump
        cx16.r3 = &lbl46b
        if x>=values[1]
            goto cx16.r3
        goto skip46b
lbl46b:   success++
skip46b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=170
    ; direct jump
        if x>=values[1]
            goto lbl47a
        goto skip47a
lbl47a:   success++
skip47a:
        ; indirect jump
        cx16.r3 = &lbl47b
        if x>=values[1]
            goto cx16.r3
        goto skip47b
lbl47b:   success++
skip47b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=30464
    ; direct jump
        if x>=values[1]
            goto lbl48a
        goto skip48a
lbl48a:   success++
skip48a:
        ; indirect jump
        cx16.r3 = &lbl48b
        if x>=values[1]
            goto cx16.r3
        goto skip48b
lbl48b:   success++
skip48b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    values[1]=32767
    ; direct jump
        if x>=values[1]
            goto lbl49a
        goto skip49a
lbl49a:   success++
skip49a:
        ; indirect jump
        cx16.r3 = &lbl49b
        if x>=values[1]
            goto cx16.r3
        goto skip49b
lbl49b:   success++
skip49b:
        ; no else
        if x>=values[1]
            success++

        ; with else
        if x>=values[1]
            success++
        else
            cx16.r0L++

    verify_success(133)
}
    sub test_cmp_expr() {
    word @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0
    x=-21829
    ; direct jump
        if x>=cx16.r4s+-21829-cx16.r5s
            goto lbl1a
        goto skip1a
lbl1a:   success++
skip1a:
        ; indirect jump
        cx16.r3 = &lbl1b
        if x>=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip1b
lbl1b:   success++
skip1b:
        ; no else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+-1-cx16.r5s
            goto lbl2a
        goto skip2a
lbl2a:   fail_word(253)
skip2a:
        ; indirect jump
        cx16.r3 = &lbl2b
        if x>=cx16.r4s+-1-cx16.r5s
            goto cx16.r3
        goto skip2b
lbl2b:   fail_word(254)
skip2b:
        ; no else
        if x>=cx16.r4s+-1-cx16.r5s
            fail_word(255)

        ; with else
        if x>=cx16.r4s+-1-cx16.r5s
            fail_word(256)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+0-cx16.r5s
            goto lbl3a
        goto skip3a
lbl3a:   fail_word(257)
skip3a:
        ; indirect jump
        cx16.r3 = &lbl3b
        if x>=cx16.r4s+0-cx16.r5s
            goto cx16.r3
        goto skip3b
lbl3b:   fail_word(258)
skip3b:
        ; no else
        if x>=cx16.r4s+0-cx16.r5s
            fail_word(259)

        ; with else
        if x>=cx16.r4s+0-cx16.r5s
            fail_word(260)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+1-cx16.r5s
            goto lbl4a
        goto skip4a
lbl4a:   fail_word(261)
skip4a:
        ; indirect jump
        cx16.r3 = &lbl4b
        if x>=cx16.r4s+1-cx16.r5s
            goto cx16.r3
        goto skip4b
lbl4b:   fail_word(262)
skip4b:
        ; no else
        if x>=cx16.r4s+1-cx16.r5s
            fail_word(263)

        ; with else
        if x>=cx16.r4s+1-cx16.r5s
            fail_word(264)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+170-cx16.r5s
            goto lbl5a
        goto skip5a
lbl5a:   fail_word(265)
skip5a:
        ; indirect jump
        cx16.r3 = &lbl5b
        if x>=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip5b
lbl5b:   fail_word(266)
skip5b:
        ; no else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(267)

        ; with else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(268)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+30464-cx16.r5s
            goto lbl6a
        goto skip6a
lbl6a:   fail_word(269)
skip6a:
        ; indirect jump
        cx16.r3 = &lbl6b
        if x>=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip6b
lbl6b:   fail_word(270)
skip6b:
        ; no else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(271)

        ; with else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(272)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+32767-cx16.r5s
            goto lbl7a
        goto skip7a
lbl7a:   fail_word(273)
skip7a:
        ; indirect jump
        cx16.r3 = &lbl7b
        if x>=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip7b
lbl7b:   fail_word(274)
skip7b:
        ; no else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(275)

        ; with else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(276)
        else
            success++

    x=-1
    ; direct jump
        if x>=cx16.r4s+-21829-cx16.r5s
            goto lbl8a
        goto skip8a
lbl8a:   success++
skip8a:
        ; indirect jump
        cx16.r3 = &lbl8b
        if x>=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip8b
lbl8b:   success++
skip8b:
        ; no else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+-1-cx16.r5s
            goto lbl9a
        goto skip9a
lbl9a:   success++
skip9a:
        ; indirect jump
        cx16.r3 = &lbl9b
        if x>=cx16.r4s+-1-cx16.r5s
            goto cx16.r3
        goto skip9b
lbl9b:   success++
skip9b:
        ; no else
        if x>=cx16.r4s+-1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+0-cx16.r5s
            goto lbl10a
        goto skip10a
lbl10a:   fail_word(277)
skip10a:
        ; indirect jump
        cx16.r3 = &lbl10b
        if x>=cx16.r4s+0-cx16.r5s
            goto cx16.r3
        goto skip10b
lbl10b:   fail_word(278)
skip10b:
        ; no else
        if x>=cx16.r4s+0-cx16.r5s
            fail_word(279)

        ; with else
        if x>=cx16.r4s+0-cx16.r5s
            fail_word(280)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+1-cx16.r5s
            goto lbl11a
        goto skip11a
lbl11a:   fail_word(281)
skip11a:
        ; indirect jump
        cx16.r3 = &lbl11b
        if x>=cx16.r4s+1-cx16.r5s
            goto cx16.r3
        goto skip11b
lbl11b:   fail_word(282)
skip11b:
        ; no else
        if x>=cx16.r4s+1-cx16.r5s
            fail_word(283)

        ; with else
        if x>=cx16.r4s+1-cx16.r5s
            fail_word(284)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+170-cx16.r5s
            goto lbl12a
        goto skip12a
lbl12a:   fail_word(285)
skip12a:
        ; indirect jump
        cx16.r3 = &lbl12b
        if x>=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip12b
lbl12b:   fail_word(286)
skip12b:
        ; no else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(287)

        ; with else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(288)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+30464-cx16.r5s
            goto lbl13a
        goto skip13a
lbl13a:   fail_word(289)
skip13a:
        ; indirect jump
        cx16.r3 = &lbl13b
        if x>=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip13b
lbl13b:   fail_word(290)
skip13b:
        ; no else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(291)

        ; with else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(292)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+32767-cx16.r5s
            goto lbl14a
        goto skip14a
lbl14a:   fail_word(293)
skip14a:
        ; indirect jump
        cx16.r3 = &lbl14b
        if x>=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip14b
lbl14b:   fail_word(294)
skip14b:
        ; no else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(295)

        ; with else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(296)
        else
            success++

    x=0
    ; direct jump
        if x>=cx16.r4s+-21829-cx16.r5s
            goto lbl15a
        goto skip15a
lbl15a:   success++
skip15a:
        ; indirect jump
        cx16.r3 = &lbl15b
        if x>=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip15b
lbl15b:   success++
skip15b:
        ; no else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+-1-cx16.r5s
            goto lbl16a
        goto skip16a
lbl16a:   success++
skip16a:
        ; indirect jump
        cx16.r3 = &lbl16b
        if x>=cx16.r4s+-1-cx16.r5s
            goto cx16.r3
        goto skip16b
lbl16b:   success++
skip16b:
        ; no else
        if x>=cx16.r4s+-1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+0-cx16.r5s
            goto lbl17a
        goto skip17a
lbl17a:   success++
skip17a:
        ; indirect jump
        cx16.r3 = &lbl17b
        if x>=cx16.r4s+0-cx16.r5s
            goto cx16.r3
        goto skip17b
lbl17b:   success++
skip17b:
        ; no else
        if x>=cx16.r4s+0-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+0-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+1-cx16.r5s
            goto lbl18a
        goto skip18a
lbl18a:   fail_word(297)
skip18a:
        ; indirect jump
        cx16.r3 = &lbl18b
        if x>=cx16.r4s+1-cx16.r5s
            goto cx16.r3
        goto skip18b
lbl18b:   fail_word(298)
skip18b:
        ; no else
        if x>=cx16.r4s+1-cx16.r5s
            fail_word(299)

        ; with else
        if x>=cx16.r4s+1-cx16.r5s
            fail_word(300)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+170-cx16.r5s
            goto lbl19a
        goto skip19a
lbl19a:   fail_word(301)
skip19a:
        ; indirect jump
        cx16.r3 = &lbl19b
        if x>=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip19b
lbl19b:   fail_word(302)
skip19b:
        ; no else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(303)

        ; with else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(304)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+30464-cx16.r5s
            goto lbl20a
        goto skip20a
lbl20a:   fail_word(305)
skip20a:
        ; indirect jump
        cx16.r3 = &lbl20b
        if x>=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip20b
lbl20b:   fail_word(306)
skip20b:
        ; no else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(307)

        ; with else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(308)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+32767-cx16.r5s
            goto lbl21a
        goto skip21a
lbl21a:   fail_word(309)
skip21a:
        ; indirect jump
        cx16.r3 = &lbl21b
        if x>=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip21b
lbl21b:   fail_word(310)
skip21b:
        ; no else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(311)

        ; with else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(312)
        else
            success++

    x=1
    ; direct jump
        if x>=cx16.r4s+-21829-cx16.r5s
            goto lbl22a
        goto skip22a
lbl22a:   success++
skip22a:
        ; indirect jump
        cx16.r3 = &lbl22b
        if x>=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip22b
lbl22b:   success++
skip22b:
        ; no else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+-1-cx16.r5s
            goto lbl23a
        goto skip23a
lbl23a:   success++
skip23a:
        ; indirect jump
        cx16.r3 = &lbl23b
        if x>=cx16.r4s+-1-cx16.r5s
            goto cx16.r3
        goto skip23b
lbl23b:   success++
skip23b:
        ; no else
        if x>=cx16.r4s+-1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+0-cx16.r5s
            goto lbl24a
        goto skip24a
lbl24a:   success++
skip24a:
        ; indirect jump
        cx16.r3 = &lbl24b
        if x>=cx16.r4s+0-cx16.r5s
            goto cx16.r3
        goto skip24b
lbl24b:   success++
skip24b:
        ; no else
        if x>=cx16.r4s+0-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+0-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+1-cx16.r5s
            goto lbl25a
        goto skip25a
lbl25a:   success++
skip25a:
        ; indirect jump
        cx16.r3 = &lbl25b
        if x>=cx16.r4s+1-cx16.r5s
            goto cx16.r3
        goto skip25b
lbl25b:   success++
skip25b:
        ; no else
        if x>=cx16.r4s+1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+170-cx16.r5s
            goto lbl26a
        goto skip26a
lbl26a:   fail_word(313)
skip26a:
        ; indirect jump
        cx16.r3 = &lbl26b
        if x>=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip26b
lbl26b:   fail_word(314)
skip26b:
        ; no else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(315)

        ; with else
        if x>=cx16.r4s+170-cx16.r5s
            fail_word(316)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+30464-cx16.r5s
            goto lbl27a
        goto skip27a
lbl27a:   fail_word(317)
skip27a:
        ; indirect jump
        cx16.r3 = &lbl27b
        if x>=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip27b
lbl27b:   fail_word(318)
skip27b:
        ; no else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(319)

        ; with else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(320)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+32767-cx16.r5s
            goto lbl28a
        goto skip28a
lbl28a:   fail_word(321)
skip28a:
        ; indirect jump
        cx16.r3 = &lbl28b
        if x>=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip28b
lbl28b:   fail_word(322)
skip28b:
        ; no else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(323)

        ; with else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(324)
        else
            success++

    x=170
    ; direct jump
        if x>=cx16.r4s+-21829-cx16.r5s
            goto lbl29a
        goto skip29a
lbl29a:   success++
skip29a:
        ; indirect jump
        cx16.r3 = &lbl29b
        if x>=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip29b
lbl29b:   success++
skip29b:
        ; no else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+-1-cx16.r5s
            goto lbl30a
        goto skip30a
lbl30a:   success++
skip30a:
        ; indirect jump
        cx16.r3 = &lbl30b
        if x>=cx16.r4s+-1-cx16.r5s
            goto cx16.r3
        goto skip30b
lbl30b:   success++
skip30b:
        ; no else
        if x>=cx16.r4s+-1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+0-cx16.r5s
            goto lbl31a
        goto skip31a
lbl31a:   success++
skip31a:
        ; indirect jump
        cx16.r3 = &lbl31b
        if x>=cx16.r4s+0-cx16.r5s
            goto cx16.r3
        goto skip31b
lbl31b:   success++
skip31b:
        ; no else
        if x>=cx16.r4s+0-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+0-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+1-cx16.r5s
            goto lbl32a
        goto skip32a
lbl32a:   success++
skip32a:
        ; indirect jump
        cx16.r3 = &lbl32b
        if x>=cx16.r4s+1-cx16.r5s
            goto cx16.r3
        goto skip32b
lbl32b:   success++
skip32b:
        ; no else
        if x>=cx16.r4s+1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+170-cx16.r5s
            goto lbl33a
        goto skip33a
lbl33a:   success++
skip33a:
        ; indirect jump
        cx16.r3 = &lbl33b
        if x>=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip33b
lbl33b:   success++
skip33b:
        ; no else
        if x>=cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+30464-cx16.r5s
            goto lbl34a
        goto skip34a
lbl34a:   fail_word(325)
skip34a:
        ; indirect jump
        cx16.r3 = &lbl34b
        if x>=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip34b
lbl34b:   fail_word(326)
skip34b:
        ; no else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(327)

        ; with else
        if x>=cx16.r4s+30464-cx16.r5s
            fail_word(328)
        else
            success++

    ; direct jump
        if x>=cx16.r4s+32767-cx16.r5s
            goto lbl35a
        goto skip35a
lbl35a:   fail_word(329)
skip35a:
        ; indirect jump
        cx16.r3 = &lbl35b
        if x>=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip35b
lbl35b:   fail_word(330)
skip35b:
        ; no else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(331)

        ; with else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(332)
        else
            success++

    x=30464
    ; direct jump
        if x>=cx16.r4s+-21829-cx16.r5s
            goto lbl36a
        goto skip36a
lbl36a:   success++
skip36a:
        ; indirect jump
        cx16.r3 = &lbl36b
        if x>=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip36b
lbl36b:   success++
skip36b:
        ; no else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+-1-cx16.r5s
            goto lbl37a
        goto skip37a
lbl37a:   success++
skip37a:
        ; indirect jump
        cx16.r3 = &lbl37b
        if x>=cx16.r4s+-1-cx16.r5s
            goto cx16.r3
        goto skip37b
lbl37b:   success++
skip37b:
        ; no else
        if x>=cx16.r4s+-1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+0-cx16.r5s
            goto lbl38a
        goto skip38a
lbl38a:   success++
skip38a:
        ; indirect jump
        cx16.r3 = &lbl38b
        if x>=cx16.r4s+0-cx16.r5s
            goto cx16.r3
        goto skip38b
lbl38b:   success++
skip38b:
        ; no else
        if x>=cx16.r4s+0-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+0-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+1-cx16.r5s
            goto lbl39a
        goto skip39a
lbl39a:   success++
skip39a:
        ; indirect jump
        cx16.r3 = &lbl39b
        if x>=cx16.r4s+1-cx16.r5s
            goto cx16.r3
        goto skip39b
lbl39b:   success++
skip39b:
        ; no else
        if x>=cx16.r4s+1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+170-cx16.r5s
            goto lbl40a
        goto skip40a
lbl40a:   success++
skip40a:
        ; indirect jump
        cx16.r3 = &lbl40b
        if x>=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip40b
lbl40b:   success++
skip40b:
        ; no else
        if x>=cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+30464-cx16.r5s
            goto lbl41a
        goto skip41a
lbl41a:   success++
skip41a:
        ; indirect jump
        cx16.r3 = &lbl41b
        if x>=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip41b
lbl41b:   success++
skip41b:
        ; no else
        if x>=cx16.r4s+30464-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+30464-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+32767-cx16.r5s
            goto lbl42a
        goto skip42a
lbl42a:   fail_word(333)
skip42a:
        ; indirect jump
        cx16.r3 = &lbl42b
        if x>=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip42b
lbl42b:   fail_word(334)
skip42b:
        ; no else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(335)

        ; with else
        if x>=cx16.r4s+32767-cx16.r5s
            fail_word(336)
        else
            success++

    x=32767
    ; direct jump
        if x>=cx16.r4s+-21829-cx16.r5s
            goto lbl43a
        goto skip43a
lbl43a:   success++
skip43a:
        ; indirect jump
        cx16.r3 = &lbl43b
        if x>=cx16.r4s+-21829-cx16.r5s
            goto cx16.r3
        goto skip43b
lbl43b:   success++
skip43b:
        ; no else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-21829-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+-1-cx16.r5s
            goto lbl44a
        goto skip44a
lbl44a:   success++
skip44a:
        ; indirect jump
        cx16.r3 = &lbl44b
        if x>=cx16.r4s+-1-cx16.r5s
            goto cx16.r3
        goto skip44b
lbl44b:   success++
skip44b:
        ; no else
        if x>=cx16.r4s+-1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+-1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+0-cx16.r5s
            goto lbl45a
        goto skip45a
lbl45a:   success++
skip45a:
        ; indirect jump
        cx16.r3 = &lbl45b
        if x>=cx16.r4s+0-cx16.r5s
            goto cx16.r3
        goto skip45b
lbl45b:   success++
skip45b:
        ; no else
        if x>=cx16.r4s+0-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+0-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+1-cx16.r5s
            goto lbl46a
        goto skip46a
lbl46a:   success++
skip46a:
        ; indirect jump
        cx16.r3 = &lbl46b
        if x>=cx16.r4s+1-cx16.r5s
            goto cx16.r3
        goto skip46b
lbl46b:   success++
skip46b:
        ; no else
        if x>=cx16.r4s+1-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+1-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+170-cx16.r5s
            goto lbl47a
        goto skip47a
lbl47a:   success++
skip47a:
        ; indirect jump
        cx16.r3 = &lbl47b
        if x>=cx16.r4s+170-cx16.r5s
            goto cx16.r3
        goto skip47b
lbl47b:   success++
skip47b:
        ; no else
        if x>=cx16.r4s+170-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+170-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+30464-cx16.r5s
            goto lbl48a
        goto skip48a
lbl48a:   success++
skip48a:
        ; indirect jump
        cx16.r3 = &lbl48b
        if x>=cx16.r4s+30464-cx16.r5s
            goto cx16.r3
        goto skip48b
lbl48b:   success++
skip48b:
        ; no else
        if x>=cx16.r4s+30464-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+30464-cx16.r5s
            success++
        else
            cx16.r0L++

    ; direct jump
        if x>=cx16.r4s+32767-cx16.r5s
            goto lbl49a
        goto skip49a
lbl49a:   success++
skip49a:
        ; indirect jump
        cx16.r3 = &lbl49b
        if x>=cx16.r4s+32767-cx16.r5s
            goto cx16.r3
        goto skip49b
lbl49b:   success++
skip49b:
        ; no else
        if x>=cx16.r4s+32767-cx16.r5s
            success++

        ; with else
        if x>=cx16.r4s+32767-cx16.r5s
            success++
        else
            cx16.r0L++

    verify_success(133)
}

}

