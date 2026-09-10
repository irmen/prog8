# Structured IR Operands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Prog8's positional, nullable IR operands with typed structured operands across the IR generator, serialization, VM, optimizers, and IR-based CPU backends without changing opcode semantics.

**Architecture:** Introduce typed operand and call models plus one `OpcodeSchema` source of truth. Adapt the existing `IRInstruction` to expose structured views first, migrate every consumer and producer in buildable stages, then make structured operands canonical and enable the breaking `.p8ir` format.

**Tech Stack:** Kotlin 2.4, Java 17, KoTest/JUnit 5, Gradle, XML `.p8ir` serialization.

**Repository constraint:** Do not run `git add`, `git commit`, `git mv`, `git rm`, or any other git write operation. Commit steps normally required by the planning workflow are intentionally omitted.

---

## File structure

### New production files

- `intermediate/src/prog8/intermediate/IROperands.kt`
  - Virtual register identities, register accesses, immediates, memory
    references, code references, hardware slots, and effect enums.
- `intermediate/src/prog8/intermediate/IRCalls.kt`
  - `CallSite`, targets, locations, arguments, results, and call effects.
- `intermediate/src/prog8/intermediate/OpcodeSchema.kt`
  - Complete opcode/type schema and schema validation.
- `intermediate/src/prog8/intermediate/IRInstructionsFactory.kt`
  - Typed construction helpers used by all producers and tests.
- `intermediate/src/prog8/intermediate/IRTextCodec.kt`
  - Canonical instruction parser and printer for the new format.

### New tests

- `intermediate/test/TestStructuredOperands.kt`
- `intermediate/test/TestOpcodeSchema.kt`
- `intermediate/test/TestIRTextCodec.kt`
- `intermediate/test/TestStructuredCalls.kt`

### Existing files changed by responsibility

- `intermediate/src/prog8/intermediate/IRInstructions.kt`
  - Temporary structured adapter, then canonical structured
    `IRInstruction`; removal of legacy fields and `instructionFormats`.
- `intermediate/src/prog8/intermediate/IRProgram.kt`
  - Register accounting and code-reference linking.
- `intermediate/src/prog8/intermediate/Utils.kt`
  - Delegate instruction parsing to `IRTextCodec`.
- `intermediate/src/prog8/intermediate/IRFileReader.kt`
  - Require the new `IRFORMAT` and read new instruction text.
- `intermediate/src/prog8/intermediate/IRFileWriter.kt`
  - Write `IRFORMAT` and new instruction text.
- `intermediate/test/TestInstructions.kt`
  - Replace positional-field assertions with structured assertions.
- `intermediate/test/TestIRFileInOut.kt`
  - New format-version and round-trip coverage.
- `intermediate/test/TestIRTraversal.kt`
  - Structured register and target traversal.
- `codeGenIntermediate/src/prog8/codegen/intermediate/IRPeepholeOptimizer.kt`
  - Structured matching and transformations.
- `codeGenIntermediate/src/prog8/codegen/intermediate/IRUnusedCodeRemover.kt`
  - Structured uses, definitions, and targets.
- `codeGenIntermediate/src/prog8/codegen/intermediate/RegisterPacker.kt`
  - Distinct integer/float register identities and structured accesses.
- `codeGenIntermediate/src/prog8/codegen/intermediate/BuiltinFuncGen.kt`
- `codeGenIntermediate/src/prog8/codegen/intermediate/ExpressionGen.kt`
- `codeGenIntermediate/src/prog8/codegen/intermediate/AssignmentGen.kt`
- `codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt`
  - Replace direct constructors with typed factories.
- `codeGenIntermediate/test/TestIRPeepholeOpt.kt`
- `codeGenIntermediate/test/TestRegisterPacker.kt`
- `codeGenIntermediate/test/TestVmCodeGen.kt`
  - Structured optimizer, allocator-analysis, and generation coverage.
- `virtualmachine/src/prog8/vm/VirtualMachine.kt`
- `virtualmachine/src/prog8/vm/VmArithmetic.kt`
- `virtualmachine/src/prog8/vm/VmProgramLoader.kt`
- `virtualmachine/test/TestVm.kt`
- `virtualmachine/test/TestRegisters.kt`
  - Consume structured operands and calls.
- `codeGenM68k/src/prog8/codegen/m68k/AsmGen.kt`
- `codeGenM68k/src/prog8/codegen/m68k/InstrArithmetic.kt`
- `codeGenM68k/src/prog8/codegen/m68k/InstrBitwise.kt`
- `codeGenM68k/src/prog8/codegen/m68k/InstrBranch.kt`
- `codeGenM68k/src/prog8/codegen/m68k/InstrControl.kt`
- `codeGenM68k/src/prog8/codegen/m68k/InstrLoadStore.kt`
- `codeGenM68k/src/prog8/codegen/m68k/InstrSyscall.kt`
- `codeGenM68k/test/TestInstructionSelectionOptimizations.kt`
  - Consume semantic operands without physical allocation changes.
- `codeGenNew6502/src/prog8/codegen/new6502/AsmGen.kt`
- `codeGenNew6502/src/prog8/codegen/new6502/InstrArithmetic.kt`
- `codeGenNew6502/src/prog8/codegen/new6502/InstrBitwise.kt`
- `codeGenNew6502/src/prog8/codegen/new6502/InstrBranch.kt`
- `codeGenNew6502/src/prog8/codegen/new6502/InstrControl.kt`
- `codeGenNew6502/src/prog8/codegen/new6502/InstrLoadStore.kt`
- `codeGenNew6502/test/TestAsmsubReturns.kt`
- `codeGenNew6502/test/TestInlineAsmSub.kt`
- `codeGenNew6502/test/TestLoopCodegen.kt`
  - Consume semantic operands without changing generated behavior.
