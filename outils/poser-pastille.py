#!/usr/bin/env python3
"""Pose (ou met à jour) la pastille de mise à jour dans une appli.

Usage :  python3 outils/poser-pastille.py rando-3d
         python3 outils/poser-pastille.py --toutes

La seule source d'information est le fichier <dossier>/version.json :
  nom, version, date (JJ/MM/AAAA), nouveautes (liste, la plus récente d'abord),
  cache (début des noms de cache à vider), sw ({"fichier", "cache"}),
  fichiers (pages html qui reçoivent la pastille), css (réglages propres à l'appli),
  pastille (false = l'appli a déjà sa propre pastille : on ne touche qu'au sw).

Le script : 1) remplace le bloc entre <!-- MAJ:debut --> et <!-- MAJ:fin --> (ou l'ajoute
avant </body>), 2) met le nom de cache du service worker à la valeur de sw.cache.
"""
import json, re, sys, pathlib

RACINE = pathlib.Path(__file__).resolve().parent.parent
MODELE = (RACINE / "outils" / "pastille-maj.html").read_text(encoding="utf-8")
BLOC = re.compile(r"<!-- MAJ:debut.*?<!-- MAJ:fin -->\n?", re.S)


def poser(dossier):
    d = RACINE / dossier
    v = json.loads((d / "version.json").read_text(encoding="utf-8"))
    for cle in ("nom", "version", "date"):
        assert v.get(cle), f"{dossier}/version.json : « {cle} » manquant"
    assert re.fullmatch(r"\d\d/\d\d/\d{4}", v["date"]), "date attendue au format JJ/MM/AAAA"

    if v.get("pastille", True):
        config = {k: v[k] for k in ("nom", "version", "date", "cache", "nouveautes") if k in v}
        bloc = (MODELE.replace("/*MAJ_CONFIG*/", json.dumps(config, ensure_ascii=False))
                      .replace("/*MAJ_CSS_APPLI*/", v.get("css", "")))
        if not bloc.endswith("\n"):
            bloc += "\n"
        for nom in v.get("fichiers", []):
            f = d / nom
            html = f.read_text(encoding="utf-8")
            if BLOC.search(html):
                html = BLOC.sub(lambda m: bloc, html, count=1)
            elif "</body>" in html and html.rfind("</body>") > html.rfind("</script>"):
                # le vrai </body> (pas un texte « </body> » caché dans un script)
                i = html.rfind("</body>")
                html = html[:i] + bloc + html[i:]
            else:
                html = html.rstrip("\n") + "\n" + bloc
            f.write_text(html, encoding="utf-8")
            print(f"  pastille {v['version']} · {v['date']} -> {dossier}/{nom}")
    else:
        # l'appli a sa propre pastille : on met juste à jour son texte (id="version-appli")
        for nom in v.get("fichiers", []):
            f = d / nom
            html, n = re.subn(r'(id="version-appli">)[^<]*(<)',
                              lambda m: f"{m.group(1)}{v['version']} · {v['date'][:5]}{m.group(2)}",
                              f.read_text(encoding="utf-8"), count=1)
            assert n == 1, f'id="version-appli" introuvable dans {f}'
            f.write_text(html, encoding="utf-8")
            print(f"  étiquette {v['version']} · {v['date'][:5]} -> {dossier}/{nom}")

    sw = v.get("sw")
    if sw:
        f = d / sw["fichier"]
        js = f.read_text(encoding="utf-8")
        prefixe = v["cache"]
        nouveau, n = re.subn(r"(['\"])" + re.escape(prefixe) + r"[^'\"]*\1",
                             lambda m: m.group(1) + sw["cache"] + m.group(1), js, count=1)
        assert n == 1, f"nom de cache commençant par {prefixe!r} introuvable dans {f}"
        f.write_text(nouveau, encoding="utf-8")
        print(f"  cache   {sw['cache']} -> {dossier}/{sw['fichier']}")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args == ["--toutes"]:
        args = sorted(p.parent.name for p in RACINE.glob("*/version.json"))
    if not args:
        sys.exit(__doc__)
    for a in args:
        print(a)
        poser(a.strip("/"))
