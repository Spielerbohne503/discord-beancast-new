/**
 * Alle Balancing-Zahlen der App an genau einer Stelle.
 *
 * Eine Zahl mit fachlicher Bedeutung gehört hierher, nirgendwo sonst — auch dann, wenn
 * sie nur an einer Stelle gebraucht wird. Verstreute Konstanten sind später nicht mehr
 * einzusammeln, und beim Nachjustieren sucht man sonst überall.
 */
export const Balance = Object.freeze({
  // ------------------------------------------------------------------ Verfall des Pets

  /** Basisstunden, in denen ein Wert ohne Ereignisse von 100 auf 0 fällt. */
  DECAY_HOURS_ENERGY: 12,
  DECAY_HOURS_SATIETY: 16,
  DECAY_HOURS_MOOD: 24,

  /** Verstrichene Zeit wird gedeckelt: Nach zwei Wochen Urlaub altert das Pet um einen Tag. */
  DECAY_MAX_ELAPSED_HOURS: 24,

  VALUE_MIN: 0,
  VALUE_MAX: 100,

  // ------------------------------------------------------------- Überfälligkeitslast

  /** Beitrag je überfälliger Aufgabe nach Priorität (Index = Priorität). */
  LOAD_PRIORITY_FACTOR: [0.5, 1.0, 1.5, 2.0],

  /** Zuschlag je angefangener Überfälligkeitswoche. */
  LOAD_PER_STARTED_WEEK: 0.2,

  /** Obergrenze der Last. */
  LOAD_CAP: 10,

  /** mBasis = 1 + (L / LOAD_CAP) × MULTIPLIER_LOAD_SPAN → 1,0 bis 3,0 */
  MULTIPLIER_LOAD_SPAN: 2.0,

  /** Die Laune hängt an den anderen Werten: Versäumnisse verstärken sich gegenseitig. */
  MOOD_COUPLING: 3.0,

  // -------------------------------------------------------------- Krankheitsstufen

  STAGE_HEALTHY_ABOVE: 70,
  STAGE_WEAKENED_ABOVE: 40,
  STAGE_SICK_ABOVE: 15,

  // -------------------------------------------------------------------- Belohnungen

  REWARD: Object.freeze({
    TASK_DONE: { energy: 0, satiety: 12, mood: 4, xp: 5, cooldownMinutes: null },
    TASK_CLEANED: { energy: 6, satiety: 6, mood: 6, xp: 5, cooldownMinutes: null },
    FOCUS_DONE: { energy: 20, satiety: 0, mood: 15, xp: 25, cooldownMinutes: null },
    HABIT_DONE: { energy: 3, satiety: 5, mood: 5, xp: 3, cooldownMinutes: null },
    FEED: { energy: 0, satiety: 35, mood: 10, xp: 5, cooldownMinutes: 4 * 60 },
    PLAY: { energy: -12, satiety: -6, mood: 30, xp: 10, cooldownMinutes: 2 * 60 },
    PAT: { energy: 0, satiety: 0, mood: 12, xp: 1, cooldownMinutes: 30 },
    TASK_CREATED: { energy: 0, satiety: 0, mood: 0, xp: 1, cooldownMinutes: null },
  }),

  /** Höchstens so viele Erfassungs-XP am Tag — sonst legt man Aufgaben an statt sie zu tun. */
  REWARD_TASK_CREATED_MAX_PER_DAY: 10,

  // ------------------------------------------------------------------------- Level

  LEVEL_FIRST_STEP_XP: 100,
  LEVEL_GROWTH: 1.5,
  LEVEL_MAX: 200,

  // ------------------------------------------------------------------ Erinnerungen

  /** Tage bis zur nächsten Eskalationsstufe der Nag-Kette. */
  NAG_DAYS_QUIET: 2,
  NAG_DAYS_LOUD: 4,
  NAG_DAYS_CLEANUP: 7,

  /** Ab so vielen überfälligen Aufgaben eine Sammelmeldung statt vieler einzelner. */
  NAG_GROUP_THRESHOLD: 3,

  QUIET_HOURS_DEFAULT_START: "23:00",
  QUIET_HOURS_DEFAULT_END: "08:00",

  /** Vorgabe-Uhrzeit, wenn zu einem Tagestermin eine Uhrzeit gewählt wird. */
  DEFAULT_DUE_HOUR: 9,

  SNOOZE_MINUTES: 60,

  // ---------------------------------------------------------------- Ungefähre Zeiten

  VAGUE_MORNING_HOUR: 8,
  VAGUE_NOON_HOUR: 12,
  VAGUE_AFTERNOON_HOUR: 15,
  VAGUE_EVENING_HOUR: 18,
  VAGUE_NIGHT_HOUR: 21,

  // ---------------------------------------------------------------------- Fokus

  FOCUS_MINUTES: 25,
  SHORT_BREAK_MINUTES: 5,
  LONG_BREAK_MINUTES: 20,
  ROUNDS_BEFORE_LONG_BREAK: 4,

  // ------------------------------------------------------------------ Heute-Ansicht

  /** So lange bleibt Erledigtes im eingeklappten Rückblick unter der Liste. */
  ARCHIVE_DAYS: 7,
  ARCHIVE_MAX_ROWS: 50,

  /** So weit zurück werden Gewohnheits-Einträge geladen. */
  HABIT_HISTORY_DAYS: 400,

  /** Wie viele Tage der Rückblick als Balken zeigt. */
  STATS_WINDOW_DAYS: 14,

  // ------------------------------------------------------------------ Sprechblasen

  BUBBLE_HUNGRY_BELOW: 30,
  BUBBLE_TIRED_BELOW: 30,
  BUBBLE_HAPPY_ABOVE: 85,
  BUBBLE_REPEAT_CHANCE: 0.35,
});

/** Die vier Prioritätsstufen. Vorgabe ist 1 — „normal“ und ohne Farbe. */
export const Priority = Object.freeze({
  LOW: 0,
  NORMAL: 1,
  HIGH: 2,
  URGENT: 3,
  DEFAULT: 1,
  coerce: (value) => Math.min(3, Math.max(0, Number(value) || 0)),
});