- `compiler/res/prog8lib/virtual/*.p8`
  - Migrate all embedded `%ir` instruction text.
- `docs/CODEBASE-KNOWLEDGE-GRAPH.md`
  - Document the structured operand contract.

## Task 1: Lock down current operand semantics

**Files:**
- Modify: `intermediate/test/TestInstructions.kt`
- Modify: `intermediate/test/TestIRFileInOut.kt`
- Modify: `codeGenIntermediate/test/TestVmCodeGen.kt`

- [ ] **Step 1: Add a failing round-trip test for two-result instruction order**

Add to `TestInstructions.kt`:

```kotlin
test("DIVMOD preserves quotient and remainder register order") {
    val original = IRInstruction(
        Opcode.DIVMOD,
        IRDataType.WORD,
        reg1 = 1,
        reg2 = 2,
        immediate = 7
    )
    val parsed = (parseIRCodeLine(original.toString()) as ParsedIRLine.Instruction).value
    parsed.reg1 shouldBe 1
    parsed.reg2 shouldBe 2
    parsed shouldBe original
}
```

- [ ] **Step 2: Run the intermediate test to expose the current ambiguity**

Run:

```bash
gradle :intermediate:test --tests "TestInstructions" --console=plain
```

Expected: the new `DIVMOD` round-trip test fails by swapping or otherwise
misidentifying the two destination registers.

- [ ] **Step 3: Add representative behavior baselines**

Add explicit construction and `toString()` assertions for:

```kotlin
IRInstruction(Opcode.LOADX, IRDataType.WORD, reg1 = 1, reg2 = 2,
    labelSymbol = "main.items", symbolOffset = 4, scale = 2)
IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1 = 3, reg2 = 4, immediate = 8)
IRInstruction(Opcode.LOADHR, IRDataType.WORD, reg1 = 5, immediate = 10)
IRInstruction(Opcode.CALLI, reg1 = 6)
```

In `TestIRFileInOut.kt`, add one file round trip containing a normal call, an
asmsub hardware-slot return, and a status-flag return.

- [ ] **Step 4: Run baseline module tests**

Run:

```bash
gradle :intermediate:test :codeGenIntermediate:test --console=plain
```

Expected: only the intentionally exposed `DIVMOD` ambiguity fails. Record its
failure text in the task notes; do not weaken the assertion.

## Task 2: Add typed structured operand values

**Files:**
- Create: `intermediate/src/prog8/intermediate/IROperands.kt`
- Create: `intermediate/test/TestStructuredOperands.kt`

- [ ] **Step 1: Write failing tests for register identity and operand validation**

Create `TestStructuredOperands.kt` with tests equivalent to:

```kotlin
class TestStructuredOperands : FunSpec({
    fun testIndexOperand() = RegisterOperand(
        VirtualRegister.IntReg(RegisterNum(3)),
        IRDataType.WORD,
        OperandRole.INDEX,
        OperandDirection.USE,
        AllocationHint.PREFER_DATA
    )

    test("integer and float registers are distinct") {
        val intReg: VirtualRegister = VirtualRegister.IntReg(RegisterNum(5))
        val floatReg: VirtualRegister = VirtualRegister.FloatReg(RegisterNum(5))
        intReg shouldNotBe floatReg
    }

    test("indexed memory keeps base index scale and displacement") {
        val index = RegisterOperand(
            VirtualRegister.IntReg(RegisterNum(3)),
            IRDataType.WORD,
            OperandRole.INDEX,
            OperandDirection.USE,
            AllocationHint.PREFER_DATA
        )
        val ref = MemoryReference.Indexed(
            AddressBase.Symbol("main.items"),
            index,
            scale = 6,
            displacement = 4
        )
        ref.scale shouldBe 6
        ref.displacement shouldBe 4
    }

    test("indexed scale must be in range") {
        shouldThrow<IllegalArgumentException> {
            MemoryReference.Indexed(
                AddressBase.Symbol("main.items"),
                testIndexOperand(),
                scale = 0
            )
        }
    }
})
```

- [ ] **Step 2: Run the new test and verify it does not compile**

Run:

```bash
gradle :intermediate:test --tests "TestStructuredOperands" --console=plain
```

Expected: compilation fails because the structured operand types do not exist.

- [ ] **Step 3: Implement the operand hierarchy**

Implement in `IROperands.kt` the exact types from the approved specification:

```kotlin
sealed interface VirtualRegister {
    val number: RegisterNum

    @JvmInline
    value class IntReg(override val number: RegisterNum) : VirtualRegister

    @JvmInline
    value class FloatReg(override val number: RegisterNum) : VirtualRegister
}

enum class OperandDirection { USE, DEF, USE_DEF }
enum class OperandRole {
    RESULT, SECONDARY_RESULT, VALUE, LEFT, RIGHT, INDEX, POINTER_BASE,
    INDIRECT_TARGET, CALL_ARGUMENT, CALL_RESULT
}
enum class AllocationHint { NONE, PREFER_DATA, PREFER_ADDRESS }

data class RegisterOperand(
    val register: VirtualRegister,
    val type: IRDataType,
    val role: OperandRole,
    val direction: OperandDirection,
    val allocationHint: AllocationHint = AllocationHint.NONE
) {
    init {
        require((register is VirtualRegister.FloatReg) == (type == IRDataType.FLOAT))
    }
}
```

Add `ImmediateOperand`, `HardwareSlotOperand`, `MemoryReference`,
`AddressBase`, `CodeReference`, `MemoryEffect`, and `StatusEffect` exactly as
specified. Enforce `scale in 1..65535`, non-negative indirect displacement in
the current LOADI/STOREI range, and valid symbol names in constructors.

