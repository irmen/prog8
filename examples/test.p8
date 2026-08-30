%import textio
%zeropage basicsafe

main {

    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

    sub start() {
;        Enemy[4] enemies          ; 4 Enemy instances in a contiguous block, initialized to zero
;        ^^Enemy[4] @nosplit enemyptrs      ; 4 Enemy pointers
;
;        for index in 0 to 3 {
;            txt.print_uwhex(enemyptrs[index], true)
;            txt.spc()
;        }
;
;        for index in 0 to 3 {
;            txt.print_uw(enemies[index].health)
;            txt.spc()
;        }
;
;        enemies[2] = enemies[3]
;        sys.memcopy(&enemies[3], &enemies[2], sizeof(Enemy))


        ; TODO: this is not actually acceptable syntax:
        ; an array of 3 Enemy structs is initialized with the syntax for an Enemy *pointer* array
        ; I expect it to be a compilation error with  ^^Enemy : [10, 20, 100, false]
        ; I expect it to work with   Enemy : [10, 20, 100, false]   (notice no ^^ pointer)
        ; I expect it to work with   [10, 20, 100, false]   (no explicit type, inferred from the array type by the compiler)
        Enemy[3] squad = [
            ^^Enemy : [10, 20, 100, false],
            ^^Enemy : [30, 40, 200, true],
            ^^Enemy : [50, 60, 150, false]
        ]

        txt.nl()
        txt.print_uw(squad[2].health)
        txt.nl()
        squad[2].health = 9977
        txt.print_uw(squad[2].health)
        txt.nl()
    }
}
