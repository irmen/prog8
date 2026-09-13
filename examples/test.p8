%import textio
%zeropage basicsafe

main {
    sub start() {

        struct Enemy {
            long z
            bool flag
            ubyte g
        }

        %assert sizeof(Enemy)<40, "enemy struct too large"
    }
}
