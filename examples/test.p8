%import textio
%zeropage basicsafe

main {
    sub start() {
        str derp = "derp" * 4
        derp = derp / "zzz"
        derp = derp - "zzz"
        derp = derp + "zzz"
        txt.print(&derp+2)
    }
}