- [ ] **Step 4: Run structured operand tests**

Run:

```bash
gradle :intermediate:test --tests "TestStructuredOperands" --console=plain
```

Expected: all tests in `TestStructuredOperands` pass.

## Task 3: Add the structured call model

**Files:**
- Create: `intermediate/src/prog8/intermediate/IRCalls.kt`
- Create: `intermediate/test/TestStructuredCalls.kt`
- Modify: `intermediate/src/prog8/intermediate/IRInstructions.kt`

- [ ] **Step 1: Write failing call-model tests**

Cover a normal parameter-memory call, an asmsub call using D0/D2, a
multi-result call, a status-flag result, an indirect call, an Amiga LVO call,
and a syscall:

```kotlin
test("call result has exactly one explicit location") {
    val destination = RegisterOperand(
        VirtualRegister.IntReg(RegisterNum(4)),
        IRDataType.WORD,
        OperandRole.CALL_RESULT,
        OperandDirection.DEF
    )
    val result = CallResult(
        destination,
        CallLocation.HardwareRegister(CallingConventionSlot(12))
    )
    result.location shouldBe CallLocation.HardwareRegister(CallingConventionSlot(12))
}

test("Amiga LVO retains signed offset") {
    val target = CallTarget.AmigaLibrary(2, -30, "Open")
    target.lvo shouldBe -30
}
```

- [ ] **Step 2: Run the call test and verify it does not compile**

Run:

```bash
gradle :intermediate:test --tests "TestStructuredCalls" --console=plain
```

Expected: compilation fails because `CallSite` and related types do not exist.

- [ ] **Step 3: Implement calls and legacy conversion**

Implement `CallTarget`, `CallLocation`, `CallArgument`, `CallResult`,
`CallEffects`, and `CallSite` from the specification. Add conversion helpers
without changing canonical storage yet:

```kotlin
fun FunctionCallArgs.toCallSite(
    opcode: Opcode,
    target: CallTarget,
    effects: CallEffects
): CallSite

fun CallSite.toLegacyFunctionCallArgs(): FunctionCallArgs
```

Map a missing slot/flag plus a named parameter to
`CallLocation.ParameterMemory`; map an unannotated value to
`CallLocation.Default`; never infer a hardware slot from a register number.

- [ ] **Step 4: Run call and existing instruction tests**

Run:

```bash
gradle :intermediate:test --tests "TestStructuredCalls" --tests "TestInstructions" --console=plain
```

Expected: structured call tests pass; only the intentionally failing DIVMOD
round-trip test remains.

## Task 4: Introduce `OpcodeSchema`

**Files:**
- Create: `intermediate/src/prog8/intermediate/OpcodeSchema.kt`
- Create: `intermediate/test/TestOpcodeSchema.kt`
- Modify: `intermediate/src/prog8/intermediate/IRInstructions.kt`

- [ ] **Step 1: Write schema completeness and representative-shape tests**

Add tests that assert:

```kotlin
Opcode.entries.forEach { opcode ->
    OpcodeSchemas.schemasFor(opcode).shouldNotBeEmpty()
}

OpcodeSchemas.get(Opcode.ADDR, IRDataType.WORD).slots shouldBe listOf(
    RegisterSlotSchema(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF,
        RegisterFile.INTEGER, TypeRule.InstructionType),
    RegisterSlotSchema(InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE,
        RegisterFile.INTEGER, TypeRule.InstructionType)
)

OpcodeSchemas.get(Opcode.DIVMOD, IRDataType.WORD).slots.map { it.slot } shouldBe
    listOf(InstructionSlot.DEST, InstructionSlot.DEST_B, InstructionSlot.IMMEDIATE)
```

Also test float `LOADX`, pointer `LOADI`, `LOADHR`, `CONCAT`, branch, return,
and every call opcode.

- [ ] **Step 2: Run schema tests and verify they fail**

Run:

```bash
gradle :intermediate:test --tests "TestOpcodeSchema" --console=plain
```

Expected: compilation fails because `OpcodeSchema` does not exist.

- [ ] **Step 3: Implement schema types and complete opcode coverage**

Implement:

```kotlin
enum class InstructionSlot {
    DEST, DEST_B, SRC_A, SRC_B, IMMEDIATE, HARDWARE_SLOT, MEMORY, TARGET, CALL_SITE
}

enum class RegisterFile { INTEGER, FLOAT }

sealed interface TypeRule {
    data object InstructionType : TypeRule
    data object IndexType : TypeRule
    data object PointerType : TypeRule
    data class Fixed(val type: IRDataType) : TypeRule
}

data class RegisterSlotSchema(
    val slot: InstructionSlot,
    val role: OperandRole,
    val direction: OperandDirection,
    val registerFile: RegisterFile,
    val typeRule: TypeRule
)
```

Port every existing `instructionFormats` entry into `OpcodeSchemas`. Use named
slots in semantic order, not old field order. Encode special forms explicitly:

- `DIVMOD`/`SDIVMOD`: `dest`, `destB`, `immediate`.
- `CONCAT`: `dest`, `srcA`, `srcB`.
- Float indexed operations: float destination/value plus integer index.
- `LOADI`/`STOREI`: pointer base through `memory`.
- `LOADHR`/`STOREHR`: `hardwareSlot`.
- Calls: only `callSite`.
- Branches/jumps: `target`, plus comparison sources where applicable.

- [ ] **Step 4: Add schema equivalence checks during migration**

While `instructionFormats` still exists, add a test that each old format and
new schema agree on register count, register file, direction, immediate
presence, address presence, and call kind. This temporary test is removed with
`instructionFormats`.

