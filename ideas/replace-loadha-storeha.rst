============================================================
Replace 6502-specific LOADHA/STOREHA opcodes with generic IR
============================================================

Problem
=======

The IR currently has 16 opcodes that are directly named after 6502 CPU registers:

- ``LOADHA``, ``LOADHX``, ``LOADHY`` (load 6502 A/X/Y into virtual register)
- ``LOADHAX``, ``LOADHAY``, ``LOADHXY`` (load 6502 register pairs into virtual register)
- ``LOADHFACZERO``, ``LOADHFACONE`` (load 6502 float accumulators into virtual register)
- ``STOREHA``, ``STOREHX``, ``STOREHY`` (store virtual register into 6502 A/X/Y)
- ``STOREHAX``, ``STOREHAY``, ``STOREHXY`` (store virtual register into 6502 register pairs)
- ``STOREHFACZERO``, ``STOREHFACONE`` (store virtual register into 6502 float accumulators)

These are purely a calling convention bridge: they move values between the IR's
platform-neutral virtual registers and the 6502's concrete hardware registers
(A, X, Y, AX, AY, XY, FAC0, FAC1).  Every non-6502 backend must either ignore
them (losing calling convention information) or emulate 6502 registers internally
(extra complexity with no benefit).

However, the actual calling convention intent is *already encoded* in the
``FunctionCallArgs`` attached to every ``CALL`` instruction (the ``cpuRegister``
field on each ``RegSpec``).  The LOADHA/STOREHA instructions are just a
redundant, 6502-specific encoding of that same information.

Solution
========

Replace the 16 opcodes with **2 parameterized opcodes** that reference an
abstract ``CallingConventionSlot``:

``LOADHR`` *reg1*, *slotIndex*   — load from calling-convention slot N into virtual register *reg1*
``STOREHR`` *reg1*, *slotIndex* — store from virtual register *reg1* into calling-convention slot N

Each slot N is an abstract ordinal whose concrete meaning is defined by the
backend.  The first 6 slots are universally understood hardware accumulator /
index register positions. The last 2 are floating-point accumulators:

+------------+-------------------------+-------------------------+---------------------------+
| Slot index | 6502 (default)          | Z80                     | 68000                     |
+============+=========================+=========================+===========================+
| 0          | A (8-bit)               | A (8-bit)               | D0 (32-bit, low byte)     |
| 1          | X (8-bit)               | F (flags)               | D1                        |
| 2          | Y (8-bit)               | B (8-bit)               | D2                        |
| 3          | AX (16-bit: A low, X hi)| BC (16-bit: B hi, C lo) | D0 (32-bit, low word)     |
| 4          | AY (16-bit: A low, Y hi)| DE (16-bit)             | D0+D1                     |
| 5          | XY (16-bit: X low, Y hi)| HL (16-bit)             | D1+D2                     |
| 6          | FAC0 (64-bit float)     | (soft float)            | FP0 / soft float          |
| 7          | FAC1 (64-bit float)     | (soft float)            | FP1 / soft float          |
+------------+-------------------------+-------------------------+---------------------------+

RegSpec and status flags
------------------------

The ``FunctionCallArgs.RegSpec.cpuRegister`` field (type ``RegisterOrStatusflag?``)
currently serves **two distinct purposes**:

1. **Hardware register binding** (``RegisterOrPair``: A, X, Y, AX, AY, XY, FAC1, FAC2)
2. **Status flag binding** (``Statusflag``: Carry, Zero, etc.)

These are handled differently at the IR level:

- Register bindings emit ``LOADHA``/``STOREHA`` opcodes (being replaced by
  ``LOADHR``/``STOREHR``)
- Status flag bindings emit ``LOAD``+``ROXL`` or ``LSR`` instruction sequences
  (completely separate mechanism, not part of LOADHA/STOREHA)

Therefore, ``cpuRegister`` is replaced by **two separate fields**:

.. code-block:: kotlin

    class RegSpec(
        val dt: IRDataType,
        val registerNum: RegisterNum,
        val callingConventionSlot: CallingConventionSlot?,
        val statusflag: Statusflag?
    )

At most one of ``callingConventionSlot`` and ``statusflag`` is non-null.

CALL annotations serialize as:

- ``@s0``, ``@s3``, etc. for calling convention slots (was ``@A``, ``@AX``)
- ``@Carry``, ``@Pc``, etc. for status flags (unchanged)

Migration summary
=================

:before:  ``LOADHA r5``, ``STOREHAY r7``, ``LOADHFACONE fr3``, ...
:after:   ``LOADHR.b r5, s0``, ``STOREHR.w r7, s4``, ``LOADHR.f fr3, s7``, ...

