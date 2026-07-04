ENTRY(prog8_program_start)
SECTIONS
{
  . = 0x10000;
  .text : { *(*) }
  .bss  : { *(COMMON) }
  _end_of_code = .;
}

