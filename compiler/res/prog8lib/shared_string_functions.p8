strings {
    ; the string functions shared across compiler targets
    %option merge, no_symbol_prefixing, ignore_unused

    sub split(str s, ^^uword parts, ubyte maxparts) -> ubyte {
        ; -- split string into parts (splits on whitespace and other non-printable characters).
        ;    Pointers to each part are stored in the given array.
        ;    Returns number of parts found (up to the given maximum). Modifies the string in place!

        if s==0 or s[0]==0
            return 0

        push(cx16.r0L)
        alias index = cx16.r0L
        ubyte numparts

        index = numparts = 0

        while numparts < maxparts {
            skipwhitespace()
            if s[index]==0
                break
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
        if numparts>0 {
            parts--
            strings.strip(parts^^)
        }
        cx16.r0L = pop()
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
        ubyte n = length(s)
        while n>0 {
            ubyte c = s[n-1]
            if isprint(c) and not isspace(c)
                break
            n--
        }
        s[n] = 0
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
        ubyte n = length(s)
        while n>0 {
            if not isspace(s[n-1])
                break
            n--
        }
        s[n] = 0
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
        if prefix_len > length(st)
            return false
        push(st[prefix_len])
        st[prefix_len] = 0
        bool result = compare(st, prefix) == 0
        st[prefix_len] = pop()
        return result
    }

    sub endswith(str st, str suffix) -> bool {
        ubyte suffix_len = length(suffix)
        ubyte str_len = length(st)
        if suffix_len > str_len
            return false
        return compare(st + str_len - suffix_len, suffix) == 0
    }

    sub next_token(str source, str delimiters) -> str {
        ; -- Tokenize the source string according to the list of delimiter characters. Like C's ``strtok`` function.
        if source == 0
            source = last_token_source
        else
            last_token_source = source

        ^^ubyte scan = last_token_source
        ^^ubyte dptr

        ; skip leading delimiter characters
        while scan^^ != 0 {
            dptr = delimiters
            while dptr^^ != 0 {
                if scan^^ == dptr^^
                    break
                dptr += 1
            }
            if dptr^^ == 0
                break
            scan += 1
        }
        last_token_source = scan
        if scan^^ == 0
            return 0

        source = scan
        while scan^^ != 0 {
            dptr = delimiters
            while dptr^^ != 0 {
                if scan^^ == dptr^^ {
                    scan^^ = 0
                    last_token_source = scan + 1
                    return source
                }
                dptr += 1
            }
            scan += 1
        }
        last_token_source = scan
        return source
    }
    ^^ubyte last_token_source

}
