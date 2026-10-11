;; Hand-maintained diskfont.library interface, trimmed to pre-3.0 content (up to Kickstart 2.0).
;; (Outline engine, diskfont control and tagged-font matching need 3.0+ and were removed.)
;; Library base: _DiskfontBase  in prog8: sys.DiskfontBase
;; Bank: 13

%import exec
%import graphics

diskfont {
    %option merge, no_symbol_prefixing

    sub openlib() -> bool {
        ; not opened at startup: diskfont.library lives on disk (not in ROM),
        ; so it must be opened on demand and may legitimately fail
        sys.DiskfontBase = exec.OpenLibrary("diskfont.library", 0)
        return sys.DiskfontBase!=0
    }

    sub closelib() {
        if sys.DiskfontBase!=0 {
            exec.CloseLibrary(sys.DiskfontBase)
            sys.DiskfontBase = 0
        }
    }

    private graphics.TextAttr[1] openfont_attr    ; scratch for openfont()

    sub openfont(str name, uword size, ubyte style) -> ^^graphics.TextFont {
        ; simplified OpenDiskFont with no flags (style e.g. graphics.FS_NORMAL).
        ; Name is like "helvetica.font". Returns 0 when the font cannot be found.
        ^^graphics.TextAttr fa = &openfont_attr[0]
        fa.Name = name
        fa.YSize = size
        fa.Style = style
        fa.Flags = 0
        return OpenDiskFont(fa)
    }

    extsub @bank 13   -30 = OpenDiskFont(^^graphics.TextAttr textAttr @A0) -> ^^graphics.TextFont @D0
    extsub @bank 13   -36 = AvailFonts(pointer buffer @A0, long bufBytes @D0, long flags @D1) -> long @D0
    extsub @bank 13   -42 = NewFontContents(pointer fontsLock @A0, str fontName @A1) -> pointer @D0
    extsub @bank 13   -48 = DisposeFontContents(pointer fontContentsHeader @A1)
    extsub @bank 13   -54 = NewScaledDiskFont(^^graphics.TextFont sourceFont @A0, ^^graphics.TextAttr destTextAttr @A1) -> pointer @D0

    ; ---- constants ----
    const uword MAXFONTPATH = $0100
    const uword FCH_ID = $0f00
    const uword TFCH_ID = $0f02
    const uword DFH_ID = $0f80
    const ubyte MAXFONTNAME = $0020
    const ubyte AFB_MEMORY = 0
    const ubyte AFF_MEMORY = $0001
    const ubyte AFB_DISK = 1
    const ubyte AFF_DISK = $0002
    const ubyte AFB_SCALED = 2
    const ubyte AFF_SCALED = $0004
    const ubyte AFB_BITMAP = 3
    const ubyte AFF_BITMAP = $0008
    const ubyte AFB_OTAG = 4
    const ubyte AFF_OTAG = $0010
    const ubyte AFB_CHARSET = 5
    const ubyte AFF_CHARSET = $0020
    const ubyte AFB_TYPE = 6
    const ubyte AFF_TYPE = $0040
}
