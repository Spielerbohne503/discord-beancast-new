var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// worker/index.js
var MAX_BYTES = 8 * 1024 * 1024;
var HALTBARKEIT_TAGE = 400;
var worker_default = {
  async fetch(anfrage, umgebung) {
    const adresse = new URL(anfrage.url);
    if (!adresse.pathname.startsWith("/sync/")) {
      return umgebung.ASSETS ? umgebung.ASSETS.fetch(anfrage) : antwort(404, { fehler: "unbekannt" });
    }
    if (!umgebung.PETODO) {
      return antwort(501, {
        fehler: "kein Speicher gebunden",
        hinweis: "In wrangler.toml die KV-Bindung PETODO eintragen \u2014 siehe docs/SYNC.md"
      });
    }
    const raum = adresse.pathname.slice("/sync/".length);
    if (!/^[A-Za-z0-9_-]{43}$/.test(raum)) return antwort(400, { fehler: "ung\xFCltige Kennung" });
    if (anfrage.method === "GET") return holen(umgebung, raum);
    if (anfrage.method === "PUT") return ablegen(anfrage, umgebung, raum);
    if (anfrage.method === "DELETE") return loeschen(umgebung, raum);
    return antwort(405, { fehler: "Methode nicht erlaubt" }, { Allow: "GET, PUT, DELETE" });
  }
};
async function holen(umgebung, raum) {
  const eintrag = await umgebung.PETODO.getWithMetadata(schluessel(raum), { type: "text" });
  if (eintrag.value === null) return antwort(404, { fehler: "nichts abgelegt" });
  return new Response(eintrag.value, {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      // Der Stempel ist die Handhabe gegen gleichzeitiges Schreiben.
      ETag: eintrag.metadata?.stempel ?? '"unbekannt"'
    }
  });
}
__name(holen, "holen");
async function ablegen(anfrage, umgebung, raum) {
  const rohtext = await anfrage.text();
  if (rohtext.length > MAX_BYTES) return antwort(413, { fehler: "zu gro\xDF" });
  let umschlag;
  try {
    umschlag = JSON.parse(rohtext);
  } catch {
    return antwort(400, { fehler: "kein JSON" });
  }
  if (typeof umschlag?.iv !== "string" || typeof umschlag?.daten !== "string") {
    return antwort(400, { fehler: "kein Umschlag" });
  }
  const vorhanden = await umgebung.PETODO.getWithMetadata(schluessel(raum), { type: "text" });
  const stempel = vorhanden.metadata?.stempel ?? null;
  const erwartet = anfrage.headers.get("If-Match");
  const nurNeu = anfrage.headers.get("If-None-Match") === "*";
  if (nurNeu && vorhanden.value !== null) return antwort(412, { fehler: "gibt es schon" });
  if (!nurNeu && erwartet === null) return antwort(428, { fehler: "If-Match fehlt" });
  if (!nurNeu && erwartet !== stempel) return antwort(412, { fehler: "veralteter Stand" });
  const neuerStempel = `"${crypto.randomUUID()}"`;
  await umgebung.PETODO.put(schluessel(raum), rohtext, {
    metadata: { stempel: neuerStempel, abgelegt: Date.now() },
    expirationTtl: HALTBARKEIT_TAGE * 86400
  });
  return antwort(200, { abgelegt: true }, { ETag: neuerStempel });
}
__name(ablegen, "ablegen");
async function loeschen(umgebung, raum) {
  await umgebung.PETODO.delete(schluessel(raum));
  return antwort(200, { geloescht: true });
}
__name(loeschen, "loeschen");
var schluessel = /* @__PURE__ */ __name((raum) => `raum:${raum}`, "schluessel");
function antwort(status, koerper, kopfzeilen = {}) {
  return new Response(JSON.stringify(koerper), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...kopfzeilen }
  });
}
__name(antwort, "antwort");

// ../../../root/.npm/_npx/c943b712072b77c4/node_modules/wrangler/templates/middleware/middleware-ensure-req-body-drained.ts
var drainBody = /* @__PURE__ */ __name(async (request, env, _ctx, middlewareCtx) => {
  try {
    return await middlewareCtx.next(request, env);
  } finally {
    try {
      if (request.body !== null && !request.bodyUsed) {
        const reader = request.body.getReader();
        while (!(await reader.read()).done) {
        }
      }
    } catch (e) {
      console.error("Failed to drain the unused request body.", e);
    }
  }
}, "drainBody");
var middleware_ensure_req_body_drained_default = drainBody;

// ../../../root/.npm/_npx/c943b712072b77c4/node_modules/wrangler/templates/middleware/middleware-miniflare3-json-error.ts
function reduceError(e) {
  return {
    name: e?.name,
    message: e?.message ?? String(e),
    stack: e?.stack,
    cause: e?.cause === void 0 ? void 0 : reduceError(e.cause)
  };
}
__name(reduceError, "reduceError");
var jsonError = /* @__PURE__ */ __name(async (request, env, _ctx, middlewareCtx) => {
  try {
    return await middlewareCtx.next(request, env);
  } catch (e) {
    const error = reduceError(e);
    const body = JSON.stringify(error);
    const headers = {
      "Content-Type": "application/json",
      "MF-Experimental-Error-Stack": "true"
    };
    const encoded = encodeURIComponent(body);
    if (encoded.length <= 8192) {
      headers["MF-Experimental-Error-Stack-Payload"] = encoded;
    }
    return new Response(body, { status: 500, headers });
  }
}, "jsonError");
var middleware_miniflare3_json_error_default = jsonError;

