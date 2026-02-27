#!/usr/bin/env python
"""
Generate a call graph from profile.csv output.

The profile.csv has three columns:
  1. call depth (hex, lower value = deeper nesting, steps down by 2)
  2. timestamp (hex)
  3. routine name
"""

import csv
from graphviz import Digraph


def parse_depth(depth_hex: str) -> int:
    """Convert hex depth to integer."""
    return int(depth_hex, 16)


def get_color_for_percentage(percentage: int) -> str:
    """Get a color based on percentage - green (low) to red (high)."""
    # Use a gradient from green (#00ff00) through yellow (#ffff00) to red (#ff0000)
    if percentage >= 50:
        # Red for very high (50%+)
        return '#ff0000'
    elif percentage >= 25:
        # Orange-red for high (25-49%)
        return '#ff6600'
    elif percentage >= 10:
        # Orange-yellow for medium-high (10-24%)
        return '#ffaa00'
    elif percentage >= 5:
        # Yellow for medium (5-9%)
        return '#ffdd00'
    elif percentage >= 2:
        # Yellow-green for low-medium (2-4%)
        return '#ccff00'
    elif percentage >= 1:
        # Light green for low (1%)
        return '#99ff00'
    else:
        # Green for very low (<1%)
        return '#66ff66'


def _is_profile_data_row(row: list) -> bool:
    """Check if a row looks like profile data (hex, hex, string)."""
    if len(row) < 3:
        return False
    # First two columns should be hex numbers, third should be routine name
    try:
        int(row[0], 16)
        int(row[1], 16)
        return True
    except ValueError:
        return False


def _read_profile_data(csv_path: str) -> list:
    """Read profile data from CSV, skipping any header/garbage."""
    calls = []
    with open(csv_path, 'r') as f:
        reader = csv.reader(f)
        data_started = False
        for row in reader:
            if not data_started:
                # Check if this row looks like data
                if _is_profile_data_row(row):
                    data_started = True
                # Or check for separator line (dashes)
                elif row and len(row) > 0 and row[0].startswith('---'):
                    data_started = True
                    continue  # Skip the separator line itself
                else:
                    continue  # Skip header/garbage
            if len(row) >= 3:
                depth_hex, timestamp_hex, routine = row[0], row[1], row[2]
                depth = parse_depth(depth_hex)
                timestamp = int(timestamp_hex, 16)
                calls.append((depth, timestamp, routine))
    return calls


def build_call_graph(csv_path: str, output_path: str, format: str = 'pdf') -> None:
    """Build and save a call graph from profile data."""

    # Read profile data, skipping header/garbage
    calls = _read_profile_data(csv_path)

    if not calls:
        print("No calls found in profile.csv")
        return

    # Track time spent in each routine (aggregated by routine name)
    # routine_name -> {'total_time': int, 'call_count': int}
    routine_stats = {}

    # Stack holds (depth, routine_name, start_timestamp)
    stack = []

    # Build call graph using a stack to track call hierarchy
    dot = Digraph(comment='Call Graph')
    dot.attr(rankdir='TB')

    # Track edges to avoid duplicates: (caller_routine, callee_routine)
    edges = set()

    # Track call hierarchy for edges
    parent_stack = []

    for depth, timestamp, routine in calls:
        # Pop stack and calculate time for routines that are ending
        while stack and stack[-1][0] <= depth:
            ended_depth, ended_routine, start_time = stack.pop()
            elapsed = timestamp - start_time
            if ended_routine not in routine_stats:
                routine_stats[ended_routine] = {'total_time': 0, 'call_count': 0}
            routine_stats[ended_routine]['total_time'] += elapsed
            routine_stats[ended_routine]['call_count'] += 1
            if parent_stack:
                parent_stack.pop()

        # Track edge from parent caller
        if parent_stack:
            caller = parent_stack[-1]
            edge = (caller, routine)
            if edge not in edges:
                edges.add(edge)

        # Push current onto stacks
        stack.append((depth, routine, timestamp))
        parent_stack.append(routine)

    # Handle any remaining items on stack (end of profile)
    if stack:
        final_time = calls[-1][1] if calls else 0
        for ended_depth, ended_routine, start_time in stack:
            elapsed = final_time - start_time
            if ended_routine not in routine_stats:
                routine_stats[ended_routine] = {'total_time': 0, 'call_count': 0}
            routine_stats[ended_routine]['total_time'] += elapsed
            routine_stats[ended_routine]['call_count'] += 1

    # Get total runtime from main.start for percentage calculation
    total_runtime = routine_stats.get('main.start', {}).get('total_time', 1)
    if total_runtime == 0:
        total_runtime = 1  # Avoid division by zero

    # Create nodes for each unique routine with timing information
    for routine, stats in routine_stats.items():
        total_time = stats['total_time']
        call_count = stats['call_count']
        avg_time = total_time // call_count if call_count > 0 else 0
        percentage = (total_time * 100) // total_runtime
        # Get color based on percentage (red = hot, green = cold)
        fillcolor = get_color_for_percentage(percentage)
        # Use HTML-like label with bold routine name and blue percentage for contrast
        label = f"<{routine}<BR/><FONT POINT-SIZE=\"10\">Total: {total_time} <FONT COLOR=\"blue\">({percentage}%)</FONT></FONT><BR/><FONT POINT-SIZE=\"10\">Calls: {call_count}</FONT><BR/><FONT POINT-SIZE=\"10\">Avg: {avg_time}</FONT>>"
        dot.node(routine, label, shape='box', style='filled', fillcolor=fillcolor)

    # Create edges between routines
    for caller, callee in edges:
        dot.edge(caller, callee)

    # Render the graph
    dot.render(output_path, format=format, cleanup=True)
    print(f"Call graph saved to {output_path}.{format}")


