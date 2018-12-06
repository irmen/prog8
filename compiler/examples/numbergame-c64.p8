%output prg
%import c64lib
%import c64utils

~ main {
    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   guess   = "??????????"
        ubyte  secretnumber = 0
        ubyte  attempts_left = 10
        memory uword freadstr_arg = $22		; argument for FREADSTR
        uword testword

        ; greeting
        c64.VMCSB = %10111  ; switch lowercase chars
        c64.STROUT("Please introduce yourself: ")
        Y = c64scr.input_chars(name)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        c64.STROUT("Hello, ")
        c64.STROUT(name)
        c64.STROUT(".\nLet's play a number guessing game.\nI am thinking of a number from 1 to 100!You'll have to guess it!\n")

        ; create a secret random number from 1-100
        c64.RND()               ; fac = random number
        c64.MUL10()             ; fac *= 10
        c64.MUL10()             ; .. and now *100
        c64.FADDH()             ; add 0.5..
        c64.FADDH()             ;   and again, so +1 total
        A, Y = c64flt.GETADRAY()
        secretnumber = A

ask_guess:
        c64.STROUT("\nYou have ")
        c64scr.print_byte_decimal(attempts_left)
        c64.STROUT(" guess")
        if(attempts_left>0) c64.STROUT("es")

        c64.STROUT(" left.\nWhat is your next guess? ")
        Y = c64scr.input_chars(guess)
        c64.CHROUT('\n')
        freadstr_arg = guess
        c64.FREADSTR(A)
        A, Y = c64flt.GETADRAY()
        if(A==secretnumber) {
            c64.STROUT("\nThat's my number, impressive!\n")
            goto goodbye
        }
        c64.STROUT("\nThat is too ")
        if(A > secretnumber)
            c64.STROUT("low!\n")
        else
            c64.STROUT("high!\n")

        attempts_left--
        if(attempts_left>0) goto ask_guess
        ; more efficient:  if_nz goto ask_guess

        ; game over.
        c64.STROUT("\nToo bad! It was: ")
        c64scr.print_byte_decimal(secretnumber)
        c64.CHROUT('\n')

goodbye:
        c64.STROUT("\nThanks for playing, ")
        c64.STROUT(name)
        c64.STROUT(".\n")
        return
    }
}
