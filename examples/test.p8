%import c64utils

~ main {

    sub start() {

        myblock2.foo()
        myblock3.foo()

    }
}

~ myblock2 {

    sub foo() {
        A=99
    }
}


~ myblock3 {

    sub foo() {
        A=99
    }
}
