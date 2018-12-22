%import c64utils
%import mathlib

; The classic number guessing game.
; This version uses mostly high level subroutine calls and loops.
; It's more readable than the low-level version, but produces a slightly larger program.

~ main {

    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   input   = "??????????"
        ubyte secretnumber = rnd() % 99 + 1     ; random number 1..100

        c64.VMCSB |= 2  ; switch lowercase chars
        c64scr.print("Please introduce yourself: ")
        c64scr.input_chars(name)
        c64scr.print("\n\nHello, ")
        c64scr.print(name)
        c64scr.print(".\nLet's play a number guessing game.\nI am thinking of a number from 1 to 100!You'll have to guess it!\n")

        for ubyte attempts_left in 10 to 1 step -1 {

; stackptr debugging
;            c64scr.print_b(X)
;            c64.CHROUT('\n')

            c64scr.print("\nYou have ")
            c64scr.print_ub(attempts_left)
            c64scr.print(" guess")
            if attempts_left>1
                c64scr.print("es")
            c64scr.print(" left.\nWhat is your next guess? ")
            c64scr.input_chars(input)
            ubyte guess = c64utils.str2ubyte(input)

; debug info
;            c64scr.print(" > attempts left=")
;            c64scr.print_b(attempts_left)
;            c64scr.print("\n > secretnumber=")
;            c64scr.print_b(secretnumber)
;            c64scr.print("\n > input=")
;            c64scr.print(input)
;            c64scr.print("\n > guess=")
;            c64scr.print_b(guess)
;            c64.CHROUT('\n')
;            c64scr.print_b(X)    ; stackptr debugging
;            c64.CHROUT('\n')

            if guess==secretnumber {
                return ending(true)
            } else {
                c64scr.print("\n\nThat is too ")
                if guess<secretnumber
                    c64scr.print("low!\n")
                else
                    c64scr.print("high!\n")
            }
        }

        return ending(false)


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
