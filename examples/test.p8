main {
    uword[10] answers_animals

    sub start() {
        ubyte current_question = 1
        uword previous_animals = 33
        current_question = msb(answers_animals[current_question])               ; TODO takes 1 more vm registers than 8.10
        answers_animals[current_question] = mkword(msb(previous_animals), 0)    ; TODO takes 1 more vm registers than 8.10
        ; TODO expected result: 7 registers in 8.10,   now takes 9 instead
    }
}
