#!/usr/bin/env python3
"""
코드 패턴 분석기

프로젝트 코드에서 실제 사용 중인 패턴을 분석하여 개발 규칙을 자동 추출합니다.

지원하는 패턴:
- React: Dialog, Snackbar, 버튼 색상, 폼 처리, API 에러
- Java/Spring: ResponseEntity, 예외 처리, 검증, 권한, 트랜잭션
- Python: 로깅, 에러 처리, 타입 힌트
- Node.js: 비동기 처리, 에러 처리, 로깅
"""

import re
from pathlib import Path
from typing import Dict, List, Optional, Set
from collections import Counter


def analyze_react_patterns(module_path: Path) -> Dict:
    """React 모듈의 패턴을 분석합니다."""
    patterns = {
        "dialog": [],
        "snackbar": [],
        "button_colors": [],
        "form_handling": [],
        "error_handling": [],
        "ui_library": None,
        "state_management": [],
    }

    src_dir = module_path / "src"
    if not src_dir.exists():
        return patterns

    # 모든 JSX/TSX 파일 분석
    jsx_files = list(src_dir.rglob("*.jsx")) + list(src_dir.rglob("*.tsx"))

    for file_path in jsx_files:
        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")

            # Dialog 패턴 분석
            analyze_dialog_patterns(content, patterns)

            # Snackbar 패턴 분석
            analyze_snackbar_patterns(content, patterns)

            # 버튼 색상 패턴 분석
            analyze_button_patterns(content, patterns)

            # 폼 처리 패턴 분석
            analyze_form_patterns(content, patterns)

            # 에러 처리 패턴 분석
            analyze_error_patterns(content, patterns)

            # UI 라이브러리 감지
            detect_ui_library(content, patterns)

            # 상태 관리 패턴 분석
            analyze_state_patterns(content, patterns)

        except Exception:
            continue

    return patterns


def analyze_dialog_patterns(content: str, patterns: Dict) -> None:
    """Dialog 사용 패턴을 분석합니다."""
    # maxWidth 분석
    max_widths = re.findall(r'maxWidth=["\'](\w+)["\']', content)
    for mw in max_widths:
        if mw not in patterns["dialog"]:
            patterns["dialog"].append({"type": "maxWidth", "value": mw})

    # fullWidth 사용 여부
    if "fullWidth" in content:
        if {"type": "fullWidth", "value": "true"} not in patterns["dialog"]:
            patterns["dialog"].append({"type": "fullWidth", "value": "true"})

    # fullScreen 패턴
    if re.search(r'fullScreen=\{.*isMobile.*\}', content):
        if {"type": "fullScreen", "value": "isMobile"} not in patterns["dialog"]:
            patterns["dialog"].append({"type": "fullScreen", "value": "isMobile"})

    # DialogTitle, DialogContent, DialogActions 구조
    if all(x in content for x in ["DialogTitle", "DialogContent", "DialogActions"]):
        if {"type": "structure", "value": "3-section"} not in patterns["dialog"]:
            patterns["dialog"].append({"type": "structure", "value": "3-section"})


def analyze_snackbar_patterns(content: str, patterns: Dict) -> None:
    """Snackbar/Toast 패턴을 분석합니다."""
    # autoHideDuration 분석
    durations = re.findall(r'autoHideDuration=\{(\d+)\}', content)
    for duration in durations:
        if {"type": "autoHideDuration", "value": duration} not in patterns["snackbar"]:
            patterns["snackbar"].append({"type": "autoHideDuration", "value": duration})

    # anchorOrigin 분석
    anchors = re.findall(r'anchorOrigin=\{\{[^}]*vertical:\s*["\'](\w+)["\'][^}]*horizontal:\s*["\'](\w+)["\']', content)
    for v, h in anchors:
        if {"type": "position", "value": f"{v}-{h}"} not in patterns["snackbar"]:
            patterns["snackbar"].append({"type": "position", "value": f"{v}-{h}"})

    # severity 분석
    severities = re.findall(r'severity=["\'](\w+)["\']', content)
    for sev in set(severities):
        if {"type": "severity", "value": sev} not in patterns["snackbar"]:
            patterns["snackbar"].append({"type": "severity", "value": sev})


def analyze_button_patterns(content: str, patterns: Dict) -> None:
    """버튼 색상/스타일 패턴을 분석합니다."""
    # color 속성 분석
    colors = re.findall(r'<Button[^>]*color=["\'](\w+)["\']', content)
    colors += re.findall(r'<IconButton[^>]*color=["\'](\w+)["\']', content)

    for color in set(colors):
        if {"type": "color", "value": color} not in patterns["button_colors"]:
            patterns["button_colors"].append({"type": "color", "value": color})

    # variant 속성 분석
    variants = re.findall(r'<Button[^>]*variant=["\'](\w+)["\']', content)
    for variant in set(variants):
        if {"type": "variant", "value": variant} not in patterns["button_colors"]:
            patterns["button_colors"].append({"type": "variant", "value": variant})