The IR peephole optimizer can now recognize the common pattern::

    STOREHR.b r5, s0
    CALL $ffd2(r5.b@s0)

and elide the first instruction because the ``CALL``'s ``FunctionCallArgs``
already says "argument 0 goes into slot 0" — the ``STOREHR`` is redundant.

Implementation steps
====================

Step 1: Add ``CallingConventionSlot`` value class
--------------------------------------------------

**File**: ``intermediate/src/prog8/intermediate/CallingConventionSlot.kt`` (new)

.. code-block:: kotlin

    package prog8.intermediate

    @JvmInline
    value class CallingConventionSlot(val ordinal: Int) {
        companion object {
            val ACCUMULATOR   = CallingConventionSlot(0)
            val INDEX_X       = CallingConventionSlot(1)
            val INDEX_Y       = CallingConventionSlot(2)
            val PAIR_ACC_X    = CallingConventionSlot(3)
            val PAIR_ACC_Y    = CallingConventionSlot(4)
            val PAIR_X_Y      = CallingConventionSlot(5)
            val FPA_0         = CallingConventionSlot(6)
            val FPA_1         = CallingConventionSlot(7)

            val ALL = listOf(ACCUMULATOR, INDEX_X, INDEX_Y, PAIR_ACC_X, PAIR_ACC_Y, PAIR_X_Y, FPA_0, FPA_1)
        }
        init { require(ordinal in 0..65535) }
    }

Step 2: Replace opcodes in the Opcode enum
-------------------------------------------

**File**: ``intermediate/src/prog8/intermediate/IRInstructions.kt``

Remove the 16 old opcodes (``LOADHA``, ``LOADHX``, ``LOADHY``, ``LOADHAX``,
``LOADHAY``, ``LOADHXY``, ``LOADHFACZERO``, ``LOADHFACONE``, ``STOREHA``,
``STOREHX``, ``STOREHY``, ``STOREHAX``, ``STOREHAY``, ``STOREHXY``,
``STOREHFACZERO``, ``STOREHFACONE``) from the ``Opcode`` enum and add the
2 new ones:

.. code-block:: kotlin

    LOADHR,    // loadhr reg1, slotIndex
    STOREHR,   // storehr reg1, slotIndex

Also remove the old opcodes from ``OpcodesThatLoad``,
``OpcodesThatSetStatusbitsButNotCarry``, ``OpcodesThatSetStatusbitsIncludingCarry``,
and ``instructionFormats``, and remove the old comment block documenting the old
opcodes (around lines 75-96).

Instruction formats — use dual format to support both integer and float registers:

.. code-block:: kotlin

    Opcode.LOADHR  to InstructionFormat.from("BWL,>r1,<i | F,>fr1,<i"),
    Opcode.STOREHR to InstructionFormat.from("BWL,<r1,<i | F,<fr1,<i"),

Flag set lists — ``LOADHR`` goes into ``OpcodesThatLoad``.  For
``OpcodesThatSetStatusbitsButNotCarry``, use a **slot guard** in the peephole
optimizer instead of adding it unconditionally (see Step 8).  ``STOREHR`` goes
into none of the sets (like the old STOREHA variants — they only affect the
target machine's CPU register, not the IR's virtual registers).

Step 3: Update ``FunctionCallArgs.RegSpec``
-------------------------------------------

**File**: ``intermediate/src/prog8/intermediate/IRInstructions.kt``

Replace ``cpuRegister: RegisterOrStatusflag?`` with two separate fields:

.. code-block:: kotlin

    class RegSpec(
        val dt: IRDataType,
        val registerNum: RegisterNum,
        val callingConventionSlot: CallingConventionSlot?,
        val statusflag: Statusflag?
    )

Update ``toString()`` (~line 1186) to serialize ``@s<N>`` for calling convention
slots and ``@<flag>`` for status flags:

.. code-block:: kotlin

    // Instead of:  "@"+it.reg.cpuRegister.registerOrPair.toString()
    // Emit:       "@s"+it.reg.callingConventionSlot.ordinal

Update ``parseRegspec()`` and ``parseReturnRegspec()`` in ``Utils.kt`` to parse
``@s0``, ``@s3`` etc. as ``CallingConventionSlot`` and ``@Carry`` etc. as
``Statusflag``.

Step 4: Custom ``toString()`` for LOADHR/STOREHR (``s<N>`` notation)
----------------------------------------------------------------------

**File**: ``intermediate/src/prog8/intermediate/IRInstructions.kt``

