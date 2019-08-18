%import c64utils
%import c64lib
%zeropage basicsafe

; The classic number guessing game.

main {

    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   input   = "??????????"
        ubyte secretnumber = rnd() % 99 + 1     ; random number 1..100
        ubyte attempts_left

        c64.VMCSB |= 2  ; switch lowercase chars
        c64scr.print("Please introduce yourself: ")
        c64scr.input_chars(name)
        c64scr.print("\n\nHello, ")
        c64scr.print(name)
        c64scr.print(".\nLet's play a number guessing game.\nI am thinking of a number from 1 to 100!You'll have to guess it!\n")

        for attempts_left in 10 to 1 step -1 {

            c64scr.print("\nYou have ")
            c64scr.print_ub(attempts_left)
            c64scr.print(" guess")
            if attempts_left>1
                c64scr.print("es")
            c64scr.print(" left.\nWhat is your next guess? ")
            c64scr.input_chars(input)
            ubyte guess = lsb(c64utils.str2uword(input))

            if guess==secretnumber {
                ending(true)
                return
            } else {
                c64scr.print("\n\nThat is too ")
                if guess<secretnumber
                    c64scr.print("low!\n")
                else
                    c64scr.print("high!\n")
            }
        }

        ending(false)
        return


        sub ending(ubyte success) {
            if success
                c64scr.print("\n\nYou guessed it, impressive!\n")
            else {
                c64scr.print("\nToo bad! My number was: ")
                c64scr.print_ub(secretnumber)
                c64scr.print(".\n")
            }
            c64scr.print("Thanks for playing, ")
            c64scr.print(name)
            c64scr.print(".\n")
        }
    }
}
