%encoding iso
%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.iso()

        ; test divmod with uword values (hits funcDivmodW -> divmod_uw_asm, remainder via R15)
        check_divmod(40000, 500, 80, 0)
        check_divmod(43211, 2, 21605, 1)
        check_divmod(65535, 1000, 65, 535)
        check_divmod(12345, 777, 15, 690)

        ; test plain division with uword (hits optimizedDivideExpr -> divmod_uw_asm directly, quotient only)
        check_div(40000, 500, 80)
        check_div(43211, 2, 21605)
        check_div(65535, 1000, 65)
        check_div(12345, 777, 15)

        ; test remainder with uword (hits optimizedRemainderExpr -> divmod_uw_preserve_r15)
        check_mod(40000, 500, 0)
        check_mod(43211, 2, 1)
        check_mod(65535, 1000, 535)
        check_mod(12345, 777, 690)

        txt.print("ALL TESTS DONE\n")
        sys.poweroff_system()
    }

    sub check_divmod(uword a1, uword a2, uword expected_q, uword expected_r) {
        uword q, r = divmod(a1, a2)
        if q==expected_q and r==expected_r
            txt.print("  ok: ")
        else
            txt.print("FAIL: ")
        txt.print("divmod(")
        txt.print_uw(a1)
        txt.print(", ")
        txt.print_uw(a2)
        txt.print(") = (")
        txt.print_uw(q)
        txt.print(", ")
        txt.print_uw(r)
        txt.print(") expected (")
        txt.print_uw(expected_q)
        txt.print(", ")
        txt.print_uw(expected_r)
        txt.print(")\n")
    }

    sub check_div(uword a1, uword a2, uword expected) {
        uword r = a1 / a2
        if r==expected
            txt.print("  ok: ")
        else
            txt.print("FAIL: ")
        txt.print("uword ")
        txt.print_uw(a1)
        txt.print(" / ")
        txt.print_uw(a2)
        txt.print(" = ")
        txt.print_uw(r)
        txt.print(" expected ")
        txt.print_uw(expected)
        txt.print("\n")
    }

    sub check_mod(uword a1, uword a2, uword expected) {
        uword r = a1 % a2
        if r==expected
            txt.print("  ok: ")
        else
            txt.print("FAIL: ")
        txt.print("uword ")
        txt.print_uw(a1)
        txt.print(" % ")
        txt.print_uw(a2)
        txt.print(" = ")
        txt.print_uw(r)
        txt.print(" expected ")
        txt.print_uw(expected)
        txt.print("\n")
    }
}
