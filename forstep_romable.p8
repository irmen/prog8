%zeropage basicsafe
%encoding iso
%option romable
%import textio

main {
    sub start() {
        txt.iso()
        byte @shared stepbyte
        byte i
        word byte_sum = 0
        stepbyte = -3
        for i in 24 to 10 step stepbyte {
            byte_sum += i
        }

        word @shared stepword
        word j
        word word_sum = 0
        stepword = 333
        for j in 1000 to 2200 step stepword {
            word_sum += j
        }

        if byte_sum == 90 and word_sum == 5998
            txt.print("pass romable variable steps\n")
        else
            txt.print("fail romable variable steps\n")
        sys.exit(0)
    }
}
