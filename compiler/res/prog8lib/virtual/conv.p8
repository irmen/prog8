; Number conversions routines.

conv {

; ----- number conversions to decimal strings ----

    %option ignore_unused

    str  string_out = "????????????????"       ; result buffer for the string conversion routines

sub  str_ub0(ubyte value) -> str {
    ; ---- convert the ubyte in A in decimal string form, with left padding 0s (3 positions total)
    ubyte hundreds = value / 100
    value -= hundreds*100
    ubyte tens = value / 10
    value -= tens*10
    string_out[0] = hundreds+'0'
    string_out[1] = tens+'0'
    string_out[2] = value+'0'
    string_out[3] = 0
    return string_out
}

sub  str_ub(ubyte value) -> str {
    ; ---- convert the ubyte in A in decimal string form, without left padding 0s
    internal_str_ub(value, string_out)
    return string_out
}

sub  str_b(byte value) -> str {
    ; ---- convert the byte in A in decimal string form, without left padding 0s
    ^^ubyte out_ptr = &string_out
    if value<0 {
        @(out_ptr) = '-'
        out_ptr++
        value = -value
    }
    internal_str_ub(value as ubyte, out_ptr)
    return string_out
}

sub internal_str_ub(ubyte value, str out_ptr) {
    ubyte hundreds = value / 100
    value -= hundreds*100
    ubyte tens = value / 10
    value -= tens*10
    if hundreds!=0
        goto output_hundreds
    if tens!=0
        goto output_tens
    goto output_ones
output_hundreds:
    @(out_ptr) = hundreds+'0'
    out_ptr++
output_tens:
    @(out_ptr) = tens+'0'
    out_ptr++
output_ones:
    @(out_ptr) = value+'0'
    out_ptr++
    @(out_ptr) = 0
}

str hex_digits = "0123456789abcdef"

sub  str_ubhex  (ubyte value) -> str {
    ; ---- convert the ubyte in A in hex string form
    string_out[0] = hex_digits[value>>4]
    string_out[1] = hex_digits[value&15]
    string_out[2] = 0
    return string_out
}

sub  str_ubbin  (ubyte value) -> str {
    ; ---- convert the ubyte in A in binary string form
    ^^ubyte out_ptr = &string_out
    repeat 8 {
        rol(value)
        if_cc
            @(out_ptr) = '0'
        else
            @(out_ptr) = '1'
        out_ptr++
    }
    @(out_ptr) = 0
    return string_out
}

sub  str_uwbin  (uword value) -> str {
    ; ---- convert the uword in A/Y in binary string form
    ^^ubyte out_ptr = &string_out
    repeat 16 {
        rol(value)
        if_cc
            @(out_ptr) = '0'
        else
            @(out_ptr) = '1'
        out_ptr++
    }
    @(out_ptr) = 0
    return string_out
}

sub  str_uwhex  (uword value) -> str {
    ; ---- convert the uword in A/Y in hexadecimal string form (4 digits)
    ubyte bits = msb(value)
    string_out[0] = hex_digits[bits>>4]
    string_out[1] = hex_digits[bits&15]
    bits = lsb(value)
    string_out[2] = hex_digits[bits>>4]
    string_out[3] = hex_digits[bits&15]
    string_out[4] = 0
    return string_out
}

sub  str_uw0  (uword value) -> str {
    ; ---- convert the uword in A/Y in decimal string form, with left padding 0s (5 positions total)
    uword value2 = value/10
    ubyte digits = value-value2*10 as ubyte
    uword value3 = value2/10
    ubyte tens = value2-value3*10 as ubyte
    uword value4 = value3/10
    ubyte hundreds = value3-value4*10 as ubyte
    uword value5 = value4/10
    ubyte thousands = value4-value5*10 as ubyte
    uword value6 = value5/10
    ubyte tenthousands = value5-value6*10 as ubyte
    string_out[0] = tenthousands+'0'
    string_out[1] = thousands+'0'
    string_out[2] = hundreds+'0'
    string_out[3] = tens+'0'
    string_out[4] = digits+'0'
    string_out[5] = 0
    return string_out
}

sub  str_uw  (uword value) -> str {
    ; ---- convert the uword in A/Y in decimal string form, without left padding 0s
    internal_str_uw(value, string_out)
    return string_out
}

sub  str_w  (word value) -> str {
    ; ---- convert the (signed) word into decimal string form, without left padding 0's
    ^^ubyte out_ptr = &string_out
    if value<0 {
        @(out_ptr) = '-'
        out_ptr++
        value = -value
    }
    internal_str_uw(value as uword, out_ptr)
    return string_out
}

sub  str_l  (long value) -> str {
    ; ---- convert the (signed) long into decimal string form, without left padding 0's
    %ir {{
        loadm.l r99200,conv.str_l.value
        load.w r99000,conv.string_out
        syscall 60 (r99200.l, r99000.w) : r99000.w
        returnr.w r99000
    }}
}

sub internal_str_uw(uword value, str out_ptr) {
    uword value2 = value/10
    ubyte digits = value-value2*10 as ubyte
    uword value3 = value2/10
    ubyte tens = value2-value3*10 as ubyte
    uword value4 = value3/10
    ubyte hundreds = value3-value4*10 as ubyte
    uword value5 = value4/10
    ubyte thousands = value4-value5*10 as ubyte
    uword value6 = value5/10
    ubyte tenthousands = value5-value6*10 as ubyte
    if tenthousands!=0
        goto output_tenthousands
    if thousands!=0
        goto output_thousands
    if hundreds!=0
        goto output_hundreds
    if tens!=0
        goto output_tens
    goto output_ones
output_tenthousands:
    @(out_ptr) = tenthousands+'0'
    out_ptr++
output_thousands:
    @(out_ptr) = thousands+'0'
    out_ptr++
output_hundreds:
    @(out_ptr) = hundreds+'0'
    out_ptr++
output_tens:
    @(out_ptr) = tens+'0'
    out_ptr++
output_ones:
    @(out_ptr) = digits+'0'
    out_ptr++
    @(out_ptr) = 0
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
    ; -- returns the unsigned word value of the string number argument in AY
    ;    the number may NOT be preceded by a + sign and may NOT contain spaces
    ;    (any non-digit character will terminate the number string that is parsed)
    %ir {{
        loadm.w r99000,conv.str2uword.string
        syscall 11 (r99000.w) : r99000.w
        returnr.w r99000
    }}
}

sub  str2word(str string) -> word {
    ; -- returns the signed word value of the string number argument in AY
    ;    the number may be preceded by a + or - sign but may NOT contain spaces
    ;    (any non-digit character will terminate the number string that is parsed)
    %ir {{
        loadm.w r99000,conv.str2word.string
        syscall 12 (r99000.w) : r99000.w
        returnr.w r99000
    }}
}

sub  hex2uword(str string) -> uword {
    ; -- hexadecimal string (with or without '$') to uword.
    ;    stops parsing at the first character that's not a hex digit (except leading $)
    uword result
    ubyte char
    if @(string)=='$'
        string++
    repeat {
        char = @(string)
        if char==0
            return result
        result <<= 4
        if char>='0' and char<='9'
            result |= char-'0'
        else
            result |= char-'a'+10
        string++
    }
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
        if char==0
            return result
        result <<= 1
        if char=='1'
            result |= 1
        string++
    }
}

sub  any2uword(str string) -> uword, ubyte {
    ; -- convert any number string (any prefix allowed) to uword.
    ;    returns the parsed word value, and the number of processed characters (including the prefix symbol)
    ubyte length
    while string[length]!=0 length++
    when string[0] {
        '$' -> return hex2uword(string), length
        '%' -> return bin2uword(string), length
        else -> return str2uword(string), length
    }
}

}
