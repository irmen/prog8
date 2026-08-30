%import textio
%zeropage basicsafe

main {

    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

    sub start() {
        Enemy[4] enemies          ; 4 Enemy instances in a contiguous block, initialized to zero
        enemies[2] = enemies[3]
        sys.memcopy(&enemies[3], &enemies[2], sizeof(Enemy))
    }
}
