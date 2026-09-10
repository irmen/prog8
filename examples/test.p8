main {
    const uword STACK_SIZE = 10
    struct Stack {
        uword[STACK_SIZE] stack1
        uword[20] stack2
        uword top
    }

    sub start() {
        ^^Stack st = []
        st.stack1[5] = 9999
        st.stack2[5] = 8888
    }
}
