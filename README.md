# Medley

Server-driven, hybrydowy framework UI dla Spring Boot — działający w duchu Blazor Server,
podobny w funkcjonowaniu do Vaadin Flow. Logika i stan komponentów żyją domyślnie na
serwerze; po sieci leci tylko **diff** (lista patchy), a nie cały HTML. Dla ciężkiej
interakcji przewidziano **wyspy klienckie** (`<medley-island>`) działające bez round-tripów.

To jest **PoC** (Proof of Concept) — celowo mały i czytelny rdzeń, gotowy do dalszego rozwoju.

---

## ⚠️ Krok 0 — wygeneruj Gradle wrapper jar

Archiwum **nie zawiera** `gradle/wrapper/gradle-wrapper.jar` (binarka nie była dostępna w
środowisku, w którym projekt powstał). Wygeneruj go raz:

```bash
# jeśli masz zainstalowany Gradle 8.x:
gradle wrapper --gradle-version 8.10.2

# albo użyj dołączonego skryptu (wymaga systemowego gradle):
./bootstrap.sh
```

Po tym kroku `./gradlew` działa samodzielnie i systemowy Gradle nie jest już potrzebny.
Instalacja Gradle: https://gradle.org/install/ (np. `brew install gradle`,
`sdk install gradle 8.10.2`).

Wymagana **Java 21**.

---

## Uruchomienie demo

```bash
./gradlew :examples:counter-demo:bootRun
```

Otwórz **http://localhost:8080/counter**.

Co zobaczysz: licznik renderowany na serwerze (SSR). Kliknięcie `+` wysyła zdarzenie po
WebSocket, serwer zmienia stan, liczy diff i odsyła pojedynczy patch `text`. W zakładce
Network (WS) widać, że przy kolejnych kliknięciach leci dokładnie jedna mała operacja.

---

## Struktura

```
medley/
├── medley-core/                      # silnik niezależny od Springa
│   └── app/besoft/medley/core/
│       ├── vnode/        VNode (VElement, VText)
│       ├── template/     parser szablonów + evaluator wyrażeń + renderer
│       ├── diff/         Differ, Patch, HtmlSerializer
│       └── component/    Component, ComponentInstance, adnotacje, ActionScanner
│
├── medley-spring-boot-starter/       # integracja ze Spring Boot
│   ├── app/besoft/medley/spring/            autokonfiguracja, WS, SSR, sesja, routing
│   └── resources/
│       ├── static/medley/medley.js   runtime kliencki (hydratacja + patch + wyspy)
│       └── META-INF/spring/...imports  rejestracja autokonfiguracji
│
└── examples/counter-demo/            # aplikacja demonstracyjna
```

---

## Jak to działa (pętla)

```
1. GET /counter            → SSR: serwer renderuje pełny HTML z data-medley-id
2. medley.js               → hydratacja (podpięcie listenerów) + otwarcie WebSocket
3. klik "+"                → WS: { componentId:"root", action:"increment" }
4. serwer                  → invokeAction → mutacja @State → re-render → diff
5. WS                      → [ { "op":"text", "id":"root.3.2", "value":"1" } ]
6. medley.js               → applyPatches: jedna zmiana w DOM
```

Stan komponentu żyje w `MedleySession` (przypiętej do sesji HTTP). WebSocket dziedziczy tę
sesję przez `MedleyHandshakeInterceptor`, więc autoryzacja i stan są wspólne ze stroną.

---

## Model komponentu

```java
@MedleyRoute("/counter")
@MedleyComponent("counter")
@Scope("prototype")
public class CounterComponent extends Component {
    @State int count = 0;
    @Param String label = "Kliknięcia";

    @Action void increment() { count++; }
    @Action void reset()     { count = 0; }
}
```

```html
<!-- templates/medley/counter.html -->
<div class="counter">
  <span>{{ label }}: {{ count }}</span>
  <button @click="increment">+</button>
  <button @click="reset" *if="count > 0">reset</button>
</div>
```

Składnia szablonu: `{{ expr }}` interpolacja · `@event="action"` zdarzenie → `@Action` ·
`:attr="expr"` atrybut wyliczany · `*if="expr"` warunek · `*for="x : items"` pętla (z `key`).

---

## Wyspy klienckie (hybryda)

`<medley-island name="x">` to granica, za którą serwer nie zarządza DOM. Deweloper rozszerza bazę
`window.medley.MedleyIsland` i rejestruje klasę:

