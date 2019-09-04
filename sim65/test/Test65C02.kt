import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*


/*

Unit test suite adapted from Py65   https://github.com/mnaberez/py65

Copyright (c) 2008-2018, Mike Naberezny and contributors.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */


@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Disabled("there is no 65C02 cpu implementation at this time")          // TODO create a 65c02 cpu!
class Test65C02 : TestCommon6502() {
    //  CMOS 65C02 Tests

    // Reset

    @Test
    fun test_reset_clears_decimal_flag() {
        // W65C02S Datasheet, Apr 14 2009, Table 7-1 Operational Enhancements
        // NMOS 6502 decimal flag = indetermine after reset, CMOS 65C02 = 0
        mpu.Status.D = true
        mpu.reset()
        assertFalse(mpu.Status.D)
    }

    // ADC Zero Page, Indirect

    @Test
    fun test_adc_bcd_off_zp_ind_carry_clear_in_accumulator_zeroes() {
        mpu.A = 0x00
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_carry_set_in_accumulator_zero() {
        mpu.A = 0
        mpu.Status.C = true
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x01, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.C)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_carry_clear_in_no_carry_clear_out() {
        mpu.A = 0x01
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFE
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0xFF, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.C)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_carry_clear_in_carry_set_out() {
        mpu.A = 0x02
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x01, mpu.A)
        assertTrue(mpu.Status.C)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_overflow_cleared_no_carry_01_plus_01() {
        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x02, mpu.A)
        assertFalse(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_overflow_cleared_no_carry_01_plus_ff() {
        mpu.Status.C = false
        mpu.A = 0x01
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_overflow_set_no_carry_7f_plus_01() {
        mpu.Status.C = false
        mpu.A = 0x7f
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_overflow_set_no_carry_80_plus_ff() {
        mpu.Status.C = false
        mpu.A = 0x80
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x7f, mpu.A)
        assertTrue(mpu.Status.V)
    }

    @Test
    fun test_adc_bcd_off_zp_ind_overflow_set_on_40_plus_40() {
        mpu.A = 0x40
        // $0000 ADC ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x72, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x40
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertFalse(mpu.Status.Z)
    }

    // AND Zero Page, Indirect

    @Test
    fun test_and_zp_ind_all_zeros_setting_zero_flag() {
        mpu.A = 0xFF
        // $0000 AND ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x32, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_and_zp_ind_zeros_and_ones_setting_negative_flag() {
        mpu.A = 0xFF
        // $0000 AND ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x32, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xAA
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0xAA, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    // BIT (Absolute, X-Indexed)

    @Test
    fun test_bit_abs_x_copies_bit_7_of_memory_to_n_flag_when_0() {
        mpu.Status.N = false
        mpu.X = 0x02
        // $0000 BIT $FEEB,X
        writeMem(memory, 0x0000, listOf(0x3C, 0xEB, 0xFE))
        memory[0xFEED] = 0xFF
        mpu.A = 0xFF
        mpu.step()
        assertTrue(mpu.Status.N)
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x0003, mpu.PC)
    }

    @Test
    fun test_bit_abs_x_copies_bit_7_of_memory_to_n_flag_when_1() {
        mpu.Status.N = true
        mpu.X = 0x02
        // $0000 BIT $FEEB,X
        writeMem(memory, 0x0000, listOf(0x3C, 0xEB, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0xFF
        mpu.step()
        assertFalse(mpu.Status.N)
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x0003, mpu.PC)
    }

    @Test
    fun test_bit_abs_x_copies_bit_6_of_memory_to_v_flag_when_0() {
        mpu.Status.V = false
        mpu.X = 0x02
        // $0000 BIT $FEEB,X
        writeMem(memory, 0x0000, listOf(0x3C, 0xEB, 0xFE))
        memory[0xFEED] = 0xFF
        mpu.A = 0xFF
        mpu.step()
        assertTrue(mpu.Status.V)
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x0003, mpu.PC)
    }

    @Test
    fun test_bit_abs_x_copies_bit_6_of_memory_to_v_flag_when_1() {
        mpu.Status.V = true
        mpu.X = 0x02
        // $0000 BIT $FEEB,X
        writeMem(memory, 0x0000, listOf(0x3C, 0xEB, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0xFF
        mpu.step()
        assertFalse(mpu.Status.V)
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x0003, mpu.PC)
    }

    @Test
    fun test_bit_abs_x_stores_result_of_and_in_z_preserves_a_when_1() {
        mpu.Status.Z = false
        mpu.X = 0x02
        // $0000 BIT $FEEB,X
        writeMem(memory, 0x0000, listOf(0x3C, 0xEB, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z)
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0xFEED])
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x0003, mpu.PC)
    }

    @Test
    fun test_bit_abs_x_stores_result_of_and_nonzero_in_z_preserves_a() {
        mpu.Status.Z = true
        mpu.X = 0x02
        // $0000 BIT $FEEB,X
        writeMem(memory, 0x0000, listOf(0x3C, 0xEB, 0xFE))
        memory[0xFEED] = 0x01
        mpu.A = 0x01
        mpu.step()
        assertFalse(mpu.Status.Z) // result of AND is non-zero
        assertEquals(0x01, mpu.A)
        assertEquals(0x01, memory[0xFEED])
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x0003, mpu.PC)
    }

    @Test
    fun test_bit_abs_x_stores_result_of_and_when_zero_in_z_preserves_a() {
        mpu.Status.Z = false
        mpu.X = 0x02
        // $0000 BIT $FEEB,X
        writeMem(memory, 0x0000, listOf(0x3C, 0xEB, 0xFE))
        memory[0xFEED] = 0x00
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z) // result of AND is zero
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0xFEED])
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x0003, mpu.PC)
    }

    // BIT (Immediate)

    @Test
    fun test_bit_imm_does_not_affect_n_and_z_flags() {
        mpu.Status.N = true
        mpu.Status.V = true
        // $0000 BIT #$FF
        writeMem(memory, 0x0000, listOf(0x89, 0xff))
        mpu.A = 0x00
        mpu.step()
        assertTrue(mpu.Status.N)
        assertTrue(mpu.Status.V)
        assertEquals(0x00, mpu.A)
        assertEquals(2, mpu.totalCycles)
        assertEquals(0x02, mpu.PC)
    }

    @Test
    fun test_bit_imm_stores_result_of_and_in_z_preserves_a_when_1() {
        mpu.Status.Z = false
        // $0000 BIT #$00
        writeMem(memory, 0x0000, listOf(0x89, 0x00))
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z)
        assertEquals(0x01, mpu.A)
        assertEquals(2, mpu.totalCycles)
        assertEquals(0x02, mpu.PC)
    }

    @Test
    fun test_bit_imm_stores_result_of_and_when_nonzero_in_z_preserves_a() {
        mpu.Status.Z = true
        // $0000 BIT #$01
        writeMem(memory, 0x0000, listOf(0x89, 0x01))
        mpu.A = 0x01
        mpu.step()
        assertFalse(mpu.Status.Z) // result of AND is non-zero
        assertEquals(0x01, mpu.A)
        assertEquals(2, mpu.totalCycles)
        assertEquals(0x02, mpu.PC)
    }

    @Test
    fun test_bit_imm_stores_result_of_and_when_zero_in_z_preserves_a() {
        mpu.Status.Z = false
        // $0000 BIT #$00
        writeMem(memory, 0x0000, listOf(0x89, 0x00))
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z) // result of AND is zero
        assertEquals(0x01, mpu.A)
        assertEquals(2, mpu.totalCycles)
        assertEquals(0x02, mpu.PC)
    }

    // BIT (Zero Page, X-Indexed)

    @Test
    fun test_bit_zp_x_copies_bit_7_of_memory_to_n_flag_when_0() {
        mpu.Status.N = false
        // $0000 BIT $0010,X
        writeMem(memory, 0x0000, listOf(0x34, 0x10))
        memory[0x0013] = 0xFF
        mpu.X = 0x03
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
        assertTrue(mpu.Status.N)
    }

    @Test
    fun test_bit_zp_x_copies_bit_7_of_memory_to_n_flag_when_1() {
        mpu.Status.N = true
        // $0000 BIT $0010,X
        writeMem(memory, 0x0000, listOf(0x34, 0x10))
        memory[0x0013] = 0x00
        mpu.X = 0x03
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_bit_zp_x_copies_bit_6_of_memory_to_v_flag_when_0() {
        mpu.Status.V = false
        // $0000 BIT $0010,X
        writeMem(memory, 0x0000, listOf(0x34, 0x10))
        memory[0x0013] = 0xFF
        mpu.X = 0x03
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
        assertTrue(mpu.Status.V)
    }

    @Test
    fun test_bit_zp_x_copies_bit_6_of_memory_to_v_flag_when_1() {
        mpu.Status.V = true
        // $0000 BIT $0010,X
        writeMem(memory, 0x0000, listOf(0x34, 0x10))
        memory[0x0013] = 0x00
        mpu.X = 0x03
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
        assertFalse(mpu.Status.V)
    }

    @Test
    fun test_bit_zp_x_stores_result_of_and_in_z_preserves_a_when_1() {
        mpu.Status.Z = false
        // $0000 BIT $0010,X
        writeMem(memory, 0x0000, listOf(0x34, 0x10))
        memory[0x0013] = 0x00
        mpu.X = 0x03
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z)
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0x0010 + mpu.X])
    }

    @Test
    fun test_bit_zp_x_stores_result_of_and_when_nonzero_in_z_preserves_a() {
        mpu.Status.Z = true
        // $0000 BIT $0010,X
        writeMem(memory, 0x0000, listOf(0x34, 0x10))
        memory[0x0013] = 0x01
        mpu.X = 0x03
        mpu.A = 0x01
        mpu.step()
        assertFalse(mpu.Status.Z) // result of AND is non-zero
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
        assertEquals(0x01, mpu.A)
        assertEquals(0x01, memory[0x0010 + mpu.X])
    }

    @Test
    fun test_bit_zp_x_stores_result_of_and_when_zero_in_z_preserves_a() {
        mpu.Status.Z = false
        // $0000 BIT $0010,X
        writeMem(memory, 0x0000, listOf(0x34, 0x10))
        memory[0x0013] = 0x00
        mpu.X = 0x03
        mpu.A = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
        assertTrue(mpu.Status.Z) // result of AND is zero
        assertEquals(0x01, mpu.A)
        assertEquals(0x00, memory[0x0010 + mpu.X])
    }

    // BRK

    @Test
    fun test_brk_clears_decimal_flag() {
        mpu.Status.D = true
        // $C000 BRK
        memory[0xC000] = 0x00
        mpu.PC = 0xC000
        mpu.step()
        assertTrue(mpu.Status.B)
        assertFalse(mpu.Status.D)
    }

    // CMP Zero Page, Indirect

    @Test
    fun test_cmp_zpi_sets_z_flag_if_equal() {
        mpu.A = 0x42
        // $0000 AND ($10)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xd2, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x42
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x42, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_cmp_zpi_resets_z_flag_if_unequal() {
        mpu.A = 0x43
        // $0000 AND ($10)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xd2, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x42
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x43, mpu.A)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    // EOR Zero Page, Indirect

    @Test
    fun test_eor_zp_ind_flips_bits_over_setting_z_flag() {
        mpu.A = 0xFF
        // $0000 EOR ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x52, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertEquals(0xFF, memory[0xABCD])
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_eor_zp_ind_flips_bits_over_setting_n_flag() {
        mpu.A = 0x00
        // $0000 EOR ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x52, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0xFF, mpu.A)
        assertEquals(0xFF, memory[0xABCD])
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    // INC Accumulator

    @Test
    fun test_inc_acc_increments_accum() {
        memory[0x0000] = 0x1A
        mpu.A = 0x42
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x43, mpu.A)
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_inc_acc_increments_accum_rolls_over_and_sets_zero_flag() {
        memory[0x0000] = 0x1A
        mpu.A = 0xFF
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    @Test
    fun test_inc_acc_sets_negative_flag_when_incrementing_above_7F() {
        memory[0x0000] = 0x1A
        mpu.A = 0x7F
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0x80, mpu.A)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)
    }

    // JMP Indirect

    @Test
    fun test_jmp_ind_does_not_have_page_wrap_bug() {
        writeMem(memory, 0x10FF, listOf(0xCD, 0xAB))
        // $0000 JMP ($10FF)
        writeMem(memory, 0, listOf(0x6c, 0xFF, 0x10))
        mpu.step()
        assertEquals(0xABCD, mpu.PC)
        assertEquals(6, mpu.totalCycles)
    }

    // JMP Indirect Absolute X-Indexed

    @Test
    fun test_jmp_iax_jumps_to_address() {
        mpu.X = 2
        // $0000 JMP ($ABCD,X)
        // $ABCF Vector to $1234
        writeMem(memory, 0x0000, listOf(0x7C, 0xCD, 0xAB))
        writeMem(memory, 0xABCF, listOf(0x34, 0x12))
        mpu.step()
        assertEquals(0x1234, mpu.PC)
        assertEquals(6, mpu.totalCycles)
    }

    // LDA Zero Page, Indirect

    @Test
    fun test_lda_zp_ind_loads_a_sets_n_flag() {
        mpu.A = 0x00
        // $0000 LDA ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xB2, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x80
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x80, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    @Test
    fun test_lda_zp_ind_loads_a_sets_z_flag() {
        mpu.A = 0x00
        // $0000 LDA ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0xB2, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
    }

    // ORA Zero Page, Indirect

    @Test
    fun test_ora_zp_ind_zeroes_or_zeros_sets_z_flag() {
        mpu.Status.Z = false
        mpu.A = 0x00
        mpu.Y = 0x12  // These should not affect the ORA
        mpu.X = 0x34
        // $0000 ORA ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x12, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_ora_zp_ind_turns_bits_on_sets_n_flag() {
        mpu.Status.N = false
        mpu.A = 0x03
        // $0000 ORA ($0010)
        // $0010 Vector to $ABCD
        writeMem(memory, 0x0000, listOf(0x12, 0x10))
        writeMem(memory, 0x0010, listOf(0xCD, 0xAB))
        memory[0xABCD] = 0x82
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x83, mpu.A)
        assertTrue(mpu.Status.N)
        assertFalse(mpu.Status.Z)
    }

    // PHX

    @Test
    fun test_phx_pushes_x_and_updates_sp() {
        mpu.X = 0xAB
        // $0000 PHX
        memory[0x0000] = 0xDA
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.X)
        assertEquals(0xAB, memory[0x01FF])
        assertEquals(0xFE, mpu.SP)
        assertEquals(3, mpu.totalCycles)
    }

    // PHY

    @Test
    fun test_phy_pushes_y_and_updates_sp() {
        mpu.Y = 0xAB
        // $0000 PHY
        memory[0x0000] = 0x5A
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.Y)
        assertEquals(0xAB, memory[0x01FF])
        assertEquals(0xFE, mpu.SP)
        assertEquals(3, mpu.totalCycles)
    }

    // PLX

    @Test
    fun test_plx_pulls_top_byte_from_stack_into_x_and_updates_sp() {
        // $0000 PLX
        memory[0x0000] = 0xFA
        memory[0x01FF] = 0xAB
        mpu.SP = 0xFE
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.X)
        assertEquals(0xFF, mpu.SP)
        assertEquals(4, mpu.totalCycles)
    }

    // PLY

    @Test
    fun test_ply_pulls_top_byte_from_stack_into_y_and_updates_sp() {
        // $0000 PLY
        memory[0x0000] = 0x7A
        memory[0x01FF] = 0xAB
        mpu.SP = 0xFE
        mpu.step()
        assertEquals(0x0001, mpu.PC)
        assertEquals(0xAB, mpu.Y)
        assertEquals(0xFF, mpu.SP)
        assertEquals(4, mpu.totalCycles)
    }

    // RMB0

    @Test
    fun test_rmb0_clears_bit_0_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB0 $43
        writeMem(memory, 0x0000, listOf(0x07, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b11111110
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_rmb0_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB0 $43
        writeMem(memory, 0x0000, listOf(0x07, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // RMB1

    @Test
    fun test_rmb1_clears_bit_1_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB1 $43
        writeMem(memory, 0x0000, listOf(0x17, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b11111101
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_rmb1_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB1 $43
        writeMem(memory, 0x0000, listOf(0x17, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // RMB2

    @Test
    fun test_rmb2_clears_bit_2_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB2 $43
        writeMem(memory, 0x0000, listOf(0x27, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b11111011
        assertEquals(expected, memory[0x0043].toInt())

    }

    @Test
    fun test_rmb2_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB2 $43
        writeMem(memory, 0x0000, listOf(0x27, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // RMB3

    @Test
    fun test_rmb3_clears_bit_3_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB3 $43
        writeMem(memory, 0x0000, listOf(0x37, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b11110111
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_rmb3_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB3 $43
        writeMem(memory, 0x0000, listOf(0x37, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // RMB4

    @Test
    fun test_rmb4_clears_bit_4_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB4 $43
        writeMem(memory, 0x0000, listOf(0x47, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b11101111
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_rmb4_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB4 $43
        writeMem(memory, 0x0000, listOf(0x47, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // RMB5

    @Test
    fun test_rmb5_clears_bit_5_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB5 $43
        writeMem(memory, 0x0000, listOf(0x57, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b11011111
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_rmb5_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB5 $43
        writeMem(memory, 0x0000, listOf(0x57, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // RMB6

    @Test
    fun test_rmb6_clears_bit_6_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB6 $43
        writeMem(memory, 0x0000, listOf(0x67, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b10111111
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_rmb6_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB6 $43
        writeMem(memory, 0x0000, listOf(0x67, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // RMB7

    @Test
    fun test_rmb7_clears_bit_7_without_affecting_other_bits() {
        memory[0x0043] = 0b11111111
        // $0000 RMB7 $43
        writeMem(memory, 0x0000, listOf(0x77, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b01111111
        assertEquals(expected, memory[0x0043].toInt())

    }

    @Test
    fun test_rmb7_does_not_affect_status_register() {
        memory[0x0043] = 0b11111111
        // $0000 RMB7 $43
        writeMem(memory, 0x0000, listOf(0x77, 0x43))
        val expected = 0b01010101
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // STA Zero Page, Indirect

    @Test
    fun test_sta_zp_ind_stores_a_leaves_a_and_n_flag_unchanged() {
        val flags = 0xFF and fNEGATIVE.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0xFF
        // $0000 STA ($0010)
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0x92, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0xFF, memory[0xFEED])
        assertEquals(0xFF, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    @Test
    fun test_sta_zp_ind_stores_a_leaves_a_and_z_flag_unchanged() {
        val flags = 0xFF and fZERO.inv()
        mpu.Status.fromByte(flags)
        mpu.A = 0x00
        // $0000 STA ($0010)
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0x92, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED] = 0xFF
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, memory[0xFEED])
        assertEquals(0x00, mpu.A)
        assertEquals(flags, mpu.Status.asByte().toInt())
    }

    // SMB0

    @Test
    fun test_smb0_sets_bit_0_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB0 $43
        writeMem(memory, 0x0000, listOf(0x87, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b00000001
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_smb0_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB0 $43
        writeMem(memory, 0x0000, listOf(0x87, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SMB1

    @Test
    fun test_smb1_sets_bit_1_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB1 $43
        writeMem(memory, 0x0000, listOf(0x97, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b00000010
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_smb1_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB1 $43
        writeMem(memory, 0x0000, listOf(0x97, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SMB2

    @Test
    fun test_smb2_sets_bit_2_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB2 $43
        writeMem(memory, 0x0000, listOf(0xA7, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b00000100
        assertEquals(expected, memory[0x0043].toInt())

    }

    @Test
    fun test_smb2_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB2 $43
        writeMem(memory, 0x0000, listOf(0xA7, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SMB3

    @Test
    fun test_smb3_sets_bit_3_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB3 $43
        writeMem(memory, 0x0000, listOf(0xB7, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b00001000
        assertEquals(expected, memory[0x0043].toInt())

    }

    @Test
    fun test_smb3_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB3 $43
        writeMem(memory, 0x0000, listOf(0xB7, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SMB4

    @Test
    fun test_smb4_sets_bit_4_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB4 $43
        writeMem(memory, 0x0000, listOf(0xC7, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b00010000
        assertEquals(expected, memory[0x0043].toInt())

    }

    @Test
    fun test_smb4_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB4 $43
        writeMem(memory, 0x0000, listOf(0xC7, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SMB5

    @Test
    fun test_smb5_sets_bit_5_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB5 $43
        writeMem(memory, 0x0000, listOf(0xD7, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b00100000
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_smb5_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB5 $43
        writeMem(memory, 0x0000, listOf(0xD7, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SMB6

    @Test
    fun test_smb6_sets_bit_6_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB6 $43
        writeMem(memory, 0x0000, listOf(0xE7, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b01000000
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_smb6_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB6 $43
        writeMem(memory, 0x0000, listOf(0xE7, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SMB7

    @Test
    fun test_smb7_sets_bit_7_without_affecting_other_bits() {
        memory[0x0043] = 0b00000000
        // $0000 SMB7 $43
        writeMem(memory, 0x0000, listOf(0xF7, 0x43))
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        val expected = 0b10000000
        assertEquals(expected, memory[0x0043].toInt())
    }

    @Test
    fun test_smb7_does_not_affect_status_register() {
        memory[0x0043] = 0b00000000
        // $0000 SMB7 $43
        writeMem(memory, 0x0000, listOf(0xF7, 0x43))
        val expected = 0b11001100
        mpu.Status.fromByte(expected)
        mpu.step()
        assertEquals(expected, mpu.Status.asByte().toInt())
    }

    // SBC Zero Page, Indirect

    @Test
    fun test_sbc_zp_ind_all_zeros_and_no_borrow_is_zero() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x00
        // $0000 SBC ($10)
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF2, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_zp_ind_downto_zero_no_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = true  // borrow = 0
        mpu.A = 0x01
        // $0000 SBC ($10)
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF2, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED] = 0x01
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_zp_ind_downto_zero_with_borrow_sets_z_clears_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x01
        // $0000 SBC ($10)
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF2, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED] = 0x00
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x00, mpu.A)
        assertFalse(mpu.Status.N)
        assertTrue(mpu.Status.C)
        assertTrue(mpu.Status.Z)
    }

    @Test
    fun test_sbc_zp_ind_downto_four_with_borrow_clears_z_n() {
        mpu.Status.D = false
        mpu.Status.C = false  // borrow = 1
        mpu.A = 0x07
        // $0000 SBC ($10)
        // $0010 Vector to $FEED
        writeMem(memory, 0x0000, listOf(0xF2, 0x10))
        writeMem(memory, 0x0010, listOf(0xED, 0xFE))
        memory[0xFEED] = 0x02
        mpu.step()
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
        assertEquals(0x04, mpu.A)
        assertFalse(mpu.Status.N)
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.C)
    }

    // STZ Zero Page

    @Test
    fun test_stz_zp_stores_zero() {
        memory[0x0032] = 0x88
        // #0000 STZ $32
        memory[0x0000] = 0x64
        memory[0x0001] = 0x32
        assertEquals(0x88, memory[0x0032])
        mpu.step()
        assertEquals(0x00, memory[0x0032])
        assertEquals(0x0002, mpu.PC)
        assertEquals(3, mpu.totalCycles)
    }

    // STZ Zero Page, X-Indexed

    @Test
    fun test_stz_zp_x_stores_zero() {
        memory[0x0032] = 0x88
        // $0000 STZ $32,X
        memory[0x0000] = 0x74
        memory[0x0001] = 0x32
        assertEquals(0x88, memory[0x0032])
        mpu.step()
        assertEquals(0x00, memory[0x0032])
        assertEquals(0x0002, mpu.PC)
        assertEquals(4, mpu.totalCycles)
    }

    // STZ Absolute

    @Test
    fun test_stz_abs_stores_zero() {
        memory[0xFEED] = 0x88
        // $0000 STZ $FEED
        writeMem(memory, 0x0000, listOf(0x9C, 0xED, 0xFE))
        assertEquals(0x88, memory[0xFEED])
        mpu.step()
        assertEquals(0x00, memory[0xFEED])
        assertEquals(0x0003, mpu.PC)
        assertEquals(4, mpu.totalCycles)
    }

    // STZ Absolute, X-Indexed

    @Test
    fun test_stz_abs_x_stores_zero() {
        memory[0xFEED] = 0x88
        mpu.X = 0x0D
        // $0000 STZ $FEE0,X
        writeMem(memory, 0x0000, listOf(0x9E, 0xE0, 0xFE))
        assertEquals(0x88, memory[0xFEED])
        assertEquals(0x0D, mpu.X)
        mpu.step()
        assertEquals(0x00, memory[0xFEED])
        assertEquals(0x0003, mpu.PC)
        assertEquals(5, mpu.totalCycles)
    }

    // TSB Zero Page

    @Test
    fun test_tsb_zp_ones() {
        memory[0x00BB] = 0xE0
        // $0000 TSB $BD
        writeMem(memory, 0x0000, listOf(0x04, 0xBB))
        mpu.A = 0x70
        assertEquals(0xE0, memory[0x00BB])
        mpu.step()
        assertEquals(0xF0, memory[0x00BB])
        assertFalse(mpu.Status.Z)
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
    }

    @Test
    fun test_tsb_zp_zeros() {
        memory[0x00BB] = 0x80
        // $0000 TSB $BD
        writeMem(memory, 0x0000, listOf(0x04, 0xBB))
        mpu.A = 0x60
        assertEquals(0x80, memory[0x00BB])
        mpu.step()
        assertEquals(0xE0, memory[0x00BB])
        assertTrue(mpu.Status.Z)
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
    }

    // TSB Absolute

    @Test
    fun test_tsb_abs_ones() {
        memory[0xFEED] = 0xE0
        // $0000 TSB $FEED
        writeMem(memory, 0x0000, listOf(0x0C, 0xED, 0xFE))
        mpu.A = 0x70
        assertEquals(0xE0, memory[0xFEED])
        mpu.step()
        assertEquals(0xF0, memory[0xFEED])
        assertFalse(mpu.Status.Z)
        assertEquals(0x0003, mpu.PC)
        assertEquals(6, mpu.totalCycles)
    }

    @Test
    fun test_tsb_abs_zeros() {
        memory[0xFEED] = 0x80
        // $0000 TSB $FEED
        writeMem(memory, 0x0000, listOf(0x0C, 0xED, 0xFE))
        mpu.A = 0x60
        assertEquals(0x80, memory[0xFEED])
        mpu.step()
        assertEquals(0xE0, memory[0xFEED])
        assertTrue(mpu.Status.Z)
        assertEquals(0x0003, mpu.PC)
        assertEquals(6, mpu.totalCycles)
    }

    // TRB Zero Page

    @Test
    fun test_trb_zp_ones() {
        memory[0x00BB] = 0xE0
        // $0000 TRB $BD
        writeMem(memory, 0x0000, listOf(0x14, 0xBB))
        mpu.A = 0x70
        assertEquals(0xE0, memory[0x00BB])
        mpu.step()
        assertEquals(0x80, memory[0x00BB])
        assertFalse(mpu.Status.Z)
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
    }

    @Test
    fun test_trb_zp_zeros() {
        memory[0x00BB] = 0x80
        // $0000 TRB $BD
        writeMem(memory, 0x0000, listOf(0x14, 0xBB))
        mpu.A = 0x60
        assertEquals(0x80, memory[0x00BB])
        mpu.step()
        assertEquals(0x80, memory[0x00BB])
        assertTrue(mpu.Status.Z)
        assertEquals(0x0002, mpu.PC)
        assertEquals(5, mpu.totalCycles)
    }

    // TRB Absolute

    @Test
    fun test_trb_abs_ones() {
        memory[0xFEED] = 0xE0
        // $0000 TRB $FEED
        writeMem(memory, 0x0000, listOf(0x1C, 0xED, 0xFE))
        mpu.A = 0x70
        assertEquals(0xE0, memory[0xFEED])
        mpu.step()
        assertEquals(0x80, memory[0xFEED])
        assertFalse(mpu.Status.Z)
        assertEquals(0x0003, mpu.PC)
        assertEquals(6, mpu.totalCycles)
    }

    @Test
    fun test_trb_abs_zeros() {
        memory[0xFEED] = 0x80
        // $0000 TRB $FEED
        writeMem(memory, 0x0000, listOf(0x1C, 0xED, 0xFE))
        mpu.A = 0x60
        assertEquals(0x80, memory[0xFEED])
        mpu.step()
        assertEquals(0x80, memory[0xFEED])
        assertTrue(mpu.Status.Z)
        assertEquals(0x0003, mpu.PC)
        assertEquals(6, mpu.totalCycles)
    }

    @Test
    fun test_dec_a_decreases_a() {
        // $0000 DEC A
        memory[0x0000] = 0x3a
        mpu.A = 0x48
        mpu.step()
        assertFalse(mpu.Status.Z)
        assertFalse(mpu.Status.N)
        assertEquals(0x47, mpu.A)
    }

    @Test
    fun test_dec_a_sets_zero_flag() {
        // $0000 DEC A
        memory[0x0000] = 0x3a
        mpu.A = 0x01
        mpu.step()
        assertTrue(mpu.Status.Z)
        assertFalse(mpu.Status.N)
        assertEquals(0x00, mpu.A)
    }

    @Test
    fun test_dec_a_wraps_at_zero() {
        // $0000 DEC A
        memory[0x0000] = 0x3a
        mpu.A = 0x00
        mpu.step()
        assertFalse(mpu.Status.Z)
        assertTrue(mpu.Status.N)
        assertEquals(0xFF, mpu.A)
    }

    @Test
    fun test_bra_forward() {
        // $0000 BRA $10
        writeMem(memory, 0x0000, listOf(0x80, 0x10))
        mpu.step()
        assertEquals(0x12, mpu.PC)
        assertEquals(2, mpu.totalCycles)
    }

    @Test
    fun test_bra_backward() {
        // $0240 BRA $F0
        writeMem(memory, 0x0000, listOf(0x80, 0xF0))
        mpu.PC = 0x0204
        mpu.step()
        assertEquals(0x1F6, mpu.PC)
        assertEquals(3, mpu.totalCycles)  // Crossed boundry
    }

    // WAI

    @Test
    fun test_wai_sets_waiting() {
        assertFalse(mpu.waiting)
        // $0240 WAI
        memory[0x0204] = 0xcb
        mpu.PC = 0x0204
        mpu.step()
        assertTrue(mpu.waiting)
        assertEquals(0x0205, mpu.PC)
        assertEquals(3, mpu.totalCycles)
    }
}
