%import textio
%import floats

main {
    sub start() {
        float x=10
        float y=20
        bool r = x!=y
        txt.print_ub(r)
        repeat 4 {
            txt.print(".")
        }
    }
}