Add a special case in ``toString()`` before the generic operand loop (before
line 1232) so that slot indices serialize as ``s<N>`` instead of generic hex
``#$xx``:

.. code-block:: kotlin

    if (opcode == Opcode.LOADHR || opcode == Opcode.STOREHR) {
        if (type == IRDataType.FLOAT) append("fr${fpReg1!!.value},")
        else append("r$reg1,")
        immediate?.let { append("s$it") }
        return@buildString trimEnd()
    }

Output examples::

    loadhr.b r5, s0          — load byte from slot 0 (6502 A) into r5
    storehr.w r3, s4         — store word from r3 into slot 4 (6502 AY)
    loadhr.f fr2, s6         -- load float from slot 6 (6502 FAC0) into fr2

Step 5: Update parser for ``s<N>`` slot operands
--------------------------------------------------

**File**: ``intermediate/src/prog8/intermediate/Utils.kt``

In ``parseIRCodeLine()``, add a slot operand check **before** the label
fallthrough (around line 170):

.. code-block:: kotlin

    } else if (oper.length > 1 && oper[0] == 's' && oper.substring(1).all { it.isDigit() }) {
        if (immediateInt != null) throw IRParseException("duplicate immediate operand")
        immediateInt = oper.substring(1).toInt()
    }

This is safe because IR label names are always longer identifiers (e.g.
``.L123``), never bare ``s<N>`` patterns.

Step 6: Update IR code generator to emit LOADHR/STOREHR
--------------------------------------------------------

**File**: ``codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt``

Replace in ``setCpuRegister()`` (line 1715):

.. code-block:: kotlin

    RegisterOrPair.A -> STOREHR.b resultReg, s0
    RegisterOrPair.X -> STOREHR.b resultReg, s1
    RegisterOrPair.Y -> STOREHR.b resultReg, s2
    RegisterOrPair.AX -> STOREHR.w resultReg, s3
    RegisterOrPair.AY -> STOREHR.w resultReg, s4
    RegisterOrPair.XY -> STOREHR.w resultReg, s5
    RegisterOrPair.FAC1 -> STOREHR.f resultFpReg, s6
    RegisterOrPair.FAC2 -> STOREHR.f resultFpReg, s7

Same pattern in ``loadFromCpuRegister()`` (line 1744) — emit ``LOADHR.b rN, s0``
instead of ``LOADHA`` / ``LOADHAX`` etc.

.. code-block:: kotlin

    RegisterOrPair.A -> LOADHR.b tempReg, s0
    RegisterOrPair.X -> LOADHR.b tempReg, s1
    RegisterOrPair.Y -> LOADHR.b tempReg, s2
    RegisterOrPair.AX -> LOADHR.w tempReg, s3
    RegisterOrPair.AY -> LOADHR.w tempReg, s4
    RegisterOrPair.XY -> LOADHR.w tempReg, s5
    RegisterOrPair.FAC1 -> LOADHR.f tempFpReg, s6
    RegisterOrPair.FAC2 -> LOADHR.f tempFpReg, s7

Status flags (``Statusflag.Pc`` etc.) are unchanged — they emit ``LOAD``+``ROXL``
or ``LSR`` instruction sequences, not LOADHR/STOREHR.

**File**: ``codeGenIntermediate/src/prog8/codegen/intermediate/AssignmentGen.kt``

Same replacement in ``assignCpuRegister()`` (line 91).

**File**: ``codeGenIntermediate/src/prog8/codegen/intermediate/BuiltinFuncGen.kt``

Replace:

- Line 191: ``LOADHAY r1=resultvalueReg`` -> ``LOADHR.w resultvalueReg, s4``
- Line 259: ``STOREHA r1=divisionReg`` -> ``STOREHR.b divisionReg, s0``
- Line 264: ``STOREHAY r1=divisionReg`` -> ``STOREHR.w divisionReg, s4``
- Line 704: ``STOREHA r1=byteReg`` -> ``STOREHR.b byteReg, s0``

Step 7: Update VM — fixed handler implementations
--------------------------------------------------

**File**: ``virtualmachine/src/prog8/vm/VirtualMachine.kt``

Add dispatcher entries for `LOADHR` and `STOREHR`:

.. code-block:: kotlin

    Opcode.LOADHR -> InsLOADHR(ins)
    Opcode.STOREHR -> InsSTOREHR(ins)

**IMPORTANT: Float slots must use ``i.fpReg1!!`` (not ``i.reg1!!``)** because
the instruction format maps FLOAT operands to ``fpReg1`` per
``"BWL,>r1,<i | F,>fr1,<i"``.

