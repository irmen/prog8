%import textio
%option no_sysinit
%zeropage basicsafe

; This file exercises real crashes in the compiler's built-in
; function handling. The same TODO guards exist in BOTH the IR codegen
; (BuiltinFuncGen.kt) and the existing 6502 codegen
; (BuiltinFunctionsAsmGen.kt) - so the crashes occur regardless of
; the chosen target. HOWEVER, the two backends differ in which
; patterns they crash on:
;
;   pattern                 IR codegen    6502 codegen
;   rol(plain[const])       works         works
;   rol(ptr.arr[const])     CRASH         CRASH
;   rol(ptr.arr[idx])       works         CRASH
;   setlsb(ptr.arr[i], v)   CRASH         CRASH
;
; Crashing patterns (commented out, uncomment to trigger the TODO):
;
;   rol/ror/rol2/ror2(ptr.arr[const])
;       TODO at BuiltinFuncGen.kt:767
;       TODO at BuiltinFunctionsAsmGen.kt:763,841,950,1024
;
;   rol/ror/rol2/ror2(ptr.arr[idx])
;       Works in IR codegen (falls through to runtime path at line 794)
;       TODO at BuiltinFunctionsAsmGen.kt:763,841,950,1024
;       (6502 codegen has no fallthrough)
;
;   setlsb/setmsb(ptr.arr[i], v)
;       TODO at BuiltinFuncGen.kt:835 (any index)
;       TODO at BuiltinFunctionsAsmGen.kt:1139 (any index)
;       Unreachable: TODO at BuiltinFuncGen.kt:870 (non-split branch
;       needs PtIdentifier base, which is never a pointer-deref)
;
; The fix in both backends is to fall through to the existing non-const
; code path (using translateExpression(arg) in the IR codegen, the
; loadScaledArrayIndexIntoRegister helper in the 6502 codegen) instead
; of throwing. The cost is a small loss of optimization for the
; affected patterns.

Data {
    struct Node {
        uword[5] arr
    }
}

main {
    ^^Data.Node @shared target
    ^^Data.Node @shared ptr = &target
    uword[5] @shared plain
    ubyte @shared value = 99
    ubyte @shared idx = 2

    sub start() {
        ; --- working on both targets: const index on a plain array ---
        rol(plain[2])
        ror(plain[2])
        rol2(plain[2])
        ror2(plain[2])

        ; --- working on IR only: non-const index on a typed pointer ---
        ; (crashes 6502 codegen at BuiltinFunctionsAsmGen.kt:763,841,950,1024)
        rol(ptr.arr[idx])
        ror(ptr.arr[idx])

        ; --- broken on both: const index on a typed pointer ---
        ; Uncomment to trigger:
        ;   BuiltinFuncGen.kt:767 (IR)
        ;   BuiltinFunctionsAsmGen.kt:763,841,950,1024 (6502)
        rol(ptr.arr[2])
        ror(ptr.arr[2])
        rol2(ptr.arr[2])
        ror2(ptr.arr[2])

        ; --- broken on both: any index on a typed pointer (setlsb/setmsb) ---
        ; Uncomment to trigger:
        ;   BuiltinFuncGen.kt:835 (IR)
        ;   BuiltinFunctionsAsmGen.kt:1139 (6502)
        setlsb(ptr.arr[idx], value)
        setmsb(ptr.arr[idx], value)
        setlsb(ptr.arr[2], value)
        setmsb(ptr.arr[2], value)
    }
}
