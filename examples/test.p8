main {
    sub start() {
        nmi_handler()
        cx16.r0++
        the_loop:
            repeat {
                cx16.r0++
            }
    }

    sub nmi_handler() {;forcefully kills the running process and returns to the shell prompt.
        cx16.r0++
        goto main.start.the_loop
    }
}
