#!/usr/bin/env node
// Retire de www/ les images des fêtes que l'appli n'embarque pas, pour l'alléger : une fête
// lointaine pèse une soixantaine de Mo et ne sert pas avant des mois.
//
//   node scripts/drop-seasons.mjs <www> [--absent halloween,…] [--today AAAA-MM-JJ]
//
// - Fêtes à ne pas embarquer : --absent, sinon la variable FETES_ABSENTES, sinon « halloween ».
//   Une liste vide (FETES_ABSENTES=) embarque tout. Un identifiant inconnu du jeu arrête tout.
// - Pour ne jamais oublier de la remettre : une fête listée qui est en cours, ou qui commence
//   dans 183 jours au plus, est embarquée quand même, et le journal le dit.
// - Le jeu apprend quelles fêtes manquent par la balise « leapmultix-seasons-absent »
//   d'index.html : il ne demande alors aucune de leurs images.
// Dates et marques des fichiers viennent du site embarqué lui-même (www/js/core/seasons.js).
// Sortie 0 : fait et contrôlé. Sortie 1 : un arrêt, dit sur la sortie d'erreur.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

/** Une fête listée qui commence dans ce délai (en jours) est embarquée quand même */
export const KEEP_WITHIN_DAYS = 183;
const DEFAULT_ABSENT = 'halloween';
const META_EMPTY = '<meta name="leapmultix-seasons-absent" content="" />';
const DAY_MS = 86_400_000;
const NAMES = new Map([
  ['halloween', 'Halloween'],
  ['christmas', 'Noël'],
  ['newyear', 'Nouvel An'],
  ['lunarnewyear', 'Nouvel An chinois'],
]);

class Stop extends Error {}

const label = id => NAMES.get(id) ?? id;
const utc = (year, [month, day]) => Date.UTC(year, month - 1, day);
const french = time => new Date(time).toLocaleDateString('fr-FR', { timeZone: 'UTC' });

function parseArgs(argv, env) {
  const options = { www: null, absent: env.FETES_ABSENTES ?? DEFAULT_ABSENT, today: null };
  const rest = [...argv];
  while (rest.length) {
    const arg = rest.shift();
    if (arg === '--absent' || arg === '--today') {
      if (!rest.length) throw new Stop(`${arg} : valeur attendue`);
      options[arg.slice(2)] = rest.shift();
    } else if (arg.startsWith('--') || options.www) {
      throw new Stop(`argument inattendu : ${arg}`);
    } else {
      options.www = arg;
    }
  }
  if (!options.www) throw new Stop('dossier www attendu');
  return options;
}

/** Date du jour à minuit UTC (seul le jour compte), ou celle de --today */
function parseToday(text) {
  if (text === null) {
    const now = new Date();
    return Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
  const time = match ? Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : NaN;
  if (Number.isNaN(time) || new Date(time).toISOString().slice(0, 10) !== text) {
    throw new Stop(`--today : date AAAA-MM-JJ attendue, reçu « ${text} »`);
  }
  return time;
}

/** Le module des fêtes du site embarqué, chargé depuis une copie (.mjs) hors du site */
async function loadSeasons(www) {
  const source = path.join(www, 'js', 'core', 'seasons.js');
  if (!fs.existsSync(source)) throw new Stop(`introuvable : ${source}`);
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'leapmultix-fetes-'));
  try {
    const copy = path.join(work, 'seasons.mjs');
    fs.copyFileSync(source, copy);
    const seasons = await import(pathToFileURL(copy).href);
    for (const name of ['SEASONS', 'seasonDays', 'isSeasonalImage']) {
      if (!(name in seasons)) throw new Stop(`${source} n'exporte pas ${name}`);
    }
    return seasons;
  } finally {
    fs.rmSync(work, { recursive: true, force: true });
  }
}

/**
 * Prochaine édition d'une fête : celle en cours, sinon la première à venir.
 * @returns {{start: number, days: number}|null} days : jours avant son début (0 en cours) ;
 *   null quand aucune édition à venir n'est connue (fête à date mobile au bout de sa table)
 */
