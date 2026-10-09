#!/usr/bin/env python3
"""Prepara o helper de toque do checkout temporário usado no CI do backend."""

import sys
from pathlib import Path


def prepare(app_dir: Path) -> None:
    helper = app_dir / "integration_test/support/e2e_actions.dart"
    source = helper.read_text(encoding="utf-8")
    original = "await tester.ensureVisible(finder);"
    centered = """// Centraliza o alvo, evitando bordas cobertas por barras/overlays.
  await Scrollable.ensureVisible(tester.element(finder), alignment: 0.5);"""
    if centered in source:
        return
    if source.count(original) != 1:
        raise ValueError("Helper E2E mudou: esperava exatamente uma chamada ensureVisible(finder).")
    source = source.replace(original, centered)
    widgets_import = "import 'package:flutter/widgets.dart';"
    if widgets_import not in source:
        source = widgets_import + "\n" + source
    helper.write_text(source, encoding="utf-8")
    print("Helper E2E preparado: alvo centralizado, com hit-test e toque reais preservados.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Uso: prepare-client-e2e.py <checkout-do-app>")
    prepare(Path(sys.argv[1]))