```js
class Sparkline extends window.medley.MedleyIsland {
  mount()           { /* renderuj lokalnie; obsłuż interakcje, zero round-tripów */ }
  onProp(name, val) { /* serwer wypchnął props (patch atrybutu hosta) */ }
  // this.commit(action, payload) — utrwal gruboziarnisty stan na serwerze
}
window.medley.registerIsland("sparkline", Sparkline);
```

Po stronie serwera: `@MedleyIsland("sparkline")` z metodami `@IslandAction` (moduł starter).
`this.commit(action, payload)` wysyła wiadomość `island-commit`, która mutuje `@State`
komponentu-właściciela; jego re-render wypycha zmienione props z powrotem na host. Wyspa działa
autonomicznie — serwer dostaje tylko rzadkie commity, co realizuje cel „mało stanu na serwerze"
dla interakcji wysokiej częstotliwości. Działający przykład: wyspa **sparkline** w
`examples/counter-demo` (trasa `/chart`).

---

## Stan implementacji PoC

| Element | Status |
|---|---|
| VNode + parser szablonu + evaluator | ✅ gotowe (rdzeń: **57 testów**) |
| Differ + patche + serializer HTML | ✅ gotowe |
| Model komponentu (@State/@Param/@Action) | ✅ gotowe |
| medley.js (hydratacja, WS, patch, reconnect, wyspy) | ✅ gotowe |
| Pętla end-to-end po WebSocket | ✅ **udowodniona** (tools/dev-server oraz realny Spring WS) |
| Starter: autokonfiguracja, WS, SSR, sesja, routing (**Etap 2**) | ✅ gotowe (starter: **19 testów**) |
| Wyspy klienckie — `MedleyIsland`/`@MedleyIsland`/`@IslandAction` (**Etap 3**) | ✅ gotowe |
| Demo: counter (`/counter`) + sparkline island (`/chart`) | ✅ gotowe |
| Biblioteka komponentów, walidacja, security | ⏳ Etap 4 |
| Format binarny patchy, Redis dla sesji, metryki | ⏳ Etap 5 |

Pełny `./gradlew build` jest zielony: 3 moduły kompilują się, **57 testów rdzenia + 19 startera**
przechodzi, a bootJar demonstracji się buduje.

---

## Testy rdzenia

```bash
./gradlew :medley-core:test
```

57 testów jednostkowych pokrywających: evaluator wyrażeń, parser szablonów, renderer (w tym
stabilność placeholdera `*if`, klucze `*for` i nieprzezroczysty host `<medley-island>`), differ
(w tym rekoncyliacja po kluczach), serializer HTML (w tym escaping XSS) oraz pełną pętlę
komponentu render → akcja → diff.

## Weryfikacja end-to-end po WebSocket (bez Springa)

Etap 1 został udowodniony „na drucie" lekkim harnessem JDK-only — patrz
**`tools/dev-server/README.md`**. Serwer renderuje SSR, serwuje `medley.js`, obsługuje
WebSocket; skryptowany klient wysyła `increment`/`decrement`/`reset` i drukuje zwracane
patche. Kluczowy wynik: drugi `increment` zwraca **dokładnie jeden** patch
`{"op":"text","id":"root.3.2","value":"2"}`.

---

## Test rdzenia bez Springa

Sam silnik można uruchomić bez Springa (patrz `MEDLEY_DESIGN.md`, sekcja pętli). Rdzeń nie
ma zależności poza Jacksonem.

Pełny dokument projektowy: **MEDLEY_DESIGN.md**.

---

## Praca z Claude Code (wirtualny zespół)

Projekt jest skonfigurowany do pracy z Claude Code jako mały zespół inżynierski. Po otwarciu
repo w IDE i uruchomieniu `claude` z katalogu głównego, Claude czyta `CLAUDE.md` (konstytucja
projektu) i konfigurację w `.claude/`.

Role (subagenci): **@architect**, **@developer**, **@tester**, **@reviewer**,
**@product-owner**. Komendy: `/project-status`, `/next-stage`, `/verify-all`, `/code-review`,
`/medley-add-component <Name> [route]`. Wiedza domenowa jest w `.claude/skills/` i ładuje się
automatycznie, gdy pasuje do zadania.

Szczegóły: **.claude/README.md**.
