; routine to draw the Commander X16's log in petscii.

%import textio

cx16logo {
    sub logo_at(ubyte column, ubyte row) {
        uword strptr
        for strptr in logo_lines {
            txt.plot(column, row)
            txt.print(strptr)
            row++
        }
    }

    sub logo() {
        uword strptr
        for strptr in logo_lines
            txt.print(strptr)
        txt.nl()
    }

    str[] logo_lines = [
            "\uf10d\uf11a\uf139\uf11b     \uf11a\uf13a\uf11b\n",
            "\uf10b\uf11a▎\uf139\uf11b   \uf11a\uf13a\uf130\uf11b\n",
            "\uf10f\uf11a▌ \uf139\uf11b \uf11a\uf13a \uf11b▌\n",
            "\uf102 \uf132\uf11a▖\uf11b \uf11a▗\uf11b\uf132\n",
            "\uf10e ▂\uf11a▘\uf11b \uf11a▝\uf11b▂\n",
            "\uf104 \uf11a \uf11b\uf13a\uf11b \uf139\uf11a \uf11b\n",
            "\uf101\uf130\uf13a   \uf139▎\uf100"
        ]
}
