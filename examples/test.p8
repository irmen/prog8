%encoding iso
%option no_sysinit
%import textio
%zeropage basicsafe

main {
    ; test values
    ubyte @shared ub0 = 0
    ubyte @shared ub1 = 1
    ubyte @shared ub127 = 127
    ubyte @shared ub128 = 128
    ubyte @shared ub255 = 255

    byte @shared bmin = -128
    byte @shared b_1 = -1
    byte @shared b0 = 0
    byte @shared b1 = 1
    byte @shared bmax = 127

    uword @shared uw0 = 0
    uword @shared uw1 = 1
    uword @shared uw32767 = 32767
    uword @shared uw32768 = 32768
    uword @shared uwmax = 65535

    word @shared wmin = -32768
    word @shared w_1 = -1
    word @shared w0 = 0
    word @shared w1 = 1
    word @shared wmax = 32767

    long @shared lmin = -2147483647
    long @shared l_1 = -1
    long @shared l0 = 0
    long @shared l1 = 1
    long @shared lmax = 2147483647
    uword @shared failures = 0

    sub start() {
        txt.iso()
        txt.print("--- sgn tests ---\n")
        test_sgn()
        txt.print("--- cmp ubyte ---\n")
        test_ubyte()
        txt.print("--- cmp byte ---\n")
        test_byte()
        txt.print("--- cmp uword ---\n")
        test_uword()
        txt.print("--- cmp word ---\n")
        test_word()
        txt.print("--- cmp long ---\n")
        test_long()
        txt.print("\n")
        txt.print_uw(failures)
        txt.print(" failures!\n")
        sys.poweroff_system()
    }

    sub print_flags(ubyte f) {
        if ((f & 128) != 0) txt.print("n") else txt.print(".")
        if ((f & 64) != 0) txt.print("v") else txt.print(".")
        if ((f & 2) != 0) txt.print("z") else txt.print(".")
        if ((f & 1) != 0) txt.print("c") else txt.print(".")
    }

    sub test_sgn() {
        check_sgn_b(b_1, "-1")
        check_sgn_b(b0, "0")
        check_sgn_b(b1, "1")
        check_sgn_l(l_1, "-1")
        check_sgn_l(l0, "0")
        check_sgn_l(l1, "1")
    }

    sub check_sgn_b(byte v, uword s) {
        txt.print("sgn(")
        txt.print(s)
        txt.print(") -> ")
        byte res = sgn(v)
        ubyte flags = sys.read_flags() & $c3
        txt.print_b(res)
        txt.print(" flags: ")
        print_flags(flags)
        txt.print("\n")
    }

    sub check_sgn_l(long v, uword s) {
        txt.print("sgn(")
        txt.print(s)
        txt.print(") -> ")
        byte res = sgn(v)
        ubyte flags = sys.read_flags() & $c3
        txt.print_b(res)
        txt.print(" flags: ")
        print_flags(flags)
        txt.print("\n")
    }

    sub check_ub(ubyte a, ubyte b, uword sa, uword sb) {
        txt.print("cmp(")
        txt.print(sa)
        txt.print(",")
        txt.print(sb)
        txt.print(") -> ")
        cmp(a, b)
        ubyte actual = sys.read_flags() & $83
        print_flags(actual)
        ubyte res = a - b
        ubyte expected = 0
        if res == 0 expected |= 2
        if (res & 128) != 0 expected |= 128
        if a >= b expected |= 1
        if actual != expected {
            txt.print(" fail!")
            failures += 1
        }
        txt.print("\n")
    }

    sub test_ubyte() {
        do_ub(ub0, ub0, "0", "0")
        do_ub(ub0, ub1, "0", "1")
        do_ub(ub127, ub128, "127", "128")
        do_ub(ub0, ub255, "0", "255")
        do_ub(ub255, ub255, "255", "255")
    }
    sub do_ub(ubyte a, ubyte b, uword sa, uword sb) {
        check_ub(a, b, sa, sb)
        if a != b check_ub(b, a, sb, sa)
    }

    sub check_b(byte a, byte b, uword sa, uword sb) {
        txt.print("cmp(")
        txt.print(sa)
        txt.print(",")
        txt.print(sb)
        txt.print(") -> ")
        cmp(a, b)
        ubyte actual = sys.read_flags() & $c3
        print_flags(actual)
        byte res = a - b
        ubyte expected = 0
        if res == 0 expected |= 2
        if (res & 128) != 0 expected |= 128
        if ((a as ubyte) >= (b as ubyte)) { expected |= 1 }
        if (((a ^ b) & (a ^ res) & 128) != 0) expected |= 64
        if actual != expected {
            txt.print(" fail!")
            failures += 1
        }
        txt.print("\n")
    }

    sub test_byte() {
        do_b(b0, b0, "0", "0")
        do_b(b0, b1, "0", "1")
        do_b(b_1, b0, "-1", "0")
        do_b(bmin, bmax, "-128", "127")
        do_b(b_1, b1, "-1", "1")
        do_b(bmin, bmin, "-128", "-128")
    }
    sub do_b(byte a, byte b, uword sa, uword sb) {
        check_b(a, b, sa, sb)
        if a != b check_b(b, a, sb, sa)
    }

    sub check_uw(uword a, uword b, uword sa, uword sb) {
        txt.print("cmp(")
        txt.print(sa)
        txt.print(",")
        txt.print(sb)
        txt.print(") -> ")
        cmp(a, b)
        ubyte actual = sys.read_flags() & $83
        print_flags(actual)
        uword res = a - b
        ubyte expected = 0
        if res == 0 expected |= 2
        if (res & $8000) != 0 expected |= 128
        if a >= b expected |= 1
        if actual != expected {
            txt.print(" fail!")
            failures += 1
        }
        txt.print("\n")
    }

    sub test_uword() {
        do_uw(uw0, uw0, "0", "0")
        do_uw(uw0, uw1, "0", "1")
        do_uw(uw32767, uw32768, "32767", "32768")
        do_uw(uw0, uwmax, "0", "65535")
        do_uw(uwmax, uwmax, "65535", "65535")
    }
    sub do_uw(uword a, uword b, uword sa, uword sb) {
        check_uw(a, b, sa, sb)
        if a != b check_uw(b, a, sb, sa)
    }

    sub check_w(word a, word b, uword sa, uword sb) {
        txt.print("cmp(")
        txt.print(sa)
        txt.print(",")
        txt.print(sb)
        txt.print(") -> ")
        cmp(a, b)
        ubyte actual = sys.read_flags() & $c3
        print_flags(actual)
        word res = a - b
        ubyte expected = 0
        if res == 0 expected |= 2
        if (res & $8000) != 0 expected |= 128
        if ((a as uword) >= (b as uword)) { expected |= 1 }
        if (((a ^ b) & (a ^ res) & $8000) != 0) expected |= 64
        if actual != expected {
            txt.print(" fail!")
            failures += 1
        }
        txt.print("\n")
    }

    sub test_word() {
        do_w(w0, w0, "0", "0")
        do_w(w0, w1, "0", "1")
        do_w(w_1, w0, "-1", "0")
        do_w(wmin, wmax, "-32768", "32767")
        do_w(w_1, w1, "-1", "1")
        do_w(wmin, wmin, "-32768", "-32768")
    }
    sub do_w(word a, word b, uword sa, uword sb) {
        check_w(a, b, sa, sb)
        if a != b check_w(b, a, sb, sa)
    }

    sub check_l(long a, long b, uword sa, uword sb) {
        txt.print("cmp(")
        txt.print(sa)
        txt.print(",")
        txt.print(sb)
        txt.print(") -> ")
        cmp(a, b)
        ubyte actual = sys.read_flags() & $c3
        print_flags(actual)
        long res = a - b
        ubyte expected = 0
        if res == 0 expected |= 2
        if (res & $80000000) != 0 expected |= 128
        if (a ^ $80000000) >= (b ^ $80000000) expected |= 1
        if (((a ^ b) & (a ^ res) & $80000000) != 0) expected |= 64
        if actual != expected {
            txt.print(" fail!")
            failures += 1
        }
        txt.print("\n")
    }

    sub test_long() {
        do_l(l0, l0, "0", "0")
        do_l(l0, l1, "0", "1")
        do_l(l_1, l0, "-1", "0")
        do_l(lmin, lmax, "lmin", "lmax")
        do_l(l_1, l1, "-1", "1")
        do_l(lmin, lmin, "lmin", "lmin")
    }
    sub do_l(long a, long b, uword sa, uword sb) {
        check_l(a, b, sa, sb)
        if a != b check_l(b, a, sb, sa)
    }
}
