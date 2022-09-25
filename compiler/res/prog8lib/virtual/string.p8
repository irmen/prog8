; 0-terminated string manipulation routines. For the Virtual Machine target.

string {
    sub length(str st) -> ubyte {
        ; Returns the number of bytes in the string.
        ; This value is determined during runtime and counts upto the first terminating 0 byte in the string,
        ; regardless of the size of the string during compilation time. Don’t confuse this with len and sizeof!
        ubyte count = 0
        while st[count]
            count++
        return count
    }

    sub left(str source, ubyte slen, str target)  {
        ; Copies the left side of the source string of the given length to target string.
        ; It is assumed the target string buffer is large enough to contain the result.
        ; Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
        ; Modifies in-place, doesn’t return a value (so can’t be used in an expression).
        target[slen] = 0
        ubyte ix
        for ix in 0 to slen-1 {
            target[ix] = source[ix]
        }
    }

    sub right(str source, ubyte slen, str target) {
        ; Copies the right side of the source string of the given length to target string.
        ; It is assumed the target string buffer is large enough to contain the result.
        ; Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
        ; Modifies in-place, doesn’t return a value (so can’t be used in an expression).
        ubyte offset = length(source)-slen
        ubyte ix
        for ix in 0 to slen-1 {
            target[ix] = source[ix+offset]
        }
        target[ix]=0
    }

    sub slice(str source, ubyte start, ubyte slen, str target) {
        ; Copies a segment from the source string, starting at the given index,
        ;  and of the given length to target string.
        ; It is assumed the target string buffer is large enough to contain the result.
        ; Also, you have to make sure yourself that start and length are within bounds of the strings.
        ; Modifies in-place, doesn’t return a value (so can’t be used in an expression).
        ubyte ix
        for ix in 0 to slen-1 {
            target[ix] = source[ix+start]
        }
        target[ix]=0
    }

    sub find(str st, ubyte character) -> ubyte {
        ; Locates the first position of the given character in the string,
        ; returns Carry set if found + index in A, or Carry clear if not found.
        ubyte ix
        for ix in 0 to length(st)-1 {
            if st[ix]==character {
                sys.set_carry()
                return ix
            }
        }
        sys.clear_carry()
        return 0
    }

    sub copy(str source, str target) -> ubyte {
        ; Copy a string to another, overwriting that one.
        ; Returns the length of the string that was copied.
        ; Often you don’t have to call this explicitly and can just write string1 = string2
        ; but this function is useful if you’re dealing with addresses for instance.
        ubyte ix
        repeat {
            ubyte char=source[ix]
            target[ix]=char
            if not char
                return ix
            ix++
        }
    }

    sub compare(str st1, str st2) -> byte {
        ; Compares two strings for sorting.
        ; Returns -1 (255), 0 or 1 depending on wether string1 sorts before, equal or after string2.
        ; Note that you can also directly compare strings and string values with eachother using
        ; comparison operators ==, < etcetera (it will use strcmp for you under water automatically).
        return prog8_lib.string_compare(st1, st2)
    }

    sub lower(str st) -> ubyte {
        ; Lowercases the petscii string in-place. Returns length of the string.
        ; (for efficiency, non-letter characters > 128 will also not be left intact,
        ;  but regular text doesn't usually contain those characters anyway.)
        ubyte ix
        repeat {
            ubyte char=st[ix]
            if not char
                return ix
            if char >= 'A' and char <= 'Z'
                st[ix] = char | %00100000
            ix++
        }
    }

    sub upper(str st) -> ubyte {
        ; Uppercases the petscii string in-place. Returns length of the string.
        ubyte ix
        repeat {
            ubyte char=st[ix]
            if not char
                return ix
            if char >= 97 and char <= 122
                st[ix] = char & %11011111
            ix++
        }
    }

    sub lowerchar(ubyte char) -> ubyte {
        if char >= 'A' and char <= 'Z'
            char |= %00100000
        return char
    }

    sub upperchar(ubyte char) -> ubyte {
        if char >= 'a' and char <= 'z'
            char &= %11011111
        return char
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
}
