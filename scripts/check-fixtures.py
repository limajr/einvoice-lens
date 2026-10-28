#!/usr/bin/env python3
"""Verify the pinned sample corpus with the official CLI; no application integration."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SAMPLES = ROOT / "lens-core/src/test/resources/samples"
NS = {"r": "http://www.xoev.de/de/validator/varl/1"}


def check_hash(path, expected):
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise ValueError(f"SHA-256 mismatch: {path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validator", type=Path, help="Pinned standalone validator JAR")
    parser.add_argument("--configuration", type=Path, help="Pinned configuration ZIP")
    parser.add_argument("--download-tools", action="store_true",
                        help="Download pinned tools into ignored lens-core/target/fixture-tools")
    args = parser.parse_args()
    manifest = json.loads((SAMPLES / "manifest.json").read_text())
    if args.download_tools and (args.validator or args.configuration):
        parser.error("Choose --download-tools or both local tool paths")
    if args.download_tools:
        cache = ROOT / "lens-core/target/fixture-tools"
        cache.mkdir(parents=True, exist_ok=True)
        for key, name in [("validator", "validator.jar"), ("configuration", "configuration.zip")]:
            destination = cache / name
            tool = manifest["tools"][key]
            if not destination.exists():
                with urllib.request.urlopen(tool["url"], timeout=60) as response:
                    data = response.read()
                if hashlib.sha256(data).hexdigest() != tool["sha256"]:
                    raise ValueError(f"Downloaded {key} checksum mismatch")
                destination.write_bytes(data)
            setattr(args, key, destination)
    if not args.validator or not args.configuration:
        parser.error("Supply --validator and --configuration, or --download-tools")
    for key in ("validator", "configuration"):
        check_hash(getattr(args, key), manifest["tools"][key]["sha256"])
    for fixture in manifest["fixtures"]:
        check_hash(SAMPLES / fixture["path"], fixture["sha256"])
    with tempfile.TemporaryDirectory(prefix="einvoice-lens-fixtures-") as temp:
        work = Path(temp)
        config, reports = work / "config", work / "reports"
        config.mkdir()
        reports.mkdir()
        # Only the checksum-verified official configuration archive is extracted.
        with zipfile.ZipFile(args.configuration) as archive:
            for member in archive.namelist():
                if not (config / member).resolve().is_relative_to(config.resolve()):
                    raise ValueError("Unsafe archive member")
            archive.extractall(config)
        result = subprocess.run([
            "java", "-jar", str(args.validator.resolve()),
            "-s", str(config / "scenarios.xml"), "-r", str(config), "-o", str(reports),
            *[str(SAMPLES / f["path"]) for f in manifest["fixtures"]]],
            capture_output=True, text=True, timeout=120)
        # Invalid fixtures intentionally make the official CLI exit with code 4.
        if result.returncode != 4:
            raise RuntimeError(result.stdout + result.stderr)
        for fixture in manifest["fixtures"]:
            report = reports / (Path(fixture["path"]).stem + "-report.xml")
            if not report.exists():
                raise RuntimeError(f"Missing report: {report.name}\n{result.stdout}\n{result.stderr}")
            root = ET.parse(report).getroot()
            actual = {
                "acceptable": root.find("r:assessment/r:accept", NS) is not None,
                "reportValid": root.get("valid") == "true",
                "messages": sorted([
                    {"code": e.get("code"), "level": e.get("level")}
                    for e in root.findall(".//r:message", NS)
                ], key=lambda x: (x["code"], x["level"]))}
            if actual != fixture["expected"]:
                raise AssertionError(f"{fixture['path']}: expected {fixture['expected']}, got {actual}")
            print(f"PASS {fixture['path']}")
    print(f"Verified {len(manifest['fixtures'])} fixture outcomes against pinned KoSIT tools.")


if __name__ == "__main__":
    main()