def generate_flame_graph(csv_path: str, output_path: str) -> None:
    """Generate a flame graph from profile data using flamegraph.pl."""
    import subprocess
    
    # Read profile data, skipping header/garbage
    calls = _read_profile_data(csv_path)
    
    if not calls:
        print("No calls found in profile.csv")
        return
    
    # Find max depth (shallowest = root)
    max_depth = max(c[0] for c in calls)
    
    # Build folded stack format for flamegraph.pl
    # Format: "func1;func2;func3 count"
    # We need to track stack at each point and output when a call ends
    
    # Stack holds (depth, routine, start_timestamp)
    stack = []
    
    # Track folded stacks with their total time
    folded_stacks = {}  # "stack;string" -> total_time
    
    for depth, timestamp, routine in calls:
        # Pop stack for routines that are ending
        while stack and stack[-1][0] <= depth:
            ended_depth, ended_routine, start_time = stack.pop()
            
            # Build stack string from root to this routine
            stack_routines = [r for (_, r, _) in stack] + [ended_routine]
            # Reverse so root is first (flamegraph.pl expects root on left)
            stack_str = ";".join(stack_routines)
            
            elapsed = timestamp - start_time
            
            if stack_str not in folded_stacks:
                folded_stacks[stack_str] = 0
            folded_stacks[stack_str] += elapsed
        
        # Push current onto stack
        stack.append((depth, routine, timestamp))
    
    # Handle remaining items on stack
    if stack:
        final_time = calls[-1][1]
        for ended_depth, ended_routine, start_time in stack:
            stack_routines = [r for (_, r, _) in stack] + [ended_routine]
            stack_str = ";".join(stack_routines)
            elapsed = final_time - start_time
            
            if stack_str not in folded_stacks:
                folded_stacks[stack_str] = 0
            folded_stacks[stack_str] += elapsed
    
    # Write folded stacks to temp file
    import tempfile
    with tempfile.NamedTemporaryFile(mode='w', suffix='.folded', delete=False) as f:
        for stack_str, count in folded_stacks.items():
            f.write(f"{stack_str} {count}\n")
        folded_file = f.name
    
    # Run flamegraph.pl to generate SVG
    output_file = f"{output_path}.svg"
    try:
        with open(output_file, 'w') as out:
            subprocess.run(
                ['flamegraph.pl', '--title', 'Prog8 Flame Graph', '--width', '1024'],
                stdin=open(folded_file, 'r'),
                stdout=out,
                stderr=subprocess.PIPE,
                check=True
            )
        print(f"Flame graph saved to {output_file}")
    except subprocess.CalledProcessError as e:
        print(f"Error running flamegraph.pl: {e}")
        if e.stderr:
            print(f"stderr: {e.stderr.decode()}")
    except FileNotFoundError:
        print("Error: flamegraph.pl not found in PATH")
    finally:
        import os
        os.unlink(folded_file)


