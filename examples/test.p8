%import textio
%zeropage basicsafe
%encoding iso

main {
    sub start() {
        txt.iso()
        ; Test multi-value return from function with arguments
        ubyte a,b = multi()
        txt.print("multi() returned: ")
        txt.print_ub(a)
        txt.print(", ")
        txt.print_ub(b)
        txt.nl()

        ; Test negative comparison optimization
        txt.print("\nTesting negative comparisons:\n")
        test_negative_comparisons()
    }

    sub multi() -> ubyte, ubyte {
        cx16.r0++
        return multi2(99)
    }

    sub multi2(ubyte x) -> ubyte, ubyte {
        x++
        return 42, 99
    }

    sub test_negative_comparisons() {
        ubyte passed = 0
        ubyte failed = 0

        ; Test if x < 0 with negative value
        word testval = -5
        if testval < 0 {
            passed++
            txt.print("  PASS: -5 < 0\n")
        } else {
            failed++
            txt.print("  FAIL: -5 < 0\n")
        }

        ; Test if x < 0 with positive value
        testval = 5
        if testval < 0 {
            failed++
            txt.print("  FAIL: 5 < 0\n")
        } else {
            passed++
            txt.print("  PASS: 5 >= 0\n")
        }

        ; Test if x >= 0 with zero
        testval = 0
        if testval >= 0 {
            passed++
            txt.print("  PASS: 0 >= 0\n")
        } else {
            failed++
            txt.print("  FAIL: 0 >= 0\n")
        }

        ; Test if x >= 0 with negative
        testval = -1
        if testval >= 0 {
            failed++
            txt.print("  FAIL: -1 >= 0\n")
        } else {
            passed++
            txt.print("  PASS: -1 < 0\n")
        }

        ; Test if x < 0 with zero
        testval = 0
        if testval < 0 {
            failed++
            txt.print("  FAIL: 0 < 0\n")
        } else {
            passed++
            txt.print("  PASS: 0 >= 0\n")
        }

        ; Test if x >= 0 with positive
        testval = 100
        if testval >= 0 {
            passed++
            txt.print("  PASS: 100 >= 0\n")
        } else {
            failed++
            txt.print("  FAIL: 100 >= 0\n")
        }

        txt.print("\n")
        txt.print("Results: ")
        txt.print_ub(passed)
        txt.print(" passed, ")
        txt.print_ub(failed)
        txt.print(" failed\n")

        if failed == 0 {
            txt.print("ALL TESTS PASSED\n")
        } else {
            txt.print("SOME TESTS FAILED!\n")
        }
    }
}
