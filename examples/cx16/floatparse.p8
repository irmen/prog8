%import floats
%import textio
%import strings
%zeropage basicsafe

main {

    ; float parsing prototype

    sub start() {
        f("")
        f("0")
        f("-0")
        f(".0")
        f("-.0")
        f("0.0")
        f("0.1")
        f("+1.1")
        f("-1.1")
        f("-.1")
        f("-.9")
        f("-99.9")
        f("99.9")
        f("123456789.888888")
        f("123.456789123456")
        f("-123.")
        f("-123.456789123456")
        f("123.45e20")
        f("+123.45e+20")
        f("123.45e-20")
        f("123.45e20")
        f("-123.45e+20")
        f("-123.45e-20")
        f("  - 1 23.  45e - 36  ")
        f("  - 1 23.  4Z 5e - 20  ")
        f("  - 1 23!.  4Z 5e - 20  ")
        f("1.7014118345e+38")           ; TODO fix overflow error
        f("-1.7014118345e+38")          ; TODO fix overflow error
    }

    sub f(str string) {
        cbm.SETTIM(0,0,0)
        repeat 100
            float value1 = floats.parse(string)
        txt.print("1=")
        txt.print_uw(cbm.RDTIM16())
        txt.spc()

        cbm.SETTIM(0,0,0)
        repeat 100
            float value2 = parse(string)
        txt.print("2=")
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        floats.print(value1)
        txt.spc()
        txt.spc()
        floats.print(value2)
        txt.nl()
    }

    sub parse(uword stringptr) -> float {
        if @(stringptr)==0
            return 0.0

        float result
        byte exponent
        bool negative

        repeat {
            cx16.r0L = @(stringptr)
            when cx16.r0L {
                0 -> goto done
                '-' -> negative=true
                '+', ' ' -> { /* skip */ }
                else -> {
                    if strings.isdigit(cx16.r0L) {
                        result *= 10
                        result += cx16.r0L - '0'
                    } else
                        break
                }
            }
            stringptr++
        }

        if cx16.r0L=='.' {
            ; read decimals
            repeat {
                stringptr++
                cx16.r0L = @(stringptr)
                if cx16.r0L==' '
                    continue
                else if strings.isdigit(cx16.r0L) {
                    exponent--
                    result *= 10
                    result += cx16.r0L - '0'
                } else
                    break
            }
        }

        if cx16.r0L=='e' or cx16.r0L=='E' {
            ; read exponent
            bool neg_exponent
            byte exp_value
            repeat {
                stringptr++
                cx16.r0L = @(stringptr)
                when cx16.r0L {
                    0 -> break
                    '+', ' ' -> { /* skip */ }
                    '-' -> neg_exponent=true
                    else -> {
                        if strings.isdigit(cx16.r0L) {
                            exp_value *= 10
                            exp_value += cx16.r0L - '0'
                        } else
                            break
                    }
                }
            }
            if neg_exponent
                exponent -= exp_value
            else
                exponent += exp_value
        }

done:
        if exponent!=0
            result *= floats.pow(10, exponent)

        if negative
            result = -result

        return floats.normalize(result)
    }
}
