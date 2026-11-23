#!/usr/bin/env python3
"""Textes du site qui seraient faux dans l'appli Android, remplacés dans www/.

Le site mesure son audience (Plausible), s'installe après une première visite en ligne et est
hébergé chez AWS ; l'appli embarque tout, et le jeu ne se connecte à aucun serveur (l'appli
demande seulement à Google Play, au lancement, s'il existe une version plus récente). Dans www/ :
- l'écran « Politique de confidentialité » montre la politique de l'appli, celle de la fiche
  Google Play (confidentialite-android.html du site, en français et en anglais) ;
- l'écran « Mentions légales » dit d'abord qu'il décrit le site, pas l'appli ;
- les textes qui parlent du site (« Questions fréquentes » : hors ligne, écrans, données,
  voix ; voix d'« À propos » ; note de confidentialité de l'accueil) décrivent l'appli : rien
  à installer, les seules voix embarquées, la progression gardée sur l'appareil ; ceux qui
  nomment le navigateur (« Sauvegarde des joueurs », messages de place manquante) nomment
  l'appareil, et le script échoue si un autre texte en parle encore ; la voix de l'appareil
  n'est promise que si le site sait la demander à l'appli (plugin LeapMultixSpeech, synthèse
  vocale d'Android) : avec un site plus ancien, la page de l'appli n'en a pas, et aucun texte
  ne la promet (même garde-fou) ;
- la section « Sauvegarde des joueurs » de « Qui joue ? » reste quand le site sait confier le
  fichier à l'appli (plugin LeapMultixFiles, fenêtre « Enregistrer sous » d'Android) ; avec un
  site plus ancien elle est cachée : la WebView ne télécharge rien, le bouton ne ferait rien ;
- les données pour moteurs de recherche (JSON-LD), invisibles et sans objet dans une appli,
  partent : elles répétaient les réponses du site.
Chaque remplacement doit trouver son texte exactement une fois, sinon le script échoue.

Usage : app-texts.py <www> <confidentialite-android.html>
"""
import json
import re
import sys
from pathlib import Path

TEXTS = {
    "fr": {
        "faq_a3": "Oui : tout le jeu est dans l'appli. Les 6 modes, les 6 jeux d'arcade, les "
        "bruitages, la voix enregistrée et les vidéos fonctionnent sans Internet. La "
        "progression est sauvegardée sur l'appareil.",
        "faq_a6": "Oui. La progression de votre enfant reste sur votre appareil, sans compte ni "
        "adresse e-mail : l'appli ne la transmet ni à l'éditeur ni à un tiers. Elle ne mesure "
        "aucune audience et n'affiche aucune publicité. Le jeu ne se connecte à aucun serveur ; "
        "l'appli demande seulement à Google Play, au lancement, s'il existe une version plus "
        "récente.",
        "faq_a5": "Absolument ! LeapMultix est fait pour tous les écrans : téléphone et tablette. "
        "L'interface s'adapte automatiquement à la taille de l'écran.",
        "faq_a8": "Une voix de synthèse enregistrée, embarquée dans l'appli : Lucie en français "
        "(créée avec ElevenLabs), Sulafat en anglais et en espagnol (créée avec Google Cloud "
        "Text-to-Speech). Les phrases sont enregistrées à l'avance : pendant une partie, l'appli "
        "n'envoie rien à ces services.",
        "about_features_voice": "Lecture à voix haute : voix enregistrées (Lucie en français, "
        "Sulafat en anglais et en espagnol)",
        "privacy_info_text": "Ta progression et tes préférences sont sauvegardées sur cet "
        "appareil.",
        "players_backup_hint": "Les joueurs sont gardés dans cette appli, sur cet appareil. "
        "Enregistre-les aussi dans un fichier\u00a0: tu pourras les reprendre si l’appli est "
        "désinstallée, ou sur un autre appareil.",
    },
    "en": {
        "faq_a3": "Yes: the whole game is inside the app. The 6 modes, the 6 arcade games, the "
        "sound effects, the recorded voice and the videos work without the internet. Progress "
        "is saved on the device.",
        "faq_a6": "Yes. Your child's progress stays on your device, with no account or email "
        "address: the app sends it neither to the publisher nor to any third party. It "
        "measures no audience and shows no ads. The game connects to no server; the app only "
        "asks Google Play, at launch, whether a newer version exists.",
        "faq_a5": "Absolutely! LeapMultix is made for all screens: phone and tablet. The "
        "interface adapts automatically to the screen size.",
        "faq_a8": "A recorded synthetic voice, included in the app: Lucie in French (made with "
        "ElevenLabs), Sulafat in English and Spanish (made with Google Cloud Text-to-Speech). "
        "The sentences are recorded in advance: during a game, the app sends nothing to these "
        "services.",
        "about_features_voice": "Read-aloud with recorded voices (Lucie in French, Sulafat in "
        "English and Spanish)",
        "privacy_info_text": "Your progress and preferences are saved on this device.",
        "players_backup_hint": "Players are kept in this app, on this device. Save them to a "
        "file too: you can bring them back if the app is uninstalled, or on another device.",
    },
    "es": {
        "faq_a3": "Sí: todo el juego está en la aplicación. Los 6 modos, los 6 juegos de arcade, "
        "los efectos de sonido, la voz grabada y los vídeos funcionan sin internet. El progreso "
        "se guarda en el dispositivo.",
        "faq_a6": "Sí. Los progresos se quedan en tu dispositivo, sin cuenta ni correo "
        "electrónico: la aplicación no los envía ni al editor ni a terceros. No mide ninguna "
        "audiencia ni muestra publicidad. El juego no se conecta a ningún servidor; la "
        "aplicación solo pregunta a Google Play, al abrirse, si existe una versión más reciente.",
        "faq_a5": "¡Absolutamente! LeapMultix está hecho para todas las pantallas: teléfono y "
        "tableta. La interfaz se adapta automáticamente al tamaño de la pantalla.",
        "faq_a8": "Una voz sintética grabada, incluida en la aplicación: Lucie en francés "
        "(creada con ElevenLabs) y Sulafat en inglés y en español (creada con Google Cloud "
        "Text-to-Speech). Las frases se graban de antemano: durante una partida, la aplicación "
        "no envía nada a estos servicios.",
        "about_features_voice": "Lectura en voz alta con voces grabadas (Lucie en francés, "
        "Sulafat en inglés y en español)",
        "privacy_info_text": "Tu progreso y preferencias se guardan en este dispositivo.",
        "players_backup_hint": "Los jugadores se guardan en esta aplicación, en este "
        "dispositivo. Guárdalos también en un archivo: podrás recuperarlos si se desinstala la "
        "aplicación o en otro dispositivo.",
    },
}

