%import custom

main {
    sub start() {
        custom.grab_system(false)
        repeat 100 custom.waitvsync()
        custom.restore_system()
    }
}
