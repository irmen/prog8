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
        c64scr.print_string("Please introduce yourself: ")
        c64scr.input_chars(name)
        c64scr.print_string("\n\nHello, ")
        c64scr.print_string(name)
        c64scr.print_string(".\nLet's play a number guessing game.\nI am thinking of a number from 1 to 100!You'll have to guess it!\n")

        for ubyte attempts_left in 10 to 1 step -1 {

; stackptr debugging
;            c64scr.print_byte_decimal(X)
;            c64.CHROUT('\n')

            c64scr.print_string("\nYou have ")
            c64scr.print_byte_decimal(attempts_left)
            c64scr.print_string(" guess")
            if attempts_left>1
                c64scr.print_string("es")
            c64scr.print_string(" left.\nWhat is your next guess? ")
            c64scr.input_chars(input)
            ubyte guess = str2ubyte(input)

; debug info
;            c64scr.print_string(" > attempts left=")
;            c64scr.print_byte_decimal(attempts_left)
;            c64scr.print_string("\n > secretnumber=")
;            c64scr.print_byte_decimal(secretnumber)
;            c64scr.print_string("\n > input=")
;            c64scr.print_string(input)
;            c64scr.print_string("\n > guess=")
;            c64scr.print_byte_decimal(guess)
;            c64.CHROUT('\n')
;            c64scr.print_byte_decimal(X)    ; stackptr debugging
;            c64.CHROUT('\n')

            if guess==secretnumber {
                return ending(true)
            } else {
                c64scr.print_string("\n\nThat is too ")
                if guess<secretnumber
                    c64scr.print_string("low!\n")
                else
                    c64scr.print_string("high!\n")
            }
        }

        return ending(false)


        sub ending(success: ubyte) {
            if success
                c64scr.print_string("\n\nYou guessed it, impressive!\n")
            else {
                c64scr.print_string("\nToo bad! My number was: ")
                c64scr.print_byte_decimal(secretnumber)
                c64scr.print_string(".\n")
            }
            c64scr.print_string("Thanks for playing, ")
            c64scr.print_string(name)
            c64scr.print_string(".\n")
        }
    }
}
