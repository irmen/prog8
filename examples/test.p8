%zeropage basicsafe

main {
    sub start() {
        bool ans
        if true {
            ans = false
            if (ans == true) {
                return
            } else {
                goto done
            }
        }
    done:
        return
    }
}