# Messages que le jeu affiche sans qu'ils soient écrits dans la page : ils nommaient le navigateur
MESSAGES = {
    "fr": {
        "save_failed": "L’appareil refuse d’enregistrer : rien n’a été sauvegardé (place "
        "manquante).",
        "trash_failed": "«\u00a0{name}\u00a0» n’a pas pu être supprimé\u00a0: l’appareil manque de "
        "place.",
        "import_storage_full": "L’appareil manque de place\u00a0: aucun joueur n’a été ajouté.",
        "video_not_supported": "Ton appareil ne peut pas lire cette vidéo.",
    },
    "en": {
        "save_failed": "The device refused to store it: nothing was saved (out of space).",
        "trash_failed": "{name} couldn’t be deleted: the device is out of space.",
        "import_storage_full": "The device is out of space: no player was added.",
        "video_not_supported": "Your device can't play this video.",
    },
    "es": {
        "save_failed": "El dispositivo se niega a guardarlo: no se ha guardado nada (falta de "
        "espacio).",
        "trash_failed": "No se ha podido eliminar a {name}: el dispositivo no tiene espacio.",
        "import_storage_full": "El dispositivo no tiene espacio: no se ha añadido ningún jugador.",
        "video_not_supported": "Tu dispositivo no puede reproducir este vídeo.",
    },
}
# La page de l'appli n'a pas de synthèse vocale (speechSynthesis absent de la WebView d'Android,
# mesuré sur téléphone). Un site qui sait demander la voix du téléphone à l'appli (plugin
# LeapMultixSpeech) garde ses textes : « Voix enregistrée » décochée, le jeu lit avec la voix de
# l'appareil, et les deux textes de l'appli qui décrivent la voix le disent aussi.
PHONE_VOICE = {
    "fr": {
        "faq_a8": " Pour la voix de l'appareil, décochez « Voix enregistrée » dans "
        "Personnalisation.",
        "about_features_voice": " ou voix de l'appareil",
    },
    "en": {
        "faq_a8": " To use your device's voice, uncheck “Recorded voice” in Personalization.",
        "about_features_voice": " or the device's voice",
    },
    "es": {
        "faq_a8": " Para usar la voz del dispositivo, desmarca «Voz grabada» en Personalización.",
        "about_features_voice": " o la voz del dispositivo",
    },
}
# Avec un site plus ancien, rien n'est lu une fois « Voix enregistrée » décochée : la fin de
# chaque explication de ce réglage (une par fournisseur de voix) le dit.
VOICE_OFF = {
    "fr": ("Décochée, le jeu lit avec la voix de l'appareil.",
           "Décochée, le jeu ne lit plus à voix haute."),
    "en": ("When unchecked, the game reads with the device's voice.",
           "When unchecked, the game no longer reads aloud."),
    "es": ("Desmarcada, el juego lee con la voz del dispositivo.",
           "Desmarcada, el juego ya no lee en voz alta."),
}
DEVICE_VOICE = re.compile(r"voix de\s+l.appareil|device.s voice|voz del dispositivo", re.I)
# Seul texte qui peut encore nommer le navigateur : celui du bouton de refus de la mesure
# d'audience, que la politique de l'appli n'affiche pas
BROWSER_WORD = re.compile(r"navigateur|browser|navegador", re.I)
BROWSER_ALLOWED = {"audience_status_unavailable"}