export function nextEdition(season, today, seasonDays) {
  const year = new Date(today).getUTCFullYear();
  // Une année sans date connue n'a pas cette fête : le jeu lui-même ne la montre pas
  const editions = [year - 1, year, year + 1].flatMap(start => {
    const days = seasonDays(season, start);
    if (!days) return [];
    const from = utc(start, days.from);
    const sameYearEnd = utc(start, days.to);
    return [{ from, to: sameYearEnd < from ? utc(start + 1, days.to) : sameYearEnd }];
  });
  const next = editions.find(edition => edition.to >= today);
  if (!next) return null;
  return { start: next.from, days: Math.max(0, Math.round((next.from - today) / DAY_MS)) };
}

/** Tous les fichiers sous un dossier (chemins relatifs) ; un lien n'est jamais suivi */
function listFiles(root) {
  const files = [];
  const walk = dir => {
    for (const entry of fs.readdirSync(path.join(root, dir), { withFileTypes: true })) {
      const relative = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(relative);
      else files.push(relative);
    }
  };
  walk('');
  return files;
}

/** Le fichier porte-t-il la marque de cette fête (« _halloween_ », « -halloween. »…) ? */
const markOf = id => new RegExp(`[_-]${id}[_.-]`);
const isOf = (id, file) => markOf(id).test(path.basename(file));

function writeFile(file, text) {
  const part = `${file}.part`;
  fs.writeFileSync(part, text);
  fs.renameSync(part, file);
}

/** Chemins de la liste hors ligne de sw.js (même lecture que bundle-www.sh) */
function precachePaths(www) {
  const lines = fs.readFileSync(path.join(www, 'sw.js'), 'utf8').split('\n');
  return lines.flatMap(line => /^ {2}'\/([^']+)'/.exec(line)?.[1] ?? []);
}

function readImageMap(www) {
  const file = path.join(www, 'assets', 'generated-images', 'image-map.json');
  if (!fs.existsSync(file)) throw new Stop(`introuvable : ${file}`);
  return { file, map: JSON.parse(fs.readFileSync(file, 'utf8')) };
}

const variantsOf = entry => Object.values(entry.resolutions ?? {});

/** Retire de la carte des images les entrées d'une fête absente (source ou variante marquée) */
function pruneImageMap(www, absentIds) {
  const { file, map } = readImageMap(www);
  const gone = entry =>
    [entry.original ?? '', ...variantsOf(entry)].some(name => absentIds.some(id => isOf(id, name)));
  const kept = Object.entries(map).filter(([, entry]) => !gone(entry));
  writeFile(file, JSON.stringify(Object.fromEntries(kept), null, 2));
  return Object.keys(map).length - kept.length;
}

/** Contrôles de fin : chacun rend la liste de ce qui ne va pas */
function finalProblems(www, absentIds) {
  const files = listFiles(www);
  const left = files.filter(file => absentIds.some(id => isOf(id, file)));
  const offline = precachePaths(www).filter(file => !fs.existsSync(path.join(www, file)));
  const images = path.join(www, 'assets', 'generated-images');
  const variants = Object.values(readImageMap(www).map)
    .flatMap(variantsOf)
    .filter(variant => !fs.existsSync(path.join(images, variant)));
  return [
    ['fichier d’une fête absente encore là', left],
    ['fichier de la liste hors ligne (sw.js) absent', offline],
    ['variante citée par la carte des images absente', variants],
  ].filter(([, list]) => list.length);
}

function stopOn(problems) {
  if (!problems.length) return;
  const lines = problems.flatMap(([what, list]) => [
    `${what} : ${list.length}`,
    ...list.slice(0, 10).map(item => `  ${item}`),
  ]);
  throw new Stop(lines.join('\n'));
}

