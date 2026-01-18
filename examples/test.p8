%import textio
%import strings
%option no_sysinit
%zeropage basicsafe


textOverlay {
  uword overlayTop, overlayBot
}

main {
    alias textOverlay_top = textOverlay.overlayTop
    alias textOverlay_bot = textOverlay.overlayBot

    sub start() {
        textOverlay_bot++
        textOverlay_top++
    }
}