def print_call_tree(csv_path: str) -> None:
    """Print routine statistics from profile data."""
    
    # Read profile data, skipping header/garbage
    calls = _read_profile_data(csv_path)
    
    if not calls:
        print("No calls found in profile.csv")
        return
    
    # Track time spent in each routine
    routine_stats = {}

    # Stack holds (depth, routine_name, start_timestamp)
    stack = []

    for depth, timestamp, routine in calls:
        # Pop stack and calculate time for routines that are ending
        while stack and stack[-1][0] <= depth:
            ended_depth, ended_routine, start_time = stack.pop()
            elapsed = timestamp - start_time
            if ended_routine not in routine_stats:
                routine_stats[ended_routine] = {'total_time': 0, 'call_count': 0}
            routine_stats[ended_routine]['total_time'] += elapsed
            routine_stats[ended_routine]['call_count'] += 1

        # Push current onto stack
        stack.append((depth, routine, timestamp))

    # Handle any remaining items on stack (end of profile)
    if stack:
        final_time = calls[-1][1] if calls else 0
        for ended_depth, ended_routine, start_time in stack:
            elapsed = final_time - start_time
            if ended_routine not in routine_stats:
                routine_stats[ended_routine] = {'total_time': 0, 'call_count': 0}
            routine_stats[ended_routine]['total_time'] += elapsed
            routine_stats[ended_routine]['call_count'] += 1

    # Get total runtime from main.start for percentage calculation
    total_runtime = routine_stats.get('main.start', {}).get('total_time', 1)
    if total_runtime == 0:
        total_runtime = 1  # Avoid division by zero
    
    # Print explanation
    print("\n--- Optimization Guide ---")
    print("  By Time:     Routines with highest total time (biggest impact if optimized)")
    print("  By Calls:    Routines called most often (benefit from small optimizations)")
    print("  By Avg Time: Slowest routines per call (may need algorithmic improvements)")
    print("  Note: Times include the routine itself and all routines called from it")
    print("--------------------------\n")
    
    # Print summary statistics
    print("\n=== Routine Statistics (by Time) ===\n")
    # Sort by total time descending
    sorted_stats = sorted(routine_stats.items(), key=lambda x: x[1]['total_time'], reverse=True)
    # Print header
    print(f"{'Routine':<45} {'Total':>10} {'%':>6} {'Calls':>7} {'Avg':>10}")
    print("-" * 82)
    for routine, stats in sorted_stats:
        total_time = stats['total_time']
        call_count = stats['call_count']
        avg_time = total_time // call_count if call_count > 0 else 0
        percentage = (total_time * 100) // total_runtime
        print(f"{routine:<45} {total_time:>10} {percentage:>5}% {call_count:>7} {avg_time:>10}")
    
    # Print statistics sorted by call count
    print("\n=== Routine Statistics (by Calls) ===\n")
    sorted_by_calls = sorted(routine_stats.items(), key=lambda x: x[1]['call_count'], reverse=True)
    # Print header
    print(f"{'Routine':<45} {'Total':>10} {'%':>6} {'Calls':>7} {'Avg':>10}")
    print("-" * 82)
    for routine, stats in sorted_by_calls:
        total_time = stats['total_time']
        call_count = stats['call_count']
        avg_time = total_time // call_count if call_count > 0 else 0
        percentage = (total_time * 100) // total_runtime
        print(f"{routine:<45} {total_time:>10} {percentage:>5}% {call_count:>7} {avg_time:>10}")
    
    # Print statistics sorted by average time
    print("\n=== Routine Statistics (by Avg Time) ===\n")
    sorted_by_avg = sorted(routine_stats.items(), key=lambda x: x[1]['total_time'] // max(x[1]['call_count'], 1), reverse=True)
    # Print header
    print(f"{'Routine':<45} {'Total':>10} {'%':>6} {'Calls':>7} {'Avg':>10}")
    print("-" * 82)
    for routine, stats in sorted_by_avg:
        total_time = stats['total_time']
        call_count = stats['call_count']
        avg_time = total_time // call_count if call_count > 0 else 0
        percentage = (total_time * 100) // total_runtime
        print(f"{routine:<45} {total_time:>10} {percentage:>5}% {call_count:>7} {avg_time:>10}")


def show_menu() -> None:
    """Display menu and get user choice."""
    print("\n=== Prog8 Profile Csv Analyzer ===")
    print("  reads run time profiling data from a CSV generated by the X16 emulator,")
    print("  when the program is compiled with -profiling instrumentation.\n")
    print("1. Create call graph PDF")
    print("2. Create call graph SVG")
    print("3. Generate flame graph SVG")
    print("4. Print routine statistics")
    print("9. Exit")

    while True:
        choice = input("\nWhat would you like to do? ").strip()
        if choice in ('1', '2', '3', '4', '9'):
            return choice
        print("Invalid choice. Please enter 1, 2, 3, 4, or 9.")


def main() -> None:
    """Main entry point with menu."""
    choice = show_menu()

    if choice == '9':
        print("Goodbye!")
        return

    if choice == '1':
        csv_file = input("Enter profile csv path (or press Enter for 'profile.csv'): ").strip()
        if not csv_file:
            csv_file = 'profile.csv'

        output_file = input("Enter output filename (or press Enter for 'callgraph'): ").strip()
        if not output_file:
            output_file = 'callgraph'

        build_call_graph(csv_file, output_file, format='pdf')

    if choice == '2':
        csv_file = input("Enter profile csv path (or press Enter for 'profile.csv'): ").strip()
        if not csv_file:
            csv_file = 'profile.csv'

        output_file = input("Enter output filename (or press Enter for 'callgraph'): ").strip()
        if not output_file:
            output_file = 'callgraph'

        build_call_graph(csv_file, output_file, format='svg')

    if choice == '3':
        csv_file = input("Enter profile csv path (or press Enter for 'profile.csv'): ").strip()
        if not csv_file:
            csv_file = 'profile.csv'

        output_file = input("Enter output filename (or press Enter for 'flamegraph'): ").strip()
        if not output_file:
            output_file = 'flamegraph'

        generate_flame_graph(csv_file, output_file)

    if choice == '4':
        csv_file = input("Enter profile csv path (or press Enter for 'profile.csv'): ").strip()
        if not csv_file:
            csv_file = 'profile.csv'

        print_call_tree(csv_file)


if __name__ == '__main__':
    main()
