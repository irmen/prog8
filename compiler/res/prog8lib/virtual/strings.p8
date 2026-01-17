; 0-terminated string manipulation routines. For the Virtual Machine target.

%import shared_string_functions

strings {
    %option ignore_unused

    sub length(str st) -> ubyte {
        ; Returns the number of bytes in the string.
        ; This value is determined during runtime and counts upto the first terminating 0 byte in the string,
        ; regardless of the size of the string during compilation time. Don’t confuse this with len and sizeof!
        ubyte count = 0
        while st[count]!=0
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
        target[slen]=0
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
        target[slen]=0
    }

    sub find(str st, ubyte character) -> ubyte, bool {
        ; Locates the first position of the given character in the string,
        ; returns index in A, and boolean if found or not.  (when false A will also be 255,  an invalid index).
        ubyte ix
        for ix in 0 to length(st)-1 {
            if st[ix]==character {
                sys.set_carry()
                return ix, true
            }
        }
        sys.clear_carry()
        return 255, false
    }

    sub find_eol(str st) -> ubyte, bool {
        ; Locates the position of the first End Of Line character in the string.
        ; This is a convenience function that looks for both a CR or LF (byte 13 or byte 10) as being a possible Line Ending.
        ; returns index in A, and boolean if found or not.  (when false A will also be 255,  an invalid index).
        ubyte ix
        for ix in 0 to length(st)-1 {
            if st[ix] in "\x0a\x0d" {
                sys.set_carry()
                return ix, true
            }
        }
        sys.clear_carry()
        return 255, false
    }

    sub rfind(str stringptr, ubyte character) -> ubyte, bool {
        ; Locates the first position of the given character in the string, starting from the right.
        ; returns index in A, and boolean if found or not.  (when false A will also be 255,  an invalid index).
        ubyte ix
        for ix in length(stringptr)-1 downto 0 {
            if stringptr[ix]==character {
                sys.set_carry()
                return ix, true
            }
        }
        sys.clear_carry()
        return 255, false
    }

    sub contains(str st, ubyte character) -> bool {
        void find(st, character)
        if_cs
            return true
        return false
    }

    sub copy(str source, str target) -> ubyte {
        ; Copy a string to another, overwriting that one.
        ; Returns the length of the string that was copied.
        %ir {{
            loadm.w r99000,strings.copy.source
            loadm.w r99001,strings.copy.target
            load.b r99100,#255
            syscall 39 (r99000.w, r99001.w, r99100.b): r99100.b
            returnr.b r99100
        }}
    }

    sub ncopy(str source, str target, ubyte maxlength) -> ubyte {
        ; Copy a string to another, overwriting that one, but limited to the given length.
        ; Returns the length of the string that was copied.
        %ir {{
            loadm.w r99000,strings.ncopy.source
            loadm.w r99001,strings.ncopy.target
            loadm.b r99100,strings.ncopy.maxlength
            syscall 39 (r99000.w, r99001.w, r99100.b): r99100.b
            returnr.b r99100
        }}
    }

    sub append(str target, str suffix) -> ubyte {
        ; Append the suffix string to the target. (make sure the buffer is large enough!)
        ; Returns the length of the resulting string.
        cx16.r0L = length(target)
        return copy(suffix, target+cx16.r0L) + cx16.r0L
    }

    sub nappend(str target, str suffix, ubyte maxlength) -> ubyte {
        ; Append the suffix string to the target, up to the given maximum length of the combined string.
        ; Returns the length of the resulting string.
        cx16.r0L = length(target)
        if maxlength<cx16.r0L
            return cx16.r0L
        maxlength -= cx16.r0L
        return ncopy(suffix, target+cx16.r0L, maxlength) + cx16.r0L
    }

    sub compare(str st1, str st2) -> byte {
        ; Compares two strings for sorting (case sensitive).
        ; Returns -1 (255), 0 or 1, meaning: string1 sorts before, equal or after string2.
        ; Note that you can also directly compare strings and string values with eachother using
        ; comparison operators ==, < etcetera (this will use strcmp automatically).
        %ir {{
            loadm.w r99000,strings.compare.st1
            loadm.w r99001,strings.compare.st2
            syscall 16 (r99000.w, r99001.w) : r99100.b
            returnr.b r99100
        }}
    }

    sub compare_nocase(str st1, str st2) -> byte {
        ; Compares two strings for sorting (case insensitive).
        ; Returns -1 (255), 0 or 1, meaning: string1 sorts before, equal or after string2.
        ; Note that you can also directly compare strings and string values with eachother using
        ; comparison operators ==, < etcetera (this will use strcmp automatically).
        %ir {{
            loadm.w r99000,strings.compare_nocase.st1
            loadm.w r99001,strings.compare_nocase.st2
            syscall 63 (r99000.w, r99001.w) : r99100.b
            returnr.b r99100
        }}
    }

    sub ncompare(str st1, str st2, ubyte length) -> byte {
        ; Compares two strings for sorting (case sensitive).
        ; Returns -1 (255), 0 or 1, meaning: string1 sorts before, equal or after string2.
        ; Only compares the strings from index 0 up to the length argument.
        %ir {{
            loadm.w r99000,strings.ncompare.st1
            loadm.w r99001,strings.ncompare.st2
            loadm.b r99100,strings.ncompare.length
            syscall 58 (r99000.w, r99001.w, r99100.b) : r99100.b
            returnr.b r99100
        }}
    }

    sub ncompare_nocase(str st1, str st2, ubyte length) -> byte {
        ; Compares two strings for sorting (case insensitive).
        ; Returns -1 (255), 0 or 1, meaning: string1 sorts before, equal or after string2.
        ; Only compares the strings from index 0 up to the length argument.
        %ir {{
            loadm.w r99000,strings.ncompare_nocase.st1
            loadm.w r99001,strings.ncompare_nocase.st2
            loadm.b r99100,strings.ncompare_nocase.length
            syscall 64 (r99000.w, r99001.w, r99100.b) : r99100.b
            returnr.b r99100
        }}
    }

    sub lower(str st) -> ubyte {
        ; Lowercases the ISO string in-place. Returns length of the string.
        ; (for efficiency, non-letter characters > 128 will also not be left intact,
        ;  but regular text doesn't usually contain those characters anyway.)
        ubyte ix
        repeat {
            ubyte char=st[ix]
            if char==0
                return ix
            if char >= 'A' and char <= 'Z'
                st[ix] = char | %00100000
            ix++
        }
    }

    sub upper(str st) -> ubyte {
        ; Uppercases the ISO string in-place. Returns length of the string.
        ubyte ix
        repeat {
            ubyte char=st[ix]
            if char==0
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

    alias lowerchar_iso = strings.lowerchar
    alias upperchar_iso = strings.upperchar
    alias lower_iso = strings.lower
    alias upper_iso = strings.upper
    alias compare_nocase_iso = strings.compare_nocase
    alias ncompare_nocase_iso = strings.ncompare_nocase

    sub hash(str st) -> ubyte {
        ; experimental 8 bit hashing function.
        ; hash(-1)=179;  hash(i) = ROL hash(i-1)  XOR  string[i]
        ; On the English word list in /usr/share/dict/words it seems to have a pretty even distribution
        ubyte hashcode = 179
        ubyte ix
        sys.clear_carry()
        repeat {
            if st[ix]!=0 {
                rol(hashcode)
                hashcode ^= st[ix]
                ix++
            } else
                return hashcode
        }
    }

    sub isdigit(ubyte character) -> bool {
        return character>='0' and character<='9'
    }

    sub isupper(ubyte character) -> bool {
        return character>='A' and character<='Z'
    }

    sub islower(ubyte character) -> bool {
        return character>='a' and character<='z'
    }

    sub isletter(ubyte character) -> bool {
        return islower(character) or isupper(character)
    }

    sub isspace(ubyte character) -> bool {
        return character in [32, 13, 9, 10, 141, 160]
    }

    sub isprint(ubyte character) -> bool {
        return character>=32 and character<=127 or character>=160
    }
}