def analyze_form_patterns(content: str, patterns: Dict) -> None:
    """폼 처리 패턴을 분석합니다."""
    # FormData 인터페이스
    if re.search(r'interface\s+\w*FormData', content):
        if "FormData interface" not in patterns["form_handling"]:
            patterns["form_handling"].append("FormData interface")

    # FormErrors 인터페이스
    if re.search(r'interface\s+\w*Errors', content):
        if "FormErrors interface" not in patterns["form_handling"]:
            patterns["form_handling"].append("FormErrors interface")

    # validate 함수
    if re.search(r'const\s+validate\s*=', content):
        if "validate function" not in patterns["form_handling"]:
            patterns["form_handling"].append("validate function")

    # handleChange 패턴
    if re.search(r'handleChange\s*\(', content):
        if "handleChange pattern" not in patterns["form_handling"]:
            patterns["form_handling"].append("handleChange pattern")

    # error/helperText 패턴
    if re.search(r'error=\{!!errors\.', content):
        if "error helperText pattern" not in patterns["form_handling"]:
            patterns["form_handling"].append("error helperText pattern")


def analyze_error_patterns(content: str, patterns: Dict) -> None:
    """에러 처리 패턴을 분석합니다."""
    # try-catch 패턴
    if re.search(r'try\s*\{[\s\S]*?catch\s*\(', content):
        if "try-catch" not in patterns["error_handling"]:
            patterns["error_handling"].append("try-catch")

    # showError 사용
    if "showError" in content:
        if "snackbar.showError" not in patterns["error_handling"]:
            patterns["error_handling"].append("snackbar.showError")

    # getErrorMessage 사용
    if "getErrorMessage" in content or "getActionErrorMessage" in content:
        if "errorHandler utility" not in patterns["error_handling"]:
            patterns["error_handling"].append("errorHandler utility")


def detect_ui_library(content: str, patterns: Dict) -> None:
    """UI 라이브러리를 감지합니다."""
    if "@mui/material" in content or "from '@mui" in content:
        patterns["ui_library"] = "MUI"
    elif "antd" in content:
        patterns["ui_library"] = "Ant Design"
    elif "chakra" in content:
        patterns["ui_library"] = "Chakra UI"


def analyze_state_patterns(content: str, patterns: Dict) -> None:
    """상태 관리 패턴을 분석합니다."""
    # useState
    if "useState" in content:
        if "useState" not in patterns["state_management"]:
            patterns["state_management"].append("useState")

    # useReducer
    if "useReducer" in content:
        if "useReducer" not in patterns["state_management"]:
            patterns["state_management"].append("useReducer")

    # useContext
    if "useContext" in content:
        if "useContext" not in patterns["state_management"]:
            patterns["state_management"].append("useContext")

    # Redux
    if "useSelector" in content or "useDispatch" in content:
        if "Redux" not in patterns["state_management"]:
            patterns["state_management"].append("Redux")


def analyze_java_patterns(module_path: Path) -> Dict:
    """Java/Spring 모듈의 패턴을 분석합니다."""
    patterns = {
        "response_entity": [],
        "exceptions": [],
        "validation": [],
        "authorization": [],
        "transaction": [],
    }

    src_dir = module_path / "src" / "main" / "java"
    if not src_dir.exists():
        return patterns

    java_files = list(src_dir.rglob("*.java"))

    for file_path in java_files:
        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")

            # ResponseEntity 패턴 분석
            analyze_response_entity_patterns(content, patterns)

            # 예외 처리 패턴 분석
            analyze_exception_patterns(content, patterns)

            # 검증 패턴 분석
            analyze_validation_patterns(content, patterns)

            # 권한 패턴 분석
            analyze_authorization_patterns(content, patterns)

            # 트랜잭션 패턴 분석
            analyze_transaction_patterns(content, patterns)

        except Exception:
            continue

    return patterns


def analyze_response_entity_patterns(content: str, patterns: Dict) -> None:
    """ResponseEntity 사용 패턴을 분석합니다."""
    # ResponseEntity.status(HttpStatus.CREATED)
    if "HttpStatus.CREATED" in content:
        if "CREATED (201)" not in patterns["response_entity"]:
            patterns["response_entity"].append("CREATED (201)")

    # ResponseEntity.ok()
    if "ResponseEntity.ok(" in content:
        if "OK (200)" not in patterns["response_entity"]:
            patterns["response_entity"].append("OK (200)")

    # ResponseEntity.noContent()
    if "noContent().build()" in content:
        if "NO_CONTENT (204)" not in patterns["response_entity"]:
            patterns["response_entity"].append("NO_CONTENT (204)")


