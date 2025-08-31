%import textio
%import strings

; Animal guessing game where the computer gets smarter every time.
; Note: this program can be compiled for multiple target systems.

main {
    const ubyte database_size = 100

    uword animal_names_buf = memory("animalnames", 500, 0)      ; area to store all animal names in, in sequence
    uword questions_buf = memory("questions", 2000, 0)          ; area to store all question texts in, in sequence
    uword animal_names_ptr
    uword questions_ptr

    uword[database_size] animals               ; pointers to the animal names
    uword[database_size] questions             ; pointers to the question texts
    uword[database_size] answers_questions     ; tree entries for question choices, indexed by question id, pair of (msb=yes, lsb=no) follow up question id (or 0 if it's an animal leaf node)
    uword[database_size] answers_animals       ; tree entries for animal leafs, indexed by question id, pair of (msb=yes, lsb=no) animal id

    ubyte new_animal_number
    ubyte new_question_number
    str userinput = "x"*80

    sub start() {
        ; initialize the database
        animal_names_ptr = animal_names_buf
        questions_ptr = questions_buf

        animals[0] = 0
        animals[1] = "dolphin"
        animals[2] = "eagle"
        animals[3] = "horse"
        new_animal_number = 4

        questions[0] = 0
        questions[1] = "does it swim"
        questions[2] = "can it fly"
        new_question_number = 3

        answers_questions[0] = mkword(0, 0)
        answers_questions[1] = mkword(0, 2)
        answers_questions[2] = mkword(0, 0)

        answers_animals[0] = mkword(0, 0)
        answers_animals[1] = mkword(1, 0)
        answers_animals[2] = mkword(2, 3)

        ; play the game
        game()
    }

    sub game() {
        repeat {
            ubyte current_question = 1
            txt.print("\n\nanimal guessing game!\nthink of an animal.\n")
            bool guessed = false
            while not guessed {
                txt.print(questions[current_question])
                txt.print("? ")
                if txt.input_chars(userinput)!=0 {
                    txt.nl()
                    ubyte animal_number
                    if userinput[0]=='y' {
                        animal_number = msb(answers_animals[current_question])
                        if animal_number!=0 {
                            guess(current_question, true, animal_number)
                            guessed = true
                        } else {
                            current_question = msb(answers_questions[current_question])
                        }
                    }
                    else if userinput[0]=='n' {
                        animal_number = lsb(answers_animals[current_question])
                        if animal_number!=0 {
                            guess(current_question, false, animal_number)
                            guessed = true
                        } else {
                            current_question = lsb(answers_questions[current_question])
                        }
                    }
                    else {
                        txt.print("answer (y)es or (n)o please.\n")
                    }
                } else
                    txt.nl()
            }
        }
    }

    sub guess(ubyte question_number, bool given_answer_yesno, ubyte animal_number) {
        txt.print("is it a ")
        txt.print(animals[animal_number])
        txt.print("? ")
        void txt.input_chars(userinput)
        if userinput[0] == 'y' {
            txt.print("\n\nsee, i knew it!\n")
            return
        }

        str name = "x"*30
        txt.print("\n\ni give up. what is it? ")
        void txt.input_chars(name)
        txt.print("\nwhat yes-no question would best articulate the difference\nbetween a ")
        txt.print(animals[animal_number])
        txt.print(" and a ")
        txt.print(name)
        txt.print("? ")
        void txt.input_chars(userinput)
        txt.print("\nin case of the ")
        txt.print(name)
        txt.print(", what is the answer to that question? ")
        str answer = "x"*10
        void txt.input_chars(answer)

        animals[new_animal_number] = animal_names_ptr
        questions[new_question_number] = questions_ptr
        animal_names_ptr += strings.copy(name, animal_names_ptr)+1  ; store animal name in buffer
        questions_ptr += strings.copy(userinput, questions_ptr)+1   ; store question in buffer

        answers_questions[new_question_number] = mkword(0, 0)
        if answer[0]=='y'
            answers_animals[new_question_number] = mkword(new_animal_number, animal_number)
        else
            answers_animals[new_question_number] = mkword(animal_number, new_animal_number)

        uword previous_animals = answers_animals[question_number]
        uword previous_questions = answers_questions[question_number]
        if given_answer_yesno {
            answers_animals[question_number] = mkword(0, lsb(previous_animals))
            answers_questions[question_number] = mkword(new_question_number, lsb(previous_questions))
        } else {
            answers_animals[question_number] = mkword(msb(previous_animals), 0)
            answers_questions[question_number] = mkword(msb(previous_questions), new_question_number)
        }

        new_animal_number++
        new_question_number++

        txt.print("\n\nthanks, i know more animals now! let's try again.\n")
    }
}
