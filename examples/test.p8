%import textio
%zeropage basicsafe


main {

     sub noCollision(ubyte xpos, ubyte ypos) -> bool {
        if xpos
             return false
        else
             return true
     }

    sub start() {
        bool z=noCollision(1,2)
    }
}
