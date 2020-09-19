%import c64textio
%import c64lib
%import conv
%zeropage basicsafe

; The classic number guessing game.

main {

    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   input   = "??????????"
        ubyte secretnumber = rnd() % 99 + 1     ; random number 1..100
        ubyte attempts_left

        txt.lowercase()
        txt.print("Please introduce yourself: ")
        void txt.input_chars(name)
        txt.print("\n\nHello, ")
        txt.print(name)
        txt.print(".\nLet's play a number guessing game.\nI am thinking of a number from 1 to 100!You'll have to guess it!\n")

        for attempts_left in 10 downto 1 {

            txt.print("\nYou have ")
            txt.print_ub(attempts_left)
            txt.print(" guess")
            if attempts_left>1
                txt.print("es")
            txt.print(" left.\nWhat is your next guess? ")
            void txt.input_chars(input)
            ubyte guess = lsb(conv.str2uword(input))

            if guess==secretnumber {
                ending(true)
                return
            } else {
                txt.print("\n\nThat is too ")
                if guess<secretnumber
                    txt.print("low!\n")
                else
                    txt.print("high!\n")
            }
        }

        ending(false)
        return


        sub ending(ubyte success) {
            if success
                txt.print("\n\nYou guessed it, impressive!\n")
            else {
                txt.print("\nToo bad! My number was: ")
                txt.print_ub(secretnumber)
                txt.print(".\n")
            }
            txt.print("Thanks for playing, ")
            txt.print(name)
            txt.print(".\n")
        }
    }
}
