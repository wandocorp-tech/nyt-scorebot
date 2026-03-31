# Scoreboard Construction Rules

This document is the authoritative specification for how game comparison scoreboards are constructed. It supersedes the individual design files in this folder.

---

## General Rules

These rules apply to all game types unless a game-specific rule states otherwise.

### Layout

All scoreboards use the following structure, rendered inside a Discord code block:

```
 [Game Header]
 
-----------------------------------
    [Player A - Score A]  |  [Player B - Score B]
-----------------------------------
 [Emoji Grid Row]
 [Emoji Grid Row]
 ...
-----------------------------------
 [Result Message]
-----------------------------------
```

- The outer separator is exactly **33 dashes**: `---------------------------------`
- The header line has a **single leading space**
- The name/score row has the left player **right-aligned** to position 15 and the right player **left-aligned** from position 21, with the `|` separator at position 18 (the centre of the 33-character line) and **2 spaces of padding** on each side
- The result message row has a **single leading space**

### Fixed Width

The table width is **33 characters**. This applies to all scenarios, including the single-player layout.

### Emoji Rendering in Discord

Different emoji characters render with different widths in Discord's monospace font. Square emoji (⬛🟨🟩🟦🟪) and circular emoji (🔵🟡💡) render at different effective widths, so the raw character spacing in scoreboards is empirically calibrated rather than mathematically precise. All sample scoreboards and spacing rules below are tested to render as visually centered in Discord.

### Column Ordering

- The player whose emoji grid has **more rows** goes on the **left**.
- If both players have the same number of rows (including all tie scenarios), column order follows the **configured player order** (as defined in `application.yml`).

### Result Messages

| Outcome | Message |
|---------|---------|
| Tie (any kind) | `🤝 Tie!` |
| Winner — both players complete | `🏆 [Winner] wins! (-N)` |
| Winner — one player incomplete | `🏆 [Winner] wins!` |
| One result only | `⏳ [Missing player] hasn't submitted` |

#### Score Differential

`(-N)` is only included when **both players completed** the puzzle. `N` is always positive and represents the winner's advantage: the absolute difference between the winner's metric and the loser's metric (e.g. guesses, mistakes, or hints used). Because the winner always has a better (lower) metric, the value is formatted as a negative number.

> Example: Winner used 4 guesses, loser used 6 → `(-2)`

### Single-Player Layout

When only one player has submitted a result, the scoreboard displays that player's data only:

- **No centre `|` separator** on the name row
- **No second column** in the emoji grid
- Result message is `⏳ [Missing player] hasn't submitted`
- Table width remains **35 characters**

```
 [Game Header]
 
-----------------------------------
 [Player A] - [Score A]
-----------------------------------
 [Emoji Grid Row]
 ...
-----------------------------------
 ⏳ [Player B] hasn't submitted
-----------------------------------
```

---

## Wordle

### Header

```
 Wordle #[puzzle number]
```

### Score Field

The score displayed next to the player's name is:
- `[1–6]` — the number of guesses used to solve the puzzle
- `X` — the player did not solve the puzzle (failed all 6 attempts)

### Emoji Grid

- Each row contains exactly **5 emojis** (one per letter of the word)
- Each row represents one guess attempt
- Valid emojis: `⬛` (absent), `⬜` (absent, light mode), `🟨` (wrong position), `🟩` (correct)
- Total rows = number of attempts made (1–6 for a complete result; 6 rows for a loss)
- The left emoji column uses **4 leading spaces** with a **5-space gap** before the right column. These values are empirically calibrated to render as centered in Discord despite the mathematical formula suggesting different values.
- single-player scoreboards have the same left column alignment as 2-player boards

### Winner Determination

The player with **fewer guesses** wins.
|-----------|---------|
| Both complete, different guess counts | Player with fewer guesses wins; `(-N)` shown |
| Both complete, same guess counts | Tie |
| Both failed (X) | Tie |
| One complete, one failed (X) | The player who completed wins; **no** `(-N)` shown |

### Scenarios

| Scenario | Left Column | Right Column | Result Message |
|----------|-------------|--------------|----------------|
| Tie (both solve with same guesses) | Configured order | Configured order | `🤝 Tie!` |
| Tie (both fail) | Configured order | Configured order | `🤝 Tie!` |
| Winner — both complete | More rows (loser) | Fewer rows (winner) | `🏆 [Winner] wins! (-N)` |
| Winner — one loss | More rows (loser with X) | Fewer rows (winner) | `🏆 [Winner] wins!` |
| One result only | The submitted player | — | `⏳ [Other] hasn't submitted` |

> **Note:** "If neither player has submitted a result for Wordle, no Wordle scoreboard is posted."

### Sample Scoreboards

