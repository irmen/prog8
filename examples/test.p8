main {
    sub start() {
    }

    asmsub loader1() -> bool @D0 {
        %asmbinary "ocean_loader.68k"
    }

    asmsub loader2() -> bool @D0 {
        %asmbinary "ocean_loader_v2.68k"
    }
}
