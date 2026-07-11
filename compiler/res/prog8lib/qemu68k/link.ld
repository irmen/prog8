ENTRY(prog8_program_start)
SECTIONS
{
  . = 0x10000;

  .text : {
    *(.text)
  }

  /* .data and .bss are merged into one output section so vlink places
     them in a single LOAD segment with a shared VMA.  If .bss were its
     own output section, vlink would compute the segment's VMA as
     "end_of_previous_segment + alignment" (ignoring the linker script's
     section address), making prog8_program_end point to the section VMA
     while QEMU places bootinfo at the end of the actual LOAD segment.
     Merging them ensures the LOAD segment VMA matches the section VMA,
     so prog8_program_end correctly indicates where QEMU's bootinfo
     data begins. */
  .data : {
    . = ALIGN(4);
    *(.data)

    . = ALIGN(4);
    prog8_bss_section_start = .;

    *(.bss)
    *(COMMON)

    . = ALIGN(2);

    /* Define the end-of-program symbol (also end of the last LOAD segment) */
    prog8_program_end = .;
  }
}