LEGAL_NOTE = """<div class="legal-section">
                <h2 class="section-title">Appli Android</h2>
                <p>
                  Ces mentions légales sont celles du site «&nbsp;leapmultix.jls42.org&nbsp;».
                  L'appli Android LeapMultix embarque tout le jeu&nbsp;: elle ne mesure aucune
                  audience et le jeu ne se connecte à aucun serveur. Au lancement, l'appli
                  demande seulement à Google Play s'il existe une version plus récente. Ce qui
                  est dit plus bas de l'hébergement et de la mesure d'audience ne concerne que le
                  site. La politique de confidentialité de l'appli est affichée dans l'écran
                  «&nbsp;Politique de confidentialité&nbsp;».
                </p>
              </div>
              """


def once(pattern, replacement, text, what):
    """Remplace `pattern` qui doit se trouver une seule fois dans `text`."""
    result, count = re.subn(pattern, lambda _: replacement, text, flags=re.S)
    if count != 1:
        sys.exit(f"app-texts : « {what} » trouvé {count} fois, attendu 1")
    return result


def article(policy, lang):
    """Corps de la politique de l'appli dans une langue, sans son titre (l'écran a le sien)."""
    found = re.search(rf'<article class="legal-body" lang="{lang}">(.*?)</article>', policy, re.S)
    if not found:
        sys.exit(f"app-texts : politique de l'appli sans article « {lang} »")
    body = re.sub(r"\s*<h1>.*?</h1>", "", found.group(1), count=1, flags=re.S)
    # Mêmes blocs que les autres écrans juridiques du jeu : une section par titre
    head, *sections = re.split(r"(?=<h2>)", body)
    blocks = "".join(
        '<div class="privacy-section">'
        + part.replace("<h2>", '<h2 class="section-title">', 1).rstrip()
        + "</div>\n"
        for part in sections
    )
    return f'<div class="legal-body" lang="{lang}">{head}{blocks}</div>'


def section(page, slide):
    """Bornes de l'écran `slide` dans la page."""
    start = page.index(f'<section id="{slide}"')
    return start, page.index("</section>", start)


def privacy_screen(page, policy):
    start, end = section(page, "slide10")
    screen = page[start:end]
    # La date et la langue sont celles de chaque article de la politique de l'appli
    screen = once(r'\s*<p class="last-updated" data-translate="privacy_last_updated">.*?</p>',
                  "", screen, "date de la politique du site")
    screen = once(r'\s*<p class="legal-language-note"[^>]*>.*?</p>', "", screen,
                  "note « en français seulement »")
    screen = once(r'<div class="legal-body" lang="fr">.*?(?=\s*<!-- Navigation traduite)',
                  article(policy, "fr") + "\n            " + article(policy, "en"),
                  screen, "corps de la politique du site")
    if "Plausible" in screen or "plausible" in screen:
        sys.exit("app-texts : la politique affichée dans l'appli parle encore de Plausible")
    return page[:start] + screen + page[end:]


def legal_screen(page):
    start, end = section(page, "slide9")
    screen = once(r'(?<=<div class="legal-body" lang="fr">\n)\s*(?=<div class="legal-section">)',
                  "              " + LEGAL_NOTE, page[start:end], "début des mentions légales")
    return page[:start] + screen + page[end:]


def hide_players_backup(page):
    return once(r'<section class="players-backup"(?! hidden)',
                '<section class="players-backup" hidden', page, "section Sauvegarde des joueurs")


