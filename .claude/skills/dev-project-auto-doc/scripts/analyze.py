#!/usr/bin/env python3
"""
프로젝트 코드 분석 스크립트

프로젝트 소스 코드 구조를 분석하고 결과를 출력합니다.

사용법:
    python analyze.py
"""

import json
import re
from pathlib import Path
from typing import Dict, List


def get_project_root() -> Path:
    """프로젝트 루트 디렉토리를 반환합니다."""
    return Path.cwd()


def find_files_by_pattern(root: Path, patterns: List[str]) -> List[Path]:
    """패턴에 맞는 파일들을 찾습니다."""
    files = []
    for pattern in patterns:
        files.extend(root.rglob(pattern))
    return files


def extract_class_name(file_path: Path) -> str:
    """파일에서 클래스명을 추출합니다."""
    content = file_path.read_text(encoding="utf-8", errors="ignore")

    # Java class
    match = re.search(r'public\s+class\s+(\w+)', content)
    if match:
        return match.group(1)

    # TypeScript/JavaScript export
    match = re.search(r'export\s+(?:default\s+)?(?:class|function|const)\s+(\w+)', content)
    if match:
        return match.group(1)

    return file_path.stem


def extract_api_endpoints(file_path: Path) -> List[Dict]:
    """Controller 파일에서 API 엔드포인트를 추출합니다."""
    content = file_path.read_text(encoding="utf-8", errors="ignore")
    endpoints = []

    # Spring @RequestMapping, @GetMapping, @PostMapping 등
    class_mapping = re.search(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', content)
    base_path = class_mapping.group(1) if class_mapping else ""

    # 개별 매핑 찾기
    patterns = [
        (r'@GetMapping\s*\(\s*["\']([^"\']*)["\']', 'GET'),
        (r'@PostMapping\s*\(\s*["\']([^"\']*)["\']', 'POST'),
        (r'@PutMapping\s*\(\s*["\']([^"\']*)["\']', 'PUT'),
        (r'@DeleteMapping\s*\(\s*["\']([^"\']*)["\']', 'DELETE'),
        (r'@PatchMapping\s*\(\s*["\']([^"\']*)["\']', 'PATCH'),
    ]

    for pattern, method in patterns:
        matches = re.findall(pattern, content)
        for match in matches:
            path = base_path + match
            endpoints.append({"method": method, "path": path})

    # 빈 GetMapping 등
    simple_patterns = [
        (r'@GetMapping\s*(?:\(\s*\))?\s*\n', 'GET', base_path),
        (r'@PostMapping\s*(?:\(\s*\))?\s*\n', 'POST', base_path),
    ]

    for pattern, method, path in simple_patterns:
        if re.search(pattern, content):
            if not any(e["path"] == path and e["method"] == method for e in endpoints):
                endpoints.append({"method": method, "path": path or "/"})

    return endpoints


def analyze_java_spring(root: Path) -> Dict:
    """Java/Spring 프로젝트를 분석합니다."""
    result = {
        "entities": [],
        "controllers": [],
        "services": [],
        "repositories": [],
        "endpoints": [],
    }

    # 소스 디렉토리 찾기
    src_dirs = list(root.rglob("src/main/java"))

    for src_dir in src_dirs:
        # Entity
        for f in src_dir.rglob("**/entity/*.java"):
            result["entities"].append({
                "name": extract_class_name(f),
                "file": str(f.relative_to(root))
            })

        # Controller
        for f in src_dir.rglob("**/*Controller.java"):
            controller_name = extract_class_name(f)
            endpoints = extract_api_endpoints(f)
            result["controllers"].append({
                "name": controller_name,
                "file": str(f.relative_to(root)),
                "endpoints": endpoints
            })
            result["endpoints"].extend(endpoints)

        # Service
        for f in src_dir.rglob("**/*Service.java"):
            if "Impl" not in f.name:  # Impl 제외
                result["services"].append({
                    "name": extract_class_name(f),
                    "file": str(f.relative_to(root))
                })

        # Repository
        for f in src_dir.rglob("**/*Repository.java"):
            result["repositories"].append({
                "name": extract_class_name(f),
                "file": str(f.relative_to(root))
            })

    return result


def analyze_react(root: Path) -> Dict:
    """React 프로젝트를 분석합니다."""
    result = {
        "pages": [],
        "components": [],
        "contexts": [],
    }

    # src 디렉토리 찾기
    src_dirs = list(root.rglob("src"))

    for src_dir in src_dirs:
        if not src_dir.is_dir():
            continue

        # Pages
        pages_dir = src_dir / "pages"
        if pages_dir.exists():
            for f in pages_dir.rglob("*.tsx"):
                result["pages"].append({
                    "name": f.stem,
                    "file": str(f.relative_to(root))
                })
            for f in pages_dir.rglob("*.jsx"):
                result["pages"].append({
                    "name": f.stem,
                    "file": str(f.relative_to(root))
                })

        # Components
        components_dir = src_dir / "components"
        if components_dir.exists():
            for f in components_dir.rglob("*.tsx"):
                result["components"].append({
                    "name": f.stem,
                    "file": str(f.relative_to(root))
                })

        # Contexts
        contexts_dir = src_dir / "contexts"
        if contexts_dir.exists():
            for f in contexts_dir.rglob("*Context.tsx"):
                result["contexts"].append({
                    "name": f.stem,
                    "file": str(f.relative_to(root))
                })

    return result


def analyze_nodejs(root: Path) -> Dict:
    """Node.js/TypeScript 프로젝트를 분석합니다."""
    result = {
        "models": [],
        "routes": [],
        "controllers": [],
    }

    # src 디렉토리 찾기
    src_dirs = [root / "src", root]

    for src_dir in src_dirs:
        if not src_dir.exists():
            continue

        # Models
        models_dir = src_dir / "models"
        if models_dir.exists():
            for f in models_dir.rglob("*.ts"):
                result["models"].append({
                    "name": f.stem,
                    "file": str(f.relative_to(root))
                })

        # Routes
        routes_dir = src_dir / "routes"
        if routes_dir.exists():
            for f in routes_dir.rglob("*.ts"):
                result["routes"].append({
                    "name": f.stem,
                    "file": str(f.relative_to(root))
                })

        # Controllers
        controllers_dir = src_dir / "controllers"
        if controllers_dir.exists():
            for f in controllers_dir.rglob("*.ts"):
                result["controllers"].append({
                    "name": f.stem,
                    "file": str(f.relative_to(root))
                })

    return result


def detect_project_type(root: Path) -> List[str]:
    """프로젝트 타입을 감지합니다."""
    types = []

    # Java/Spring
    if list(root.rglob("pom.xml")) or list(root.rglob("build.gradle")):
        types.append("java_spring")

    # React
    if list(root.rglob("package.json")):
        for pkg in root.rglob("package.json"):
            try:
                content = json.loads(pkg.read_text())
                deps = content.get("dependencies", {})
                if "react" in deps:
                    types.append("react")
                    break
            except:
                pass

    # Node.js
    if list(root.rglob("package.json")) and "react" not in types:
        types.append("nodejs")

    return types if types else ["unknown"]


def analyze_project() -> Dict:
    """프로젝트 전체를 분석합니다."""
    root = get_project_root()
    project_types = detect_project_type(root)

    result = {
        "project_name": root.name,
        "project_types": project_types,
        "java_spring": {},
        "react": {},
        "nodejs": {},
    }

    if "java_spring" in project_types:
        result["java_spring"] = analyze_java_spring(root)

    if "react" in project_types:
        result["react"] = analyze_react(root)

    if "nodejs" in project_types:
        result["nodejs"] = analyze_nodejs(root)

    return result


def print_analysis(analysis: Dict) -> None:
    """분석 결과를 출력합니다."""
    print("\n📊 프로젝트 코드 분석 결과\n")
    print(f"📁 프로젝트: {analysis['project_name']}")
    print(f"🔧 감지된 타입: {', '.join(analysis['project_types'])}")
    print()

    # Java/Spring
    java = analysis.get("java_spring", {})
    if java:
        print("═" * 50)
        print("☕ Java/Spring")
        print("═" * 50)

        if java.get("entities"):
            print(f"\n📦 Entities ({len(java['entities'])}개):")
            for e in java["entities"]:
                print(f"   • {e['name']}")

        if java.get("controllers"):
            print(f"\n🌐 Controllers ({len(java['controllers'])}개):")
            for c in java["controllers"]:
                print(f"   • {c['name']}")
                for ep in c.get("endpoints", []):
                    print(f"      {ep['method']:6} {ep['path']}")

        if java.get("services"):
            print(f"\n⚙️  Services ({len(java['services'])}개):")
            for s in java["services"]:
                print(f"   • {s['name']}")

        if java.get("repositories"):
            print(f"\n💾 Repositories ({len(java['repositories'])}개):")
            for r in java["repositories"]:
                print(f"   • {r['name']}")

    # React
    react = analysis.get("react", {})
    if react:
        print("\n" + "═" * 50)
        print("⚛️  React")
        print("═" * 50)

        if react.get("pages"):
            print(f"\n📄 Pages ({len(react['pages'])}개):")
            for p in react["pages"]:
                print(f"   • {p['name']}")

        if react.get("components"):
            print(f"\n🧩 Components ({len(react['components'])}개):")
            for c in react["components"][:10]:  # 최대 10개만
                print(f"   • {c['name']}")
            if len(react["components"]) > 10:
                print(f"   ... 외 {len(react['components']) - 10}개")

        if react.get("contexts"):
            print(f"\n🔗 Contexts ({len(react['contexts'])}개):")
            for c in react["contexts"]:
                print(f"   • {c['name']}")

    # Node.js
    nodejs = analysis.get("nodejs", {})
    if nodejs and nodejs != react:
        print("\n" + "═" * 50)
        print("🟢 Node.js")
        print("═" * 50)

        if nodejs.get("models"):
            print(f"\n📦 Models ({len(nodejs['models'])}개):")
            for m in nodejs["models"]:
                print(f"   • {m['name']}")

        if nodejs.get("routes"):
            print(f"\n🛣️  Routes ({len(nodejs['routes'])}개):")
            for r in nodejs["routes"]:
                print(f"   • {r['name']}")

    print()


def main():
    """메인 함수"""
    analysis = analyze_project()
    print_analysis(analysis)

    # JSON으로도 저장 (다른 스크립트에서 사용)
    cache_dir = get_project_root() / ".claude" / ".cache"
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache_file = cache_dir / "project_analysis.json"
    cache_file.write_text(json.dumps(analysis, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
