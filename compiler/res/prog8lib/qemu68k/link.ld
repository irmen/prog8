ENTRY(prog8_program_start)
SECTIONS
{
  . = 0x10000;
  .text : { *(.text) }
  .data : { *(.data) }
  .bss  : { *(.bss)  *(COMMON) }
  _end_of_code = .;

}

