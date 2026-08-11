%zeropage basicsafe
%encoding iso
%import textio

main {
    sub start() {
        txt.iso()
        txt.print("forstep gate\n")

        test_ubyte_asc()
        test_ubyte_desc()
        test_word_asc()
        test_signed_word_asc()
        test_long_asc()
        test_step_zero()
        test_wrong_direction()
        test_wrong_direction_negative()
        test_from_equals_to()
        test_body_mutates_step()
        test_wrap_ubyte()
        test_nested_break()
        test_non_const_bounds()
        test_pointer_asc()
        test_side_effect_order()
        test_continue()

        txt.print("all done\n")
        sys.exit(0)
    }

    sub test_ubyte_asc() {
        ubyte @shared step = 3
        ubyte i
        ubyte sum = 0
        for i in 0 to 10 step step {
            sum += i
        }
        ; 0+3+6+9 = 18
        if sum == 18
            txt.print("PASS ubyte asc\n")
        else
            txt.print("FAIL ubyte asc\n")
    }

    sub test_ubyte_desc() {
        byte @shared step = -2
        ubyte i
        ubyte sum = 0
        for i in 10 downto 0 step step {
            sum += i
        }
        ; 10+8+6+4+2+0 = 30
        if sum == 30
            txt.print("PASS ubyte desc\n")
        else
            txt.print("FAIL ubyte desc\n")
    }

    sub test_word_asc() {
        uword @shared step = 100
        uword i
        uword sum = 0
        for i in 0 to 500 step step {
            sum += i
        }
        ; 0+100+200+300+400+500 = 1500
        if sum == 1500
            txt.print("PASS word asc\n")
        else
            txt.print("FAIL word asc\n")
    }

    sub test_signed_word_asc() {
        word @shared step = 4
        word i
        word count = 0
        word sum = 0
        for i in -10 to 10 step step {
            count += 1
            sum += i
        }
        ; -10,-6,-2,2,6,10: six values, sum 0
        if count == 6 and sum == 0
            txt.print("PASS signed word asc\n")
        else
            txt.print("FAIL signed word asc\n")
    }

    sub test_long_asc() {
        long @shared step = 10000
        long i
        long sum = 0
        for i in 0 to 50000 step step {
            sum += i
        }
        ; 0+10000+20000+30000+40000+50000 = 150000
        if sum == 150000
            txt.print("PASS long asc\n")
        else
            txt.print("FAIL long asc\n")
    }

    sub test_step_zero() {
        ubyte @shared step = 0
        ubyte i
        ubyte count = 0
        for i in 0 to 10 step step {
            count += 1
        }
        if count == 0
            txt.print("PASS step zero\n")
        else
            txt.print("FAIL step zero\n")
    }

    sub test_wrong_direction() {
        ubyte @shared step = 1
        ubyte i
        ubyte count = 0
        for i in 10 to 0 step step {
            count += 1
        }
        if count == 0
            txt.print("PASS wrong dir\n")
        else
            txt.print("FAIL wrong dir\n")
    }

    sub test_wrong_direction_negative() {
        byte @shared step = -1
        ubyte i
        ubyte count = 0
        for i in 0 to 10 step step {
            count += 1
        }
        if count == 0
            txt.print("PASS wrong dir negative\n")
        else
            txt.print("FAIL wrong dir negative\n")
    }

    sub test_from_equals_to() {
        ubyte @shared step = 5
        ubyte i
        ubyte count = 0
        for i in 7 to 7 step step {
            count += 1
        }
        if count == 1
            txt.print("PASS from==to\n")
        else
            txt.print("FAIL from==to\n")
    }

    sub test_body_mutates_step() {
        ubyte @shared step = 2
        ubyte i
        ubyte sum = 0
        for i in 0 to 10 step step {
            sum += i
            step = 99
        }
        ; step captured as 2, so 0+2+4+6+8+10 = 30
        if sum == 30
            txt.print("PASS mutate step\n")
        else
            txt.print("FAIL mutate step\n")
    }

    sub test_wrap_ubyte() {
        ubyte @shared step = 3
        ubyte i
        ubyte count = 0
        for i in 254 to 255 step step {
            count += 1
        }
        if count == 1
            txt.print("PASS wrap ubyte\n")
        else
            txt.print("FAIL wrap ubyte\n")
    }

    sub test_nested_break() {
        ubyte @shared step = 1
        ubyte i
        ubyte j
        ubyte sum = 0
        for i in 0 to 5 step step {
            for j in 0 to 5 step step {
                sum += 1
                if j == 2
                    break
            }
        }
        ; 6 iterations * 3 inner = 18
        if sum == 18
            txt.print("PASS nested break\n")
        else
            txt.print("FAIL nested break\n")
    }

    sub test_non_const_bounds() {
        ubyte @shared step = 2
        ubyte a = 1
        ubyte b = 9
        ubyte i
        ubyte sum = 0
        for i in a to b step step {
            sum += i
        }
        ; 1+3+5+7+9 = 25
        if sum == 25
            txt.print("PASS nonconst bounds\n")
        else
            txt.print("FAIL nonconst bounds\n")
    }

    sub test_pointer_asc() {
        pointer @shared step = 2
        pointer i
        pointer sum = 0
        for i in 0 to 6 step step {
            sum += i
        }
        ; 0+2+4+6 = 12
        if sum == 12
            txt.print("PASS pointer asc\n")
        else
            txt.print("FAIL pointer asc\n")
    }

    ubyte @shared side_order = 0

    sub side_from() -> ubyte {
        side_order = 1
        return 1
    }

    sub side_to() -> ubyte {
        side_order = side_order * 10 + 2
        return 5
    }

    sub side_step() -> byte {
        side_order = side_order * 10 + 3
        return 2
    }

    sub test_side_effect_order() {
        ubyte i
        ubyte sum = 0
        side_order = 0
        for i in side_from() to side_to() step side_step() {
            sum += i
        }
        ; source order is from, to, step; each is evaluated once
        if side_order == 123 and sum == 9
            txt.print("PASS side effects\n")
        else
            txt.print("FAIL side effects\n")
    }

    sub test_continue() {
        ubyte @shared step = 1
        ubyte i
        ubyte count = 0
        ubyte sum = 0
        for i in 0 to 5 step step {
            if i == 2
                continue
            count += 1
            sum += i
        }
        ; skip 2: five values and 0+1+3+4+5 = 13
        if count == 5 and sum == 13
            txt.print("PASS continue\n")
        else
            txt.print("FAIL continue\n")
    }
}
