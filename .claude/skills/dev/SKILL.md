---
name: dev
description: Unified shortcut for development process commands. Combines dev-req (requirements) and dev-project-auto-doc (documentation) into simple shortcuts like /dev init, /dev req, /dev save, /dev status, /dev doc
---

# Dev - Development Process Shortcuts

Unified command dispatcher for development process management. Provides simple shortcuts to `dev-req` (requirements) and `dev-project-auto-doc` (documentation) skills.

## Quick Reference

| Shortcut | Full Command | Description |
|----------|--------------|-------------|
| `/dev init` | `/dev-req init` | Initialize requirements structure |
| `/dev req <name>` | `/dev-req create <name>` | Create requirement spec |
| `/dev save` | `/dev-req checkpoint-save` | Save checkpoint |
| `/dev load` | `/dev-req checkpoint-load` | Load checkpoint |
| `/dev status` | `/dev-req progress-report` | Show progress report |
| `/dev doc` | `/dev-project-auto-doc init` | Initialize project docs (interactive) |
| `/dev doc init [--scale=<s>]` | `/dev-project-auto-doc init` | Initialize with scale (minimal/standard/full) |
| `/dev doc refresh` | `/dev-project-auto-doc refresh` | Refresh all docs |
| `/dev doc refresh api` | `/dev-project-auto-doc refresh api` | Refresh api.md only |
| `/dev doc upgrade <scale>` | `/dev-project-auto-doc upgrade` | Upgrade scale (standard/full) |
| `/dev doc add <doc>` | `/dev-project-auto-doc add-doc` | Add individual document |
| `/dev doc modules` | `/dev-project-auto-doc modules` | Initialize module docs |
| `/dev doc modules refresh` | `/dev-project-auto-doc modules refresh` | Refresh all module docs |
| `/dev doc modules refresh <name>` | `/dev-project-auto-doc modules refresh --module=<name>` | Refresh specific module |
| `/dev doc modules status` | `/dev-project-auto-doc modules status` | Check module docs status |
| `/dev doc modules add-rule <name>` | `/dev-project-auto-doc modules add-rule --module=<name>` | Add custom rule to module |

---

## Commands

### Requirements Management (dev-req)

#### init

Initialize requirements management structure.

```bash
/dev init [--force]
```

Creates `.claude/requirements/` with templates and updates CLAUDE.md.

---

#### req

Create a new requirement specification.

```bash
/dev req <requirement_name> [--priority=high|medium|low]
```

---

#### save

Save current development checkpoint.

```bash
/dev save [--reason="<reason>"]
```

---

#### load

Load a saved checkpoint.

```bash
/dev load [--checkpoint_id=<id>]
```

---

#### status

Show progress report for current requirement.

```bash
/dev status [--format=markdown|json]
```

---

### Documentation (dev-project-auto-doc)

#### doc

Initialize or manage project documentation with scale-based options.

**Scales**: minimal (3 docs), standard (6 docs), full (12 docs)

```bash
/dev doc                       # Initialize project docs (interactive)
/dev doc init                  # Same as above
/dev doc init --scale=standard # Initialize with specific scale
/dev doc refresh               # Refresh all docs
/dev doc refresh api           # Refresh api.md only
/dev doc refresh architecture  # Refresh architecture.md only
/dev doc analyze               # Analyze code structure
/dev doc status                # Check sync status
/dev doc upgrade standard      # Upgrade to standard scale
/dev doc upgrade full          # Upgrade to full scale
/dev doc add testing           # Add testing.md document
/dev doc add security          # Add security.md document

# Module documentation (for multi-module projects)
/dev doc modules               # Detect modules and init docs
/dev doc modules init          # Same as above
/dev doc modules refresh       # Refresh all module docs
/dev doc modules refresh api   # Refresh specific module doc
/dev doc modules status        # Check module docs status
/dev doc modules add-rule web  # Add custom rule to web module
/dev doc modules add-rule api --category="트랜잭션 규칙"  # With category
```

#### 모듈 문서 AI 분석 지침 (MANDATORY)

`/dev doc modules refresh` 실행 시, AI 어시스턴트는 **반드시** 다음을 수행해야 합니다:

1. **Python 스크립트 실행**: 모듈 감지, 컴포넌트 목록, 코드 패턴 분석
2. **AI 직접 분석** (플레이스홀더 금지):
   - 빌드 파일 (pom.xml, package.json) 읽고 기술 스택 상세 분석
   - src 디렉토리 구조 확인하고 실제 구조로 문서 작성
   - 모듈 역할을 컴포넌트 기반으로 추론하여 작성

**MUST DO**:
- `[이 모듈의 주요 역할 1]` 같은 플레이스홀더를 **절대** 그대로 두지 않음
- 실제 코드를 분석하여 **구체적인 내용**으로 작성

> 상세 지침은 `/dev-project-auto-doc` 스킬의 "AI 모듈 문서 작성 지침" 섹션 참고

---

## Typical Workflow

```bash
# 1. Initialize requirements (once)
/dev init

# 2. Initialize documentation (once)
/dev doc

# 3. Create first requirement
/dev req user-authentication

# 4. During development - check progress
/dev status

# 5. Before break - save checkpoint
/dev save --reason="lunch break"

# 6. Resume work - load checkpoint
/dev load

# 7. After code changes - refresh docs
/dev doc refresh api
```

---

## Full Commands

For advanced options, use the full skill commands:

- `/dev-req` - Requirements management (init, create, checkpoint-save, checkpoint-load, progress-report)
- `/dev-project-auto-doc` - Documentation (init, refresh, analyze, status, upgrade, add-doc, modules)

---

## Notes

- All commands delegate to their respective skills
- `/dev` without arguments shows help
- CLAUDE.md is automatically backed up before modifications
