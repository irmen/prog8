main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"
        uword[] @split names = [name1, name2, "name3"]
        cx16.r0++
        names = [1111,2222,3333]
    }
}
