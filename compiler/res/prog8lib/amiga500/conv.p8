; Number conversions routines.

%import strings

conv {

; ----- number conversions to decimal strings ----

    %option ignore_unused
    str  string_out = "????????????????"       ; result buffer for the string conversion routines
    private long @shared fmt_value_l
    private uword @shared fmt_value_w

    private asmsub putcharproc(ubyte character @D0, pointer output @A3) -> ubyte @D0 {
        %asm {{
            move.b  d0, (a3)+           ; Store character in buffer and advance pointer
            rts                         ; Return control to RawDoFmt loop
        }}
    }

sub  str_ub0(ubyte value) -> str {
    ; ---- convert the ubyte in A in decimal string form, with left padding 0s (3 positions total)
    fmt_value_w = value as uword
    void exec.RawDoFmt("%03d", &fmt_value_w, putcharproc, string_out)
    return string_out
}

sub  str_ub(ubyte value) -> str {
    ; ---- convert the ubyte in A in decimal string form, without left padding 0s
    fmt_value_w = value as uword
    void exec.RawDoFmt("%d", &fmt_value_w, putcharproc, string_out)
    return string_out
}

sub  str_b(byte value) -> str {
    ; ---- convert the byte in A in decimal string form, without left padding 0s
    fmt_value_w = value as uword
    void exec.RawDoFmt("%d", &fmt_value_w, putcharproc, string_out)
    return string_out
}

sub  str_ubhex  (ubyte value) -> str {
    ; ---- convert the ubyte in A in hex string form
    fmt_value_w = value as uword
    void exec.RawDoFmt("%02x", &fmt_value_w, putcharproc, string_out)
    return string_out
}

sub  str_ubbin  (ubyte value) -> str {
    ; ---- convert the ubyte in binary string form
    ^^ubyte out_ptr = &string_out
    ubyte mask = 128
    repeat 8 {
        if value & mask != 0
            @(out_ptr) = '1'
        else
            @(out_ptr) = '0'
        out_ptr++
        mask >>= 1
    }
    @(out_ptr) = 0
    return string_out
}

sub  str_uwbin  (uword value) -> str {
    ; ---- convert the uword in binary string form
    ^^ubyte out_ptr = &string_out
    uword mask = $8000
    repeat 16 {
        if value & mask != 0
            @(out_ptr) = '1'
        else
            @(out_ptr) = '0'
        out_ptr++
        mask >>= 1
    }
    @(out_ptr) = 0
    return string_out
}

sub  str_uwhex  (uword value) -> str {
    ; ---- convert the uword in hexadecimal string form (4 digits)
    void exec.RawDoFmt("%04x", &value, putcharproc, string_out)
    return string_out
}

sub  str_ulhex  (long value) -> str {
    ; ---- convert the long in hexadecimal string form (8 digits)
    void exec.RawDoFmt("%08lx", &value, putcharproc, string_out)
    return string_out
}

sub  str_uw0  (uword value) -> str {
    ; ---- convert the uword in decimal string form, with left padding 0s (5 positions total)
    ; requires kickstart 2.0+
    void exec.RawDoFmt("%05u", &value, putcharproc, string_out)
    return string_out
}

sub  str_uw  (uword value) -> str {
    ; ---- convert the uword in decimal string form, without left padding 0s
    ; requires kickstart 2.0+
    void exec.RawDoFmt("%u", &value, putcharproc, string_out)
    return string_out
}

sub  str_w  (word value) -> str {
    ; ---- convert the (signed) word into decimal string form, without left padding 0's
    void exec.RawDoFmt("%d", &value, putcharproc, string_out)
    return string_out
}

sub  str_l  (long value) -> str {
    ; ---- convert the (signed) long into decimal string form
    void exec.RawDoFmt("%ld", &value, putcharproc, string_out)
    return string_out
}


; ---- string conversion to numbers -----

sub  str2ubyte(str string) -> ubyte {
    ; -- returns in A the unsigned byte value of the string number argument in AY
    ;    the number may NOT be preceded by a + sign and may NOT contain spaces
    ;    (any non-digit character will terminate the number string that is parsed)
    return str2uword(string) as ubyte
}

sub  str2byte(str string) -> byte {
    ; -- returns in A the signed byte value of the string number argument in AY
    ;    the number may be preceded by a + or - sign but may NOT contain spaces
    ;    (any non-digit character will terminate the number string that is parsed)
    return str2word(string) as byte
}

sub  str2uword(str string) -> uword {
    uword result
    ubyte ii
    while string[ii] >= '0' and string[ii] <= '9' {
        result = result * 10 + (string[ii] - '0') as uword
        ii++
    }
    return result
}

sub  str2word(str string) -> word {
    bool negative
    ubyte ii
    if string[0] == '-' {
        negative = true
        ii = 1
    } else {
        if string[0] == '+'
            ii = 1
    }
    uword result
    while string[ii] >= '0' and string[ii] <= '9' {
        result = result * 10 + (string[ii] - '0') as uword
        ii++
    }
    if negative
        return 0 - result as word
    return result as word
}

sub  hex2uword(str string) -> uword {
    ; -- hexadecimal string (with or without '$') to uword.
    ;    stops parsing at the first character that's not a hex digit (except leading $)
    uword result
    ubyte char
    if @(string)=='$'
        string++
    repeat {
        char = strings.lowerchar(@(string))
        if strings.isxdigit(char) {
            result <<= 4
            if char>='0' and char<='9'
                result |= char-'0'
            else
                result |= char-'a'+10
        } else
            return result
        string++
    }
}

sub  hex2long(str string) -> long {
    ; -- hexadecimal string (with or without '$') to long.
    ;    stops parsing at the first character that's not a hex digit (except leading $)
    long result
    ubyte char
    if @(string)=='$'
        string++
    repeat {
        char = strings.lowerchar(@(string))
        if strings.isxdigit(char) {
            result <<= 4
            if char>='0' and char<='9'
                result |= char-'0'
            else
                result |= char-'a'+10
        } else
            return result
        string++
    }
}

sub  str2long(str string) -> long {
    ; -- convert a decimal string (terminated with a zero byte) into a long. Clobbers R0
    long result = 0
    bool negative = string[0] == '-'
    if negative or string[0]=='+'
        string++
    repeat {
        ubyte digit_char = @(string)
        if strings.isdigit(digit_char) {
            result = (result<<1) + (result<<3)  ; multiply by 10
            result += digit_char - '0'  ; add digit
        } else {
            break   ; Invalid character, stop processing
        }
        string++
    }

    if negative
        return -result
    else
        return result
}

sub  bin2uword(str string) -> uword {
    ; -- binary string (with or without '%') to uword.
    ;    stops parsing at the first character that's not a 0 or 1. (except leading %)
    uword result
    ubyte char
    if @(string)=='%'
        string++
    repeat {
        char = @(string)
        if char=='0' {
            result <<= 1
        } else if char=='1' {
            result <<= 1
            result |= 1
        } else
            return result
        string++
    }
}

sub  any2uword(str string) -> uword, ubyte {
    ; -- convert any number string (any prefix allowed) to uword.
    ;    returns the parsed word value, and the number of processed characters (including the prefix symbol)
    ubyte count
    when string[0] {
        '$' -> {
            count = 1
            while strings.isxdigit(string[count])
                count++
            return hex2uword(string), count
        }
        '%' -> {
            count = 1
            while string[count]=='0' or string[count]=='1'
                count++
            return bin2uword(string), count
        }
        else -> {
            count = 0
            while strings.isdigit(string[count])
                count++
            return str2uword(string), count
        }
    }
}

}
