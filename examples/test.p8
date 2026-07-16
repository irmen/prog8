%import custom


main {
    sub start() {
        custom.grab_system()

        ; TODO wait for raster position 100
        ; TODO draw a nice raster bar pattern, color register = custom.COLOR0

        custom.return_system()
    }
}
