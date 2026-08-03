# KraftReports

**The transactional-document engine for JVM backends.**

> Define your data. Design your document. Trust the output.

---

## 0. What changed from v1, and why

The original spec tried to be JasperReports + Apache POI + iText + a scheduling platform + a cloud dashboard, all at once, for every output format, for every language. That's not a v1 — that's five products stapled together, and it's the same shape as at least four things already shipping in 2026 (NextReport, Docmosis, Carbone, DynamicReports). "Modern DX" alone won't beat them; they all say that too.

To actually be top-2 for JVM, KraftReports needs to **win one battle decisively** before fighting five. This version picks that battle, adds the features needed to win it, and pushes everything else to a clearly sequenced roadmap.

---

## 1. The Specific Problem

**Who:** Backend engineers on Kotlin/Java teams (Spring Boot, Ktor, Quarkus) who need to generate **transactional business documents** — invoices, receipts, statements, contracts, certificates — from application code, at request time or in batch, and ship them without a design team or a report-server ops burden.

**What currently happens to them:**

1. They reach for JasperReports, spend two days fighting JRXML and a desktop designer that doesn't match production data, and give up on iterating quickly.
2. They reach for Apache POI/iText directly, and six months later have 2,000 lines of imperative cell-by-cell code that only one engineer understands and that breaks silently when the data shape changes.
3. Their reports have **zero automated test coverage**, because none of the legacy tools were built with testing in mind — so a layout regression ships to production and someone notices when a customer complains about a broken invoice.
4. Regulated customers (finance, healthcare, insurance) need **byte-for-byte reproducible output** for audit trails, and none of the incumbent tools guarantee this — re-rendering the same data can produce a different PDF depending on fonts, timestamps, or library version.

**The wedge:** *Nobody in this space treats document generation as a testable, version-controlled, CI-friendly engineering artifact.* Jasper treats it as a design tool. POI treats it as a low-level API. Docmosis/Carbone treat it as template-filling. KraftReports should treat it as **code you test, diff, and trust** — that's the differentiator that isn't just a slogan.

---

## 2. Competitive Reality Check

| | JasperReports | Apache POI | Docmosis / Carbone | NextReport | **KraftReports** |
|---|---|---|---|---|---|
| Definition format | XML (JRXML) | Imperative code | Word/template-based | JSON schema | Kotlin DSL + JSON/YAML |
| Multi-format output | Yes | Excel only | Yes | Yes | Yes |
| Native automated testing | No | No | No | Unclear | **Yes — first-class** |
| Reproducible/deterministic output | No | No | No | Unclear | **Yes — guaranteed** |
| Git-diffable definitions | Poor (binary-adjacent XML) | N/A | Poor (binary templates) | Good | **Yes — plain text by design** |
| Kotlin-native / type-safe | No | No | No | No | **Yes** |
| Self-hostable core | Yes | Yes | Partial | Yes | Yes |

The columns that matter are the three where every competitor is weak or absent: **testing, reproducibility, diffability.** That's the beachhead.

---

## 3. Killer Features (the reason to switch)

### 3.1 Snapshot & Golden-File Testing, Built In

Not "you can technically assert on PDF text" — a real testing framework shipped with the core library.

```kotlin
@Test
fun `invoice matches golden snapshot`() {
    val doc = report.generate(data = sampleInvoice, format = PDF)

    assertThat(doc).matchesSnapshot("invoice-standard-v3")
    // on mismatch: generates a visual diff image + a text diff of extracted content
    // fails CI with an artifact link, not just a boolean
}
```

* First run records a snapshot; subsequent runs diff against it.
* Diffs are rendered as **visual overlays** (red/green highlight of what moved or changed), not just byte comparison — byte-level PDF diffs are useless because two correct renders can differ in internal metadata.
* `kraftreports test --update-snapshots` for intentional layout changes, same ergonomics as Jest/Playwright snapshot testing that JVM engineers increasingly already know from full-stack teams.
* CI plugin (GitHub Actions, GitLab CI) that posts the visual diff as a PR comment.

This single feature is the strongest reason a team migrates: it turns "reports broke in prod and we found out from a customer" into "reports broke in a PR and CI caught it."

### 3.2 Deterministic, Reproducible Rendering

```kotlin
val doc = report.generate(
    data = invoice,
    format = PDF,
    determinism = Determinism.STRICT  // pins fonts, disables embedded timestamps, fixed PRNG seed for any layout randomness
)

doc.contentHash() // same input -> same hash, every time, every machine
```

* Byte-identical output for identical input, independent of machine, timezone, or font-rendering environment (font subsetting pinned, no embedded generation timestamps unless explicitly requested).
* `doc.contentHash()` gives compliance teams a verifiable fingerprint — "this is the exact document that was issued on this date," provable in an audit.
* This is a checkbox regulated customers (insurance, banking, healthcare) explicitly ask for and that none of the incumbents guarantee today.

### 3.3 Git-Native Report Definitions

Every report definition (Kotlin DSL, JSON, or YAML) is plain text by construction — not a side option, a hard constraint on the format. That means:

* `git diff` on a report shows exactly what changed in the layout, not a binary blob.
* Code review works on reports the same way it works on code.
* No separate "report repository" or binary asset server needed.

### 3.4 Type-Safe Data Binding

