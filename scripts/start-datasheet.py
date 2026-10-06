#!/usr/bin/env python3
"""Build an isolated copy of the existing Java MCP and run it on its configured port."""

import argparse
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=ROOT.parent / "datenportal-datenblatt-editor/mcp-java")
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    if not args.java_home:
        parser.error("--java-home oder JAVA_HOME mit JDK 25 erforderlich")
    copied = ROOT / ".datenportal-integrator/tools/datasheet-source"
    shutil.copytree(
        args.source, copied, dirs_exist_ok=True, ignore=shutil.ignore_patterns(".git", ".gradle", "build")
    )
    env = {**os.environ, "JAVA_HOME": str(args.java_home)}
    subprocess.run(
        ["bash", str(copied / "gradlew"), "-p", str(copied), "bootJar", "--console=plain"],
        env=env,
        check=True,
    )
    os.execve(
        str(args.java_home / "bin/java"),
        [str(args.java_home / "bin/java"), "-jar", str(copied / "build/libs/datasheet-mcp.jar")],
        env,
    )


if __name__ == "__main__":
    main()
