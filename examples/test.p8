%import textio
%zeropage basicsafe


main {

     ubyte key

     sub func() -> ubyte {
        return key=='a'
     }

     sub func2() -> bool {
        return key=='z'
     }

    sub start() {
        bool @shared z1=func()
        bool @shared z2=func2()
    }
}