```kotlin
data class Invoice(val number: String, val customer: String, val lines: List<LineItem>)

val invoiceReport = typedReport<Invoice>("invoice") {
    table(Invoice::lines) {
        column("Description") { it.description }   // compiler-checked against LineItem
        column("Total") { it.total }
    }
}

invoiceReport.generate(data = someInvoice, format = PDF) // won't compile if `someInvoice` isn't an Invoice
```

Nobody else in this space offers compile-time-checked report definitions against real domain objects. This is a Kotlin-specific advantage worth leaning on hard — it's the single biggest structural edge Kotlin has over Jasper's Java+XML world.

### 3.5 Live Preview / Hot Reload

```bash
kraftreports dev invoice.kt --data sample-invoice.json
# renders on save, opens a browser tab, refreshes on file change — a "vite for reports" loop
```

Jasper's designer requires a separate GUI tool disconnected from your IDE. This keeps the entire loop inside the terminal/IDE the developer already lives in, with sub-second feedback.

### 3.6 LLM-Native Report Generation

Since a JSON schema definition format already exists (Section 6.3 in the original spec), expose it explicitly as an **agent-friendly interface**:

```kotlin
val reportJson = claude.generate("Create a monthly sales report schema with a bar chart and a totals table")
KraftReports.fromJson(reportJson).generate(data = salesData, format = PDF)
```

This ties directly into the "KraftAI" module already planned in the Kraft Suite (Section 36) — an LLM can draft a report definition because the schema is plain, documented JSON, not proprietary XML. Worth being explicit and marketing this, since it's a natural fit for 2026-era buyers evaluating AI-agent compatibility.

---

## 4. Revised MVP Scope (narrower, shippable, testable)

Cut the visual designer, cloud platform, scheduling, and email delivery from v1 — they're real, but they're what everyone else already has, and building them first burns the runway needed to make the differentiators (3.1–3.6) actually solid.

**MVP = one thing done better than anyone else: testable, reproducible, type-safe transactional PDF/XLSX generation for Kotlin/Java backends.**

* **Core:** Kotlin DSL + typed data binding, JVM only
* **Output:** PDF and XLSX only (not five formats — two, done extremely well)
* **Components:** Text, tables, images, headers/footers, page numbers, grouping, basic charts
* **The differentiators:** snapshot testing (3.1), deterministic rendering (3.2), git-native definitions (3.3), typed binding (3.4) — all ship in MVP, none deferred
* **Integration:** Spring Boot starter only (the largest JVM install base) — Ktor/Quarkus come after traction, not before
* **Distribution:** open-source core on GitHub, Apache-licensed, positioned explicitly as "the tested, typed alternative to JasperReports"

Everything else — CSV/HTML/DOCX output, visual designer, scheduling, email delivery, cloud platform, Ktor/Quarkus/Micronaut integrations, CLI beyond `dev`/`test` — becomes **Phase 2 and Phase 3**, built once the core has proven adoption.

---

## 5. Phased Roadmap

**Phase 1 (0–6 months) — Prove the wedge**
PDF + XLSX, Spring Boot, snapshot testing, deterministic rendering, typed DSL. Ship to open source. Target: JVM teams currently on JasperReports or raw POI who are in pain over testing/reliability, not teams shopping on format breadth.

**Phase 2 (6–12 months) — Round out the format story**
Add CSV, HTML, DOCX renderers. Add Ktor and Quarkus integrations. Add the CLI preview/dev loop (3.5). This is where format-independence (the original Section 3.2) becomes real.

**Phase 3 (12–18 months) — Automation and cloud, now optional and monetizable**
Scheduling, email delivery, event-based generation, hosted history/audit logs. This is the Pro/Enterprise tier — but it's built on a core that's already trusted, not used to compensate for a shaky core.

**Phase 4 (18+ months) — Visual designer, AI-native workflows, multi-language SDKs**
Browser-based designer for business users, explicit LLM report-generation SDK (3.6), Python/.NET/Node SDKs over the REST API. This is where the original "generate anything, from anything" vision is earned rather than assumed.

---

## 6. What "Top 2 for JVM" Actually Requires

Being top 2 doesn't mean matching Jasper's format list on day one — Jasper has a 20-year head start there and that's not a fair fight yet. It means being **unambiguously better on the axis JVM teams are increasingly measuring on: can I trust this in CI, can I trust this in an audit, does it fit how I already write Kotlin.**

Concretely, that means:

* A **published benchmark**: same invoice template, JasperReports vs. KraftReports, lines of code and setup time to get a snapshot-tested, CI-integrated report pipeline running. This should be a blog post and a repo, not a claim.
* A **migration guide with a working converter** for at least the common JRXML patterns (tables, groups, subreports-as-nested-tables) — reduces switching cost, which is the single biggest barrier for teams already invested in Jasper.
* **Case studies from regulated industries** (fintech, insurtech) specifically about the deterministic-rendering guarantee — this is a feature almost no engineer asks for by name until you show them the audit problem it solves, at which point it becomes a hard requirement.
* Getting the Spring Boot starter into "the top result when a Kotlin developer searches 'jasperreports alternative kotlin'" — SEO/content matters as much as the engine, given how crowded this space already is.

---

## 7. Positioning

* **Tagline:** *The reporting engine you can actually test.*
* **One-line pitch:** KraftReports is a Kotlin-native document engine that treats your invoices and statements like code — typed, tested, diffable, and reproducible — instead of like a design file you hope doesn't break.
* **Elevator context:** JasperReports wasn't built for CI. Apache POI wasn't built for humans. KraftReports is built for JVM teams who ship reports the same way they ship everything else: with tests.