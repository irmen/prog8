%import syslib

main {
    sub start() {
        for cx16.r0L in "Hello, world!\n" {
            sys.CHAROUT(cx16.r0L)
        }
    }
}