**IMPORTANT: All code paths must call ``nextPc()``** — no early ``return``
statements that skip it.

Fixed ``InsLOADHR`` — outer ``when`` dispatches float vs integer, all paths
converge on ``nextPc()``:

.. code-block:: kotlin

    private fun InsLOADHR(i: IRInstruction) {
        val slot = CallingConventionSlot(i.immediate!!)
        when(slot) {
            CallingConventionSlot.FPA_0 ->
                registers.setFloat(i.fpReg1!!, hardwareRegisterFAC0)
            CallingConventionSlot.FPA_1 ->
                registers.setFloat(i.fpReg1!!, hardwareRegisterFAC1)
            else -> {
                val value = when(slot) {
                    CallingConventionSlot.ACCUMULATOR -> hardwareRegisterA.toInt()
                    CallingConventionSlot.INDEX_X -> hardwareRegisterX.toInt()
                    CallingConventionSlot.INDEX_Y -> hardwareRegisterY.toInt()
                    CallingConventionSlot.PAIR_ACC_X ->
                        (hardwareRegisterX.toUInt() shl 8 or hardwareRegisterA.toUInt()).toInt()
                    CallingConventionSlot.PAIR_ACC_Y ->
                        (hardwareRegisterY.toUInt() shl 8 or hardwareRegisterA.toUInt()).toInt()
                    CallingConventionSlot.PAIR_X_Y ->
                        (hardwareRegisterY.toUInt() shl 8 or hardwareRegisterX.toUInt()).toInt()
                    else -> throw IllegalArgumentException("unsupported slot $slot")
                }
                when(i.type) {
                    IRDataType.BYTE -> registers.setUB(i.reg1!!, value.toUByte())
                    IRDataType.WORD -> registers.setUW(i.reg1!!, value.toUShort())
                    else -> throw IllegalArgumentException("invalid type for LOADHR")
                }
            }
        }
        nextPc()
    }

``InsSTOREHR`` — same structure, float slots use ``i.fpReg1!!``:

.. code-block:: kotlin

    private fun InsSTOREHR(i: IRInstruction) {
        val slot = CallingConventionSlot(i.immediate!!)
        when(slot) {
            CallingConventionSlot.FPA_0 ->
                hardwareRegisterFAC0 = registers.getFloat(i.fpReg1!!)
            CallingConventionSlot.FPA_1 ->
                hardwareRegisterFAC1 = registers.getFloat(i.fpReg1!!)
            CallingConventionSlot.ACCUMULATOR ->
                hardwareRegisterA = registers.getUB(i.reg1!!)
            CallingConventionSlot.INDEX_X ->
                hardwareRegisterX = registers.getUB(i.reg1!!)
            CallingConventionSlot.INDEX_Y ->
                hardwareRegisterY = registers.getUB(i.reg1!!)
            CallingConventionSlot.PAIR_ACC_X -> {
                val word = registers.getUW(i.reg1!!).toUInt()
                hardwareRegisterA = (word and 255u).toUByte()
                hardwareRegisterX = (word shr 8).toUByte()
            }
            CallingConventionSlot.PAIR_ACC_Y -> {
                val word = registers.getUW(i.reg1!!).toUInt()
                hardwareRegisterA = (word and 255u).toUByte()
                hardwareRegisterY = (word shr 8).toUByte()
            }
            CallingConventionSlot.PAIR_X_Y -> {
                val word = registers.getUW(i.reg1!!).toUInt()
                hardwareRegisterX = (word and 255u).toUByte()
                hardwareRegisterY = (word shr 8).toUByte()
            }
            else -> throw IllegalArgumentException("unsupported slot $slot")
        }
        nextPc()
    }

The old handler methods are removed along with the deprecated opcodes.

Step 8: Peephole optimizer — slot guard for status bits
---------------------------------------------------------

**File**: ``codeGenIntermediate/src/prog8/codegen/intermediate/IRPeepholeOptimizer.kt``

Do **not** add LOADHR unconditionally to ``OpcodesThatSetStatusbitsButNotCarry``,
because loading from a float slot (6, 7) does **not** set CPU status bits.
Instead, add LOADHR to that set and add a **slot guard** at the optimization
point:

