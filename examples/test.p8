
main {
    struct Node {
        ubyte num
        bool flag
        ^^Node next
    }


    sub start() {
        ^^Node @shared ptr1 = 0   ; OK!
        ^^Node @shared ptr3 = 99999 ; OK!
        ^^Node @shared ptr2 = $2000  ; OK!
        ptr1.next = 0       ; OK !
        ptr1.flag = true
        ptr1.next = $1000    ; OK!
    }
}