- [ ] **Step 5: Run schema and instruction tests**

Run:

```bash
gradle :intermediate:test --tests "TestOpcodeSchema" --tests "TestInstructions" --console=plain
```

Expected: schema tests pass; the known old parser round-trip failure remains.

## Task 5: Add structured views and factories to the legacy instruction

**Files:**
- Create: `intermediate/src/prog8/intermediate/IRInstructionsFactory.kt`
- Modify: `intermediate/src/prog8/intermediate/IRInstructions.kt`
- Modify: `intermediate/test/TestInstructions.kt`
- Modify: `intermediate/test/TestStructuredOperands.kt`

- [ ] **Step 1: Write failing tests for structured views**

For representative integer, float, indexed-memory, branch, hardware-slot, and
call instructions, assert:

```kotlin
val add = IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1 = 1, reg2 = 2)
add.dest shouldBe RegisterOperand(
    VirtualRegister.IntReg(RegisterNum(1)),
    IRDataType.WORD,
    OperandRole.RESULT,
    OperandDirection.USE_DEF
)
add.srcA shouldBe RegisterOperand(
    VirtualRegister.IntReg(RegisterNum(2)),
    IRDataType.WORD,
    OperandRole.RIGHT,
    OperandDirection.USE
)
add.uses shouldBe setOf(
    VirtualRegister.IntReg(RegisterNum(1)),
    VirtualRegister.IntReg(RegisterNum(2))
)
add.definitions shouldBe setOf(VirtualRegister.IntReg(RegisterNum(1)))
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
gradle :intermediate:test --tests "TestStructuredOperands" --tests "TestInstructions" --console=plain
```

Expected: compilation fails because the structured views do not exist.

- [ ] **Step 3: Implement a schema-driven legacy adapter**

Keep the existing fields canonical temporarily. Add derived properties:

```kotlin
val IRInstruction.dest: RegisterOperand?
val IRInstruction.destB: RegisterOperand?
val IRInstruction.srcA: RegisterOperand?
val IRInstruction.srcB: RegisterOperand?
val IRInstruction.structuredImmediate: ImmediateOperand?
val IRInstruction.hardwareSlot: HardwareSlotOperand?
val IRInstruction.memory: MemoryReference?
val IRInstruction.target: CodeReference?
val IRInstruction.callSite: CallSite?
val IRInstruction.registerAccesses: List<RegisterOperand>
val IRInstruction.uses: Set<VirtualRegister>
val IRInstruction.definitions: Set<VirtualRegister>
```

Resolve each property through `OpcodeSchema`; do not duplicate opcode switches
in the individual getters.

- [ ] **Step 4: Add typed factories**

Implement factory methods that produce the current instruction fields from
structured input during the migration:

```kotlin
object IRInstructions {
    fun load(destination: RegisterOperand, immediate: ImmediateOperand): IRInstruction
    fun loadMemory(destination: RegisterOperand, memory: MemoryReference): IRInstruction
    fun move(destination: RegisterOperand, source: RegisterOperand): IRInstruction
    fun binary(opcode: Opcode, destination: RegisterOperand, source: RegisterOperand): IRInstruction
    fun branch(opcode: Opcode, target: CodeReference, srcA: RegisterOperand? = null,
               srcB: RegisterOperand? = null, immediate: ImmediateOperand? = null): IRInstruction
    fun call(opcode: Opcode, site: CallSite): IRInstruction
}
```

All factories call one schema validator before constructing the legacy form.

- [ ] **Step 5: Run all intermediate tests except the known parser failure**

Run:

```bash
gradle :intermediate:test --console=plain
```

Expected: structured view and factory tests pass; the recorded DIVMOD
round-trip test is the only failure.

## Task 6: Replace instruction text parsing and printing

**Files:**
- Create: `intermediate/src/prog8/intermediate/IRTextCodec.kt`
- Create: `intermediate/test/TestIRTextCodec.kt`
- Modify: `intermediate/src/prog8/intermediate/Utils.kt`
- Modify: `intermediate/src/prog8/intermediate/IRInstructions.kt`
- Modify: `intermediate/test/TestInstructions.kt`

- [ ] **Step 1: Write failing canonical text tests**

Cover every token category from the specification:

```kotlin
IRTextCodec.parse("load.w r1.w,#$1234.w").print() shouldBe
    "load.w r1.w,#$1234.w"
IRTextCodec.parse("loadi.b r1.b,[r2.p+8]").print() shouldBe
    "loadi.b r1.b,[r2.p+8]"
IRTextCodec.parse("loadx.w r1.w,[main.items+4+r2.w*2]").print() shouldBe
    "loadx.w r1.w,[main.items+4+r2.w*2]"
IRTextCodec.parse("loadhr.w r1.w,s10.w").print() shouldBe
    "loadhr.w r1.w,s10.w"
```

Add explicit tests for `DIVMOD`, `SDIVMOD`, every branch target form, and all
four call opcodes.

- [ ] **Step 2: Run text-codec tests and verify they fail**

Run:

```bash
gradle :intermediate:test --tests "TestIRTextCodec" --console=plain
```

Expected: compilation fails because `IRTextCodec` does not exist.

- [ ] **Step 3: Implement token parsing and printing**

Implement a tokenizer with explicit token classes rather than comma splitting:

```kotlin
sealed interface IRToken {
    data class Register(val text: String) : IRToken
    data class Immediate(val text: String) : IRToken
    data class Memory(val text: String) : IRToken
    data class CodeTarget(val text: String) : IRToken
    data class HardwareSlot(val text: String) : IRToken
    data class StatusFlag(val text: String) : IRToken
}
```

