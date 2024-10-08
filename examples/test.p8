%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str name1 = "irmen"
        str name2 = "other"
        bool[2] flags = [true, false]

        txt.print(name1)
        txt.nl()
        name1 = name2
        txt.print(name1)
        txt.nl()
        flags = [false, true]

        ubyte[10] array
        ubyte[10] array2

        void string.copy(name2, name1)
        array = array2
        name2 = "zzz"
        array = [1,2,3,4,5,6,7,8,9,10]
        ;; array = cx16.r0
        ;; array = name1
        ;; name1 = array
        ;; name1 = cx16.r0
    }
}
