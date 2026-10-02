# Markdown Docstrings and Reference Documentation

**Status: partly implemented; Markdown generator still TODO.** Listed as an active item in `docs/source/todo.rst`.

> Note (Oct 2026): the docstring *capture* side is done. The `-gendoc` compiler flag exists (`compiler/src/prog8/CompilerMain.kt:77`) and prints AST nodes with documentation comments (`compiler/src/prog8/compiler/Compiler.kt:182`). `/** ... */` comments are accepted and attached before blocks, variables, structs, enums, and subroutines, including ones **nested inside a block** (`main { /** ... */ sub foo() {} }` parses and prints fine). The earlier parse-error limitation for nested subroutines has been fixed.
>
> The missing piece is the actual Markdown generator that replaces/extends that temporary stdout output with real reference files and Sphinx integration.

## Goal

Generate user-facing reference documentation from Markdown documentation comments in Prog8 source files, while integrating the generated pages with the existing Sphinx/reStructuredText documentation.

## Source Syntax

Use the existing block-comment container. Only comments beginning with `/**` are documentation comments; ordinary `/* ... */` comments remain non-documentation comments.

```text
/**
Calculate the checksum of a byte array.

### Parameters

- `data`: Input bytes.
- `length`: Number of bytes to process.

### Returns

The calculated checksum.
*/
sub checksum(byte[] data, word length) -> word
```

Documentation content is Markdown. The generator strips the opening and closing comment markers and applies Kotlin `trimIndent()` before rendering.

For library modules, the documentation comment attached to the first `Block` node is used as the module introduction. It is emitted as module-level documentation and not repeated as documentation for that block. Later blocks retain their own documentation comments.

## Compiler Responsibilities

- Preserve hidden-channel block comments during lexing (the lexer already puts `BLOCK_COMMENT` on a hidden channel; verify the visitor reads them).
- Attach leading documentation comments to `Block`, `VarDecl`, and `Subroutine` AST nodes (`blockComment` fields already exist on these classes; `Antlr2KotlinVisitor.kt` already populates them at lines 315, 642, and 705).
- ~~Fix parser support for doc comments on nested declarations.~~ Done: the grammar/visitor accept doc comments before nested declarations (verified Oct 2026 for subroutines, variables, and structs inside a block).
- Keep ordinary block comments available to the AST printer but exclude them from generated documentation.
- `-gendoc` already exists; ensure it inspects the compiler AST before optimization and code generation.
- Decide whether `-gendoc` should continue printing to stdout or write generated Markdown files directly.
- Print only nodes with a non-blank comment body beginning with `/**`.
- Include the node kind, name, source position, and normalized Markdown body in the temporary diagnostic output.

## Documentation Generator

Replace or extend the temporary `-gendoc` output with generated Markdown files. The generator should:

- Group declarations by source module and block.
- Use the first documented block comment in a library module as the module introduction.
- Render blocks, variables, and subroutine signatures consistently.
- Preserve Markdown paragraphs, lists, tables, links, and fenced code blocks.
- Render parameter and return descriptions as ordinary Markdown sections rather than requiring Javadoc-style tags.
- Use source positions for useful diagnostics when documentation is malformed or attached ambiguously.
- Define how undocumented declarations and private declarations are handled.

Suggested generated structure:

```text
reference/
    index.md
    modules/
        textio.md
        strings.md
```

## Sphinx Integration

Add `myst-parser` to `docs/requirements.txt` and enable it in `docs/source/conf.py`:

```python
extensions = [
    # existing extensions
    "myst_parser",
]
```

This allows Sphinx to render generated `.md` pages alongside the existing `.rst` pages. Add the generated reference index to the Sphinx documentation navigation or an appropriate `toctree`.

## Validation

- Add compiler tests for documentation comments on blocks, variables, and normal, assembly, and external subroutines - including subroutines nested inside blocks.
- Verify ordinary `/* ... */` comments and blank `/** ... */` comments are omitted by `-gendoc`.
- Verify multiline Markdown is deindented correctly.
- Verify generated Markdown builds successfully with Sphinx and MyST.
- Verify daemon-mode compilation forwards `-gendoc` correctly.
- Check that generated documentation does not alter normal compilation or AST optimization behavior.

## Open Decisions

- Whether `-gendoc` should write generated Markdown files directly (the current implementation only prints diagnostic output to stdout).
- Whether documentation should include library modules by default.
- Whether private declarations should be omitted or included in a separate section.
- Whether documentation comments should be copied through AST transformations and into later compiler representations.
- Which Markdown/MyST extensions should be enabled beyond the common Markdown feature set.
