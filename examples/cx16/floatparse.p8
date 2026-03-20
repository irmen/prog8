%import floats
%import textio
%import strings
%zeropage basicsafe

main {
    ubyte tests_run
    ubyte tests_passed
    ubyte tests_failed

    sub test(str repr, float expected) {
        float result = parse(repr)
        float diff = result - expected
        if diff < 0
            diff = -diff
        ; Use tolerance for float comparison (5-byte MFLPT5 has ~7-8 digits precision)
        float tolerance = 1.0e-5 + 1.0e-5 * abs(expected)
        tests_run++
        if diff <= tolerance {
            tests_passed++
            txt.print("PASS: ")
            txt.print(repr)
            txt.spc()
            floats.print(result)
        } else {
            tests_failed++
            txt.print("FAIL: ")
            txt.print(repr)
            txt.spc()
            txt.print("expected=")
            floats.print(expected)
            txt.spc()
            txt.print("got=")
            floats.print(result)
        }
        txt.nl()
    }

    ; Test that compares our parse() against the library floats.parse()
    sub testlib(str repr) {
        float our_result = parse(repr)
        float lib_result = floats.parse(repr)
        float diff = our_result - lib_result
        if diff < 0
            diff = -diff
        float tolerance = 1.0e-6 + 1.0e-6 * abs(lib_result)
        tests_run++
        if diff <= tolerance {
            tests_passed++
            txt.print("PASS: ")
            txt.print(repr)
            txt.spc()
            floats.print(our_result)
            txt.print(" (matches floats.parse)")
        } else {
            tests_failed++
            txt.print("MISMATCH: ")
            txt.print(repr)
            txt.spc()
            txt.print("parse()=")
            floats.print(our_result)
            txt.spc()
            txt.print("floats.parse()=")
            floats.print(lib_result)
        }
        txt.nl()
    }

    sub report() {
        txt.nl()
        txt.print("Tests run: ")
        txt.print_ub(tests_run)
        txt.spc()
        txt.print("Passed: ")
        txt.print_ub(tests_passed)
        txt.spc()
        txt.print("Failed: ")
        txt.print_ub(tests_failed)
        txt.nl()
        if tests_failed > 0 {
            txt.print("ERROR: Some tests failed!")
        } else {
            txt.print("All tests passed!")
        }
        txt.nl()
    }

    sub start() {
        txt.lowercase()

        ; === MFLPT5 EDGE CASES ===
        ; Maximum representable value (exponent=255, mantissa=all 1s)
        test("1.7014118345e+38", 1.7014118345e+38)
        test("-1.7014118345e+38", -1.7014118345e+38)
        ; Just below maximum
        test("1.7014118344e+38", 1.7014118344e+38)
        test("-1.7014118344e+38", -1.7014118344e+38)
        ; Near overflow boundary
        test("1.7e+38", 1.7e+38)
        test("-1.7e+38", -1.7e+38)
        test("1.6e+38", 1.6e+38)
        test("1.5e+38", 1.5e+38)
        test("1.0e+38", 1.0e+38)
        test("5e+37", 5.0e+37)
        test("9.999999e+37", 9.999999e+37)
        test("1.7e38", 1.7e+38)
        test("-1.7e38", -1.7e+38)
        test("1e-38", 1.0e-38)
        test("5e-39", 5.0e-39)
        test("3e-39", 3.0e-39)

        test("9.999999e-39", 9.999999e-39)
        test("1e-37", 1.0e-37)
        test("1e-36", 1.0e-36)
        test("1e-35", 1.0e-35)

        ; Zero variations (exponent=0)
        test("0", 0.0)
        test("0.0", 0.0)
        test("0.00", 0.0)
        test("0.000", 0.0)
        test(".0", 0.0)
        test("-0", 0.0)
        test("-0.0", 0.0)
        test("0e0", 0.0)
        test("0.0e0", 0.0)
        test("0e10", 0.0)
        test("0e-10", 0.0)
        test("0.00000000000000000001", 0.0)  ; Underflows to zero

        ; === Very small numbers ===
        test("1e-30", 1.0e-30)
        test("1.23e-30", 1.23e-30)
        test("0.0000001", 0.0000001)
        test("0.000000001", 0.000000001)
        test("1.0e-20", 1.0e-20)

        ; === Very large numbers ===
        test("9.999999e+37", 9.999999e+37)

        ; === Powers of 2 boundaries (exponent transitions) ===
        test("0.5", 0.5)       ; 2^-1, exp=127
        test("0.25", 0.25)     ; 2^-2, exp=126
        test("0.125", 0.125)   ; 2^-3, exp=125
        test("0.0625", 0.0625) ; 2^-4, exp=124
        test("1.0", 1.0)       ; 2^0, exp=128
        test("2.0", 2.0)       ; 2^1, exp=129
        test("4.0", 4.0)       ; 2^2, exp=130
        test("8.0", 8.0)       ; 2^3, exp=131
        test("16.0", 16.0)     ; 2^4, exp=132
        test("32.0", 32.0)     ; 2^5, exp=133
        test("64.0", 64.0)     ; 2^6, exp=134
        test("128.0", 128.0)   ; 2^7, exp=135
        test("256.0", 256.0)   ; 2^8, exp=136
        test("512.0", 512.0)   ; 2^9, exp=137
        test("1024.0", 1024.0) ; 2^10, exp=138
        test("65536.0", 65536.0) ; 2^16, exp=144
        test("16777216.0", 16777216.0) ; 2^24, exp=152

        ; === Numbers around 1.0 (mantissa precision tests) ===
        test("1", 1.0)
        test("1.0", 1.0)
        test("1.00", 1.0)
        test("1.000", 1.0)
        test("0.999999", 0.999999)
        test("1.000001", 1.000001)
        test("0.9999999999", 0.9999999999)
        test("1.0000000001", 1.0000000001)
        test("1e0", 1.0)
        test("1.0e0", 1.0)

        ; === Powers of 10 ===
        test("10", 10.0)
        test("100", 100.0)
        test("1000", 1000.0)
        test("10000", 10000.0)
        test("100000", 100000.0)
        test("1000000", 1000000.0)
        test("0.1", 0.1)
        test("0.01", 0.01)
        test("0.001", 0.001)
        test("0.0001", 0.0001)
        test("0.00001", 0.00001)
        test("0.000001", 0.000001)
        test("1e1", 10.0)
        test("1e2", 100.0)
        test("1e3", 1000.0)
        test("1e-1", 0.1)
        test("1e-2", 0.01)
        test("1e-3", 0.001)

        ; === Leading zeros ===
        test("000123", 123.0)
        test("000.123", 0.123)
        test("00123.456", 123.456)
        test("00000000000000000000123", 123.0)
        test("000000000000000000000.123", 0.123)

        ; === Trailing zeros ===
        test("1.000", 1.0)
        test("1.2300", 1.23)
        test("123.456000", 123.456)
        test("0.1000", 0.1)
        test("0.1000000000", 0.1)

        ; === Pure decimal (no integer part) ===
        test(".5", 0.5)
        test(".123", 0.123)
        test(".999999", 0.999999)
        test("-.5", -0.5)
        test("-.123", -0.123)
        test("-.999999", -0.999999)
        test(".1", 0.1)
        test(".01", 0.01)
        test(".001", 0.001)

        ; === Integer numbers ===
        test("123", 123.0)
        test("999999", 999999.0)
        test("123456789", 123456789.0)
        test("2147483647", 2147483647.0)
        test("4294967295", 4294967295.0)
        test("-123", -123.0)
        test("-999999", -999999.0)
        test("-123456789", -123456789.0)

        ; === Scientific notation (positive exp) ===
        test("1e0", 1.0)
        test("1e1", 10.0)
        test("1e2", 100.0)
        test("1e10", 1.0e+10)
        test("1e20", 1.0e+20)
        test("1e30", 1.0e+30)
        test("1.5e0", 1.5)
        test("1.5e1", 15.0)
        test("1.5e10", 1.5e+10)
        test("1.5e20", 1.5e+20)
        test("1.23456e10", 1.23456e+10)
        test("9.99999e30", 9.99999e+30)
        test("+1e10", 1.0e+10)
        test("+1.5e+10", 1.5e+10)

        ; === Scientific notation (negative exp) ===
        test("1e-1", 0.1)
        test("1e-5", 1.0e-5)
        test("1e-10", 1.0e-10)
        test("1e-20", 1.0e-20)
        test("1e-30", 1.0e-30)
        test("1.5e-1", 0.15)
        test("1.5e-5", 1.5e-5)
        test("1.5e-10", 1.5e-10)
        test("1.23456e-10", 1.23456e-10)
        test("9.99999e-30", 9.99999e-30)

        ; === Scientific notation (uppercase E) ===
        test("1E10", 1.0e+10)
        test("1.5E10", 1.5e+10)
        test("1.5E-10", 1.5e-10)
        test("1.5E+10", 1.5e+10)

        ; === Numbers with spaces ===
        test("  123", 123.0)
        test("123  ", 123.0)
        test("  123  ", 123.0)
        test("1 2 3", 123.0)
        test("  1.23e10  ", 1.23e+10)

        ; === Negative numbers ===
        test("-1", -1.0)
        test("-1.0", -1.0)
        test("-0.5", -0.5)
        test("-123.456", -123.456)
        test("-1e10", -1.0e+10)
        test("-1.5e-10", -1.5e-10)
        test("+1", 1.0)
        test("+1.0", 1.0)
        test("+0.5", 0.5)
        test("+123.456", 123.456)
        test("+1e10", 1.0e+10)
        test("+1.5e-10", 1.5e-10)

        ; === Mixed decimal and exponent ===
        test("123.456e10", 1.23456e+12)
        test("123.456e-10", 1.23456e-8)
        test("0.00123e5", 123.0)
        test("123000e-5", 1.23)
        test("1.23e2", 123.0)
        test("12.3e1", 123.0)
        test("123e0", 123.0)
        test("1230e-1", 123.0)
        test("12300e-2", 123.0)

        ; === Many decimal places (precision tests) ===
        test("0.123456789", 0.123456789)
        test("0.123456789012345", 0.123456789012345)
        test("3.14159265358979", 3.14159265358979)
        test("2.71828182845904", 2.71828182845904)
        test("1.41421356237309", 1.41421356237309)
        test("0.33333333333333", 0.33333333333333)
        test("0.66666666666666", 0.66666666666666)

        report()

        ; === COMPARISON WITH floats.parse() LIBRARY ===
        txt.nl()
        txt.print("=== Comparing with floats.parse() library ===")
        txt.nl()

        ; Test various inputs against library routine
        testlib("0")
        testlib("1")
        testlib("123")
        testlib("123.456")
        testlib("0.123456789")
        testlib("1e10")
        testlib("1.23e-10")
        testlib("-456.789")
        testlib("3.14159265358979")
        testlib("2.71828182845904")
        testlib("1.7014118345e+38")
        testlib("-1.7014118345e+38")
        testlib("1e-38")
        testlib("9.999999e+37")  ; DISABLED: causes overflow
        testlib("1.7e38")
        testlib("-1.7e38")
        testlib("0.5")
        testlib("0.25")
        testlib("0.1")
        testlib("10")
        testlib("1000000")
        testlib("123456789")
        testlib("2147483647")
        testlib("4294967295")
        testlib(".5")
        testlib(".123")
        testlib("1.5e20")
        testlib("1.5e-20")
        testlib("  123  ")
        testlib("1.23e10")
        testlib("-0.001")
        testlib("65536")
        testlib("16777216")

        txt.nl()
        txt.print("Final results:")
        txt.nl()
        report()
    }

    sub parse(str stringptr) -> float {
        ; -- Parse string to MFLPT5 float
        float mantissa
        byte decimal_exp     ; tracks decimal point position
        bool negative

        ; 1-2. Combined: Skip whitespace, parse sign
        repeat {
            when @(stringptr) {
                0 -> return 0.0  ; empty string
                '-' -> negative=true
                '+', ' ' -> { /* skip */ }
                else -> break
            }
            stringptr++
        }

        ; Skip leading zeros
        while @(stringptr) == '0' {
            stringptr++
        }

        ; 3-5. Combined: Parse integer part, decimal part, and explicit exponent
        repeat {
            cx16.r0L = @(stringptr)
            if strings.isdigit(cx16.r0L) {
                add_digit_to_mantissa()
            } else if cx16.r0L == '.' {
                stringptr++
                ; Handle leading zeros after decimal (only when mantissa is still 0)
                if mantissa == 0 {
                    while @(stringptr) == '0' {
                        decimal_exp--
                        stringptr++
                    }
                }
                ; Read decimal digits
                repeat {
                    cx16.r0L = @(stringptr)
                    if strings.isdigit(cx16.r0L) {
                        add_digit_to_mantissa()
                        decimal_exp--
                    } else {
                        break
                    }
                    stringptr++
                }
                continue  ; Continue main loop
            } else if cx16.r0L == 'e' or cx16.r0L == 'E' {
                ; Handle explicit exponent
                stringptr++
                bool exp_neg = false
                byte exp_val = 0
                repeat {
                    cx16.r0L = @(stringptr)
                    when cx16.r0L {
                        0 -> break
                        '+', ' ' -> { /* skip */ }
                        '-' -> exp_neg=true
                        else -> {
                            if strings.isdigit(cx16.r0L) {
                                exp_val *= 10
                                exp_val += cx16.r0L - '0'
                            } else {
                                break
                            }
                        }
                    }
                    stringptr++
                }
                if exp_neg
                    decimal_exp -= exp_val
                else
                    decimal_exp += exp_val
                break  ; Done parsing
            } else if cx16.r0L != ' ' {
                break  ; Invalid character
            }
            stringptr++
        }

        ; 6. Handle zero
        if mantissa == 0
            return 0.0

        if decimal_exp < 0 {
;            repeat -decimal_exp {
;                mantissa *= 0.1
;            }
            decimal_exp = -decimal_exp
            %asm {{
                lda  #<p8v_mantissa
                ldy  #>p8v_mantissa
                jsr  floats.MOVFM
-               jsr  floats.DIV10
                dec  p8v_decimal_exp
                bne  -
                ldx  #<p8v_mantissa
                ldy  #>p8v_mantissa
                jsr  floats.MOVMF
            }}
        } else if decimal_exp != 0 {
;            repeat decimal_exp as ubyte {
;                mantissa *= 10.0
;            }
            %asm {{
                lda  #<p8v_mantissa
                ldy  #>p8v_mantissa
                jsr  floats.MOVFM
-               jsr  floats.MUL10
                dec  p8v_decimal_exp
                bne  -
                ldx  #<p8v_mantissa
                ldy  #>p8v_mantissa
                jsr  floats.MOVMF
            }}
        }
        if negative
            mantissa = -mantissa

        return floats.normalize(mantissa)

        asmsub add_digit_to_mantissa() clobbers (A,X,Y) {
;            mantissa *= 10
;            mantissa += (cx16.r0L - '0') as float
            %asm {{
                lda  #<p8v_mantissa
                ldy  #>p8v_mantissa
                jsr  floats.MOVFM
                jsr  floats.MUL10
                jsr  floats.MOVAF
                lda  cx16.r0L
                sec
                sbc  #'0'
                tay
                lda  #0
                jsr  floats.GIVAYF
                jsr  floats.FADDT
                ldx  #<p8v_mantissa
                ldy  #>p8v_mantissa
                jmp  floats.MOVMF
            }}
        }
    }
}
