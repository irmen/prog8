main {
    sub start() {
        goto labeloutside

        if true {
            if true {
                goto labeloutside
                goto iflabel
            }
iflabel:
        }

        repeat {
            goto labelinside
labelinside:
        }

labeloutside:
    }

    start:
    start:
    start:
}
