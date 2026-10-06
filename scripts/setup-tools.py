#!/usr/bin/env python3
"""Provision pinned validator locally; never edit a sibling repository."""

import hashlib
import io
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
URL = "https://downloads.interlis.ch/ilivalidator/ilivalidator-1.15.0.zip"
SHA256 = "df7ee8961743e3dfc586f646af4f63d49dabf920e98a4836a32edf676d0b20c7"


def main():
    payload = urllib.request.urlopen(URL, timeout=60).read()
    if hashlib.sha256(payload).hexdigest() != SHA256:
        raise SystemExit("ilivalidator checksum mismatch")
    destination = ROOT / ".datenportal-integrator/tools/ilivalidator"
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        for entry in archive.infolist():
            if not (destination / entry.filename).resolve().is_relative_to(destination.resolve()):
                raise SystemExit("Unsafe archive entry")
        archive.extractall(destination)
    print(destination / "ilivalidator-1.15.0.jar")


if __name__ == "__main__":
    main()
