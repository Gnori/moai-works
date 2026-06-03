---
name: dev-req
description: Automate standard 5-phase development process workflow. Use when users need to (1) Initialize requirements structure, (2) Create requirement specification documents, (3) Save development checkpoints during interruptions, (4) Load saved checkpoints to resume work, (5) Generate progress reports
---

# Development Requirements Manager

Automates requirement management, checkpoints, and progress tracking for the 5-phase process in CLAUDE.md.

## Key Features

- **Init Structure**: Initialize requirements directory and templates
- **Requirements Spec**: Create requirement specifications from templates
- **Checkpoint System**: Save/load development state for session continuity
- **Progress Tracking**: Generate progress reports with checklist analysis
- **Template Support**: Uses templates for consistent documentation

---

## Scripts

### init

Initialize requirements management structure.

```bash
# Interactive initialization
/dev-req init

# Force overwrite existing structure
/dev-req init --force
```

**Actions**:
1. Create `.claude/requirements/` directory
2. Create templates (`requirement_spec.md`, `checkpoint.md`)
3. Create `active_development.md` for tracking
4. Create `README.md` with usage guide
5. **Update CLAUDE.md** (see CLAUDE.md 처리 로직 below)

---

### create

Create requirement specification document from scale-based templates.

```bash
/dev-req create <name> [--priority=high|medium|low] [--scale=simple|standard|complex]

# Examples
/dev-req create user-authentication
/dev-req create bug-fix --scale=simple
/dev-req create payment-integration --priority=high --scale=standard
/dev-req create microservice-migration --scale=complex
```

**Scales**:
| Scale | Use Case | Template Size |
|-------|----------|---------------|
| simple | Bug fixes, minor changes | ~50 lines |
| standard | Regular feature development (default) | ~150 lines |
| complex | Large features, architecture changes | ~400 lines |

**Auto-recommendation**: If `--scale` is omitted, the system recommends based on requirement name keywords:
- "버그", "수정", "fix", "bug" → simple
- "추가", "구현", "feature", "add" → standard
- "아키텍처", "마이그레이션", "리팩토링" → complex

Creates `.claude/requirements/[YYYYMMDD-HHMMSS-SSS]_<name>.md` and updates `active_development.md`.

---

### checkpoint-save

Save current development state for later resumption.

```bash
/dev-req checkpoint-save [--reason="<reason>"]

# Examples
/dev-req checkpoint-save --reason="end of day"
/dev-req checkpoint-save --reason="token exhaustion"
```

Captures:
- Git status (branch, modified files)
- Completed/in-progress tasks
- Open files and line numbers
- Technical notes

Saves to `.claude/requirements/checkpoints/CP-[timestamp].md`.

---

### checkpoint-load

Restore development context from checkpoint.

```bash
# Latest checkpoint
/dev-req checkpoint-load

# Specific checkpoint
/dev-req checkpoint-load --checkpoint_id=CP-20260109-153000-123
```

Shows: completed tasks, next steps, open files, requirement context.

---

### progress-report

Analyze requirement and generate progress report.

```bash
# Active requirement
/dev-req progress-report

# Specific requirement
/dev-req progress-report --requirement_file=<filename>

# JSON output
/dev-req progress-report --format=json
```

Reports: completion percentage, checklist status, test coverage, estimated completion.

---

## Typical Workflow

**Initial setup (once per project):**
```bash
/dev-req init
```

**Start new feature:**
```bash
/dev-req create user-auth
# Edit .claude/requirements/[timestamp]_user-auth.md with 5 phases
```

**During development:**
```bash
# Check progress
/dev-req progress-report

# Save before break/token exhaustion
/dev-req checkpoint-save --reason="end of day"
```

**Resume work:**
```bash
/dev-req checkpoint-load
# Review context and continue from last checkpoint
```

---

## Generated Structure

```
.claude/requirements/
├── README.md                     # Requirements management guide
├── active_development.md         # Current active requirement tracker
├── [YYYYMMDD-HHMMSS-SSS]_<name>.md  # Requirement specifications
├── templates/
│   ├── requirement_spec.md       # 5-phase template
│   └── checkpoint.md             # Checkpoint template
├── checkpoints/
│   └── CP-[timestamp].md         # Saved checkpoints
└── archive/                      # Completed requirements
```

---

## Templates

### Requirements Templates

Templates are stored in skill's `templates/requirements/`:
- `README.md.template` - Requirements directory guide
- `active_development.md.template` - Active development tracker
- `templates/simple.md.template` - Simple requirement spec (~50 lines)
- `templates/standard.md.template` - Standard requirement spec (~150 lines)
- `templates/complex.md.template` - Complex requirement spec (~400 lines)
- `templates/checkpoint.md.template` - Checkpoint document

### CLAUDE.md Section Template

Template for CLAUDE.md section: `templates/claude_md_section.md.template`
- Contains AI assistant guidelines for requirement management
- Defines 5-phase development process rules

**Template Structure**:
```
templates/
├── requirements/
│   ├── README.md.template
│   ├── active_development.md.template
│   └── templates/
│       ├── simple.md.template      # 간단 (~50줄)
│       ├── standard.md.template    # 표준 (~150줄)
│       ├── complex.md.template     # 복잡 (~400줄)
│       └── checkpoint.md.template
└── claude_md_section.md.template
```

---

## 5-Phase Process Integration

1. **Requirement Analysis** - Use `init` + `create`
2. **Feature Definition** - Fill feature list in spec
3. **Feature Design** - Document architecture/APIs
4. **Development & Progress** - Use `checkpoint-save` + `progress-report`
5. **Testing & Validation** - Track test results in spec

See CLAUDE.md for full process details.

---

## Configuration

Settings in `skill_config.yaml`:
- `requirements_dir`: `.claude/requirements`
- `checkpoint_dir`: `.claude/requirements/checkpoints`
- `filename_format`: `[YYYYMMDD-HHMMSS-SSS]_{name}.md`

---

## Shortcut Commands

Use with `/dev` dispatcher:

```bash
/dev init                              # Same as /dev-req init
/dev req <name>                        # Same as /dev-req create <name> (auto scale)
/dev req <name> --scale=simple         # Simple scale
/dev req <name> --scale=standard       # Standard scale
/dev req <name> --scale=complex        # Complex scale
/dev save                              # Same as /dev-req checkpoint-save
/dev load                              # Same as /dev-req checkpoint-load
/dev status                            # Same as /dev-req progress-report
```

---

## Notes

- Scripts run from project root
- Checkpoints are local (backup separately if needed)
- Multiple requirements can be active; `active_development.md` tracks current one
- Commit important checkpoints to Git for team sharing
- CLAUDE.md is automatically backed up before modifications
