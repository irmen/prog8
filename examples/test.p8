
main {
    sub start() {
        ubyte col
        ubyte row
        repeat {
            col = rnd() % 33
            row = rnd() % 33
            ;cx16logo.logo_at(col, row)
            ;txt.plot(col-3, row+7 )
            ;txt.print("commander x16")
            col++
            row++
        }
    }
}
