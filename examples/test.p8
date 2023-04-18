%import textio

main {
    sub start() {
        sys.set_leds_brightness(255, 255)
        sys.wait(120)
        sys.reset_system()
    }
}