Parsing flow:

1. Parse opcode and optional type suffix.
2. Resolve `OpcodeSchema`.
3. Tokenize balanced `[]` and `()` expressions.
4. Parse each token according to the schema's canonical slot order.
5. Construct through `IRInstructions` factories.
6. Reject extra, missing, or wrong-kind tokens with `IRParseException`.

- [ ] **Step 4: Route existing public helpers through the codec**

Make `parseIRCodeLine` delegate instruction lines to `IRTextCodec.parse`.
Make `IRInstruction.toString()` delegate to `IRTextCodec.print`. Keep label-line
parsing in `Utils.kt`.

- [ ] **Step 5: Run all intermediate tests**

Run:

```bash
gradle :intermediate:test --console=plain
```

Expected: all intermediate tests pass, including the original DIVMOD
round-trip regression.

## Task 7: Version and round-trip the `.p8ir` format

**Files:**
- Modify: `intermediate/src/prog8/intermediate/IRFileWriter.kt`
- Modify: `intermediate/src/prog8/intermediate/IRFileReader.kt`
- Modify: `intermediate/test/TestIRFileInOut.kt`

- [ ] **Step 1: Write failing format-version tests**

Change the expected root to:

```xml
<PROGRAM NAME="unittest-irwriter" COMPILERVERSION="99.99" IRFORMAT="2">
```

Add:

```kotlin
shouldThrowWithMessage<IRParseException>("unsupported IR format: 1") {
    IRFileReader().read(irFileWithRoot("""<PROGRAM ... IRFORMAT="1">"""))
}

shouldThrowWithMessage<IRParseException>("missing IRFORMAT") {
    IRFileReader().read(irFileWithoutFormatAttribute())
}
```

- [ ] **Step 2: Run file I/O tests and verify they fail**

Run:

```bash
gradle :intermediate:test --tests "TestIRFileInOut" --console=plain
```

Expected: root-attribute and rejection tests fail.

- [ ] **Step 3: Implement strict format versioning**

Add:

```kotlin
const val IR_FORMAT_VERSION = 2
```

Write `IRFORMAT="2"` in `IRFileWriter`. Require exactly `2` in
`IRFileReader` before reading options or code. Do not retain a legacy parser.

- [ ] **Step 4: Convert all fixture instruction lines in `TestIRFileInOut.kt`**

Use typed register and explicit memory syntax, for example:

```text
load.b r1.b,#42.b
loadm.w r0.w,[sys.wait.jiffies]
```

- [ ] **Step 5: Run intermediate tests**

Run:

```bash
gradle :intermediate:test --console=plain
```

Expected: all intermediate tests pass.

## Task 8: Migrate generic analyses and linking

**Files:**
- Modify: `intermediate/src/prog8/intermediate/IRProgram.kt`
- Modify: `intermediate/test/TestIRTraversal.kt`
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/IRUnusedCodeRemover.kt`
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/RegisterPacker.kt`
- Modify: `codeGenIntermediate/test/TestRegisterPacker.kt`

- [ ] **Step 1: Write failing mixed-register and linkage tests**

Add a traversal test in which `r5` and `fr5` both occur and assert that two
different `VirtualRegister` values are reported. Add a linking test asserting:

```kotlin
program.resolveCodeTarget(CodeReference.Label("main.target")) shouldBe targetChunk
```

Add RegisterPacker coverage showing integer and float register 5 never share
one graph node.

- [ ] **Step 2: Run focused tests and verify failure**

Run:

```bash
gradle :intermediate:test --tests "TestIRTraversal" --console=plain
gradle :codeGenIntermediate:test --tests "TestRegisterPacker" --console=plain
```

Expected: the tests fail because analyses still use integer field numbers.

- [ ] **Step 3: Rebuild register accounting on structured accesses**

Replace enumeration of `reg1..reg3` and `fpReg1..fpReg2` with:

```kotlin
for (access in instruction.registerAccesses) {
    when (access.direction) {
        OperandDirection.USE -> addRead(access)
        OperandDirection.DEF -> addWrite(access)
        OperandDirection.USE_DEF -> {
            addRead(access)
            addWrite(access)
        }
    }
}
```

Change internal maps that can contain both register files to use
`VirtualRegister` keys.

- [ ] **Step 4: Replace mutable branch targets with program linkage**

Build a `Map<String, IRCodeChunkBase>` in `IRProgram.linkChunks()` and expose:

```kotlin
fun resolveCodeTarget(reference: CodeReference): IRCodeChunkBase? = when (reference) {
    is CodeReference.Label -> linkedCodeTargets[reference.name]
    is CodeReference.Absolute, is CodeReference.Indirect -> null
}
```

Update validation and unused-code traversal to use `instruction.target` and
this resolver. Remove writes to `IRInstruction.branchTarget` only after all
consumers have migrated.

- [ ] **Step 5: Update RegisterPacker access extraction**

Return `Set<VirtualRegister>` for written and read values. Keep integer and
float interference separate. Preserve the packer's documented prototype
limitations; do not turn this task into the M68k allocator.

- [ ] **Step 6: Run focused module tests**

Run:

```bash
gradle :intermediate:test :codeGenIntermediate:test --console=plain
```

Expected: both modules pass.

## Task 9: Migrate IR optimizers to structured matching

**Files:**
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/IRPeepholeOptimizer.kt`
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/IRUnusedCodeRemover.kt`
- Modify: `codeGenIntermediate/test/TestIRPeepholeOpt.kt`

- [ ] **Step 1: Add tests for transformations that previously reconstructed instructions**

Cover at least:

