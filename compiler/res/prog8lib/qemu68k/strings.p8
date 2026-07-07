%option ignore_unused

strings {
    sub isdigit(ubyte char) -> bool {
        return char >= '0' and char <= '9'
    }

    sub isxdigit(ubyte char) -> bool {
        return (char >= '0' and char <= '9') or (char >= 'a' and char <= 'f') or (char >= 'A' and char <= 'F')
    }

    sub lowerchar(ubyte char) -> ubyte {
        if char >= 'A' and char <= 'Z'
            return char + 32
        return char
    }


    ; TODO missing routines
}