def analyze_exception_patterns(content: str, patterns: Dict) -> None:
    """예외 처리 패턴을 분석합니다."""
    # 커스텀 예외 클래스 사용
    exception_types = [
        "ResourceNotFoundException",
        "BadRequestException",
        "UnauthorizedException",
        "ForbiddenException",
    ]

    for exc in exception_types:
        if exc in content:
            if exc not in patterns["exceptions"]:
                patterns["exceptions"].append(exc)

    # @ExceptionHandler 사용
    if "@ExceptionHandler" in content:
        if "@ExceptionHandler" not in patterns["exceptions"]:
            patterns["exceptions"].append("@ExceptionHandler")

    # @RestControllerAdvice 사용
    if "@RestControllerAdvice" in content:
        if "@RestControllerAdvice" not in patterns["exceptions"]:
            patterns["exceptions"].append("@RestControllerAdvice")


def analyze_validation_patterns(content: str, patterns: Dict) -> None:
    """Bean Validation 패턴을 분석합니다."""
    validation_annotations = [
        "@Valid",
        "@NotNull",
        "@NotBlank",
        "@NotEmpty",
        "@Size",
        "@Email",
        "@Pattern",
        "@Min",
        "@Max",
        "@Positive",
    ]

    for anno in validation_annotations:
        if anno in content:
            if anno not in patterns["validation"]:
                patterns["validation"].append(anno)


def analyze_authorization_patterns(content: str, patterns: Dict) -> None:
    """권한 패턴을 분석합니다."""
    # @PreAuthorize
    if "@PreAuthorize" in content:
        if "@PreAuthorize" not in patterns["authorization"]:
            patterns["authorization"].append("@PreAuthorize")

    # @RequirePermission (커스텀)
    if "@RequirePermission" in content:
        if "@RequirePermission" not in patterns["authorization"]:
            patterns["authorization"].append("@RequirePermission")

    # PermissionCode enum
    if "PermissionCode" in content:
        if "PermissionCode enum" not in patterns["authorization"]:
            patterns["authorization"].append("PermissionCode enum")

    # UserPrincipal
    if "UserPrincipal" in content:
        if "UserPrincipal" not in patterns["authorization"]:
            patterns["authorization"].append("UserPrincipal")


def analyze_transaction_patterns(content: str, patterns: Dict) -> None:
    """트랜잭션 패턴을 분석합니다."""
    # @Transactional
    if "@Transactional" in content:
        if "@Transactional" not in patterns["transaction"]:
            patterns["transaction"].append("@Transactional")

    # @Transactional(readOnly = true)
    if "readOnly = true" in content or "readOnly=true" in content:
        if "@Transactional(readOnly=true)" not in patterns["transaction"]:
            patterns["transaction"].append("@Transactional(readOnly=true)")


def analyze_python_patterns(module_path: Path) -> Dict:
    """Python 모듈의 패턴을 분석합니다."""
    patterns = {
        "logging": [],
        "error_handling": [],
        "type_hints": False,
        "async": [],
    }

    py_files = list(module_path.rglob("*.py"))

    for file_path in py_files:
        if "__pycache__" in str(file_path) or ".venv" in str(file_path):
            continue

        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")

            # 로깅 패턴
            if "import logging" in content:
                if "logging module" not in patterns["logging"]:
                    patterns["logging"].append("logging module")

            if "logger = logging.getLogger" in content:
                if "getLogger pattern" not in patterns["logging"]:
                    patterns["logging"].append("getLogger pattern")

            # 에러 처리 패턴
            if re.search(r'try:[\s\S]*?except\s+\w+', content):
                if "try-except" not in patterns["error_handling"]:
                    patterns["error_handling"].append("try-except")

            if re.search(r'raise\s+\w+Exception', content):
                if "custom exceptions" not in patterns["error_handling"]:
                    patterns["error_handling"].append("custom exceptions")

            # 타입 힌트
            if re.search(r'def\s+\w+\([^)]*:\s*\w+', content):
                patterns["type_hints"] = True

            # async 패턴
            if "async def" in content:
                if "async/await" not in patterns["async"]:
                    patterns["async"].append("async/await")

        except Exception:
            continue

    return patterns


