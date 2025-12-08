# C-Bench-64 comparison

Several benchmarks of https://thred.github.io/c-bench-64/
have been ported to equivalent Prog8 code and the benchmark results have been penciled in in the graphs below.
Because they use the CIA timer for measuring the duration of the runs, the code only works on a C64.

Maybe one day I'll try to integrate the prog8 data properly but their benchmark site is comparing C compilers, which Prog8 clearly is not.

However conclusions so far (note: these are micro benchmarks so interpret the results as you will!)

* Prog8 program size is consistently one of the smallest.
* Prog8 execution speed places it more or less in the middle of the stack.

Measured with Prog8 V12.0 .
Benchmarks ran on a PAL C64 emulated through VICE.

textual results:

| benchmark | size | time     |
|-----------|------|----------|
| crc8      | 1568 | 2.0 sec  |
| crc16     | 1608 | 2.5 sec  |
| crc32     | 1812 | 4.2 sec  |
| pow       | 2152 | 11.7 sec |
| sieve     | 1772 | 22.1 sec |
| sieve_bit | 1918 | 40.1 sec |


![crc8](result-crc8.png)

![crc16](result-crc16.png)

![crc32](result-crc32.png)

![pow](result-pow.png)

![sieve](result-sieve.png)

![sieve-bit](result-sieve-bit.png)
