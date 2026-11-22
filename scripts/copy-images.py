#!/usr/bin/env python3
"""Copie dans l'appli les variantes WebP des images que le site embarqué a encore.

Le dossier des images générées du jeu (non versionné) garde les variantes d'images retirées
depuis : elles n'entrent pas dans l'appli. La carte des images est réécrite avec les seules
images copiées.

    copy-images.py <images générées du jeu> <liste des sources de la branche> <destination>

La liste donne, une par ligne, les fichiers de assets/images/ dans la branche du site
(git ls-tree). Arrêt si une image de la carte a perdu une variante, ou si une source de la
branche n'a aucune variante : le dossier des images générées est alors à refaire
(npm run assets:generate).
"""

import json
import shutil
import sys
from pathlib import Path

SOURCE_ROOT = "assets/images/"
SOURCE_TYPES = (".png", ".jpg", ".jpeg")


def main() -> int:
    generated, listing, destination = (Path(arg) for arg in sys.argv[1:4])
    sources = {
        line[len(SOURCE_ROOT):]
        for line in listing.read_text(encoding="utf-8").splitlines()
        if line.startswith(SOURCE_ROOT) and line.lower().endswith(SOURCE_TYPES)
    }
    image_map = json.loads((generated / "image-map.json").read_text(encoding="utf-8"))

    kept = {}
    removed_files = 0
    removed_bytes = 0
    missing = []
    for key, entry in image_map.items():
        variants = [generated / path for path in entry["resolutions"].values()]
        if entry["original"] not in sources:
            present = [path for path in variants if path.is_file()]
            removed_files += len(present)
            removed_bytes += sum(path.stat().st_size for path in present)
            continue
        missing += [str(path) for path in variants if not path.is_file()]
        kept[key] = entry

    without_variant = sorted(sources - {entry["original"] for entry in kept.values()})
    if missing or without_variant:
        for path in missing[:10]:
            print(f"variante absente : {path}", file=sys.stderr)
        for source in without_variant[:10]:
            print(f"aucune variante pour : {SOURCE_ROOT}{source}", file=sys.stderr)
        print(
            f"{len(missing)} variantes absentes, {len(without_variant)} images sans variante : "
            "refaire les images générées du jeu (npm run assets:generate)",
            file=sys.stderr,
        )
        return 1

    copied = 0
    for entry in kept.values():
        for path in entry["resolutions"].values():
            target = destination / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(generated / path, target)
            copied += 1
    (destination / "image-map.json").write_text(
        json.dumps(kept, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(
        f"images générées : {len(kept)} images, {copied} variantes copiées ; "
        f"{removed_files} variantes d'images retirées du jeu laissées de côté "
        f"({removed_bytes / 2**20:.0f} Mo)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