**Win** — William guessed in 6, Conor in 4. William (more rows) goes left.
```
 Wordle #1234
 
-----------------------------------
    William - 6  |  Conor - 4
-----------------------------------
    ⬛⬛⬛🟨⬛     ⬛⬛⬛🟨⬛
    ⬛⬛⬛⬛🟨     ⬛⬛⬛⬛🟨
    🟨🟨🟩⬛⬛     🟨🟨🟩⬛⬛
    🟩🟩🟩🟩⬛     🟩🟩🟩🟩🟩
    ⬛🟨🟩🟨⬛
    🟩🟩🟩🟩⬛
-----------------------------------
 🏆 Conor wins! (-2)
-----------------------------------
```

**Tie** — Both guessed in 4. Configured order (William left).
```
 Wordle #1234
 
-----------------------------------
    William - 4  |  Conor - 4
-----------------------------------
    ⬛⬛⬛🟨⬛     ⬛⬛⬛🟨⬛
    ⬛⬛⬛⬛🟨     ⬛⬛⬛⬛🟨
    🟨🟨🟩⬛⬛     🟨🟨🟩⬛⬛
    🟩🟩🟩🟩🟩     🟩🟩🟩🟩🟩
-----------------------------------
 🤝 Tie!
-----------------------------------
```

**One submitted** — Only William has submitted.
```
 Wordle #1234
 
-----------------------------------
    William - 6          
-----------------------------------
    ⬛⬛⬛🟨⬛
    ⬛⬛⬛⬛🟨
    🟨🟨🟩⬛⬛
    🟩🟩🟩🟩⬛
    ⬛🟨🟩🟨⬛
    🟩🟩🟩🟩⬛
-----------------------------------
 ⏳ Conor hasn't submitted
-----------------------------------
```

---

## Connections

### Header

```
 Connections #[puzzle number]
```

### Score Field

The score displayed next to the player's name is:
- `[0–N]` — the number of mistakes made
- `X` — the player did not solve the puzzle

A **mistake** is any guess row that is not a correctly solved group. The total number of mistakes equals the total number of rows in the emoji grid minus the 4 rows for the correctly solved groups.

### Emoji Grid

- Each row contains exactly **4 emojis**
- Valid colours: `🟩` (green), `🟦` (blue), `🟨` (yellow), `🟪` (purple)
- A **correctly solved group** appears as a row of 4 identical-colour emojis
- A **mistake row** contains a mix of colours
- The grid is **dynamic in length**: 4 rows minimum (a perfect solve) up to many rows for heavy losses
- The left emoji column uses **6 leading spaces** with a **5-space gap** before the right column. These values are empirically calibrated to render as centered in Discord despite the mathematical formula suggesting different values.
- single-player scoreboards have the same left column alignment as 2-player boards

### Winner Determination

The player with **fewer mistakes** wins. A completed result always beats a failed (`X`) result.

| Condition | Outcome |
|-----------|---------|
| Both complete, different mistake counts | Player with fewer mistakes wins; `(-N)` shown |
| Both complete, same mistake counts | Tie |
| Both failed (X) | Tie |
| One complete, one failed (X) | The player who completed wins; **no** `(-N)` shown |

### Scenarios

| Scenario | Left Column | Right Column | Result Message |
|----------|-------------|--------------|----------------|
| Tie (both solve, same mistakes) | Configured order | Configured order | `🤝 Tie!` |
| Tie (both fail) | Configured order | Configured order | `🤝 Tie!` |
| Winner — both complete | Longer grid | Shorter grid | `🏆 [Winner] wins! (-N)` |
| Winner — one loss | Longer grid (may be loser or winner) | Shorter grid | `🏆 [Winner] wins!` |
| One result only | The submitted player | — | `⏳ [Other] hasn't submitted` |

> When a winner is determined, the longer emoji grid always goes on the left regardless of whether that grid belongs to the winner or the loser.

### Sample Scoreboards

**Win** — William made 2 mistakes (6 rows), Conor made 0 mistakes (4 rows). William (more rows) goes left.
```
 Connections #1234
 
-----------------------------------
    William - 2  |  Conor - 0
-----------------------------------
      🟩🟩🟩🟩     🟩🟩🟩🟩
      🟨🟪🟪🟦     🟦🟦🟦🟦
      🟨🟪🟪🟦     🟨🟨🟨🟨
      🟨🟨🟨🟨     🟪🟪🟪🟪
      🟦🟦🟦🟦
      🟪🟪🟪🟪
-----------------------------------
 🏆 Conor wins! (-2)
-----------------------------------
```

