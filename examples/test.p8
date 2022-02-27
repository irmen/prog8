%import textio
%import floats
%import test_stack
%zeropage basicsafe

main {
    sub start() {
    get_player(1)
        |> determine_score()
        |> add_bonus()
        |> txt.print_uw()
    }

    sub get_player(ubyte xx) -> ubyte {
        return xx+33
    }

    sub determine_score() -> ubyte {
        return 33
    }

    sub add_bonus(ubyte qq) {
        qq++
    }


}
