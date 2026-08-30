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


        Enemy[3] squad = [
            [10, 20, 100, false],
            [30, 40, 200, true],
            [50, 60, 150, false]
        ]

        txt.nl()
        txt.print_uw(squad[2].health)
        txt.nl()
        squad[2].health = 9977
        txt.print_uw(squad[2].health)
        txt.nl()
    }
}
