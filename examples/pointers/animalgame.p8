; Animal guessing game where the computer guesses your secret animal, and gets smarter every time it fails.
; The tree is in memory it is not saved on disk right now so restarting the program loses all knowledge...
;
; Exercise for the reader to store the memory containing the nodes and strings and reload it on new runs...?

; TODO: remove the examples/animals.p8 once this one compiles for 6502 too

%import textio
%import strings

main {

    str userinput = "?"*80      ; buffer for user input

    sub start() {
        db.init()

        txt.lowercase()
        intro()

         ^^db.Node active = db.first

        ; The game loop asks questions and navigates the y/n tree of animals.
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
            str new_animal = "?" * 30
            str answer = "?" * 10

            ; note that we make copies of the animal name and question strings to store them later
            txt.print("\nI give up. What is the animal? ")
            ubyte new_animal_length = txt.input_chars(new_animal)
            uword new_animal_copy = arena.alloc(new_animal_length+1)
            void strings.copy(new_animal, new_animal_copy)
            txt.print("\nWhat yes/no question would best tell a ")
            txt.print(new_animal)
            txt.print(" apart from a ")
            txt.print(active.animal)
            txt.print("? ")
            ubyte question_length = txt.input_chars(userinput)
            uword question_copy = arena.alloc(question_length+1)
            void strings.copy(userinput, question_copy)
            txt.print("In case of the ")
            txt.print(new_animal)
            txt.print(", what is the answer to that question? ")
            ubyte yesno = ask_yes_no()

            ; cannot use struct initializer  db.Node(....)  here because we need to have a new node every time
            ^^db.Node new_animal_node = arena.alloc(sizeof(db.Node))
            new_animal_node.animal = new_animal_copy
            new_animal_node.question = new_animal_node.negative = new_animal_node.positive = 0
            ^^db.Node wrong_animal_node = arena.alloc(sizeof(db.Node))
            wrong_animal_node.animal = active.animal
            wrong_animal_node.question = wrong_animal_node.negative = wrong_animal_node.positive = 0

            active.question = question_copy
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
    ; knowledge about animals is stored in a tree that can grow with new animals.
    ; questions have y/n answers that point to possible animals.

    struct Node {
        str question
        str animal
        ^^Node negative
        ^^Node positive
    }

    ^^Node first

    sub init() {
        first = Node("does it swim", 0, 0, 0)
        ^^Node question = Node("can it fly", 0, 0, 0)
        first.negative = question
        first.positive = Node(0, "dolpin", 0, 0)
        question.negative = Node(0, "horse", 0, 0)
        question.positive = Node(0, "eagle", 0, 0)
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