/** Chaque image de fête du site appartient à une fête connue, et à une seule */
function assertKnownSeasons(files, { SEASONS, isSeasonalImage }) {
  const strays = files.filter(file => {
    if (!isSeasonalImage(path.basename(file))) return false;
    return SEASONS.filter(({ id }) => isOf(id, file)).length !== 1;
  });
  stopOn(strays.length ? [['image de fête sans fête unique', strays]] : []);
}

/** Répartit les fêtes listées : absentes pour de bon, ou embarquées quand même (proches) */
function sortSeasons(asked, seasons, today, log) {
  const known = new Map(seasons.SEASONS.map(season => [season.id, season]));
  const unknown = asked.filter(id => !known.has(id));
  if (unknown.length) {
    throw new Stop(`fête inconnue du jeu : ${unknown.join(', ')} (${[...known.keys()].join(', ')})`);
  }
  const absent = [];
  for (const id of asked) {
    const next = nextEdition(known.get(id), today, seasons.seasonDays);
    if (!next) {
      log(`ATTENTION : ${label(id)} : date inconnue, embarquée par prudence ; retire-la de la liste`);
    } else if (next.days <= KEEP_WITHIN_DAYS) {
      const when = next.days === 0 ? 'est en cours' : `commence le ${french(next.start)}, dans ${next.days} jours`;
      log(`ATTENTION : ${label(id)} ${when} : embarquée ; retire-la de la liste`);
    } else {
      absent.push({ id, start: next.start, back: next.start - KEEP_WITHIN_DAYS * DAY_MS });
    }
  }
  return absent;
}

function removeFiles(www, files, absentIds) {
  let bytes = 0;
  const removed = files.filter(file => absentIds.some(id => isOf(id, file)));
  for (const file of removed) {
    const full = path.join(www, file);
    bytes += fs.statSync(full).size;
    fs.unlinkSync(full);
  }
  return { count: removed.length, bytes };
}

export async function main(argv, { env = process.env, log = console.log } = {}) {
  const options = parseArgs(argv, env);
  const today = parseToday(options.today);
  const asked = [...new Set(options.absent.split(',').map(id => id.trim()).filter(Boolean))];
  const seasons = await loadSeasons(options.www);
  const absent = sortSeasons(asked, seasons, today, log);
  const absentIds = absent.map(({ id }) => id);

  // La balise d'abord : sans elle, le jeu demanderait des images qui ne sont plus là
  const indexFile = path.join(options.www, 'index.html');
  const page = fs.readFileSync(indexFile, 'utf8');
  if (page.split(META_EMPTY).length !== 2) {
    throw new Stop(`balise des fêtes absentes introuvable (ou en double) dans ${indexFile}`);
  }
  const files = listFiles(options.www);
  assertKnownSeasons(files, seasons);

  const embarked = seasons.SEASONS.map(({ id }) => id).filter(id => !absentIds.includes(id));
  log(`fêtes embarquées : ${embarked.map(label).join(', ') || 'aucune'}`);
  if (!absent.length) {
    stopOn(finalProblems(options.www, absentIds));
    log('fêtes absentes : aucune');
    return;
  }
  const removed = removeFiles(options.www, files, absentIds);
  const entries = pruneImageMap(options.www, absentIds);
  writeFile(indexFile, page.replace(META_EMPTY, META_EMPTY.replace('""', `"${absentIds.join(',')}"`)));
  stopOn(finalProblems(options.www, absentIds));
  for (const { id, start, back } of absent) {
    log(
      `fête absente : ${label(id)} (prochaine le ${french(start)}) ; ` +
        `une appli construite à partir du ${french(back)} la reprend`
    );
  }
  log(
    `retiré : ${removed.count} fichiers, ${(removed.bytes / 2 ** 20).toFixed(1)} Mo, ` +
      `${entries} images de la carte`
  );
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(import.meta.filename)) {
  main(process.argv.slice(2)).catch(error => {
    console.error(error instanceof Stop ? `fêtes : ${error.message}` : error);
    process.exitCode = 1;
  });
}
