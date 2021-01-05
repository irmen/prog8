all: perfecthash.c opcodes.asm

perfecthash.c:  gen_opcodes.py
	python gen_opcodes.py --mnemlist | gperf --no-strlen --null-strings -7 -C -D -E -m 100 > perfecthash.c

opcodes.asm:  gen_opcodes.py
	python gen_opcodes.py --parser > opcodes.asm

