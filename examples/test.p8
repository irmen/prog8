%import textio

main {

    sub start() {
        ubyte[] array = ['h', 'e', 'l', 'l', 'o', 0]
        str name = "hello"

        name[5] = '!'       ; don't do this in real code...
        name[5] = 0
        name[6] = 99        ; out of bounds
        name[-1] = 99       ; ok
        name[-5] = 99       ; ok

        cx16.r0L = name[5]
        cx16.r1L = name[6]   ; out of bounds
        cx16.r1L = name[-1]  ; ok
        cx16.r1L = name[-5]  ; ok

        array[5] = '!'
        array[5] = 0
        array[6] = 99        ; out of bounds
        array[-1] = 99       ; ok
        array[-5] = 99       ; ok
        array[-6] = 99       ; ok

        cx16.r0L = array[5]
        cx16.r1L = array[6]  ; out of bounds
        cx16.r1L = array[-1] ; ok
        cx16.r1L = array[-5] ; ok
        cx16.r1L = array[-6] ; ok
    }
}