- `LOADX`/`STOREX` retaining scale and symbol displacement.
- Calls retaining all arguments, results, locations, and effects.
- Branch rewrites retaining `CodeReference`.
- Register replacement changing only the selected virtual register.

Use structured assertions rather than old field assertions.

- [ ] **Step 2: Run optimizer tests before migration**

Run:

```bash
gradle :codeGenIntermediate:test --tests "TestIRPeepholeOpt" --console=plain
```

Expected: new structured assertions fail at legacy reconstruction sites.

- [ ] **Step 3: Replace positional matches**

Use semantic accessors:

```kotlin
val destination = instruction.dest ?: return noMatch
val source = instruction.srcA ?: return noMatch
val memory = instruction.memory
```

Replace direct `.copy(reg1 = ...)` and full constructor rebuilding with:

```kotlin
instruction.mapRegisters { register ->
    replacements[register] ?: register
}
```

Use `withTarget` and `mapMemoryReferences` for their respective changes.

- [ ] **Step 4: Remove duplicate direction/type helpers**

Delete optimizer-local helpers that reconstruct destination order, float
register roles, index roles, or register types when equivalent information is
available through `OpcodeSchema`.

- [ ] **Step 5: Run optimizer and packer tests**

Run:

```bash
gradle :codeGenIntermediate:test --tests "TestIRPeepholeOpt" --tests "TestRegisterPacker" --console=plain
```

Expected: all selected tests pass.

## Task 10: Migrate the virtual machine

**Files:**
- Modify: `virtualmachine/src/prog8/vm/VirtualMachine.kt`
- Modify: `virtualmachine/src/prog8/vm/VmArithmetic.kt`
- Modify: `virtualmachine/src/prog8/vm/VmProgramLoader.kt`
- Modify: `virtualmachine/test/TestVm.kt`
- Modify: `virtualmachine/test/TestRegisters.kt`

- [ ] **Step 1: Add structured execution tests**

Construct instructions with `IRInstructions` factories and cover:

- Integer and float register number equality without identity collision.
- `LOADI` and `LOADX` memory addressing.
- `CALL`, `CALLI`, multi-result return, hardware-slot return, and flag return.
- Branch target resolution through `IRProgram`.

- [ ] **Step 2: Run VM tests and verify adapter dependence**

Run:

```bash
gradle :virtualmachine:test --tests "TestVm" --tests "TestRegisters" --console=plain
```

Expected: tests using factories compile, but structured-only assertions expose
remaining field-based handlers.

- [ ] **Step 3: Add typed operand extraction helpers**

Use narrow helpers that fail with opcode context:

```kotlin
fun IRInstruction.requireIntDest(): RegisterOperand
fun IRInstruction.requireFloatDest(): RegisterOperand
fun IRInstruction.requireIntSourceA(): RegisterOperand
fun IRInstruction.requireMemory(): MemoryReference
fun IRInstruction.requireCallSite(): CallSite
```

Convert `RegisterNum` to VM array indexes only at `Registers` access.

- [ ] **Step 4: Migrate opcode handlers**

Replace every `reg1!!`, `reg2!!`, `fpReg1!!`, `immediate!!`, `address!!`,
`labelSymbol`, and `fcallArgs` read with the semantic helper matching that
opcode. Resolve code labels through `IRProgram.resolveCodeTarget`.

- [ ] **Step 5: Migrate loader transformations**

Replace `.copy(address=...)`, `.copy(labelSymbol=...)`, and direct
`branchTarget` assignment in `VmProgramLoader.kt` with `withTarget`,
`mapMemoryReferences`, and program linkage.

- [ ] **Step 6: Run the complete VM module**

Run:

```bash
gradle :virtualmachine:test --console=plain
```

Expected: all VM tests pass.

## Task 11: Migrate the M68k backend

**Files:**
- Modify: `codeGenM68k/src/prog8/codegen/m68k/AsmGen.kt`
- Modify: `codeGenM68k/src/prog8/codegen/m68k/InstrArithmetic.kt`
- Modify: `codeGenM68k/src/prog8/codegen/m68k/InstrBitwise.kt`
- Modify: `codeGenM68k/src/prog8/codegen/m68k/InstrBranch.kt`
- Modify: `codeGenM68k/src/prog8/codegen/m68k/InstrControl.kt`
- Modify: `codeGenM68k/src/prog8/codegen/m68k/InstrLoadStore.kt`
- Modify: `codeGenM68k/src/prog8/codegen/m68k/InstrSyscall.kt`
- Modify: `codeGenM68k/test/TestInstructionSelectionOptimizations.kt`

- [ ] **Step 1: Convert test construction to factories**

Replace positional `IRInstruction(...)` construction in
`TestInstructionSelectionOptimizations` with `IRInstructions` factories.
Add assertions that indexed and indirect references still emit their current
assembly forms.

- [ ] **Step 2: Run the M68k test before backend migration**

Run:

```bash
gradle :codeGenM68k:test --tests "prog8tests.codegen.m68k.TestInstructionSelectionOptimizations" --console=plain
```

Expected: factory-based tests pass through the adapter, establishing unchanged
assembly before field removal.

- [ ] **Step 3: Add backend-local typed accessors**

Use structured inputs while preserving current memory-regfile code generation:

```kotlin
fun RegisterOperand.intNumber(): Int =
    (register as VirtualRegister.IntReg).number.value

fun RegisterOperand.floatNumber(): RegisterNum =
    (register as VirtualRegister.FloatReg).number
```

Do not add allocation, operand-location selection, or new peepholes in this
task.

- [ ] **Step 4: Migrate instruction translators**

