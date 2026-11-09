%import textio
%import strings

; Test program exercising all case-conversion routines.
; First the PETSCII routines (lowercase charset),
; then (after pressing Enter) the ISO routines (ISO charset).

main {
    sub start() {
        txt.lowercase()
        ; ---- PETSCII routines (lowercase charset) ----
        str s = petscii:"Hello World"
        void strings.lower(s)
        txt.print(petscii:"lower: ")
        txt.print(s)
        txt.print(petscii:"   (expected: hello world)")
        txt.nl()

        str s2 = petscii:"Hello World"
        void strings.upper(s2)
        txt.print(petscii:"upper: ")
        txt.print(s2)
        txt.print(petscii:"   (expected: HELLO WORLD)")
        txt.nl()

        txt.print(petscii:"lowerchar: ")
        txt.chrout(strings.lowerchar(petscii:'H'))
        txt.spc()
        txt.chrout(strings.lowerchar(petscii:'E'))
        txt.spc()
        txt.chrout(strings.lowerchar(petscii:'L'))
        txt.spc()
        txt.chrout(strings.lowerchar(petscii:'L'))
        txt.spc()
        txt.chrout(strings.lowerchar(petscii:'O'))
        txt.print(petscii:"   (expected: h e l l o)")
        txt.nl()

        txt.print(petscii:"upperchar: ")
        txt.chrout(strings.upperchar(petscii:'h'))
        txt.spc()
        txt.chrout(strings.upperchar(petscii:'e'))
        txt.spc()
        txt.chrout(strings.upperchar(petscii:'l'))
        txt.spc()
        txt.chrout(strings.upperchar(petscii:'l'))
        txt.spc()
        txt.chrout(strings.upperchar(petscii:'o'))
        txt.print(petscii:"   (expected: H E L L O)")
        txt.nl()

        txt.print(petscii:"Press ENTER for ISO tests...")
        txt.nl()
        void txt.waitkey()

        ; ---- ISO routines ----
        txt.iso()

        str si = iso:"Hello World"
        void strings.lower_iso(si)
        txt.print(iso:"lower_iso: ")
        txt.print(si)
        txt.print(iso:"   (expected: hello world)")
        txt.nl()

        str si2 = iso:"Hello World"
        void strings.upper_iso(si2)
        txt.print(iso:"upper_iso: ")
        txt.print(si2)
        txt.print(iso:"   (expected: HELLO WORLD)")
        txt.nl()

        txt.print(iso:"lowerchar_iso: ")
        txt.chrout(strings.lowerchar_iso(iso:'H'))
        txt.spc()
        txt.chrout(strings.lowerchar_iso(iso:'E'))
        txt.spc()
        txt.chrout(strings.lowerchar_iso(iso:'L'))
        txt.spc()
        txt.chrout(strings.lowerchar_iso(iso:'L'))
        txt.spc()
        txt.chrout(strings.lowerchar_iso(iso:'O'))
        txt.print(iso:"   (expected: h e l l o)")
        txt.nl()

        txt.print(iso:"upperchar_iso: ")
        txt.chrout(strings.upperchar_iso(iso:'h'))
        txt.spc()
        txt.chrout(strings.upperchar_iso(iso:'e'))
        txt.spc()
        txt.chrout(strings.upperchar_iso(iso:'l'))
        txt.spc()
        txt.chrout(strings.upperchar_iso(iso:'l'))
        txt.spc()
        txt.chrout(strings.upperchar_iso(iso:'o'))
        txt.print(iso:"   (expected: H E L L O)")
        txt.nl()
        void txt.waitkey()

        sys.poweroff_system()
    }
}
