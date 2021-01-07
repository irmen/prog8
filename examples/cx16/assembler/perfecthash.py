
def in_word_set(string: str) -> bool:
    length = len(string)

    wordlist = [
      "PHP",
      "ROR",
      "STP",
      "RTS",
      "RTI",
      "TXS",
      "PHY",
      "TRB",
      "EOR",
      "STY",
      "PHX",
      "TSB",
      "TAY",
      "STX",
      "BRK",
      "LSR",
      "TAX",
      "TSX",
      "PHA",
      "PLP",
      "BRA",
      "STA",
      "ROL",
      "BCS",
      "SEI",
      "TXA",
      "LDY",
      "PLY",
      "INY",
      "LDX",
      "PLX",
      "NOP",
      "INX",
      "CLI",
      "ASL",
      "SBC",
      "BMI",
      "LDA",
      "PLA",
      "ORA",
      "BNE",
      "ADC",
      "BBS7",
      "BBR7",
      "CMP",
      "CPY",
      "INC",
      "SEC",
      "BCC",
      "CPX",
      "BPL",
      "DEY",
      "TYA",
      "CLV",
      "DEX",
      "CLC",
      "BBS6",
      "BBR6",
      "BBS5",
      "BBR5",
      "SMB7",
      "RMB7",
      "STZ",
      "SED",
      "BBS4",
      "BBR4",
      "DEC",
      "BBS3",
      "BBR3",
      "BBS2",
      "BBR2",
      "AND",
      "CLD",
      "BBS1",
      "BBR1",
      "BEQ",
      "SMB6",
      "RMB6",
      "SMB5",
      "RMB5",
      "BBS0",
      "BBR0",
      "BVS",
      "WAI",
      "SMB4",
      "RMB4",
      "JSR",
      "SMB3",
      "RMB3",
      "SMB2",
      "RMB2",
      "BIT",
      "SMB1",
      "RMB1",
      "SMB0",
      "RMB0",
      "BVC",
      "JMP"
    ]

    lookup = [
      -1,  0, -1,  1,  2,  3, -1,  4,  5,  6,  7,  8,  9, 10,
      11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
      25, 26, 27, -1, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
      38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, -1, 50,
      51, 52, -1, 53, 54, 55, -1, 56, 57, 58, 59, -1, 60, 61,
      62, 63, 64, 65, 66, 67, 68, 69, 70, 71, -1, 72, 73, 74,
      75, 76, 77, 78, 79, 80, 81, 82, -1, 83, 84, 85, 86, 87,
      88, 89, 90, 91, -1, -1, 92, 93, -1, -1, -1, -1, -1, 94,
      95, -1, -1, -1, -1, 96, -1, -1, -1, -1, -1, -1, -1, 97
    ]

    asso_values = [
        126, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        126, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        126, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        126, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        126, 126, 126, 126, 126, 126, 126, 126,  73,  66,
        61,  59,  56,  49,  47,  30, 126, 126, 126, 126,
        126, 126, 126, 126, 126,  10,   3,  13,  23,   9,
        25, 126, 126,   1,  90,   7,  12,  22,  35,  23,
        0,  28,   1,   0,   4,   4,  12,  88,   6,   4,
        33, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        126, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        126, 126, 126, 126, 126, 126, 126, 126, 126, 126,
        126, 126, 126, 126, 126, 126, 126, 126, 126
    ]

    print(len(lookup))
    print(len(asso_values))

    def hash(string: str, length: len) -> int:
        return asso_values[ord(string[2])] + \
               asso_values[ord(string[1])+1] + \
               asso_values[ord(string[0])] + \
               asso_values[ord(string[length - 1])]

    MAX_HASH_VALUE = 125

    if 3<=length<=4:
        key = hash(string, length)
        if key <= MAX_HASH_VALUE:
            index = lookup[key]
            if index>=0:
                word = wordlist[index]
                return word==string
    return False