// .wrangler/tmp/bundle-IJntM2/middleware-insertion-facade.js
var __INTERNAL_WRANGLER_MIDDLEWARE__ = [
  middleware_ensure_req_body_drained_default,
  middleware_miniflare3_json_error_default
];
var middleware_insertion_facade_default = worker_default;

// ../../../root/.npm/_npx/c943b712072b77c4/node_modules/wrangler/templates/middleware/common.ts
var __facade_middleware__ = [];
function __facade_register__(...args) {
  __facade_middleware__.push(...args.flat());
}
__name(__facade_register__, "__facade_register__");
function __facade_invokeChain__(request, env, ctx, dispatch, middlewareChain) {
  const [head, ...tail] = middlewareChain;
  const middlewareCtx = {
    dispatch,
    next(newRequest, newEnv) {
      return __facade_invokeChain__(newRequest, newEnv, ctx, dispatch, tail);
    }
  };
  return head(request, env, ctx, middlewareCtx);
}
__name(__facade_invokeChain__, "__facade_invokeChain__");
function __facade_invoke__(request, env, ctx, dispatch, finalMiddleware) {
  return __facade_invokeChain__(request, env, ctx, dispatch, [
    ...__facade_middleware__,
    finalMiddleware
  ]);
}
__name(__facade_invoke__, "__facade_invoke__");

// .wrangler/tmp/bundle-IJntM2/middleware-loader.entry.ts
var __Facade_ScheduledController__ = class ___Facade_ScheduledController__ {
  constructor(scheduledTime, cron, noRetry) {
    this.scheduledTime = scheduledTime;
    this.cron = cron;
    this.#noRetry = noRetry;
  }
  scheduledTime;
  cron;
  static {
    __name(this, "__Facade_ScheduledController__");
  }
  #noRetry;
  noRetry() {
    if (!(this instanceof ___Facade_ScheduledController__)) {
      throw new TypeError("Illegal invocation");
    }
    this.#noRetry();
  }
};
function wrapExportedHandler(worker) {
  if (__INTERNAL_WRANGLER_MIDDLEWARE__ === void 0 || __INTERNAL_WRANGLER_MIDDLEWARE__.length === 0) {
    return worker;
  }
  for (const middleware of __INTERNAL_WRANGLER_MIDDLEWARE__) {
    __facade_register__(middleware);
  }
  const fetchDispatcher = /* @__PURE__ */ __name(function(request, env, ctx) {
    if (worker.fetch === void 0) {
      throw new Error("Handler does not export a fetch() function.");
    }
    return worker.fetch(request, env, ctx);
  }, "fetchDispatcher");
  return {
    ...worker,
    fetch(request, env, ctx) {
      const dispatcher = /* @__PURE__ */ __name(function(type, init) {
        if (type === "scheduled" && worker.scheduled !== void 0) {
          const controller = new __Facade_ScheduledController__(
            Date.now(),
            init.cron ?? "",
            () => {
            }
          );
          return worker.scheduled(controller, env, ctx);
        }
      }, "dispatcher");
      return __facade_invoke__(request, env, ctx, dispatcher, fetchDispatcher);
    }
  };
}
__name(wrapExportedHandler, "wrapExportedHandler");
function wrapWorkerEntrypoint(klass) {
  if (__INTERNAL_WRANGLER_MIDDLEWARE__ === void 0 || __INTERNAL_WRANGLER_MIDDLEWARE__.length === 0) {
    return klass;
  }
  for (const middleware of __INTERNAL_WRANGLER_MIDDLEWARE__) {
    __facade_register__(middleware);
  }
  return class extends klass {
    #fetchDispatcher = /* @__PURE__ */ __name((request, env, ctx) => {
      this.env = env;
      this.ctx = ctx;
      if (super.fetch === void 0) {
        throw new Error("Entrypoint class does not define a fetch() function.");
      }
      return super.fetch(request);
    }, "#fetchDispatcher");
    #dispatcher = /* @__PURE__ */ __name((type, init) => {
      if (type === "scheduled" && super.scheduled !== void 0) {
        const controller = new __Facade_ScheduledController__(
          Date.now(),
          init.cron ?? "",
          () => {
          }
        );
        return super.scheduled(controller);
      }
    }, "#dispatcher");
    fetch(request) {
      return __facade_invoke__(
        request,
        this.env,
        this.ctx,
        this.#dispatcher,
        this.#fetchDispatcher
      );
    }
  };
}
__name(wrapWorkerEntrypoint, "wrapWorkerEntrypoint");
var WRAPPED_ENTRY;
if (typeof middleware_insertion_facade_default === "object") {
  WRAPPED_ENTRY = wrapExportedHandler(middleware_insertion_facade_default);
} else if (typeof middleware_insertion_facade_default === "function") {
  WRAPPED_ENTRY = wrapWorkerEntrypoint(middleware_insertion_facade_default);
}
var middleware_loader_entry_default = WRAPPED_ENTRY;
export {
  __INTERNAL_WRANGLER_MIDDLEWARE__,
  middleware_loader_entry_default as default
};
//# sourceMappingURL=index.js.map
