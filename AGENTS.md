# Agent Instructions for Prog8 Project

## Required Startup Actions

When starting a new session with this project, you **MUST** perform these actions before proceeding with any user requests:

1. **Read QWEN.md** - Execute: `read /home/irmen/Projects/prog8/QWEN.md`
   - This file contains critical project context including:
     - Project overview (Prog8 compiler for 8-bit retro systems)
     - Module descriptions for all 20+ modules
     - Gradle commands and build instructions
     - Prog8 compiler usage examples
     - Development shortcuts

2. **Check startup workflow** - Execute: `read /home/irmen/Projects/prog8/.agent/workflows/startup.md`
   - This file may contain additional startup instructions

## Why This Matters

The prog8 project is a complex compiler with:
- Multiple code generation backends (6502, IR, Virtual Machine)
- 20+ interconnected modules
- Specific build and development workflows
- Proprietary language syntax and conventions

Without reading QWEN.md first, you will lack essential context needed to:
- Understand module relationships
- Use correct gradle commands
- Reference the right files
- Follow project conventions

**DO NOT PROCEED** with any user requests until you've read QWEN.md!
