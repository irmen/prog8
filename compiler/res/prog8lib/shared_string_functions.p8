strings {
    ; the string functions shared across compiler targets
    %option merge, no_symbol_prefixing, ignore_unused

    sub split(str s, ^^uword parts, ubyte maxparts) -> ubyte {
        ; -- split string into parts (splits on whitespace and other non-printable characters).
        ;    Pointers to each part are stored in the given array.
        ;    Returns number of parts found (up to the given maximum). Modifies the string in place!

        if s==0 or s[0]==0
            return 0

        sys.push(cx16.r0L)
        alias index = cx16.r0L
        ubyte numparts

        index = numparts = 0

        while numparts < maxparts {
            skipwhitespace()
            if s[index]!=0 {
                parts^^ = s+index
                numparts++
                parts++
                skipchars()
                if s[index]==0
                    break
                if numparts < maxparts
                    s[index] = 0
                index++
            }
        }
        if numparts>0 {
            parts--
            strings.strip(parts^^)
        }
        cx16.r0L = sys.pop()
        return numparts

        sub skipwhitespace() {
            while s[index]!=0 and (strings.isspace(s[index]) or not strings.isprint(s[index]))
                index++
        }

        sub skipchars() {
            while s[index]!=0 and not strings.isspace(s[index])
                index++
        }
    }

    sub strip(str s) {
        ; -- gets rid of whitespace and other non-visible characters at the edges of the string
        rstrip(s)
        lstrip(s)
    }

    sub rstrip(str s) {
        ; -- gets rid of whitespace and other non-visible characters at the end of the string
        if s[0]==0
            return
        cx16.r0L = length(s)
        do {
            cx16.r0L--
            cx16.r1L = s[cx16.r0L]
        } until cx16.r0L==0 or isprint(cx16.r1L) and not isspace(cx16.r1L)
        s[cx16.r0L+1] = 0
    }

    sub lstrip(str s) {
        ; -- gets rid of whitespace and other non-visible characters at the start of the string (destructive)
        cx16.r0 = lstripped(s)
        if cx16.r0 != s
            void copy(cx16.r0, s)
    }

    sub lstripped(str s) -> str {
        ; -- returns pointer to first non-whitespace and non-visible character at the start of the string (non-destructive lstrip)
        if s[0]==0
            return s
        cx16.r0L = 255
        do {
            cx16.r0L++
            cx16.r1L = s[cx16.r0L]
        } until cx16.r1L==0 or isprint(cx16.r1L) and not isspace(cx16.r1L)
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
        cx16.r0L = length(s)
        do {
            cx16.r0L--
            cx16.r1L = s[cx16.r0L]
        } until cx16.r0L==0 or not isspace(cx16.r1L)
        s[cx16.r0L+1] = 0
    }

    sub ltrim(str s) {
        ; -- gets rid of whitespace characters at the start of the string (destructive)
        cx16.r0 = ltrimmed(s)
        if cx16.r0 != s
            void copy(cx16.r0, s)
    }

    sub ltrimmed(str s) -> str {
        ; -- return pointer to first non-whitespace character at the start of the string (non-destructive ltrim)
        if s[0]==0
            return s
        cx16.r0L = 255
        do {
            cx16.r0L++
            cx16.r1L = s[cx16.r0L]
        } until not isspace(cx16.r1L)
        return s+cx16.r0L
    }

    sub startswith(str st, str prefix) -> bool {
        ubyte prefix_len = length(prefix)
        ubyte str_len = length(st)
        if prefix_len > str_len
            return false
        cx16.r9L = st[prefix_len]
        st[prefix_len] = 0
        cx16.r9H = compare(st, prefix) as ubyte
        st[prefix_len] = cx16.r9L
        return cx16.r9H==0
    }

    sub endswith(str st, str suffix) -> bool {
        ubyte suffix_len = length(suffix)
        ubyte str_len = length(st)
        if suffix_len > str_len
            return false
        return compare(st + str_len - suffix_len, suffix) == 0
    }

    sub findstr(str haystack, str needle) -> ubyte {
        ; searches for needle in haystack.
        ; returns index in haystack where it first occurs, and Carry set,
        ; or if needle doesn't occur in haystack it returns Carry clear and 255 (an invalid index.)
        cx16.r2L = length(haystack)
        cx16.r3L = length(needle)
        if cx16.r3L <= cx16.r2L {
            cx16.r2L = cx16.r2L-cx16.r3L+1
            cx16.r3 = haystack
            repeat cx16.r2L {
                if startswith(cx16.r3, needle) {
                    sys.set_carry()
                    return cx16.r3-(haystack as uword) as ubyte
                }
                cx16.r3++
            }
        }
        sys.clear_carry()
        return 255
    }
}
