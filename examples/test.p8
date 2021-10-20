main {
    sub start() {
        uword address = &irq
        ; cx16.set_irq(&irq, false)
        address++
    }

    sub irq() {
    }
}