Replace all old operand-field reads with `dest`, `destB`, `srcA`, `srcB`,
`immediate`, `memory`, `target`, `hardwareSlot`, and `callSite`. Centralize
`MemoryReference` lowering in one `AsmGen` helper so `LOADX`, `STOREX`,
`LOADI`, `STOREI`, and float variants do not decode address fields separately.

- [ ] **Step 5: Migrate call translation**

In `InstrControl.kt`, translate:

- `CallTarget.Direct(CodeReference.Label/Absolute/Indirect)`.
- `CallTarget.AmigaLibrary`.
- `CallTarget.SystemCall`.
- `CallLocation.ParameterMemory`, `HardwareRegister`, `StatusFlag`, and
  `Default`.

Preserve current argument ordering, multi-return behavior, and
`ImmediateCallOptimization`; do not yet enforce the proposed allocator ABI.

- [ ] **Step 6: Run the M68k module**

Run:

```bash
gradle :codeGenM68k:test --console=plain
```

Expected: all M68k tests pass with unchanged assembly assertions.

## Task 12: Migrate the new6502 backend

**Files:**
- Modify: `codeGenNew6502/src/prog8/codegen/new6502/AsmGen.kt`
- Modify: `codeGenNew6502/src/prog8/codegen/new6502/InstrArithmetic.kt`
- Modify: `codeGenNew6502/src/prog8/codegen/new6502/InstrBitwise.kt`
- Modify: `codeGenNew6502/src/prog8/codegen/new6502/InstrBranch.kt`
- Modify: `codeGenNew6502/src/prog8/codegen/new6502/InstrControl.kt`
- Modify: `codeGenNew6502/src/prog8/codegen/new6502/InstrLoadStore.kt`
- Modify: `codeGenNew6502/test/TestAsmsubReturns.kt`
- Modify: `codeGenNew6502/test/TestInlineAsmSub.kt`
- Modify: `codeGenNew6502/test/TestLoopCodegen.kt`

- [ ] **Step 1: Convert backend tests to factories and structured calls**

Replace `FunctionCallArgs.RegSpec` construction with `CallResult` and
`CallLocation`. Keep all expected assembly unchanged.

- [ ] **Step 2: Run focused backend tests**

Run:

```bash
gradle :codeGenNew6502:test --tests "prog8tests.codegen.new6502.TestAsmsubReturns" --tests "prog8tests.codegen.new6502.TestInlineAsmSub" --tests "prog8tests.codegen.new6502.TestLoopCodegen" --console=plain
```

Expected: tests pass through the adapter before old fields are removed.

- [ ] **Step 3: Migrate translators and register scans**

Replace old fields with semantic slots and structured memory/call targets.
Convert to 6502 register-file indexes only at the backend boundary. Treat
unknown call or inline-assembly effects conservatively.

- [ ] **Step 4: Run the complete new6502 module**

Run:

```bash
gradle :codeGenNew6502:test --console=plain
```

Expected: all new6502 tests pass.

## Task 13: Migrate IR producers

**Files:**
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/BuiltinFuncGen.kt`
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/ExpressionGen.kt`
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/AssignmentGen.kt`
- Modify: `codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt`
- Modify: `codeGenIntermediate/test/TestVmCodeGen.kt`
- Modify: `codeGenIntermediate/test/TestIRPeepholeOpt.kt`

- [ ] **Step 1: Add generation tests for every structured operand category**

Compile small Simple AST inputs and assert the resulting instruction has:

- `ImmediateOperand.SymbolAddress` for address-of.
- `MemoryReference.Indirect` for pointer dereference.
- `MemoryReference.Indexed` for array indexing.
- `HardwareSlotOperand` for LOADHR/STOREHR.
- `CallSite` for normal calls, asmsubs, extsubs, and syscalls.

- [ ] **Step 2: Run generator tests before migration**

Run:

```bash
gradle :codeGenIntermediate:test --tests "TestVmCodeGen" --console=plain
```

Expected: structured assertions pass through the legacy adapter.

- [ ] **Step 3: Replace direct constructors in the smallest producer**

Migrate `BuiltinFuncGen.kt` to `IRInstructions` factories. Compile:

```bash
gradle :codeGenIntermediate:compileKotlin --console=plain
```

Expected: compilation succeeds.

- [ ] **Step 4: Migrate expression and main IR generation**

Migrate `ExpressionGen.kt` and `IRCodeGen.kt`. Build call sites directly as:

```kotlin
IRInstructions.call(
    Opcode.CALL,
    CallSite(target, arguments, results, effects)
)
```

Fix `replaceMemoryMappedVars` by using `mapMemoryReferences`; it must preserve
index scale, displacement, calls, and targets.

- [ ] **Step 5: Migrate assignment generation**

Replace all direct constructors in `AssignmentGen.kt`, preserving evaluation
order and current temporary-register allocation.

- [ ] **Step 6: Prove no production producer uses the legacy constructor**

Run:

```bash
rg -n "IRInstruction\\(" codeGenIntermediate/src --glob '*.kt'
```

Expected: only `IRInstructionsFactory.kt` or explicitly approved low-level
factory internals match; generator files contain no direct construction.

- [ ] **Step 7: Run codeGenIntermediate tests**

Run:

```bash
gradle :codeGenIntermediate:test --console=plain
```

Expected: all tests pass.

## Task 14: Make structured operands canonical

**Files:**
- Modify: `intermediate/src/prog8/intermediate/IRInstructions.kt`
- Modify: `intermediate/src/prog8/intermediate/IRInstructionsFactory.kt`
- Modify: `intermediate/src/prog8/intermediate/OpcodeSchema.kt`
- Modify: all remaining test files that construct `IRInstruction` directly

- [ ] **Step 1: Search for legacy field and constructor dependencies**

Run:

```bash
rg -n "\\.(reg[123]|fpReg[12]|immediateFp|labelSymbol|labelSymbolOffset|branchTarget|fcallArgs|scale)\\b|IRInstruction\\(" \
  intermediate codeGenIntermediate virtualmachine codeGenM68k codeGenNew6502 \
  --glob '*.kt'
