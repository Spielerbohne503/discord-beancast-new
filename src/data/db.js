/**
 * Ein schmaler Überzug über IndexedDB.
 *
 * IndexedDB spricht in Ereignissen, der Rest der App in Zusagen — hier wird übersetzt,
 * und sonst nichts. Kein eigener Abfrageübersetzer, keine Zwischenschicht mit eigenem
 * Gedächtnis: Das Fachwissen steht in `domain/`, die Abfragen in `repo.js`.
 */

import { DB_NAME, DB_VERSION, STORES } from "./schema.js";

/** Eine IndexedDB-Anfrage als Zusage. */
export function request(idbRequest) {
  return new Promise((resolve, reject) => {
    idbRequest.onsuccess = () => resolve(idbRequest.result);
    idbRequest.onerror = () => reject(idbRequest.error);
  });
}

/** Wartet, bis die Transaktion wirklich geschrieben hat — nicht nur bis der Aufruf zurückkam. */
export function done(transaction) {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error ?? new Error("Transaktion abgebrochen"));
  });
}

let offen = null;

export function openDatabase(name = DB_NAME, version = DB_VERSION) {
  if (offen !== null) return offen;

  offen = new Promise((resolve, reject) => {
    const anfrage = indexedDB.open(name, version);

    anfrage.onupgradeneeded = (ereignis) => migrate(anfrage.result, ereignis.oldVersion);

    anfrage.onsuccess = () => {
      const db = anfrage.result;
      // Ein zweiter Tab, der eine neuere Fassung mitbringt, darf diesen hier nicht
      // blockieren — schließen und den Nutzer neu laden lassen ist ehrlicher.
      db.onversionchange = () => db.close();
      resolve(db);
    };

    anfrage.onerror = () => reject(anfrage.error);
    anfrage.onblocked = () => reject(new Error("Datenbank von einem anderen Tab blockiert"));
  });

  return offen;
}

/**
 * Migrationen.
 *
 * Aufsteigend und ohne Lücke: Wer von Fassung 1 auf 3 springt, durchläuft beide Schritte.
 * Eine Migration, die Daten wegwirft, gibt es nicht — ein Nutzer, der einmal Aufgaben
 * verloren hat, kommt nicht zurück.
 */
function migrate(db, oldVersion) {
  // Fassung 1 legte alles an, was es damals gab. Fassung 2 kam mit den Vorlagen dazu.
  // Deshalb wird hier nicht nach Fassungen verzweigt, sondern nachgeholt, was fehlt: Das
  // ist gegen jede Reihenfolge robust und kann keine Tabelle doppelt anlegen.
  for (const [name, definition] of Object.entries(STORES)) {
    const store = db.objectStoreNames.contains(name)
      ? null
      : db.createObjectStore(name, { keyPath: definition.keyPath });
    if (store === null) continue;

    for (const [indexName, keyPath] of Object.entries(definition.indexes)) {
      store.createIndex(indexName, keyPath);
    }
  }

  // `oldVersion` bleibt im Spiel, sobald einmal eine Tabelle **umgebaut** werden muss —
  // dann reicht Nachholen nicht mehr.
  void oldVersion;
}

export async function transaction(stores, mode, arbeit) {
  const db = await openDatabase();
  const tx = db.transaction(stores, mode);
  const ergebnis = await arbeit(tx);
  await done(tx);
  return ergebnis;
}

export async function getAll(store, index = null, query = null) {
  return transaction([store], "readonly", (tx) => {
    const quelle = index === null ? tx.objectStore(store) : tx.objectStore(store).index(index);
    return request(quelle.getAll(query));
  });
}

export async function getOne(store, key) {
  return transaction([store], "readonly", (tx) => request(tx.objectStore(store).get(key)));
}

export async function putAll(store, rows) {
  if (rows.length === 0) return;
  return transaction([store], "readwrite", (tx) => {
    const ziel = tx.objectStore(store);
    for (const row of rows) ziel.put(row);
  });
}

export async function put(store, row) {
  return putAll(store, [row]);
}

/** Löscht wirklich — nur für Aufräumarbeiten. Fachliches Löschen setzt `deletedAt`. */
export async function hardDelete(store, key) {
  return transaction([store], "readwrite", (tx) => request(tx.objectStore(store).delete(key)));
}

export async function clearAll() {
  return transaction(Object.keys(STORES), "readwrite", (tx) => {
    for (const name of Object.keys(STORES)) tx.objectStore(name).clear();
  });
}

/** Nur für Tests: die geöffnete Verbindung vergessen. */
export function forgetConnection() {
  offen = null;
}
