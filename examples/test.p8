%zeropage basicsafe

main {
    ubyte @shared prefix_len = 3

    sub start() {
        startswith1("irmen")
        startswith2("irmen")
    }

    sub startswith1(uword @zp st) {
        cx16.r9L = st[prefix_len]
        st[prefix_len] = 'a'
    }

    sub startswith2(str @zp st) {
        cx16.r9L = st[prefix_len]
        st[prefix_len] = 'a'
    }

}
