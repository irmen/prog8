ENTRY(prog8_program_start)
SECTIONS
{
  . = 0x10000;

  .text : {
    *(.text)
  }

  .data : {
    . = ALIGN(4);
    *(.data)
  }

  .bss : {
    . = ALIGN(4);
    prog8_bss_section_start = .;

    *(.bss)
    *(COMMON)

    . = ALIGN(2);

    /* 2. Define the absolute RAM ceiling symbol inside the block */
    prog8_program_end = .;
  }
}
