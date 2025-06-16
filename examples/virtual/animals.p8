%import textio
%import strings

; Animal guessing game where the computer gets smarter every time.
; Note: this program can be compiled for multiple target systems.
; TODO: move this to examples/animals.p8 once it compiles for 6502 too

main {

    str userinput = "x"*80      ; buffer for user input

    sub start() {
        db.init()

        txt.lowercase()
        intro()

         ^^db.Node active = db.first

        ; the game loop asks questions and navigates the y/n tree of animals.
        ; if an animal node is found, that animal could be the answer.
        ; if it's not correct, a new question and the animal are inserted at that position in the tree.
        repeat {
            if active.question!=0 {
                txt.print(active.question)
                txt.print("? ")

                if ask_yes_no()=='y'
                    active = active.positive
                else
                    active = active.negative
            } else {
                txt.print("Is it a ")
                txt.print(active.animal)
                txt.print("? ")

                if ask_yes_no()=='y' {
                    txt.print("\nYay, I knew it!\n")
                    txt.print("Let's go for another round.\n\n")
                }
                else
                    learn_new_animal()

                active = db.first
                intro()
            }
        }

        sub intro() {
            txt.print("Hello. Please think of an animal.\n")
            txt.print("I will try to guess what it is!\n\n")
        }

        sub ask_yes_no() -> ubyte {
            repeat {
                if txt.input_chars(userinput)!=0 {
                    txt.nl()
                    if userinput[0]=='y' or userinput[0]=='n'
                        return userinput[0]
                    txt.print("Please answer yes or no.\n")
                } else {
                    txt.nl()
                }
            }
        }

        sub learn_new_animal() {
            str new_animal = "x" * 30
            str answer = "x" * 10
            txt.print("\nI give up. What is the animal? ")
            void txt.input_chars(new_animal)
            txt.print("\nWhat yes/no question would best tell a ")
            txt.print(new_animal)
            txt.print(" apart from a ")
            txt.print(active.animal)
            txt.print("? ")
            ubyte question_length = txt.input_chars(userinput)
            uword question_addr = arena.alloc(question_length+1)
            void strings.copy(userinput, question_addr)
            txt.print("In case of the ")
            txt.print(new_animal)
            txt.print(", what is the answer to that question? ")
            ubyte yesno = ask_yes_no()

            ^^db.Node new_animal_node = db.Node(0, new_animal, 0, 0)
            ^^db.Node wrong_animal_node = db.Node(0, 0, 0, 0)
            wrong_animal_node.animal = active.animal
            active.question = question_addr
            active.animal = 0
            if yesno=='y' {
                active.positive = new_animal_node
                active.negative = wrong_animal_node
            } else {
                active.positive = wrong_animal_node
                active.negative = new_animal_node
            }

            txt.print("\nI've learned about that new animal now!\n")
            txt.print("Let's go for another round.\n\n")
        }
    }
}

db {
    struct Node {
        str question
        str animal
        ^^Node negative
        ^^Node positive
    }

    ^^Node first

    sub init() {
        first = Node("does it swim", 0, 0, 0)
        ^^Node eagle = Node(0, "eagle", 0, 0)
        ^^Node dolpin = Node(0, "dolpin", 0, 0)
        ^^Node question = Node("can it fly", 0, 0, 0)
        ^^Node horse = Node(0, "horse", 0, 0)
        first.negative = question
        first.positive = dolpin
        question.negative = horse
        question.positive = eagle
    }
}

arena {
    ; extremely trivial arena allocator (that never frees)
    uword buffer = memory("arena", 10000, 0)
    uword next = buffer

    sub alloc(ubyte size) -> uword {
        defer next += size
        return next
    }
}
