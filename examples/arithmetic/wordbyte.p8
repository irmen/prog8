%import textio
%import syslib
%zeropage basicsafe


main {
    sub start() {
        repeat 25 {
            txt.nl()
        }

        ubyte ub
        byte bb
        uword uwsum
        word wsum

        uwsum = 50000
        ub=50
        uwsum += ub
        ub=250
        uwsum += ub

        if uwsum==50300
            txt.print("1 ok\n")
        else {
            txt.print("1 fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }

        wsum  = -30000
        bb = 100
        wsum += bb
        bb = -50
        wsum += bb

        if wsum==-29950
            txt.print("2 ok\n")
        else {
            txt.print("2 fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        uwsum = 50000
        ub=50
        uwsum -= ub
        ub=250
        uwsum -= ub

        if uwsum==49700
            txt.print("3 ok\n")
        else {
            txt.print("3 fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }
        wsum  = -30000
        bb = 100
        wsum -= bb
        bb = -50
        wsum -= bb

        if wsum==-30050
            txt.print("4 ok\n")
        else
            txt.print("4 fail\n")



        uwsum = 50000
        bb=50
        uwsum += bb as uword
        bb=-100
        uwsum += bb as uword

        if uwsum==49950
            txt.print("5 ok\n")
        else
            txt.print("5 fail\n")

        uwsum = 50000
        bb=50
        uwsum -= bb as uword
        bb=100
        uwsum -= bb as uword

        if uwsum==49850
            txt.print("6 ok\n")
        else {
            txt.print("6 fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }

        wsum  = -30000
        ub = 50
        wsum += ub
        ub = 250
        wsum += ub

        if wsum==-29700
            txt.print("7 ok\n")
        else {
            txt.print("7 fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        wsum  = -30000
        ub = 50
        wsum -= ub
        ub = 250
        wsum -= ub

        if wsum==-30300
            txt.print("8 ok\n")
        else {
            txt.print("8 fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        txt.nl()



        uwsum = 50000
        ub=0
        uwsum += (50+ub)
        uwsum += (250+ub)

        if uwsum==50300
            txt.print("1b ok\n")
        else {
            txt.print("1b fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }

        bb = 0
        wsum  = -30000
        wsum += (100+bb)
        wsum += (-50+bb)

        if wsum==-29950
            txt.print("2b ok\n")
        else {
            txt.print("2b fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        uwsum = 50000
        uwsum -= (50+ub)
        uwsum -= (250+ub)

        if uwsum==49700
            txt.print("3b ok\n")
        else {
            txt.print("3b fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }
        wsum  = -30000
        wsum -= (100+bb)
        wsum -= (-50+bb)

        if wsum==-30050
            txt.print("4b ok\n")
        else
            txt.print("4b fail\n")


        uwsum = 50000
        uwsum += (50+bb) as uword
        uwsum += (-100+bb) as uword

        if uwsum==49950
            txt.print("5b ok\n")
        else
            txt.print("5b fail\n")

        uwsum = 50000
        uwsum -= (50+bb) as uword
        uwsum -= (100+bb) as uword

        if uwsum==49850
            txt.print("6b ok\n")
        else {
            txt.print("6b fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }

        wsum  = -30000
        wsum += (50+ub)
        wsum += (250+ub)

        if wsum==-29700
            txt.print("7b ok\n")
        else {
            txt.print("7b fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        wsum  = -30000
        wsum -= (50+ub)
        wsum -= (250+ub)

        if wsum==-30300
            txt.print("8b ok\n")
        else {
            txt.print("8b fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        txt.nl()



        uwsum = 50000
        uwsum += 50
        uwsum += 250

        if uwsum==50300
            txt.print("1c ok\n")
        else {
            txt.print("1c fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }

        wsum  = -30000
        wsum += 100
        wsum += -50

        if wsum==-29950
            txt.print("2c ok\n")
        else {
            txt.print("2c fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        uwsum = 50000
        uwsum -= 50
        uwsum -= 250

        if uwsum==49700
            txt.print("3c ok\n")
        else {
            txt.print("3c fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }
        wsum  = -30000
        wsum -= 100
        wsum -= -50

        if wsum==-30050
            txt.print("4c ok\n")
        else
            txt.print("4c fail\n")


        uwsum = 50000
        uwsum += 50 as uword
        uwsum += -100 as uword

        if uwsum==49950
            txt.print("5c ok\n")
        else
            txt.print("5c fail\n")

        uwsum = 50000
        uwsum -= 50 as uword
        uwsum -= 100 as uword

        if uwsum==49850
            txt.print("6c ok\n")
        else {
            txt.print("6c fail:")
            txt.print_uw(uwsum)
            txt.nl()
        }

        wsum  = -30000
        wsum += 50
        wsum += 250

        if wsum==-29700
            txt.print("7c ok\n")
        else {
            txt.print("7c fail:")
            txt.print_w(wsum)
            txt.nl()
        }

        wsum  = -30000
        wsum -= 50
        wsum -= 250

        if wsum==-30300
            txt.print("8c ok\n")
        else {
            txt.print("8c fail:")
            txt.print_w(wsum)
            txt.nl()
        }
    }

}
