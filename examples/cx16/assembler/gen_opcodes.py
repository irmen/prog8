import sys
from collections import Counter
from enum import IntEnum


class AddrMode(IntEnum):
    Imp = 1,
    Acc = 2,
    Imm = 3,
    Zp = 4,
    ZpX = 5,
    ZpY = 6,
    Rel = 7,
    Abs = 8,
    AbsX = 9,
    AbsY = 10,
    Ind = 11,
    IzX = 12,
    IzY = 13,
    Zpr = 14,
    Izp = 15,
    IaX = 16


AllInstructions = [
    (0x00, "brk", AddrMode.Imp),
    (0x01, "ora", AddrMode.IzX),
    (0x02, "nop", AddrMode.Imm),
    (0x03, "nop", AddrMode.Imp),
    (0x04, "tsb", AddrMode.Zp),
    (0x05, "ora", AddrMode.Zp),
    (0x06, "asl", AddrMode.Zp),
    (0x07, "rmb0", AddrMode.Zp),
    (0x08, "php", AddrMode.Imp),
    (0x09, "ora", AddrMode.Imm),
    (0x0a, "asl", AddrMode.Acc),
    (0x0b, "nop", AddrMode.Imp),
    (0x0c, "tsb", AddrMode.Abs),
    (0x0d, "ora", AddrMode.Abs),
    (0x0e, "asl", AddrMode.Abs),
    (0x0f, "bbr0", AddrMode.Zpr),
    (0x10, "bpl", AddrMode.Rel),
    (0x11, "ora", AddrMode.IzY),
    (0x12, "ora", AddrMode.Izp),
    (0x13, "nop", AddrMode.Imp),
    (0x14, "trb", AddrMode.Zp),
    (0x15, "ora", AddrMode.ZpX),
    (0x16, "asl", AddrMode.ZpX),
    (0x17, "rmb1", AddrMode.Zp),
    (0x18, "clc", AddrMode.Imp),
    (0x19, "ora", AddrMode.AbsY),
    (0x1a, "inc", AddrMode.Acc),
    (0x1b, "nop", AddrMode.Imp),
    (0x1c, "trb", AddrMode.Abs),
    (0x1d, "ora", AddrMode.AbsX),
    (0x1e, "asl", AddrMode.AbsX),
    (0x1f, "bbr1", AddrMode.Zpr),
    (0x20, "jsr", AddrMode.Abs),
    (0x21, "and", AddrMode.IzX),
    (0x22, "nop", AddrMode.Imm),
    (0x23, "nop", AddrMode.Imp),
    (0x24, "bit", AddrMode.Zp),
    (0x25, "and", AddrMode.Zp),
    (0x26, "rol", AddrMode.Zp),
    (0x27, "rmb2", AddrMode.Zp),
    (0x28, "plp", AddrMode.Imp),
    (0x29, "and", AddrMode.Imm),
    (0x2a, "rol", AddrMode.Acc),
    (0x2b, "nop", AddrMode.Imp),
    (0x2c, "bit", AddrMode.Abs),
    (0x2d, "and", AddrMode.Abs),
    (0x2e, "rol", AddrMode.Abs),
    (0x2f, "bbr2", AddrMode.Zpr),
    (0x30, "bmi", AddrMode.Rel),
    (0x31, "and", AddrMode.IzY),
    (0x32, "and", AddrMode.Izp),
    (0x33, "nop", AddrMode.Imp),
    (0x34, "bit", AddrMode.ZpX),
    (0x35, "and", AddrMode.ZpX),
    (0x36, "rol", AddrMode.ZpX),
    (0x37, "rmb3", AddrMode.Zp),
    (0x38, "sec", AddrMode.Imp),
    (0x39, "and", AddrMode.AbsY),
    (0x3a, "dec", AddrMode.Acc),
    (0x3b, "nop", AddrMode.Imp),
    (0x3c, "bit", AddrMode.AbsX),
    (0x3d, "and", AddrMode.AbsX),
    (0x3e, "rol", AddrMode.AbsX),
    (0x3f, "bbr3", AddrMode.Zpr),
    (0x40, "rti", AddrMode.Imp),
    (0x41, "eor", AddrMode.IzX),
    (0x42, "nop", AddrMode.Imm),
    (0x43, "nop", AddrMode.Imp),
    (0x44, "nop", AddrMode.Zp),
    (0x45, "eor", AddrMode.Zp),
    (0x46, "lsr", AddrMode.Zp),
    (0x47, "rmb4", AddrMode.Zp),
    (0x48, "pha", AddrMode.Imp),
    (0x49, "eor", AddrMode.Imm),
    (0x4a, "lsr", AddrMode.Acc),
    (0x4b, "nop", AddrMode.Imp),
    (0x4c, "jmp", AddrMode.Abs),
    (0x4d, "eor", AddrMode.Abs),
    (0x4e, "lsr", AddrMode.Abs),
    (0x4f, "bbr4", AddrMode.Zpr),
    (0x50, "bvc", AddrMode.Rel),
    (0x51, "eor", AddrMode.IzY),
    (0x52, "eor", AddrMode.Izp),
    (0x53, "nop", AddrMode.Imp),
    (0x54, "nop", AddrMode.ZpX),
    (0x55, "eor", AddrMode.ZpX),
    (0x56, "lsr", AddrMode.ZpX),
    (0x57, "rmb5", AddrMode.Zp),
    (0x58, "cli", AddrMode.Imp),
    (0x59, "eor", AddrMode.AbsY),
    (0x5a, "phy", AddrMode.Imp),
    (0x5b, "nop", AddrMode.Imp),
    (0x5c, "nop", AddrMode.Abs),
    (0x5d, "eor", AddrMode.AbsX),
    (0x5e, "lsr", AddrMode.AbsX),
    (0x5f, "bbr5", AddrMode.Zpr),
    (0x60, "rts", AddrMode.Imp),
    (0x61, "adc", AddrMode.IzX),
    (0x62, "nop", AddrMode.Imm),
    (0x63, "nop", AddrMode.Imp),
    (0x64, "stz", AddrMode.Zp),
    (0x65, "adc", AddrMode.Zp),
    (0x66, "ror", AddrMode.Zp),
    (0x67, "rmb6", AddrMode.Zp),
    (0x68, "pla", AddrMode.Imp),
    (0x69, "adc", AddrMode.Imm),
    (0x6a, "ror", AddrMode.Acc),
    (0x6b, "nop", AddrMode.Imp),
    (0x6c, "jmp", AddrMode.Ind),
    (0x6d, "adc", AddrMode.Abs),
    (0x6e, "ror", AddrMode.Abs),
    (0x6f, "bbr6", AddrMode.Zpr),
    (0x70, "bvs", AddrMode.Rel),
    (0x71, "adc", AddrMode.IzY),
    (0x72, "adc", AddrMode.Izp),
    (0x73, "nop", AddrMode.Imp),
    (0x74, "stz", AddrMode.ZpX),
    (0x75, "adc", AddrMode.ZpX),
    (0x76, "ror", AddrMode.ZpX),
    (0x77, "rmb7", AddrMode.Zp),
    (0x78, "sei", AddrMode.Imp),
    (0x79, "adc", AddrMode.AbsY),
    (0x7a, "ply", AddrMode.Imp),
    (0x7b, "nop", AddrMode.Imp),
    (0x7c, "jmp", AddrMode.IaX),
    (0x7d, "adc", AddrMode.AbsX),
    (0x7e, "ror", AddrMode.AbsX),
    (0x7f, "bbr7", AddrMode.Zpr),
    (0x80, "bra", AddrMode.Rel),
    (0x81, "sta", AddrMode.IzX),
    (0x82, "nop", AddrMode.Imm),
    (0x83, "nop", AddrMode.Imp),
    (0x84, "sty", AddrMode.Zp),
    (0x85, "sta", AddrMode.Zp),
    (0x86, "stx", AddrMode.Zp),
    (0x87, "smb0", AddrMode.Zp),
    (0x88, "dey", AddrMode.Imp),
    (0x89, "bit", AddrMode.Imm),
    (0x8a, "txa", AddrMode.Imp),
    (0x8b, "nop", AddrMode.Imp),
    (0x8c, "sty", AddrMode.Abs),
    (0x8d, "sta", AddrMode.Abs),
    (0x8e, "stx", AddrMode.Abs),
    (0x8f, "bbs0", AddrMode.Zpr),
    (0x90, "bcc", AddrMode.Rel),
    (0x91, "sta", AddrMode.IzY),
    (0x92, "sta", AddrMode.Izp),
    (0x93, "nop", AddrMode.Imp),
    (0x94, "sty", AddrMode.ZpX),
    (0x95, "sta", AddrMode.ZpX),
    (0x96, "stx", AddrMode.ZpY),
    (0x97, "smb1", AddrMode.Zp),
    (0x98, "tya", AddrMode.Imp),
    (0x99, "sta", AddrMode.AbsY),
    (0x9a, "txs", AddrMode.Imp),
    (0x9b, "nop", AddrMode.Imp),
    (0x9c, "stz", AddrMode.Abs),
    (0x9d, "sta", AddrMode.AbsX),
    (0x9e, "stz", AddrMode.AbsX),
    (0x9f, "bbs1", AddrMode.Zpr),
    (0xa0, "ldy", AddrMode.Imm),
    (0xa1, "lda", AddrMode.IzX),
    (0xa2, "ldx", AddrMode.Imm),
    (0xa3, "nop", AddrMode.Imp),
    (0xa4, "ldy", AddrMode.Zp),
    (0xa5, "lda", AddrMode.Zp),
    (0xa6, "ldx", AddrMode.Zp),
    (0xa7, "smb2", AddrMode.Zp),
    (0xa8, "tay", AddrMode.Imp),
    (0xa9, "lda", AddrMode.Imm),
    (0xaa, "tax", AddrMode.Imp),
    (0xab, "nop", AddrMode.Imp),
    (0xac, "ldy", AddrMode.Abs),
    (0xad, "lda", AddrMode.Abs),
    (0xae, "ldx", AddrMode.Abs),
    (0xaf, "bbs2", AddrMode.Zpr),
    (0xb0, "bcs", AddrMode.Rel),
    (0xb1, "lda", AddrMode.IzY),
    (0xb2, "lda", AddrMode.Izp),
    (0xb3, "nop", AddrMode.Imp),
    (0xb4, "ldy", AddrMode.ZpX),
    (0xb5, "lda", AddrMode.ZpX),
    (0xb6, "ldx", AddrMode.ZpY),
    (0xb7, "smb3", AddrMode.Zp),
    (0xb8, "clv", AddrMode.Imp),
    (0xb9, "lda", AddrMode.AbsY),
    (0xba, "tsx", AddrMode.Imp),
    (0xbb, "nop", AddrMode.Imp),
    (0xbc, "ldy", AddrMode.AbsX),
    (0xbd, "lda", AddrMode.AbsX),
    (0xbe, "ldx", AddrMode.AbsY),
    (0xbf, "bbs3", AddrMode.Zpr),
    (0xc0, "cpy", AddrMode.Imm),
    (0xc1, "cmp", AddrMode.IzX),
    (0xc2, "nop", AddrMode.Imm),
    (0xc3, "nop", AddrMode.Imp),
    (0xc4, "cpy", AddrMode.Zp),
    (0xc5, "cmp", AddrMode.Zp),
    (0xc6, "dec", AddrMode.Zp),
    (0xc7, "smb4", AddrMode.Zp),
    (0xc8, "iny", AddrMode.Imp),
    (0xc9, "cmp", AddrMode.Imm),
    (0xca, "dex", AddrMode.Imp),
    (0xcb, "wai", AddrMode.Imp),
    (0xcc, "cpy", AddrMode.Abs),
    (0xcd, "cmp", AddrMode.Abs),
    (0xce, "dec", AddrMode.Abs),
    (0xcf, "bbs4", AddrMode.Zpr),
    (0xd0, "bne", AddrMode.Rel),
    (0xd1, "cmp", AddrMode.IzY),
    (0xd2, "cmp", AddrMode.Izp),
    (0xd3, "nop", AddrMode.Imp),
    (0xd4, "nop", AddrMode.ZpX),
    (0xd5, "cmp", AddrMode.ZpX),
    (0xd6, "dec", AddrMode.ZpX),
    (0xd7, "smb5", AddrMode.Zp),
    (0xd8, "cld", AddrMode.Imp),
    (0xd9, "cmp", AddrMode.AbsY),
    (0xda, "phx", AddrMode.Imp),
    (0xdb, "stp", AddrMode.Imp),
    (0xdc, "nop", AddrMode.Abs),
    (0xdd, "cmp", AddrMode.AbsX),
    (0xde, "dec", AddrMode.AbsX),
    (0xdf, "bbs5", AddrMode.Zpr),
    (0xe0, "cpx", AddrMode.Imm),
    (0xe1, "sbc", AddrMode.IzX),
    (0xe2, "nop", AddrMode.Imm),
    (0xe3, "nop", AddrMode.Imp),
    (0xe4, "cpx", AddrMode.Zp),
    (0xe5, "sbc", AddrMode.Zp),
    (0xe6, "inc", AddrMode.Zp),
    (0xe7, "smb6", AddrMode.Zp),
    (0xe8, "inx", AddrMode.Imp),
    (0xe9, "sbc", AddrMode.Imm),
    (0xea, "nop", AddrMode.Imp),
    (0xeb, "nop", AddrMode.Imp),
    (0xec, "cpx", AddrMode.Abs),
    (0xed, "sbc", AddrMode.Abs),
    (0xee, "inc", AddrMode.Abs),
    (0xef, "bbs6", AddrMode.Zpr),
    (0xf0, "beq", AddrMode.Rel),
    (0xf1, "sbc", AddrMode.IzY),
    (0xf2, "sbc", AddrMode.Izp),
    (0xf3, "nop", AddrMode.Imp),
    (0xf4, "nop", AddrMode.ZpX),
    (0xf5, "sbc", AddrMode.ZpX),
    (0xf6, "inc", AddrMode.ZpX),
    (0xf7, "smb7", AddrMode.Zp),
    (0xf8, "sed", AddrMode.Imp),
    (0xf9, "sbc", AddrMode.AbsY),
    (0xfa, "plx", AddrMode.Imp),
    (0xfb, "nop", AddrMode.Imp),
    (0xfc, "nop", AddrMode.AbsX),
    (0xfd, "sbc", AddrMode.AbsX),
    (0xfe, "inc", AddrMode.AbsX),
    (0xff, "bbs7", AddrMode.Zpr)
]

