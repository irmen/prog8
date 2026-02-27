*********************
Performance profiling
*********************


Run-time memory profiling with the X16 emulator
-----------------------------------------------
.. index:: single: Memory profiling

The compiler has the ``-dumpvars`` switch that will print a list of all variables and where they are placed into memory.
This can be useful to track which variables end up in zeropage for instance. But it doesn't really show if the choices
made are good, i.e. if the variables that are actually the most used in your program, are placed in zeropage.

But there is a way to actually *measure* the behavior of your program as it runs on the X16.
See it as a simple way of *profiling* your program to find the hotspots that maybe need optimizing:

The X16 emulator has a ``-memorystats`` option that enables it to keep track of memory access count statistics,
and write the accumulated counts to a file on exit.
Prog8 then provides a Python script ``profiler.py`` (find it in the "scripts" subdirectory of the source code distribution,
or :source:`online here <scripts/profiler.py>`).
This script cross-references the memory stats file with an assembly listing of the program, produced by the Prog8 compiler with the ``-asmlist`` option.
It then prints the top N lines in your (assembly) program source that perform the most reads and writes,
which you can use to identify possible hot spots/bottlenecks/variables that should be better placed in zeropage etc.
Note that the profiler simply works with the total number of accesses to memory locations.
This is *not* the same as the most run-time (cpu instructions cycle times aren't taken into account at all)!
Here is an example of the output it generates::

    $ scripts/profiler.py -n 10 cobramk3-gfx.list memstats.txt                                                                             ✔

    number of actual lines in the assembly listing: 2134
    number of distinct addresses read from  : 22006
    number of distinct addresses written to : 8179
    total number of reads  : 375106285 (375M)
    total number of writes : 63601962 (63M)

    top 10 most reads:
    $007f (7198687) : $007e 'P8ZP_SCRATCH_W2' (line 13), $007e 'remainder' (line 1855)
    $007e (6990527) : $007e 'P8ZP_SCRATCH_W2' (line 13), $007e 'remainder' (line 1855)
    $0265 (5029230) : unknown
    $007c (4455140) : $007c 'P8ZP_SCRATCH_W1' (line 12), $007c 'dividend' (line 1854), $007c 'result' (line 1856)
    $007d (4275195) : $007c 'P8ZP_SCRATCH_W1' (line 12), $007c 'dividend' (line 1854), $007c 'result' (line 1856)
    $0076 (3374800) : $0076 'label_asm_35_counter' (line 2082)
    $15d7 (3374800) : $15d7 '9c 23 9f               stz  cx16.VERA_DATA0' (line 2022), $15d7 'label_asm_34_repeat' (line 2021)
    $15d8 (3374800) : $15d7 '9c 23 9f               stz  cx16.VERA_DATA0' (line 2022), $15d7 'label_asm_34_repeat' (line 2021)
    $15d9 (3374800) : $15da '9c 23 9f               stz  cx16.VERA_DATA0' (line 2023)
    $15da (3374800) : $15da '9c 23 9f               stz  cx16.VERA_DATA0' (line 2023)

    top 10 most writes:
    $9f23 (14748104) : $9f23 'VERA_DATA0' (line 1451)
    $0265 (5657743) : unknown
    $007e (4464393) : $007e 'P8ZP_SCRATCH_W2' (line 13), $007e 'remainder' (line 1855)
    $007f (4464393) : $007e 'P8ZP_SCRATCH_W2' (line 13), $007e 'remainder' (line 1855)
    $007c (4416537) : $007c 'P8ZP_SCRATCH_W1' (line 12), $007c 'dividend' (line 1854), $007c 'result' (line 1856)
    $007d (3820272) : $007c 'P8ZP_SCRATCH_W1' (line 12), $007c 'dividend' (line 1854), $007c 'result' (line 1856)
    $0076 (3375568) : $0076 'label_asm_35_counter' (line 2082)
    $01e8 (1310425) : cpu stack
    $01e7 (1280140) : cpu stack
    $0264 (1258159) : unknown

Apparently the most cpu activity while running this program is spent in a division routine which uses the 'remainder' and 'dividend' variables.
As you can see, sometimes even actual assembly instructions end up in the tables above if they are in a routine that is executed very often (the 'stz' instructions in this example).
The tool isn't powerful enough to see what routine the variables or instructions are part of, but it prints the line number in the assembly listing file so you can investigate that manually.

You can see in the example above that the variables that are among the most used are neatly placed in zeropage already.
If you see for instance a variable that is heavily used and that is *not* in zeropage, you
could consider adding ``@zp`` to that variable's declaration to prioritize it to be put into zeropage.


Subroutine call profiling with the X16 emulator
-----------------------------------------------
.. index:: single: Subroutine profiling
.. index:: single: Profiling

For the Commander X16, the compiler has a ``-profiling`` option that instruments your Prog8 program with subroutine call profiling.
This allows you to analyze which subroutines take the most time during program execution, helping you identify performance bottlenecks.
When you compile with this flag, the instrumented subroutine calls write data to the X16 emulator's debug output console.
You'll have to redirect the emulator's output to a csv file to capture it.  The information contains:

- The subroutine name
- A timestamp (emulator cycle count)
- The call depth in the call stack

To use subroutine profiling:

1. Compile your program with the ``-profiling`` flag::

       prog8c -target cx16 -profiling your_program.p8

2. Run the compiled program in the x16 emulator. When the program exits, the emulator will have generated a ``profile.csv`` file.

3. The ``profile.csv`` file contains the profiling data in CSV format with three columns:

   - **Call depth** (hex): The nesting level of the call (lower values = deeper in the call stack, steps down by 2)
   - **Timestamp** (hex): The emulator cycle count when the call was made
   - **Routine name**: The fully qualified name of the subroutine (e.g., ``main.start``, ``galaxy.init``)

   The file may contain compiler output headers before the actual CSV data starts. The data section begins after a line of dashes (``---``).

Using the parse_profile_csv.py script
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The ``scripts/parse_profile_csv.py`` script provides several ways to analyze and visualize the profiling data.
It requires the following Python packages:

- ``graphviz`` (for PDF/SVG call graph generation)
- The ``flamegraph.pl`` Perl script (for flame graph generation, usually available via your system package manager)

Run the script::

    python scripts/parse_profile_csv.py

This presents an interactive menu with the following options:

**1. Create call graph PDF**
  Generates a directed graph showing the call relationships between subroutines.
  Each node displays the routine name, total time, percentage of total runtime, call count, and average time per call.
  Nodes are color-coded based on their percentage of total runtime:
  
  - **Red** (≥50%): Very hot routines - major bottlenecks
  - **Orange-red** (25-49%): Hot routines
  - **Orange-yellow** (10-24%): Significant routines
  - **Yellow** (5-9%): Moderate routines
  - **Green** (<5%): Low impact routines
  
**2. Create call graph SVG**
  Same as option 1, but outputs an SVG file instead of PDF.
  The SVG format is interactive and can be viewed in any web browser.

  Example call graph:

  .. image:: callgraph.svg

**3. Generate flame graph SVG**
  Creates an interactive flame graph visualization using the standard ``flamegraph.pl`` tool.
  Flame graphs show the call stack horizontally, with the width of each box proportional to the time spent in that routine.
  This makes it easy to spot performance bottlenecks at a glance.
  Open the resulting SVG file in a web browser to interact with it (zoom, search, click for details).

  Example flame graph:

  .. image:: _static/flamegraph.png

**4. Print routine statistics**
  Prints three tables to the console:

  - **By Time**: Routines sorted by total time (identifies biggest optimization opportunities)
  - **By Calls**: Routines sorted by call count (identifies frequently called routines)
  - **By Avg Time**: Routines sorted by average time per call (identifies inherently slow routines)

  Each table shows the routine name, total time, percentage of total runtime, call count, and average time.
  An optimization guide explains how to interpret each table.

Example workflow
^^^^^^^^^^^^^^^^

Here's a typical workflow for profiling a Prog8 program::

    # Compile with profiling enabled
    prog8c -target cx16 -profiling examples/textelite.p8
    
    # Run in emulator, saving output to profile.csv
    x16emu -run -prg textelite.prg > profile.csv
    
    # Analyze the results
    python scripts/parse_profile_csv.py
    # Select option 4 to see statistics, or option 3 for a flame graph

The output will show you which routines consume the most time. For example, if a routine shows 49% of total time,
optimizing that routine would have the biggest impact on overall performance.

Tips for effective profiling
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- Run your program through typical usage scenarios to get representative profiling data
- Look for routines with high total time percentages - these are your optimization priorities
- A routine with many calls but low average time might benefit from small optimizations
- A routine with high average time per call may need algorithmic improvements
- Use the flame graph to quickly identify hot paths in the call stack
- Compare profiles before and after optimizations to measure improvement

Limitations
^^^^^^^^^^^

- Profiling only works on the cx16 target with the x16emu emulator
- Only subroutine call *statements* are instrumented, and *some* function call *expressions* (extsubs)
- The profiler measures cumulative time (time in the routine plus all routines it calls)
- Very short routines may have measurement overhead that skews results
- Recursive routines are supported but may be hard to interpret in the output

