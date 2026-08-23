/**
 * Alle sichtbaren Texte an einer Stelle.
 *
 * Kein Text steht im Markup oder mitten in einer Funktion — sonst findet man beim
 * Umformulieren die Hälfte nicht wieder, und eine zweite Sprache wäre eine Suche durch
 * die ganze App statt eine zweite Datei.
 */

export const S = Object.freeze({
  app_name: "PeTodo",
  app_tagline: "Aufgaben und ein Begleiter, der davon lebt",

  // ------------------------------------------------------------------- Navigation
  nav_today: "Heute",
  nav_browse: "Listen",
  nav_focus: "Fokus",
  nav_companion: "Begleiter",
  nav_habits: "Gewohnheiten",
  nav_stats: "Rückblick",
  nav_settings: "Einstellungen",
  nav_more: "Mehr",

  // ----------------------------------------------------------------------- Listen
  list_inbox: "Posteingang",
  list_someday: "Irgendwann",
  lists_title: "Listen",
  lists_smart: "Schlaue Listen",
  lists_own: "Eigene Listen",
  list_new: "Neue Liste",
  list_new_placeholder: "Name der Liste",
  list_delete: "Liste löschen",
  list_delete_last: "Die letzte Liste bleibt stehen — irgendwo müssen Aufgaben hin.",
  list_no_nag: "Mahnt nie",
  list_no_nag_hint:
    "Aufgaben in dieser Liste werden nie überfällig gemeldet und machen den Begleiter nicht krank.",

  scope_all_open: "Alles Offene",
  scope_next_seven: "Nächste 7 Tage",
  scope_completed: "Erledigt",

  // ------------------------------------------------------------------ Heute
  today_overdue: "Überfällig",
  today_today: "Heute",
  today_later: "Später",
  today_done_today: "Heute geschafft",
  today_done_earlier: "Früher erledigt",
  today_all_done: "Alles weg",
  today_empty_title: "Nichts offen",
  today_empty_body: "Kein offener Punkt für heute. Das ist kein Fehler, das ist das Ziel.",
  today_open_count: (n) => (n === 1 ? "1 offener Punkt" : `${n} offene Punkte`),

  // ------------------------------------------------------------- Schnell-Eingabe
  quickadd_placeholder: "Was ist zu tun?",
  quickadd_beispiel: "z. B. „morgen 9 Uhr Zahnarzt“",
  quickadd_hint: "Datum und Uhrzeit werden aus dem Text gelesen.",
  quickadd_add: "Hinzufügen",
  quickadd_list: "Liste",

  // ---------------------------------------------------------------- Aufgabe
  task_title: "Titel",
  task_note: "Notiz",
  task_note_placeholder: "Notiz — Verweise gehen wie in Markdown: [Text](Adresse)",
  task_due: "Fällig",
  task_due_none: "Kein Termin",
  task_due_today: "Heute",
  task_due_tomorrow: "Morgen",
  task_due_yesterday: "Gestern",
  task_priority: "Priorität",
  task_repeat: "Wiederholung",
  task_repeat_none: "Einmalig",
  task_subtasks: "Unteraufgaben",
  task_subtask_add: "Unteraufgabe",
  task_tags: "Etiketten",
  task_delete: "Löschen",
  task_tomorrow: "Auf morgen schieben",
  task_swipe_done: "Erledigt",
  task_swipe_tomorrow: "Morgen",
  task_focus_time: (text) => `Fokus: ${text}`,
  verweis_fehlt: "Diese Aufgabe gibt es nicht (mehr)",

  auswahl_titel: (n) => (n === 1 ? "1 ausgewählt" : `${n} ausgewählt`),
  auswahl_abhaken: "Abhaken",
  auswahl_verschieben: "Verschieben nach",
  auswahl_loeschen: "Löschen",
  auswahl_morgen: "Auf morgen",
  auswahl_abbrechen: "Abbrechen",
  auswahl_hinweis: "Lange drücken wählt aus.",

  tags_titel: "Etiketten",
  tags_neu: "Neues Etikett",
  tags_platzhalter: "Name",
  tags_keine: "Noch keine Etiketten.",
  tags_loeschen: "Etikett löschen",

  vorlagen_titel: "Vorlagen",
  vorlagen_neu: "Neue Vorlage",
  vorlagen_name: "Name der Vorlage",
  vorlagen_punkte: "Punkte, einer je Zeile",
  vorlagen_anlegen: "Anlegen",
  vorlagen_benutzen: "Übernehmen",
  vorlagen_loeschen: "Vorlage löschen",
  vorlagen_keine: "Noch keine Vorlage. Eine Vorlage ist ein Name und ein paar Punkte — „Wocheneinkauf“ mit fünf Zeilen, per Knopf neu angelegt.",
  vorlagen_punkte_zahl: (n) => (n === 1 ? "1 Punkt" : `${n} Punkte`),
  vorlagen_uebernommen: (name) => `„${name}“ angelegt.`,

  kalender_titel: "Kalender",
  kalender_heute: "Heute",
  kalender_vor: "Nächster Monat",
  kalender_zurueck: "Voriger Monat",
  kalender_leer: "Nichts an diesem Tag.",

  skins_titel: "Gestalt",
  skins_gesperrt: (level) => `ab Stufe ${level}`,
  skins_naechste: (level) => `Die nächste Gestalt gibt es ab Stufe ${level}.`,

  habits_art: "Rhythmus",
  habits_art_tage: "Feste Tage",
  habits_art_ziel: "So oft pro Woche",
  habits_ziel: (n) => `${n}× pro Woche`,
  habits_wochen_serie: (n) => (n === 1 ? "1 Woche in Folge" : `${n} Wochen in Folge`),

  stats_nach_liste: "Wohin die Zeit geht",
  stats_keine_zeit: "Noch keine Fokusrunde einer Aufgabe zugeordnet.",
  stats_liste_zeile: (erledigt) => `${erledigt} erledigt`,
  task_restore: "Zurückholen",
  task_done: "Abhaken",
  task_undone: "Haken zurücknehmen",
  task_open_detail: "Öffnen",
  task_missed: (n) => `${n}× verpasst`,
  task_overdue_days: (n) => (n === 1 ? "1 Tag überfällig" : `${n} Tage überfällig`),
  task_start_focus: "Fokus starten",

  priority_low: "Niedrig",
  priority_normal: "Normal",
  priority_high: "Hoch",
  priority_urgent: "Dringend",

  repeat_daily: "Täglich",
  repeat_weekly: "Wöchentlich",
  repeat_weekdays: "Werktags",
  repeat_monthly: "Monatlich",
  repeat_yearly: "Jährlich",

  // ------------------------------------------------------------------- Begleiter
  pet_energy: "Energie",
  pet_satiety: "Sättigung",
  pet_mood: "Laune",
  pet_level: (n) => `Stufe ${n}`,
  pet_xp_to_next: (n) => `noch ${n} XP`,
  pet_feed: "Füttern",
  pet_play: "Spielen",
  pet_pat: "Streicheln",
  pet_cooldown: (text) => `noch ${text}`,
  pet_load: "Überfälligkeitslast",
  pet_load_hint:
    "Nur überfällige Aufgaben zählen. Etwas aufzuschreiben schadet dem Begleiter nie.",

  stage_healthy: "Wohlauf",
  stage_weakened: "Angeschlagen",
  stage_sick: "Krank",
  stage_miserable: "Elend",

  // ---------------------------------------------------------------- Sprechblasen
  speech: Object.freeze({
    start: ["Da bist du ja.", "Fangen wir an.", "Was steht an?"],
    task_done: ["Einer weniger.", "Sauber.", "Das war der.", "Weiter so."],
    list_empty: ["Alles leer. Ungewohnt.", "Nichts offen. Genieß das."],
    task_due: ["Da wartet was.", "Das wird langsam Zeit."],
    focus_begins: ["Fünfundzwanzig Minuten. Los.", "Ich bin still, versprochen."],
    focus_ends: ["Geschafft. Steh mal auf.", "Runde durch."],
    break_over: ["Pause vorbei.", "Weiter geht’s."],
    fed: ["Danke.", "Das war nötig.", "Besser."],
    played: ["Das hat gutgetan.", "Nochmal!"],
    patted: ["Mmh.", "Gern wieder."],
    hungry: ["Ich hätte Hunger.", "Da knurrt was."],
    tired: ["Ich bin müde.", "Mir fallen die Augen zu."],
    sick: ["Mir geht’s nicht gut.", "Da liegt einiges quer.", "Räum was weg, bitte."],
    happy: ["Guter Tag.", "So kann’s bleiben."],
    bored: ["Und jetzt?", "Ich warte.", "Alles ruhig hier."],
  }),

  // ----------------------------------------------------------------------- Fokus
  focus_title: "Fokus",
  focus_start: "Starten",
  focus_pause: "Anhalten",
  focus_resume: "Weiter",
  focus_stop: "Abbrechen",
  focus_skip: "Überspringen",
  focus_phase_focus: "Fokus",
  focus_phase_short: "Kurze Pause",
  focus_phase_long: "Lange Pause",
  focus_rounds_today: (n) => (n === 1 ? "1 Runde heute" : `${n} Runden heute`),
  focus_no_task: "Ohne Aufgabe",
  focus_pick_task: "Aufgabe wählen",
  focus_done_notice: "Runde geschafft.",
  focus_aborted_notice: "Abgebrochen — das gibt nichts, und das ist in Ordnung.",

  // --------------------------------------------------------------- Gewohnheiten
  habits_title: "Gewohnheiten",
  habits_new: "Neue Gewohnheit",
  habits_new_placeholder: "Was willst du regelmäßig tun?",
  habits_empty: "Noch keine Gewohnheit. Eine reicht für den Anfang.",
  habits_streak: (n) => (n === 1 ? "1 Termin in Folge" : `${n} Termine in Folge`),
  habits_streak_none: "Keine laufende Serie",
  habits_longest: (n) => `Beste Serie: ${n}`,
  habits_week: (done, due) => `${done} von ${due} diese Woche`,
  habits_schedule: "Zeitplan",
  habits_never_overdue: "Gewohnheiten werden nie überfällig und mahnen nie.",
  habits_delete: "Gewohnheit löschen",

  weekdays_short: ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"],
  weekdays_long: [
    "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag",
  ],

  // ------------------------------------------------------------------- Rückblick
  stats_title: "Rückblick",
  stats_streak: "Serie",
  stats_streak_days: (n) => (n === 1 ? "1 Tag" : `${n} Tage`),
  stats_longest: "Beste Serie",
  stats_total: "Insgesamt erledigt",
  stats_period: (n) => `In den letzten ${n} Tagen`,
  stats_focus_rounds: "Fokusrunden",
  stats_busiest: "Bester Tag",
  stats_today_counts_never_against_you: "Der heutige Tag zählt nie gegen dich.",

  // --------------------------------------------------------------- Einstellungen
  nag_group: (n) => `${n} überfällige Aufgaben warten.`,
  nag_stage: (stufe) =>
    ({
      first: "ist fällig",
      again: "steht immer noch offen",
      loud: "liegt schon eine Weile",
      cleanup: "Willst du das noch?",
    })[stufe] ?? "ist fällig",

  settings_title: "Einstellungen",
  settings_focus: "Fokus",
  settings_focus_minutes: "Fokusrunde (Minuten)",
  settings_short_break: "Kurze Pause (Minuten)",
  settings_long_break: "Lange Pause (Minuten)",
  settings_rounds: "Runden bis zur langen Pause",
  settings_quiet: "Ruhezeit",
  settings_quiet_enabled: "Ruhezeit einhalten",
  settings_quiet_start: "Von",
  settings_quiet_end: "Bis",
  settings_quiet_hint: "Erinnerungen in der Ruhezeit werden verschoben, nie verworfen.",
  settings_notifications: "Erinnerungen",
  settings_notifications_toggle: "Im Browser erinnern",
  settings_notifications_hint:
    "Erinnerungen erscheinen, solange die Seite geöffnet ist. Ohne Konto, ohne Server.",
  settings_notifications_hint_app:
    "In der App stellt Android den Wecker — Erinnerungen kommen auch bei geschlossener App. Ohne Konto, ohne Server.",
  settings_notifications_denied_app:
    "Android meldet nichts, solange die Erlaubnis fehlt. In den App-Einstellungen erlauben.",
  settings_exact_alarms_missing:
    "Ohne die Erlaubnis für genaue Wecker kann Android eine Erinnerung um einige Minuten verschieben.",
  settings_app_version: (fassung) => `App-Hülle ${fassung}`,
  settings_notifications_denied: "Vom Browser abgelehnt. In den Seiteneinstellungen erlauben.",
  settings_motion: "Darstellung",
  settings_reduce_motion: "Bewegung reduzieren",
  settings_fassung: "Fassung",
  settings_fassung_hell: "Hell",
  settings_fassung_dunkel: "Dunkel",
  settings_sync: "Geräteübergreifend",
  settings_sync_toggle: "Abgleich einschalten",
  settings_sync_hint:
    "Deine Aufgaben liegen verschlüsselt auf deinem eigenen Server. Er kann sie nicht lesen — der Schlüssel bleibt auf deinen Geräten. Kein Konto, keine Anmeldung, nichts auszudenken.",
  settings_sync_einschalten: "Abgleich einschalten",
  settings_sync_einschalten_allein: "Stattdessen hier neu anfangen",
  settings_sync_koppeln: "Koppeln",

  settings_sync_link_einfuegen: "Schon ein Gerät eingerichtet?",
  settings_sync_link_platzhalter: "Koppel-Link einfügen",
  settings_sync_link_hint:
    "Den Link findest du auf dem anderen Gerät unter Einstellungen → Geräteübergreifend.",
  settings_sync_link_hint_huelle:
    "Erforderlich: Der Link bringt die Adresse deines Servers mit, und die gibt es hier nicht von selbst. Du findest ihn im Browser unter Einstellungen → Geräteübergreifend.",
  settings_sync_link_unbrauchbar: "Das ist kein Koppel-Link. Kopier ihn noch einmal ganz.",
  settings_sync_link_ohne_adresse:
    "In diesem Link fehlt die Adresse. Kopier ihn auf dem anderen Gerät noch einmal ganz.",

  settings_sync_link_zeigen: "Koppel-Link für das nächste Gerät",
  settings_sync_link_zeigen_hint:
    "Auf dem anderen Gerät öffnen — mehr gehört nicht dazu. Wer den Link hat, hat deine Aufgaben: also nur an dich selbst schicken.",
  settings_sync_link_kopieren: "Kopieren",
  settings_sync_link_kopiert: "Koppel-Link kopiert",
  settings_sync_link_von_hand: "Kopieren ging nicht — der Link ist markiert.",

  settings_sync_jetzt: "Jetzt abgleichen",
  settings_sync_trennen: "Trennen",
  settings_sync_nie: "Noch nie abgeglichen",
  settings_sync_stand: (text) => `Zuletzt abgeglichen: ${text}`,
  settings_sync_gekoppelt: "Gerät gekoppelt — die Aufgaben kommen gleich.",
  settings_sync_ergebnis: Object.freeze({
    abgeglichen: "Abgeglichen.",
    gleichstand: "Alles war schon gleich.",
    losung_falsch: "Der Schlüssel passt nicht zu dem, was dort liegt.",
    unerreichbar: "Die Ablage ist nicht erreichbar.",
    fehler: "Das hat nicht geklappt.",
    aus: "Abgleich ist aus.",
  }),
  settings_sync_uebernommen: (n) => (n === 1 ? "1 Änderung übernommen" : `${n} Änderungen übernommen`),

  settings_data: "Daten",
  settings_backup_export: "Sicherung herunterladen",
  settings_backup_import: "Sicherung einlesen",
  settings_backup_hint:
    "Alles bleibt auf diesem Gerät. Die Sicherung ist eine JSON-Datei; sie fügt zusammen, statt zu überschreiben.",
  settings_backup_ok: (report) =>
    `${report.inserted} neu, ${report.updated} aktualisiert, ${report.skipped} übergangen.`,
  settings_backup_failed: "Die Datei ist keine lesbare PeTodo-Sicherung. Es wurde nichts geändert.",
  settings_reset: "Alle Daten löschen",
  settings_reset_confirm: "Wirklich alles löschen? Das lässt sich nicht rückgängig machen.",
  settings_about: "Über",
  settings_about_body:
    "Offline, ohne Konto, ohne Telemetrie. Die Daten liegen im Browser dieses Geräts.",

  // ---------------------------------------------------------------------- Sonstiges
  action_cancel: "Abbrechen",
  action_save: "Speichern",
  action_close: "Schließen",
  action_undo: "Rückgängig",
  action_search: "Suchen",
  search_placeholder: "In Titeln und Notizen suchen",
  search_empty: "Nichts gefunden.",
  loading: "Einen Moment …",
  error_generic: "Das hat nicht geklappt.",

  onboarding_title: "Willkommen",
  onboarding_body:
    "Schreib auf, was ansteht. Der Begleiter lebt davon, dass du überfällige Dinge wegräumst — nicht davon, dass du nichts aufschreibst.",
  onboarding_start: "Los geht’s",
});

/** Ein Prioritätsname. */
export const PRIORITY_NAMES = [S.priority_low, S.priority_normal, S.priority_high, S.priority_urgent];

export const STAGE_NAMES = Object.freeze({
  healthy: S.stage_healthy,
  weakened: S.stage_weakened,
  sick: S.stage_sick,
  miserable: S.stage_miserable,
});
