string {
    ; the string functions shared across compiler targets
    %option merge, no_symbol_prefixing, ignore_unused

    sub strip(str s) {
        ; -- gets rid of whitespace and other non-visible characters at the edges of the string
        rstrip(s)
        lstrip(s)
    }

    sub rstrip(str s) {
        ; -- gets rid of whitespace and other non-visible characters at the end of the string
        if s[0]==0
            return
        cx16.r0L = string.length(s)
        do {
            cx16.r0L--
            cx16.r1L = s[cx16.r0L]
        } until cx16.r0L==0 or string.isprint(cx16.r1L) and not string.isspace(cx16.r1L)
        s[cx16.r0L+1] = 0
    }

    sub lstrip(str s) {
        ; -- gets rid of whitespace and other non-visible characters at the start of the string (destructive)
        cx16.r0 = lstripped(s)
        if cx16.r0 != s
            void string.copy(cx16.r0, s)
    }

    sub lstripped(str s) -> str {
        ; -- returns pointer to first non-whitespace and non-visible character at the start of the string (non-destructive lstrip)
        if s[0]==0
            return s
        cx16.r0L = 255
        do {
            cx16.r0L++
            cx16.r1L = s[cx16.r0L]
        } until cx16.r1L==0 or string.isprint(cx16.r1L) and not string.isspace(cx16.r1L)
        return s+cx16.r0L
    }

    sub trim(str s) {
        ; -- gets rid of whitespace characters at the edges of the string
        rtrim(s)
        ltrim(s)
    }

    sub rtrim(str s) {
        ; -- gets rid of whitespace characters at the end of the string
        if s[0]==0
            return
        cx16.r0L = string.length(s)
        do {
            cx16.r0L--
            cx16.r1L = s[cx16.r0L]
        } until cx16.r0L==0 or not string.isspace(cx16.r1L)
        s[cx16.r0L+1] = 0
    }

    sub ltrim(str s) {
        ; -- gets rid of whitespace characters at the start of the string (destructive)
        cx16.r0 = ltrimmed(s)
        if cx16.r0 != s
            void string.copy(cx16.r0, s)
    }

    sub ltrimmed(str s) -> str {
        ; -- return pointer to first non-whitespace character at the start of the string (non-destructive ltrim)
        if s[0]==0
            return s
        cx16.r0L = 255
        do {
            cx16.r0L++
            cx16.r1L = s[cx16.r0L]
        } until not string.isspace(cx16.r1L)
        return s+cx16.r0L
    }

}
