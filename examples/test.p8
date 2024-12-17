main {
    sub start() {
        uword[] @nosplit tasklist = [&start-1]
;        uword task_address = tasklist[0]
;        goto task_address
    }
}
