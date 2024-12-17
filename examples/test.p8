main {
    sub start() {
        uword[] tasklist = [1111,2222,3333]
        uword task_address = tasklist[0]
        goto task_address
        goto task_address+1
    }
}