def drop_search_engine_data(page):
    page, count = re.subn(r'\s*<script type="application/ld\+json">.*?</script>', "", page,
                          flags=re.S)
    if count < 1:
        sys.exit("app-texts : aucune donnée JSON-LD trouvée dans index.html")
    return page


def app_texts(lang, phone_voice):
    """Textes de l'appli dans une langue ; avec la voix du téléphone, ceux de la voix la nomment."""
    texts = dict(TEXTS[lang])
    if phone_voice:
        for key, more in PHONE_VOICE[lang].items():
            texts[key] += more
    return texts


def voice_off_hints(texts, lang, phone_voice):
    """Fin des explications de « Voix enregistrée » : la voix de l'appareil, si l'appli l'a."""
    old, new = VOICE_OFF[lang]
    hints = [key for key in texts if key.startswith("recorded_voice_hint")]
    if not hints:
        sys.exit(f"app-texts : aucune explication de « Voix enregistrée » dans {lang}.json")
    for key in hints:
        if texts[key].count(old) != 1:
            sys.exit(f"app-texts : fin attendue introuvable dans {key} ({lang}.json)")
        if not phone_voice:
            texts[key] = texts[key].replace(old, new)


def translated_texts(www, phone_voice):
    for lang in TEXTS:
        path = www / "assets" / "translations" / f"{lang}.json"
        texts = json.loads(path.read_text(encoding="utf-8"))
        for key, answer in {**app_texts(lang, phone_voice), **MESSAGES[lang]}.items():
            if key not in texts:
                sys.exit(f"app-texts : clé {key} absente de {lang}.json")
            texts[key] = answer
        voice_off_hints(texts, lang, phone_voice)
        left = [key for key, text in texts.items()
                if isinstance(text, str) and BROWSER_WORD.search(text) and key not in BROWSER_ALLOWED]
        if left:
            sys.exit(f"app-texts : {lang}.json parle encore du navigateur ({', '.join(left)})")
        left = [key for key, text in texts.items()
                if isinstance(text, str) and DEVICE_VOICE.search(text)]
        if left and not phone_voice:
            sys.exit(f"app-texts : {lang}.json promet encore la voix de l'appareil "
                     f"({', '.join(left)})")
        path.write_text(json.dumps(texts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def texts_in_page(page, phone_voice):
    """Le français est aussi écrit dans la page, avant que les traductions ne se chargent."""
    for key, text in app_texts("fr", phone_voice).items():
        # La balise fermante est celle de la balise ouvrante (« </span\n> » compris)
        pattern = rf'(<(p|li|span)\b[^>]*data-translate="{key}"[^>]*>).*?(</\2\s*>)'
        page, count = re.subn(pattern, lambda found: found.group(1) + text + found.group(3), page,
                              flags=re.S)
        if count != 1:
            sys.exit(f"app-texts : texte « {key} » trouvé {count} fois dans la page, attendu 1")
    return page


def main():
    www, policy_path = Path(sys.argv[1]), Path(sys.argv[2])
    policy = policy_path.read_text(encoding="utf-8")
    index = www / "index.html"
    page = index.read_text(encoding="utf-8")
    page = privacy_screen(page, policy)
    page = legal_screen(page)
    # Un site qui sait demander la voix du téléphone à l'appli : ses textes sur la voix restent
    phone_voice = "LeapMultixSpeech" in (www / "js/speech.js").read_text(encoding="utf-8")
    page = texts_in_page(page, phone_voice)
    voice_off = r"Décochée, le jeu lit avec la voix de\s+l'appareil\."
    if phone_voice:
        if len(re.findall(voice_off, page)) != 1:
            sys.exit("app-texts : explication de « Voix enregistrée » introuvable dans la page")
    else:
        page = once(voice_off, VOICE_OFF["fr"][1], page, "explication de « Voix enregistrée »")
    # Un site d'avant le plugin ne sait que télécharger : son bouton ne ferait rien dans l'appli
    if "LeapMultixFiles" not in (www / "js/components/playerTools.js").read_text(encoding="utf-8"):
        page = hide_players_backup(page)
    page = drop_search_engine_data(page)
    for word in ("Plausible Analytics, hébergé", "première visite en ligne", "iOS et Android",
                 "Marie en français", "dans ton navigateur"):
        if word in page:
            sys.exit(f"app-texts : « {word} » encore écrit dans la page de l'appli")
    if DEVICE_VOICE.search(page) and not phone_voice:
        sys.exit("app-texts : la page de l'appli promet encore la voix de l'appareil")
    index.write_text(page, encoding="utf-8")
    translated_texts(www, phone_voice)


if __name__ == "__main__":
    main()