```

Expected: matches are limited to the legacy implementation and tests awaiting
factory conversion.

- [ ] **Step 2: Convert remaining tests and low-level helpers**

Use `IRInstructions` factories and structured assertions. Do not add
test-only compatibility getters.

- [ ] **Step 3: Flip canonical storage**

Replace the old `IRInstruction` fields with:

```kotlin
data class IRInstruction(
    val opcode: Opcode,
    val type: IRDataType? = null,
    val dest: RegisterOperand? = null,
    val destB: RegisterOperand? = null,
    val srcA: RegisterOperand? = null,
    val srcB: RegisterOperand? = null,
    val immediate: ImmediateOperand? = null,
    val hardwareSlot: HardwareSlotOperand? = null,
    val memory: MemoryReference? = null,
    val target: CodeReference? = null,
    val callSite: CallSite? = null
) {
    init {
        OpcodeSchemas.validate(this)
    }
}
```

Implement `registerAccesses`, `uses`, `definitions`, `memoryEffect`, and
`statusEffect` directly from the canonical fields and schema.

- [ ] **Step 4: Remove legacy implementation**

Delete:

- `InstructionFormat` and `instructionFormats`.
- `FunctionCallArgs`.
- Positional register/address/immediate fields.
- `determineReg1Type`, `determineReg2Type`, and `determineReg3Type`.
- Legacy structured adapters and conversion helpers.
- Mutable `branchTarget` and non-serialized `extSubName`.

Store the external function name in `CallTarget.AmigaLibrary` or the direct
call metadata where required.

- [ ] **Step 5: Compile all IR consumers**

Run:

```bash
gradle :intermediate:compileKotlin :codeGenIntermediate:compileKotlin \
  :virtualmachine:compileKotlin :codeGenM68k:compileKotlin \
  :codeGenNew6502:compileKotlin --console=plain
```

Expected: compilation succeeds with no legacy API.

## Task 15: Migrate embedded IR and end-to-end fixtures

**Files:**
- Modify: `compiler/res/prog8lib/virtual/*.p8` files containing `%ir`
- Modify: compiler tests containing literal old-format IR

- [ ] **Step 1: Locate all old embedded IR**

Run:

```bash
rg -n "%ir|\\b(load|store|call|return|jump|add|sub|cmp)[.]?[bwlfp]?\\s+r[0-9]" \
  compiler/res/prog8lib compiler/test examples --glob '*.{p8,kt,p8ir}'
```

Expected: a finite list of standard-library blocks and fixtures to migrate.

- [ ] **Step 2: Convert each `%ir` block through the new codec**

For every instruction, add typed registers and structured memory, slot, and
target syntax. Example:

```text
loadm.w r0.w,[sys.wait.jiffies]
sub.w r0.w,#1.w
storem.w r0.w,[sys.wait.jiffies]
```

Do not change surrounding Prog8 source or assembly formatting.

- [ ] **Step 3: Rebuild and reinstall the compiler**

Run:

```bash
gradle installdist installshadowdist --console=plain
```

Expected: build and installation succeed.

- [ ] **Step 4: Compile representative virtual programs**

Run the existing virtual-target examples or compiler tests that import each
module containing `%ir`. Use:

```bash
gradle :compiler:test --console=plain
```

Expected: all compiler tests pass and no old-format parse errors occur.

## Task 16: Remove migration scaffolding and document the contract

**Files:**
- Modify: `intermediate/src/prog8/intermediate/IRInstructions.kt`
- Modify: `intermediate/src/prog8/intermediate/IRInstructionsFactory.kt`
- Modify: `intermediate/src/prog8/intermediate/OpcodeSchema.kt`
- Modify: `docs/CODEBASE-KNOWLEDGE-GRAPH.md`

- [ ] **Step 1: Prove legacy symbols are gone**

Run:

```bash
rg -n "InstructionFormat|instructionFormats|FunctionCallArgs|reg1direction|reg2direction|fpReg1direction|labelSymbolOffset" \
  intermediate codeGenIntermediate virtualmachine codeGenM68k codeGenNew6502 \
  --glob '*.kt'
```

Expected: no matches.

- [ ] **Step 2: Add final schema invariants**

At startup in tests, validate:

```kotlin
require(OpcodeSchemas.coveredOpcodes == Opcode.entries.toSet())
require(OpcodeSchemas.all().groupBy { it.opcode to it.type }.values.all { it.size == 1 })
```

Ensure every schema has canonical print order and declared memory, status, and
control-flow effects.

- [ ] **Step 3: Update architecture documentation**

Document in `docs/CODEBASE-KNOWLEDGE-GRAPH.md` that:

- `IRInstruction` uses structured operands.
- `OpcodeSchema` is the semantic source of truth.
- Machine backends consume typed uses/definitions and lower structured memory
  and calls.
- `.p8ir` format 2 is intentionally incompatible with older files.

- [ ] **Step 4: Run focused module verification**

Run:

```bash
gradle :intermediate:test :codeGenIntermediate:test :virtualmachine:test \
  :codeGenM68k:test :codeGenNew6502:test --console=plain
```

Expected: all selected module tests pass.

- [ ] **Step 5: Run full repository verification**

Run:

```bash
gradle build --console=plain
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 6: Check the final diff**

Run:

```bash
git --no-pager diff --check
git --no-pager status --short
```

Expected: no whitespace errors; only files required by this migration are
modified or newly created.
