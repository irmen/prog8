package prog8.vm

fun main(args: Array<String>) {
    val memsrc = """
$4000 strz "Hello from program! "derp" bye.\n"
$2000 ubyte 65,66,67,68,0
$2100 uword $1111,$2222,$3333,$4444
"""
    val src = """
; enable lores gfx screen
load r0, 0
syscall 8   
load.w r10, 320
load.w r11, 240
load.b r12, 0

_forever:
load.w r1, 0
_yloop:
load.w r0, 0
_xloop:
mul.b r2,r0,r1
add.b r2,r2,r12
syscall 10 
addi.w r0,r0,1
blt.w r0, r10, _xloop
addi.w r1,r1,1
blt.w r1, r11,_yloop
addi.b r12,r12,1
jump _forever

load.w r0, 2000
syscall 7
load.w r0,0
return"""
    val memory = Memory()
    val assembler = Assembler()
    assembler.initializeMemory(memsrc, memory)
    val program = assembler.assembleProgram(src)

    val vm = VirtualMachine(memory, program)
    vm.run()
}
