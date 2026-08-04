%import textio
%zeropage basicsafe

main {
    sub start() {
        check_unsigned(40000, 7, 5714, 2)
        check_unsigned(30000, 13, 2307, 9)
        check_signed(-1000, 7, -142, -6)
        check_signed(-255, 16, -15, -15)
        txt.print("done\n")
        ;sys.poweroff_system()
    }

    sub check_unsigned(uword a, uword b, uword exp_q, uword exp_r) {
        uword q
        uword r
        q, r = divmod(a, b)
        txt.print_uw(a)
        txt.print(" / ")
        txt.print_uw(b)
        txt.print(" u: q=")
        txt.print_uw(q)
        txt.print(" r=")
        txt.print_uw(r)
        txt.print("  (expected q=")
        txt.print_uw(exp_q)
        txt.print(" r=")
        txt.print_uw(exp_r)
        txt.print(")\n")
    }

    sub check_signed(word a, word b, word exp_q, word exp_r) {
        word q
        word r
        q, r = divmod(a, b)
        txt.print_w(a)
        txt.print(" / ")
        txt.print_w(b)
        txt.print(" s: q=")
        txt.print_w(q)
        txt.print(" r=")
        txt.print_w(r)
        txt.print("  (expected q=")
        txt.print_w(exp_q)
        txt.print(" r=")
        txt.print_w(exp_r)
        txt.print(")\n")
    }
}