**Tie** — Both made 0 mistakes. Configured order (William left).
```
 Connections #1234
 
-----------------------------------
    William - 0  |  Conor - 0
-----------------------------------
      🟩🟩🟩🟩     🟩🟩🟩🟩
      🟦🟦🟦🟦     🟦🟦🟦🟦
      🟨🟨🟨🟨     🟨🟨🟨🟨
      🟪🟪🟪🟪     🟪🟪🟪🟪
-----------------------------------
 🤝 Tie!
-----------------------------------
```

**One submitted** — Only William has submitted.
```
 Connections #1234
 
-----------------------------------
    William - 2
-----------------------------------
      🟩🟩🟩🟩
      🟨🟪🟪🟦
      🟨🟪🟪🟦
      🟨🟨🟨🟨
      🟦🟦🟦🟦
      🟪🟪🟪🟪
-----------------------------------
 ⏳ Conor hasn't submitted                                             
-----------------------------------
```

---

## Strands

### Header

```
 Strands #[puzzle number]
```

### Score Field

The score displayed next to the player's name is the **number of hints used**:
- `[0–N]` — the number of hint bulbs (💡) in the player's result
- Strands cannot be failed; every result is a completed solve

### Emoji Grid

- Each row contains a maximum of **4 emojis**
- Valid emojis: `🔵` (theme word), `💡` (hint used). A `🟡` spangram may appear in raw content but its position is not persisted or used for tiebreaking.
- Both players will always have the same number of `🔵` theme words
- Total emojis = (number of theme words) + (number of hints used)
- Rows are filled to a maximum of 4 emojis; the final row may be shorter
- The left emoji column uses **6 leading spaces** with a **5-space gap** before the right column. These values are empirically calibrated to render as centered in Discord despite the mathematical formula suggesting different values.
- single-player scoreboards have the same left column alignment as 2-player boards

### Winner Determination

Comparison is solely by the number of hints used:

1. **Primary — Hints used**: The player with fewer hints wins.

| Condition | Outcome |
|-----------|---------|
| Different hint counts | Player with fewer hints wins; `(-N)` shown where N = difference in hints |
| Same hint counts | Tie |

> **Note:** Spangram position is not tracked and is not used as a tiebreaker.

### Scenarios

| Scenario | Left Column | Right Column | Result Message |
|----------|-------------|--------------|----------------|
| Tie | Configured order | Configured order | `🤝 Tie!` |
| Win by hints | Longer grid | Shorter grid | `🏆 [Winner] wins! (-N)` |
| Win by hints | Longer grid | Shorter grid | `🏆 [Winner] wins! (-N)` |
| One result only | The submitted player | — | `⏳ [Other] hasn't submitted` |

### Sample Scoreboards

**Win by hints** — William used 2 hints (9 emojis, 3 rows), Conor used 0 hints (7 emojis, 2 rows). William (more rows) goes left.
```
 Strands #1234 - "Animals"
 
-----------------------------------
    William - 2  |  Conor - 0
-----------------------------------
      🔵💡🔵🟡     🟡🔵🔵🔵
      🔵🔵💡🔵     🔵🔵🔵
      🔵
-----------------------------------
 🏆 Conor wins! (-2)
-----------------------------------
```

**Tie** — Both used 0 hints. Configured order (William left).
```
 Strands #1234 - "Animals"
 
-----------------------------------
    William - 0  |  Conor - 0
-----------------------------------
      🟡🔵🔵🔵     🟡🔵🔵🔵
      🔵🔵🔵       🔵🔵🔵
-----------------------------------
 🤝 Tie!
-----------------------------------
```

**One submitted** — Only William has submitted.
```
 Strands #1234 - "Animals"
 
-----------------------------------
    William - 2
-----------------------------------
      🔵💡🔵🟡
      🔵🔵💡🔵
      🔵
-----------------------------------
 ⏳ Conor hasn't submitted
-----------------------------------
```

---

## Corrections to Source Design Files

The following errors were found in the original design files and are corrected by this document:

| File | Error | Correction |
|------|-------|------------|
| `strands.md` — Scenario 6 header | Says `Connections #1234` | Should say `Strands #[puzzle number]` |
| `strands.md` — Scenario 2 notes | Contains `"X denotes that a player did not solve the puzzle"` | Strands cannot be failed; this note is a copy-paste error from `connections.md` and does not apply |
| `strands.md` — Scenario numbering | Scenarios 4 and 5 are absent; numbering jumps 3 → 3 → 6 | Scenario numbers were assigned incorrectly in the source; this document replaces them with a complete scenario table |
| `connections.md` — Duplicate Scenario 5 | Two sections labelled "Scenario 5" | The first is "Winner — winner has longer grid"; the second is "One result only". Both are covered in the table above |
| All games — Header leading space | `strands.md` header examples omit the leading space | All game headers have a single leading space for consistency |
