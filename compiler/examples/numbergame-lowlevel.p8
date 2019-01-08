%import c64utils
%import c64flt


; The classic number guessing game.
; This version uses more low-level subroutines (calls directly into the C64's ROM routines)
; and instead of a loop (with the added behind the scenes processing), uses absolute jumps.
; It's less readable I think, but produces a smaller program.


~ main {
    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   input   = "??????????"
        ubyte  guess
        ubyte  secretnumber = 0
        ubyte  attempts_left = 10
        memory uword freadstr_arg = $22		; argument for FREADSTR ($22/$23)

        ; greeting
        c64.VMCSB |= 2  ; switch lowercase chars
        c64.STROUT("Please introduce yourself: ")
        c64scr.input_chars(name)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        c64.STROUT("Hello, ")
        c64.STROUT(name)
        c64.STROUT(".\nLet's play a number guessing game.\nI am thinking of a number from 1 to 100!You'll have to guess it!\n")

        ; create a secret random number from 1-100
        c64flt.RND()               ; fac = random number between 0 and 1
        c64flt.MUL10()             ; fac *= 10
        c64flt.MUL10()             ; .. and now *100
        c64flt.FADDH()             ; add 0.5..
        c64flt.FADDH()             ;   and again, so +1 total
        A, Y = c64flt.GETADRAY()
        secretnumber = A        ; secret number = rnd()*100+1

ask_guess:
        c64.STROUT("\nYou have ")
        c64scr.print_ub(attempts_left)
        c64.STROUT(" guess")
        if(attempts_left>1)
            c64.STROUT("es")

        c64.STROUT(" left.\nWhat is your next guess? ")
        Y=c64scr.input_chars(input)
        c64.CHROUT('\n')
        freadstr_arg = input
        c64flt.FREADSTR(Y)
        A, Y = c64flt.GETADRAY()
        guess=A
        if(guess==secretnumber) {
            c64.STROUT("\nThat's my number, impressive!\n")
            goto goodbye
        }
        c64.STROUT("\nThat is too ")
        if(guess < secretnumber)
            c64.STROUT("low!\n")
        else
            c64.STROUT("high!\n")

        attempts_left--
        if_nz goto ask_guess

        ; game over.
        c64.STROUT("\nToo bad! It was: ")
        c64scr.print_ub(secretnumber)
        c64.CHROUT('\n')

goodbye:
        c64.STROUT("\nThanks for playing, ")
        c64.STROUT(name)
        c64.STROUT(".\n")
        return
    }
}
