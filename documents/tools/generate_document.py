#!/usr/bin/env python3
"""Generate one local document bundle into build/*.docx."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from document_generator import Diagnostic, GenerationError, build_bundle, load_bundle, validate_bundle


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", required=True, help="Document-bundle directory, relative to the repository root.")
    parser.add_argument("--output-dir", default="build", help="Generated-artifact directory, relative to the repository root.")
    parser.add_argument("--validate", action="store_true", help="Validate local bundle inputs without rendering diagrams or DOCX.")
    args = parser.parse_args()
    root = Path.cwd().resolve()
    try:
        bundle_path = (root / args.bundle).resolve()
        output_dir = (root / args.output_dir).resolve()
        try:
            bundle_path.relative_to(root)
        except ValueError as exc:
            raise GenerationError(
                Diagnostic("bundle-outside-repository", "bundle must stay inside the repository.")
            ) from exc
        if args.validate:
            bundle = load_bundle(bundle_path)
            diagrams = validate_bundle(bundle)
            print(f"[VALID] report_id={bundle.report_id!r} fragments={len(bundle.fragments)} diagrams={len(diagrams)}")
        else:
            build_bundle(bundle_path, root, output_dir)
    except GenerationError as exc:
        print(exc.diagnostic.format(), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