def analyze_nodejs_patterns(module_path: Path) -> Dict:
    """Node.js 모듈의 패턴을 분석합니다."""
    patterns = {
        "async": [],
        "error_handling": [],
        "logging": [],
    }

    # JS/TS 파일 분석
    js_files = list(module_path.rglob("*.js")) + list(module_path.rglob("*.ts"))

    for file_path in js_files:
        if "node_modules" in str(file_path):
            continue

        try:
            content = file_path.read_text(encoding="utf-8", errors="ignore")

            # async/await 패턴
            if "async " in content and "await " in content:
                if "async/await" not in patterns["async"]:
                    patterns["async"].append("async/await")

            if "Promise.all" in content:
                if "Promise.all" not in patterns["async"]:
                    patterns["async"].append("Promise.all")

            # 에러 처리 패턴
            if re.search(r'try\s*\{[\s\S]*?catch\s*\(', content):
                if "try-catch" not in patterns["error_handling"]:
                    patterns["error_handling"].append("try-catch")

            # 로깅 패턴
            if "winston" in content:
                if "winston" not in patterns["logging"]:
                    patterns["logging"].append("winston")

            if "console.log" in content or "console.error" in content:
                if "console" not in patterns["logging"]:
                    patterns["logging"].append("console")

        except Exception:
            continue

    return patterns


def summarize_patterns(pattern_list: List[Dict], type_key: str = "type", value_key: str = "value") -> Dict[str, Counter]:
    """패턴 리스트를 타입별로 요약합니다."""
    summary = {}
    for item in pattern_list:
        ptype = item.get(type_key, "unknown")
        pvalue = item.get(value_key, "unknown")
        if ptype not in summary:
            summary[ptype] = Counter()
        summary[ptype][pvalue] += 1
    return summary