# NOP is weird, it is all over the place.
# For the 'common' immediate NOP, keep only the $EA opcode (this was the original NOP on the 6502)
Instructions = [ins for ins in AllInstructions if ins[1] != "nop"] + [(0xea, "nop", AddrMode.Imp)]


InstructionsByName = {}
for ins in Instructions:
    if ins[1] not in InstructionsByName:
        InstructionsByName[ins[1]] = {ins[2]: ins[0]}
    else:
        InstructionsByName[ins[1]][ins[2]] = ins[0]

InstructionsByMode = {}
for ins in Instructions:
    if ins[2] not in InstructionsByMode:
        InstructionsByMode[ins[2]] = [(ins[1], ins[0])]
    else:
        InstructionsByMode[ins[2]].append((ins[1], ins[0]))


def generate_mnemonics_parser():
    print("; generated by opcodes.py")
    print("; addressing modes:")
    for mode in AddrMode:
        print(";", mode.value, "=", mode.name)
    print()

    print("""
        .enc "petscii"  ;define an ascii to petscii encoding
        .cdef " @", 32  ;characters
        .cdef "AZ", $c1
        .cdef "az", $41
        .cdef "[[", $5b
        .cdef "]]", $5d
        .edef "<nothing>", [];replace with no bytes
    """)

    for instr in sorted(InstructionsByName.items()):
        print("i_" + instr[0] + ":\n\t.byte  ", end="")
        if len(instr[1]) == 1:
            # many instructions have just 1 addressing mode, save space for those
            info = instr[1].popitem()
            print("1,", info[0].value,",", info[1])
        else:
            print("0, ", end='')
            mode_opcodes = []
            for mode in AddrMode:
                if mode in instr[1]:
                    mode_opcodes.append(instr[1][mode])
                else:
                    mode_opcodes.append(0)
            print(",".join(str(o) for o in mode_opcodes), end="")
            print()

    def determine_mnemonics():
        mnemonics = list(sorted(set(ins[1] for ins in Instructions)))

        # opcodes histogram (ordered by occurrence)  (in kernal + basic roms of the c64):
        opcode_occurrences = [
            (32, 839), (133, 502), (165, 488), (0, 429), (208, 426), (169, 390), (76, 324), (240, 322), (2, 314), (160, 245),
            (96, 228), (3, 201), (1, 191), (255, 186), (144, 182), (170, 175), (162, 169), (177, 165), (104, 159), (164, 158),
            (132, 157), (201, 156), (72, 151), (141, 150), (200, 146), (173, 144), (166, 139), (176, 139), (16, 138),
            (134, 138), (73, 127), (24, 119), (101, 113), (69, 109), (13, 107), (34, 104), (145, 103), (4, 102), (168, 101),
            (221, 98), (230, 93), (48, 91), (189, 87), (41, 86), (6, 86), (9, 86), (8, 85), (79, 85), (138, 80), (10, 80),
            (7, 79), (185, 77), (56, 75), (44, 75), (78, 74), (105, 73), (5, 73), (174, 73), (220, 71), (198, 69), (232, 69),
            (36, 69), (202, 67), (152, 67), (95, 67), (100, 65), (102, 65), (247, 65), (188, 64), (136, 64), (84, 64),
            (122, 62), (128, 61), (80, 61), (186, 60), (82, 59), (97, 58), (15, 57), (70, 57), (229, 56), (19, 55), (40, 54),
            (183, 54), (65, 54), (233, 53), (180, 53), (12, 53), (171, 53), (197, 53), (83, 52), (248, 52), (112, 51),
            (237, 51), (89, 50), (11, 50), (158, 50), (74, 49), (224, 48), (20, 47), (238, 47), (108, 46), (234, 46),
            (251, 46), (254, 46), (184, 45), (14, 44), (163, 44), (226, 43), (211, 43), (88, 43), (98, 42), (17, 42),
            (153, 42), (243, 41), (228, 41), (99, 41), (253, 41), (209, 41), (187, 39), (123, 39), (67, 39), (196, 38),
            (68, 38), (35, 38), (172, 38), (175, 38), (161, 38), (85, 38), (191, 37), (113, 37), (182, 37), (151, 37),
            (71, 36), (181, 35), (214, 35), (121, 35), (157, 35), (178, 35), (77, 35), (42, 34), (212, 33), (18, 33),
            (127, 33), (241, 33), (21, 33), (249, 32), (23, 31), (245, 30), (142, 30), (55, 29), (140, 29), (46, 29),
            (192, 29), (179, 29), (252, 29), (115, 29), (22, 29), (43, 28), (215, 28), (45, 28), (246, 28), (38, 28),
            (86, 27), (225, 27), (25, 26), (239, 26), (58, 26), (167, 26), (147, 26), (217, 26), (149, 25), (30, 25),
            (206, 25), (28, 24), (47, 24), (37, 24), (155, 24), (129, 23), (148, 23), (111, 23), (29, 23), (39, 23),
            (51, 22), (193, 22), (236, 22), (120, 22), (64, 22), (204, 21), (210, 21), (244, 21), (52, 21), (66, 21),
            (114, 20), (250, 20), (106, 20), (93, 19), (199, 19), (218, 19), (154, 19), (205, 19), (50, 19), (159, 19),
            (194, 19), (49, 19), (190, 19), (103, 18), (216, 18), (213, 18), (107, 18), (131, 18), (63, 18), (94, 18),
            (91, 17), (242, 17), (109, 17), (53, 16), (227, 16), (139, 16), (31, 16), (75, 16), (60, 16), (195, 15),
            (231, 15), (62, 15), (59, 15), (87, 14), (207, 14), (27, 14), (90, 14), (110, 13), (223, 13), (57, 13),
            (118, 12), (26, 12), (203, 12), (81, 12), (156, 12), (54, 12), (235, 12), (146, 11), (135, 11), (126, 11),
            (150, 11), (130, 11), (143, 10), (61, 10), (219, 10), (124, 9), (222, 9), (125, 9), (119, 7), (137, 7),
            (33, 7), (117, 5), (92, 4), (116, 3)
        ]

        cnt = Counter()
        for opcode, amount in opcode_occurrences:
            cnt[AllInstructions[opcode][1]] += amount
        cnt["nop"] = 13
        cnt["tsb"] = 13

        four_letter_mnemonics = list(sorted([ins[1] for ins in AllInstructions if len(ins[1])>3]))
        for ins4 in four_letter_mnemonics:
            del cnt[ins4]
            cnt[ins4] = 1
        mnem2 = [c[0] for c in cnt.most_common()]
        if len(mnem2)!=len(mnemonics):
            raise ValueError("mnem count mismatch")
        return mnem2

    mnemonics = determine_mnemonics()

    def first_letters():
        firstletters = {m[0]: 0 for m in mnemonics}
        return firstletters.keys()

    def second_letters(firstletter):
        secondletters = {m[1]: 0 for m in mnemonics if m[0] == firstletter}
        return secondletters.keys()

    def third_letters(firstletter, secondletter):
        thirdletters = {m[2]: 0 for m in mnemonics if m[0] == firstletter and m[1] == secondletter}
        return thirdletters.keys()

    def fourth_letters(firstletter, secondletter, thirdletter):
        longmnem = [m for m in mnemonics if len(m) > 3]
        fourthletters = {m[3]: 0 for m in longmnem if m[0] == firstletter and m[1] == secondletter and m[2] == thirdletter}
        return fourthletters.keys()

    def make_tree():
        tree = {}
        for first in first_letters():
            tree[first] = {
                secondletter: {
                    thirdletter: {
                        fourthletter: {}
                        for fourthletter in fourth_letters(first, secondletter, thirdletter)
                    }
                    for thirdletter in third_letters(first, secondletter)
                }
                for secondletter in second_letters(first)
            }
        return tree

    tree = make_tree()

    print("get_opcode_info    .proc")
    print("_mnem_fourth_letter = cx16.r4")
    print("_mnem_fifth_letter = cx16.r5")
    for first in tree:
        print("    cmp  #'%s'" % first)
        print("    bne  _not_%s" % first)
        for second in tree[first]:
            print("    cpx  #'%s'" % second)
            print("    bne  _not_%s%s" % (first,second))
            for third in tree[first][second]:
                print("    cpy  #'%s'" % third)
                print("    bne  _not_%s%s%s" % (first, second, third))
                fourth = tree[first][second][third]
                if fourth:
                    if "".join(fourth.keys()) != "01234567":
                        raise ValueError("fourth", fourth.keys())
                    print("    bra  _check_%s%s%s" % (first, second, third))
                else:
                    print("    lda  _mnem_fourth_letter")   # check that the fourth letter is not present
                    print("    bne  _invalid")
                    print("    lda  #<i_%s%s%s" % (first, second, third))
                    print("    ldy  #>i_%s%s%s" % (first, second, third))
                    print("    rts")
                print("_not_%s%s%s:" % (first, second, third))
            print("_not_%s%s:" % (first, second))
        print("_not_%s:" % first)
    print("_invalid:")
    print("    lda  #0")
    print("    ldy  #0")
    print("    rts")

    # the 4-letter mnemonics are:
    # smb[0-7]
    # bbr[0-7]
    # rmb[0-7]
    # bbs[0-7]
    for fourlettermnemonic in ["smb", "bbr", "rmb", "bbs"]:
        print("_check_%s" % fourlettermnemonic)
        print("    lda  #<_tab_%s" % fourlettermnemonic)
        print("    ldy  #>_tab_%s" % fourlettermnemonic)
        print("""    sta  P8ZP_SCRATCH_W2
        sty  P8ZP_SCRATCH_W2+1
        bra  _check4""")

    print("""_check4
        lda  _mnem_fourth_letter
        cmp  #'0'
        bcc  _invalid
        cmp  #'8'
        bcs  _invalid
        lda  _mnem_fifth_letter     ; must have no fifth letter
        bne  _invalid
        lda  _mnem_fourth_letter
        sec
        sbc  #'0'
        asl  a
        tay
        lda  (P8ZP_SCRATCH_W2),y
        pha
        iny
        lda  (P8ZP_SCRATCH_W2),y
        tay
        pla
        rts""")

    for fourlettermnemonic in ["smb", "bbr", "rmb", "bbs"]:
        print("_tab_%s" % fourlettermnemonic)
        for ii in "01234567":
            print("    .word   i_%s%s" % (fourlettermnemonic, ii))

    print("    .pend")


def generate_mnem_list():
    for m in sorted(InstructionsByName):
        print(m.upper())


if __name__=="__main__":
    if sys.argv[1]=="--mnemlist":
        generate_mnem_list()
    elif sys.argv[1]=="--parser":
        generate_mnemonics_parser()
    else:
        print("invalid arg")
