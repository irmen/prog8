%output prg
%import c64lib
%import c64utils

~ main {
    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   guessstr   = "??????????"
        ubyte  guess
        ubyte  secretnumber = 0
        ubyte  attempts_left = 10
        memory uword freadstr_arg = $22		; argument for FREADSTR
        uword testword

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
        c64.RND()               ; fac = random number
        c64.MUL10()             ; fac *= 10
        c64.MUL10()             ; .. and now *100
        c64.FADDH()             ; add 0.5..
        c64.FADDH()             ;   and again, so +1 total
        A, Y = c64flt.GETADRAY()
        secretnumber = A        ; secret number = rnd()*100+1

ask_guess:
        c64.STROUT("\nYou have ")
        c64scr.print_byte_decimal(attempts_left)
        c64.STROUT(" guess")
        if(attempts_left>0) c64.STROUT("es")

        c64.STROUT(" left.\nWhat is your next guess? ")
        c64scr.input_chars(guessstr)
        c64.CHROUT('\n')
        freadstr_arg = guessstr
        rsave()
        c64.FREADSTR(Y)
        A, Y = c64flt.GETADRAY()
        guess=A
        rrestore()
        c64.EXTCOL=guess    ; @debug
        c64.BGCOL0=secretnumber ;@debug
        if(guess==secretnumber) {               ; @todo equal_b doesn't work
            c64.STROUT("\nThat's my number, impressive!\n")
            goto goodbye
        }
        c64.STROUT("\nThat is too ")
        if(guess > secretnumber)        ; @todo greater_ub doesn't work?
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