def format_detected_patterns(module_type: str, patterns: Dict) -> str:
    """감지된 패턴을 마크다운 형식으로 변환합니다."""
    lines = []

    # React 패턴 감지 (nodejs 모듈이면서 React 관련 패턴이 있는 경우)
    is_react = (module_type == "nodejs" and
                (patterns.get("ui_library") or
                 patterns.get("dialog") or
                 patterns.get("snackbar") or
                 patterns.get("button_colors")))

    if is_react:
        # React 패턴 포맷팅
        lines.append("### Dialog 패턴")
        lines.append("")
        if patterns.get("dialog"):
            summary = summarize_patterns(patterns["dialog"])
            lines.append("| 속성 | 사용 값 |")
            lines.append("|------|---------|")
            for ptype, values in summary.items():
                # 가장 많이 사용된 순서로 정렬
                sorted_values = values.most_common()
                value_str = ", ".join([f"`{v}` ({c}회)" for v, c in sorted_values[:3]])
                lines.append(f"| **{ptype}** | {value_str} |")
        else:
            lines.append("Dialog 패턴이 감지되지 않았습니다.")
        lines.append("")

        lines.append("### Snackbar/알림 패턴")
        lines.append("")
        if patterns.get("snackbar"):
            summary = summarize_patterns(patterns["snackbar"])
            lines.append("| 속성 | 사용 값 |")
            lines.append("|------|---------|")
            for ptype, values in summary.items():
                sorted_values = values.most_common()
                value_str = ", ".join([f"`{v}` ({c}회)" for v, c in sorted_values[:3]])
                lines.append(f"| **{ptype}** | {value_str} |")
        else:
            lines.append("Snackbar 패턴이 감지되지 않았습니다.")
        lines.append("")

        lines.append("### 버튼 스타일 패턴")
        lines.append("")
        if patterns.get("button_colors"):
            summary = summarize_patterns(patterns["button_colors"])
            if "color" in summary:
                colors = [v for v, _ in summary["color"].most_common()]
                lines.append(f"- **사용 색상**: {', '.join([f'`{c}`' for c in colors])}")
            if "variant" in summary:
                variants = [v for v, _ in summary["variant"].most_common()]
                lines.append(f"- **사용 variant**: {', '.join([f'`{v}`' for v in variants])}")
        else:
            lines.append("버튼 패턴이 감지되지 않았습니다.")
        lines.append("")

        lines.append("### 폼 처리 패턴")
        lines.append("")
        if patterns.get("form_handling"):
            unique_patterns = list(set(patterns["form_handling"]))
            for item in unique_patterns:
                lines.append(f"- {item}")
        else:
            lines.append("폼 처리 패턴이 감지되지 않았습니다.")
        lines.append("")

        lines.append("### 에러 처리 패턴")
        lines.append("")
        if patterns.get("error_handling"):
            unique_patterns = list(set(patterns["error_handling"]))
            for item in unique_patterns:
                lines.append(f"- {item}")
        else:
            lines.append("에러 처리 패턴이 감지되지 않았습니다.")

    elif module_type == "java":
        # Java 패턴 포맷팅
        lines.append("### API 응답 패턴")
        lines.append("")
        if patterns.get("response_entity"):
            lines.append("| HTTP 상태 | 용도 |")
            lines.append("|-----------|------|")
            for item in patterns["response_entity"]:
                if "201" in item:
                    lines.append(f"| {item} | POST 성공 응답 |")
                elif "200" in item:
                    lines.append(f"| {item} | GET/PUT 성공 응답 |")
                elif "204" in item:
                    lines.append(f"| {item} | DELETE 성공 응답 |")
        lines.append("")

        lines.append("### 예외 처리 패턴")
        lines.append("")
        if patterns.get("exceptions"):
            for item in patterns["exceptions"]:
                if "Exception" in item:
                    lines.append(f"- `{item}` - 커스텀 예외")
                else:
                    lines.append(f"- `{item}` - 글로벌 예외 핸들러")
        lines.append("")

        lines.append("### 검증 패턴")
        lines.append("")
        if patterns.get("validation"):
            lines.append("| 어노테이션 | 용도 |")
            lines.append("|------------|------|")
            for item in patterns["validation"]:
                usage = get_validation_usage(item)
                lines.append(f"| `{item}` | {usage} |")
        lines.append("")

        lines.append("### 권한 처리 패턴")
        lines.append("")
        if patterns.get("authorization"):
            for item in patterns["authorization"]:
                lines.append(f"- `{item}`")
        lines.append("")

        lines.append("### 트랜잭션 패턴")
        lines.append("")
        if patterns.get("transaction"):
            for item in patterns["transaction"]:
                if "readOnly" in item:
                    lines.append(f"- `{item}` - 읽기 전용 쿼리")
                else:
                    lines.append(f"- `{item}` - 쓰기 작업")

    elif module_type == "python":
        # Python 패턴 포맷팅
        lines.append("### 로깅 패턴")
        lines.append("")
        if patterns.get("logging"):
            for item in patterns["logging"]:
                lines.append(f"- {item}")
        else:
            lines.append("로깅 패턴이 감지되지 않았습니다.")
        lines.append("")

        lines.append("### 에러 처리 패턴")
        lines.append("")
        if patterns.get("error_handling"):
            for item in patterns["error_handling"]:
                lines.append(f"- {item}")
        lines.append("")

        lines.append("### 타입 힌트")
        lines.append("")
        if patterns.get("type_hints"):
            lines.append("- 타입 힌트 사용 중")
        else:
            lines.append("- 타입 힌트 미사용")

    else:
        # Node.js 패턴 포맷팅
        lines.append("### 비동기 처리 패턴")
        lines.append("")
        if patterns.get("async"):
            for item in patterns["async"]:
                lines.append(f"- {item}")
        lines.append("")

        lines.append("### 에러 처리 패턴")
        lines.append("")
        if patterns.get("error_handling"):
            for item in patterns["error_handling"]:
                lines.append(f"- {item}")
        lines.append("")

        lines.append("### 로깅 패턴")
        lines.append("")
        if patterns.get("logging"):
            for item in patterns["logging"]:
                lines.append(f"- {item}")

    return "\n".join(lines)


def get_validation_usage(annotation: str) -> str:
    """검증 어노테이션의 용도를 반환합니다."""
    usages = {
        "@Valid": "DTO 검증 활성화",
        "@NotNull": "null 불허",
        "@NotBlank": "빈 문자열 불허",
        "@NotEmpty": "빈 컬렉션 불허",
        "@Size": "길이 제한",
        "@Email": "이메일 형식 검증",
        "@Pattern": "정규식 패턴 검증",
        "@Min": "최소값 검증",
        "@Max": "최대값 검증",
        "@Positive": "양수 검증",
    }
    return usages.get(annotation, "검증")


def analyze_module_patterns(module_path: Path, module_type: str) -> Dict:
    """모듈의 코드 패턴을 분석합니다."""
    if module_type == "java":
        return analyze_java_patterns(module_path)
    elif module_type == "nodejs":
        # React 여부 확인
        pkg_path = module_path / "package.json"
        if pkg_path.exists():
            try:
                import json
                pkg = json.loads(pkg_path.read_text())
                deps = pkg.get("dependencies", {})
                if "react" in deps:
                    return analyze_react_patterns(module_path)
            except:
                pass
        return analyze_nodejs_patterns(module_path)
    elif module_type == "python":
        return analyze_python_patterns(module_path)

    return {}