.. code-block:: kotlin

    // Line 352, before:
    } else if (previous.opcode in OpcodesThatSetStatusbitsButNotCarry) {

    // After:
    } else if (previous.opcode in OpcodesThatSetStatusbitsButNotCarry
               && (previous.opcode != Opcode.LOADHR || previous.immediate!! < 6)) {

This says: "LOADHR sets status bits only for integer slots 0-5. Skip the
optimization for float slots 6-7."

No guard is needed for the ``OpcodesThatLoad`` check at line 376, because
LOADHR from any slot always loads a value into a virtual register.

| Approach comparison:
| Option 1 (split opcode): LOADHR + LOADHRF — undermines unification goal
| Option 2 (slot-aware sets): changes data structures — higher complexity
| Option 3 (drop from set): loses optimization for integer-slot LOADHR
| **Option 4 (slot guard): keeps 2 opcodes, zero data-structure changes, cheap runtime check** — selected

Step 9: Update 6502 backend
----------------------------

The 6502 codegen (``codeGenCpu6502/``) does **not** process IR opcodes at all —
it works from the Simplified AST directly.  No changes needed there.

However, if a future IR-based 6502 backend is built, it would map slots back
to real 6502 registers:

::

    slot 0  ->  lda / sta     (6502 A register, byte)
    slot 1  ->  ldx / stx     (6502 X register, byte)
    slot 2  ->  ldy / sty     (6502 Y register, byte)
    slot 3  ->  sta_lsb / stx_msb  (6502 AX pair, word)
    slot 4  ->  sta_lsb / sty_msb  (6502 AY pair, word)
    slot 5  ->  stx / sty     (6502 XY pair, word)
    slot 6  ->  (6502 FAC0 via KERNAL or software float)
    slot 7  ->  (6502 FAC1 via KERNAL or software float)

Step 10: Test
-------------

1. ``gradle build --console=plain`` — full test suite must pass without regressions
2. Write unit tests for the new LOADHR/STOREHR opcodes (currently no tests exist
   for the old opcodes either — add coverage)
3. Manual: compile a Prog8 program with an ``extsub`` call and verify the
   generated IR contains ``LOADHR``/``STOREHR`` with ``s<N>`` slot notation
4. Manual: ``prog8c -target virtual -emu`` on programs using ``asmsub`` /
   ``extsub`` — verify correct output


Documentation
=============

A. IR instruction spec comment (``IRInstructions.kt``)
--------------------------------------------------------

Add to the comment block (around line 66)::

    CALLING CONVENTION SLOTS
    ------------------------
    loadhr      reg1, slotIndex     - load register reg1 from calling-convention slot slotIndex.
                                      The backend maps each slot to a real CPU register.
                                      Slots 0-5 are 8/16-bit integer, 6-7 are 64-bit float.
    storehr     reg1, slotIndex     - store register reg1 into calling-convention slot slotIndex.

    Slot usage convention:
        0 = primary accumulator (A on 6502, AL on 8086, D0 on 68000)
        1 = index/secondary       (X on 6502, D1 on 68000)
        2 = index/secondary       (Y on 6502, D2 on 68000)
        3 = 16-bit pair: slot 0 + slot 1  (AX on 6502, BC on Z80)
        4 = 16-bit pair: slot 0 + slot 2  (AY on 6502)
        5 = 16-bit pair: slot 1 + slot 2  (XY on 6502, HL on Z80)
        6 = floating-point accumulator 0   (FAC0 on 6502)
        7 = floating-point accumulator 1   (FAC1 on 6502)

    See CallingConventionSlot.kt for the authoritative list.

B. Backend developer guide (new file: ``docs/source/backends.rst``)
--------------------------------------------------------------------

.. code-block:: rst

    .. _backends:

    ============================
    Writing a new code generator
    ============================

    The IR instruction set is mostly target-agnostic, except for the
    **calling-convention slots**: ``LOADHR`` and ``STOREHR``.  Every backend
    must define what each slot index means on its target.


    Calling Convention Slots
    ========================

    ====== ====== ========== ========= ========= ============
    Slot    Width  6502       Z80       8086      68000
    ====== ====== ========== ========= ========= ============
    0      8-bit  A          A         AL        D0
    1      8-bit  X          F         DH        D1
    2      8-bit  Y          B         DL        D2
    3      16-bit AX         BC        AX        D0
    4      16-bit AY         DE        DX        D0:D1
    5      16-bit XY         HL        BX        D1:D2
    6      64-bit FAC0      (soft)    (x87)     FP0
    7      64-bit FAC1      (soft)    (x87)     FP1
    ====== ====== ========== ========= ========= ============

    When building a backend, implement each slot with the corresponding
    register move instruction for your target.  The ``CALL`` instruction's
    ``FunctionCallArgs`` already carries the slot index for each argument
    and return value, so you can match ``STOREHR`` ... ``CALL`` pairs to
    emit optimal prologue/epilogue code.
