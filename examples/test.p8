%import textio
%zeropage dontuse
%option no_sysinit

main {
    sub start() {
        uword @shared pointer

;        if cx16.r0L>10
;            goto label1
;        if cx16.r0L>11
;            goto label2

        pointer = &label2
        goto pointer

label1:
        txt.print("fail\n")
        return

label2:
        txt.print("indirect jump ok\n")
        return


    }
}



;%import textio
;%import floats
;%zeropage dontuse
;%option no_sysinit
;
;main {
;    sub start() {
;        test_bool()
;;        test_byte()
;;        test_ubyte()
;;        test_word()
;;        test_uword()
;;        test_float()
;    }
;
;    /*
;    tests:
;    - if with only a single goto, direct
;    - if with only a single indirect goto
;    - if without an else block
;    - if with both if and else blocks
;    carthesian product with:
;    - comparison with const zero
;    - comparison with const values
;    - comparison with variable
;    - comparison with array
;    - comparison with expression
;    */
;
;    sub fail(uword test) {
;        txt.print("failed ")
;        txt.print_uw(test)
;        txt.nl()
;    }
;
;    sub test_bool() {
;        bool @shared var1, var2
;        uword success = 0
;
;        single_goto()
;        single_goto_indirect()
;        no_else()
;        ifelse()
;
;        sub single_goto() {
;            txt.print("bool single_goto\n")
;test1:
;            var1 = false
;            if var1
;                goto lbl1
;            success++
;            goto test2
;lbl1:
;            fail(1)
;
;test2:
;            var1 = true
;            if var1
;                goto lbl2
;            fail(1)
;            goto test3
;lbl2:       success++
;
;test3:
;            if success==2
;                txt.print("  ok\n")
;            else
;                txt.print("  failed\n")
;        }
;
;        sub single_goto_indirect() {
;            uword pointer
;            txt.print("bool single_goto_indirect\n")
;test1:
;            var1 = false
;            pointer = &lbl1
;            if var1
;                goto pointer
;            success++
;            goto test2
;lbl1:
;            fail(1)
;
;test2:
;            var1 = true
;            pointer = &lbl2
;            if var1
;                goto pointer
;            fail(1)
;            goto test3
;lbl2:       success++
;
;test3:
;            if success==2
;                txt.print("  ok\n")
;            else
;                txt.print("  failed\n")
;        }
;
;        sub no_else() {
;        }
;
;        sub ifelse() {
;        }
;    }
;
;}
