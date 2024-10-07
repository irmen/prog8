main {
    sub foo() {
        cx16.r0++
    }
    asmsub baz() {
        %asm{{
            jsr p8s_foo
        }}
    }
    asmsub bar() {
        %asm{{
            inx
            jmp p8s_foo
        }}
    }
    sub start() {
        bar()
    }
}
