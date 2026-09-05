import { test } from "node:test";
import assert from "node:assert/strict";

import { alsMarkdown } from "../src/domain/markdown.js";
import { makeTask } from "../src/domain/tasks.js";
import { Priority } from "../src/domain/balance.js";

const JETZT = Date.UTC(2026, 8, 5, 10, 0, 0);

function aufgabe(felder) {
  return makeTask({
    id: felder.id,
    listId: felder.listId ?? "l1",
    sortKey: felder.sortKey ?? "a0",
    createdAt: JETZT,
    ...felder,
  });
}

const LISTEN = [
  { id: "l1", name: "Posteingang", sortKey: "a0", deletedAt: null },
  { id: "l2", name: "Irgendwann", sortKey: "a1", deletedAt: null },
];

function bauen(felder = {}) {
  return alsMarkdown(
    {
      tasks: felder.tasks ?? [],
      lists: felder.lists ?? LISTEN,
      tags: felder.tags ?? [],
      tagLinks: felder.tagLinks ?? [],
      habits: felder.habits ?? [],
    },
    felder.jetzt ?? JETZT,
  );
}

test("der_vorspann_macht_die_datei_wiedererkennbar", () => {
  // Eine KI, die das Vault pflegt, muss ihre eigene Datei erkennen können, ohne zu raten.
  const text = bauen({ tasks: [aufgabe({ id: "t1", title: "Zahnarzt" })] });

  assert.ok(text.startsWith("---\n"));
  assert.match(text, /^quelle: petodo$/m);
  assert.match(text, /^fassung: 1$/m);
  assert.match(text, /^stand: 2026-09-05T10:00:00\.000Z$/m);
  assert.match(text, /^offen: 1$/m);
  assert.match(text, /^erledigt: 0$/m);
});

test("offen_und_erledigt_stehen_als_obsidian_kaestchen_da", () => {
  const text = bauen({
    tasks: [
      aufgabe({ id: "t1", title: "Zahnarzt" }),
      aufgabe({ id: "t2", title: "Steuer", sortKey: "a1", completedAt: JETZT }),
    ],
  });

  assert.match(text, /^- \[ \] Zahnarzt$/m);
  assert.match(text, /^- \[x\] Steuer$/m);
});

test("unteraufgaben_stehen_eingerueckt_und_bleiben_abhakbar", () => {
  const text = bauen({
    tasks: [
      aufgabe({ id: "t1", title: "Wocheneinkauf" }),
      aufgabe({ id: "t2", title: "Milch", parentId: "t1" }),
      aufgabe({ id: "t3", title: "Brot", parentId: "t1", sortKey: "a1" }),
    ],
  });

  assert.match(text, /^- \[ \] Wocheneinkauf\n {4}- \[ \] Milch\n {4}- \[ \] Brot$/m);
});

test("faelligkeit_prioritaet_und_wiederholung_stehen_in_der_zeile", () => {
  const text = bauen({
    tasks: [
      aufgabe({
        id: "t1",
        title: "Müll",
        dueAt: Date.UTC(2026, 8, 7, 16, 30),
        hasTime: true,
        dueTimeLocal: 18 * 60 + 30,
        priority: Priority.URGENT,
        rrule: "FREQ=WEEKLY",
      }),
    ],
  });

  assert.match(text, /📅 2026-09-07 18:30/);
  assert.match(text, /⏫ dringend/);
  assert.match(text, /🔁 FREQ=WEEKLY/);
});

test("normale_prioritaet_steht_nicht_da", () => {
  // Sonst trägt jede einzelne Zeile eine Angabe, die nichts aussagt.
  const text = bauen({ tasks: [aufgabe({ id: "t1", title: "Zahnarzt" })] });
  assert.ok(!text.includes("⏫"));
});

test("etiketten_werden_zu_obsidian_etiketten", () => {
  const text = bauen({
    tasks: [aufgabe({ id: "t1", title: "Steuer" })],
    tags: [{ id: "g1", name: "haus", deletedAt: null }],
    tagLinks: [{ taskId: "t1", tagId: "g1", deletedAt: null }],
  });

  assert.match(text, /^- \[ \] Steuer #haus$/m);
});

test("ein_etikett_mit_leerzeichen_bekommt_bindestriche", () => {
  // `#zwei worte` ist in Obsidian nur `#zwei` — der Rest fiele lautlos ab.
  const text = bauen({
    tasks: [aufgabe({ id: "t1", title: "Steuer" })],
    tags: [{ id: "g1", name: "alte papiere", deletedAt: null }],
    tagLinks: [{ taskId: "t1", tagId: "g1", deletedAt: null }],
  });

  assert.match(text, /#alte-papiere/);
});

test("geloeschtes_taucht_nirgends_auf", () => {
  const text = bauen({
    tasks: [
      aufgabe({ id: "t1", title: "Bleibt" }),
      aufgabe({ id: "t2", title: "Weg", sortKey: "a1", deletedAt: JETZT }),
    ],
    tags: [{ id: "g1", name: "wegetikett", deletedAt: JETZT }],
    tagLinks: [{ taskId: "t1", tagId: "g1", deletedAt: null }],
    habits: [{ id: "h1", name: "Weggewohnheit", sortKey: "a0", deletedAt: JETZT }],
  });

  assert.ok(!text.includes("Weg\n") && !text.includes("- [ ] Weg"));
  assert.ok(!text.includes("wegetikett"));
  assert.ok(!text.includes("Weggewohnheit"));
});

test("heikle_zeichen_im_titel_zerlegen_die_datei_nicht", () => {
  // Ein `#` im Titel wäre in Obsidian ein Etikett, ein `[[` ein Verweis ins Leere.
  const text = bauen({ tasks: [aufgabe({ id: "t1", title: "Rechnung #42 [[wichtig]]" })] });

  assert.match(text, /^- \[ \] Rechnung 42 wichtig$/m);
});

test("eine_leere_liste_bekommt_keine_ueberschrift", () => {
  const text = bauen({ tasks: [aufgabe({ id: "t1", title: "Zahnarzt", listId: "l1" })] });

  assert.ok(text.includes("## Posteingang"));
  assert.ok(!text.includes("## Irgendwann"));
});

test("die_notiz_steht_unter_der_aufgabe", () => {
  const text = bauen({
    tasks: [aufgabe({ id: "t1", title: "Steuer", note: "Unterlagen von [[Zahnarzt]] holen" })],
  });

  // Der Verweis bleibt stehen: `[[…]]` ist in Obsidian dasselbe wie hier.
  assert.match(text, /^- \[ \] Steuer\n {4}Unterlagen von \[\[Zahnarzt\]\] holen$/m);
});

test("zweimal_erzeugt_ist_zeichengleich", () => {
  // Wer die Datei ins Vault legt, will keinen Unterschied sehen, wo keiner ist — sonst
  // meldet die Versionsverwaltung bei jedem Export eine Änderung.
  const daten = {
    tasks: [aufgabe({ id: "t1", title: "Zahnarzt" }), aufgabe({ id: "t2", title: "Steuer", sortKey: "a1" })],
  };

  assert.equal(bauen(daten), bauen(daten));
});

test("die_datei_endet_mit_genau_einem_umbruch", () => {
  const text = bauen({ tasks: [aufgabe({ id: "t1", title: "Zahnarzt" })] });
  assert.ok(text.endsWith("\n"));
  assert.ok(!text.endsWith("\n\n"));
});

test("ohne_aufgaben_kommt_trotzdem_eine_gueltige_datei", () => {
  const text = bauen();
  assert.match(text, /^offen: 0$/m);
  assert.ok(text.includes("# PeTodo"));
});
