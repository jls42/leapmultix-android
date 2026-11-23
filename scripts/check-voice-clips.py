#!/usr/bin/env python3
"""Contrôle la voix enregistrée du site embarqué : de l'Opus, et tous ses clips.

    check-voice-clips.py <www/voice> <dépôt des voix>/opus

Pour chacune des trois langues, l'index du site (index.json) doit annoncer le format « opus »,
et le dossier de la version annoncée doit contenir exactement les clips du manifeste Opus de
cette version (opus/<langue>/<version>.json), en fichiers .ogg, sans rien d'autre.
Code de sortie 0 : tout y est. 1 : un écart, dit sur la sortie d'erreur.
"""

import json
import sys
from pathlib import Path

LANGUAGES = ("fr", "en", "es")


def language_gaps(site, opus, lang, entry):
    """Écarts d'une langue : phrases prêtes à afficher, liste vide quand tout y est."""
    version = entry.get("version")
    if entry.get("format") != "opus" or not version:
        return [f"voix {lang} : l'index du site ne l'annonce pas en Opus"]
    manifest = opus / lang / f"{version}.json"
    folder = site / lang / version
    if not manifest.is_file() or not folder.is_dir():
        return [f"voix {lang} : manifeste Opus ou dossier des clips introuvable ({version})"]
    expected = set(json.loads(manifest.read_text(encoding="utf-8"))["clips"])
    files = list(folder.iterdir())
    clips = {path.stem for path in files if path.suffix == ".ogg"}
    others = [path.name for path in files if path.suffix != ".ogg"]
    print(f"voix {lang} ({version}) : {len(clips)} clips en Opus, {len(expected)} attendus")
    if clips == expected and not others:
        return []
    return [
        f"voix {lang} : {len(expected - clips)} clips absents, {len(clips - expected)} en trop, "
        f"{len(others)} fichiers qui ne sont pas des clips"
    ]


def main() -> int:
    site, opus = (Path(arg) for arg in sys.argv[1:3])
    languages = json.loads((site / "index.json").read_text(encoding="utf-8"))["languages"]
    gaps = [
        gap
        for lang in LANGUAGES
        for gap in language_gaps(site, opus, lang, languages.get(lang, {}))
    ]
    for gap in gaps:
        print(gap, file=sys.stderr)
    return 1 if gaps else 0


if __name__ == "__main__":
    sys.exit(main())
