%import textio
%encoding iso
main {
    sub start() {
        txt.iso()
        long @shared l = -1
        l >>= 32
        txt.print_ulhex(l, false)
        txt.nl()
        long @shared l2 = $80000000
        l2 >>= 31
        txt.print_ulhex(l2, false)
        txt.nl()
        long @shared l3 = -1
        l3 >>= 40
        txt.print_ulhex(l3, false)
        txt.nl()
        long @shared w = $ffff
        w >>= 16
        txt.print_ulhex(w, false)
        txt.nl()
        long @shared w2 = 1
        w2 <<= 16
        txt.print_ulhex(w2, false)
        txt.nl()
        byte @shared b = $80 as byte
        b >>= 8
        txt.print_b(b)
        txt.nl()
        ubyte @shared b2 = 1
        b2 <<= 8
        txt.print_ub(b2)
        txt.nl()
        ubyte @shared cnt = 33
        long @shared l4 = 5
        l4 <<= cnt
        txt.print_ulhex(l4, false)
        txt.nl()
        ubyte[2] @shared arr
        arr[0] = 1
        arr[0] <<= 33
        txt.print_ub(arr[0])
        txt.nl()
        sys.poweroff_system()
    }
}
