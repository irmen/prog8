%import textio
%zeropage kernalsafe


main {
    struct Node {
        ubyte type, frame, framecounter
    }


    sub start() {
        ^^Node storageElementBuffer = 20000
        ^^Node t_element = 30000

        ubyte @shared i = 0
        storageElementBuffer[0] = t_element^^
        storageElementBuffer[0] = t_element^^
        storageElementBuffer[1] = t_element^^
        storageElementBuffer[2] = t_element^^
        storageElementBuffer[i] = t_element^^
        storageElementBuffer[10]^^ = t_element^^
        storageElementBuffer[i]^^ = t_element^^

        t_element^^ = storageElementBuffer[2]
        t_element^^ = storageElementBuffer[2]^^
        t_element^^ = storageElementBuffer[i]
        t_element^^ = storageElementBuffer[i]^^
    }
}

structdefs {
    struct OtherNode {
        ubyte type, frame, framecounter
    }
}
