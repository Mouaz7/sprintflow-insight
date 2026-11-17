# Arbetsytans funktioner

Översikt över arbetsytans funktioner och var de finns i gränssnittet.

| Funktion | Innehåll |
| --- | --- |
| Kommentarer | Uppgiftsdiskussion, skapa/redigera/radera, eget innehåll och administrativ moderering |
| Notisklocka | Oläst antal, tilldelning/kommentar/deadline, läsmarkering, Escape och klick utanför |
| Checklistor | Lägg till/byt namn/kryssa/radera, framsteg, obligatoriska steg innan Klart |
| Projektmallar | Tomt, Scrum och Kanban; sammanhängande innehåll skapas i samma transaktion |
| Gmail/Google OAuth | Officiell Google-knapp, serververifiering, tjänststatus och lokalt konfigurationsscript för Gmail SMTP |
| Profilinställningar | Extra profilflik för språk, tema och notiser; sparas per konto |
| Projektarkiv | Arkivera/återaktivera; arkiverat arbete kan läsas och avvisar ändringar |
| Papperskorg | Återställ projekt, uppgifter, berättelser och sprintar; separat permanent radering |
| Kalender | Månadsvy, sprintperioder, lokala deadlinedatum och mobilvy |
| Etiketter | Sparade etiketter, neutrala märken och etikettfilter |
| Sparade filter | Personliga filter per projekt, Mina uppgifter/Blockerat, spara/byt namn/radera |
| Retrospektiv | Det gick bra/Att förbättra/Nästa steg, redigering och moderering per sprint |
| Tillgänglighet | Namngivna kontroller, tangentbord, fokus, 44 px kontroller, befintligt stöd för minskad rörelse och temakontroller; ett `<main>`, skip-länk, fokus på sidrubriken vid sidbyte och axe-tester (mål WCAG 2.2 AA) |
| Introduktion | Valfri guide, sparat slutförande och möjlighet att öppna den igen från profilen |
| Delade rapporter | Ögonblicksbild, bekräftelse före delning, sju dagars giltighet, återkallelse och utskrift |

## Var hittar jag funktionerna?

- **Uppgifter:** öppna Etiketter och checklista, eller Kommentarer på ett uppgiftskort.
- **Toppmenyn:** notisklockan visar dina personliga notiser.
- **Projekt:** välj en mall vid skapande, eller öppna Arkiverade projekt.
- **Papperskorg:** återställ ett objekt, eller välj permanent radering efter bekräftelse.
- **Kalender/Retrospektiv:** välj ett projekt och, för retrospektivet, en sprint.
- **Mätvärden:** skapa eller återkalla en delbar rapportlänk.
- **Profil → Inställningar:** språk, tema, notiser och introduktion.

Den aktuella vyn sparas per konto och webbläsarflik och återställs när sidan laddas om. Aktivt projekt sparas också. Okända sparade vyer öppnar Översikt.

Systemhändelser i Uppdateringar och översiktens senaste aktivitet visas på valt språk, även för tidigare sparade poster. Projektnamn, uppgiftstitlar, blockeringsorsaker och fritext behålls som de skrevs. Språkbytet ändrar visningen utan att skriva om historiken i databasen.

## Radering och återställning

Vanlig radering i gränssnittet flyttar innehållet till papperskorgen. Projektets innehåll följer med och skyddas från vanliga läs-/skrivanrop. Radering av en berättelse eller sprint flyttar dess aktiva arbetsinnehåll tillsammans, så relationerna kan återställas. Ett objekt som redan låg i papperskorgen återställs inte automatiskt med en senare borttagen förälder.

Återställ föräldern först om en uppgift hör till en borttagen berättelse/sprint. En aktiv sprint kan inte återställas om projektet redan har en annan aktiv sprint. Återaktivera arkiverade projekt innan arbete ändras.

Projektägaren kan radera permanent. Projektets beroende poster tas bort tillsammans i en databastransaktion och uppladdade filer tas bort efter lyckad commit. Permanent radering går inte att ångra. Papperskorgen har ingen automatisk tidsgräns i denna version.

## Rapportdelning

Rapporten innehåller projektets namn, beskrivning och sammanställda mätvärden, inte medlemslistor, kommentarer eller inloggningsuppgifter. Alla med länken kan läsa den tills den går ut eller återkallas. Rapporten är en ögonblicksbild. Administratör/ägare behöver bekräfta delningen; länken sparas inte i klartext på servern.

## Gmail och Google

Se [kontoguiden](account-services.md) för hur Gmail SMTP och Google-inloggning konfigureras. Automatiska tester använder en simulerad mejltjänst och skickar inga riktiga mejl.

## Databasavbrott

Om databasen tillfälligt inte går att nå svarar API:t med `503 SERVICE_UNAVAILABLE` och `Retry-After: 5`, även när en sparad session inte kan läsas. Databasens felmeddelanden visas inte för klienten. Försök igen efter pausen; när anslutningen fungerar återhämtar sig appen utan omstart. Se [testguiden](testing.md) för hur detta verifieras med Toxiproxy.

## Transaction boundaries and notifications (A3)

Account, project, story and task operations now pass through application services; API URLs, role rules and version checks remain the same. Task movement and comments produce typed, durable domain events. Inbox delivery happens after commit, so it may appear shortly after the originating change. A temporary listener failure is retried from PostgreSQL without creating duplicate inbox items. Project/account deletion and email-reference changes still complete atomically; pending notifications do not recreate inbox data for a deleted recipient or project.

See [ADR 0006](adr/0006-modulith-and-transactional-events.md) and the [generated module diagram](architecture/components.puml) for boundaries and operational retry checks. No new frontend route or public endpoint is introduced by A3.

## Java 25 platform (A4)

The app uses Spring Boot 4 with virtual request and asynchronous delivery threads. Existing API URLs, permissions, JSON columns and PostgreSQL event publications remain compatible. Google verification retains Google's checks and now uses explicit outbound connection/read timeouts. Local development and container/CI builds require Java 25. Upgrade and reproducible performance-check details are in [ADR 0007](adr/0007-boot4-java25.md).

## Projektnycklar, uppgifts-id och medlemskap (B0)

Varje projekt har en unik nyckel på 2–10 bokstäver, till exempel `SF`. Nyckeln föreslås automatiskt från projektnamnet (initialer, annars de fyra första bokstäverna) och kan väljas när projektet skapas eller ändras av ägare och administratörer under **Projekt → Redigera**. Varje uppgift får ett löpande nummer per projekt, och tillsammans blir de ett id som `SF-123`. Id:t visas på uppgiftskorten och i uppgiftsredigeraren. Numren delas ut säkert även när flera skapar uppgifter samtidigt och återanvänds aldrig. Byter projektet nyckel följer alla id med (`SF-12` blir `NY-12`).

`GET /api/tasks/by-key/SF-123` hämtar en uppgift via id. Den som saknar läsbehörighet får samma 404 som för ett id som inte finns.

Medlemskap knyts nu till kontots oföränderliga id i stället för e-postadressen. Ett byte av e-postadress kan därför varken ge eller ta bort åtkomst. Migreringen V15 tar bort medlemsrader utan konto, så att den som senare registrerar den adressen inte ärver åtkomst.

Hemligheter som sparas av kommande funktioner krypteras med AES-256-GCM. Nyckeln `SPRINTFLOW_ENCRYPTION_KEY` skapas av startskripten i `.env`. Säkerhetskopiera `.env`, för utan nyckeln går krypterade värden inte att läsa. Nyckelrotation beskrivs i [ADR 0015](adr/0015-encryption-and-key-rotation.md).

## Sök och kommandopalett (B9)

Tryck **Ctrl+K** (**⌘K** på Mac) eller **/**, eller klicka på **Sök** i sidhuvudet, för att öppna kommandopaletten. Den söker i uppgifter, stories, epics, sprintar, kommentarer och projekt i alla projekt där du är medlem, även arkiverade. Saker i papperskorgen visas inte, och projekt där du inte är medlem syns aldrig, inte heller via ett uppgifts-id.

- **Sökningen** förstår svenska och engelska böjningar ("inloggning" hittar "Inloggningen", "redirect" hittar "redirected"), räcker med början av ett ord och tål små stavfel i titlar. De ord som matchade markeras. Bästa träff kommer först, och **Visa fler träffar** hämtar nästa sida.
- **Uppgifts-id:** skriv `SF-123` och tryck Enter för att hoppa direkt till uppgiften, även innan träfflistan har laddats.
- **Navigering:** en uppgift eller kommentar öppnar tavlan i rätt projekt, filtrerad på uppgiftens id. En story eller epic öppnar projektets backlog (epicvyn kommer i B3), en sprint sprintvyn och ett projekt översikten.
- **Kommandon:** utan sökord visar paletten kommandon som "Gå till tavlan", "Ny uppgift" och "Kortkommandon". Skriver du något filtreras även kommandona.
- **Tangentbord:** piltangenterna flyttar mellan träffar, Enter öppnar och Esc stänger. Paletten följer WAI-ARIA-mönstret combobox med lista, så skärmläsare läser upp vald träff och antalet träffar.

### Kortkommandon

| Tangenter | Gör |
|---|---|
| Ctrl/⌘ + K | Öppna eller stäng sök och kommandon |
| / | Öppna sök |
| ? | Visa alla kortkommandon |
| C | Ny uppgift |
| G sedan O / B / L / S / M / P | Gå till översikt, tavlan, backlog, sprintar, mätvärden eller projekt |

Kortkommandona gäller inte när du skriver i ett fält eller när en annan dialog är öppen. Hjälpdialogen (?) är en modal dialog där fokus stannar tills du stänger den.

`GET /api/search?q=…` är API:t bakom paletten. Valfria parametrar är `projectId`, `types` (till exempel `task,comment`), `limit` (högst 50) och `cursor` för nästa sida. Sökningen bygger på PostgreSQL:s fulltextsökning och `pg_trgm`. Migreringen V16 installerar `pg_trgm`, vilket kräver att databasanvändaren äger databasen, som i `compose.yaml`. Se [ADR 0016](adr/0016-postgresql-full-text-search.md).

## Optimerad sprintplanering (C1, under arbete)

"Föreslå sprint" ska välja de stories som ger mest affärsvärde inom teamets kapacitet. Funktionens kärna finns nu i backend. API och knapp i sprintplaneringen kommer i ett senare steg.

- **Villkor:** förslaget håller sig inom den totala kapaciteten och, om den anges, inom varje medlems kapacitet. En story väljs bara om alla dess föregångare redan är klara eller också väljs. Du kan låsa in en story (den måste med, tillsammans med dess föregångare) eller låsa ut den (den och allt som beror på den stannar utanför).
- **Svar:** förslaget anger det totala värdet och om det är bevisat optimalt. Om tidsgränsen på 2 sekunder nås visas i stället en övre gräns och ett optimalitetsgap.
- **Förklaring:** du ser hur mycket av varje kapacitet som används och vilka kapaciteter som begränsar förslaget. För varje story utanför sprinten visas varför: utlåst, saknat beroende, inte plats, lägre värde per poäng än det som valdes, eller inget tillfört värde.
- **Omöjliga lås:** om låsen inte går att uppfylla, till exempel en inlåst story som beror på en utlåst story eller inlåsta stories som tillsammans överskrider kapaciteten, föreslås ingen sprint. I stället visas vilka lås som krockar.

Metoden, baslinjerna (girig och dynamisk programmering) och begränsningarna beskrivs i [ADR 0011](adr/0011-cp-sat-sprint-optimisation.md).

## Adresser per vy (A6)

Varje vy har en egen adress som går att ladda om, bokmärka och dela: `/overview`, `/projects`, `/backlog`, `/sprints`, `/tasks`, `/metrics`, `/updates`, `/profile`, `/calendar`, `/retrospective` och `/trash`. `/` öppnar den vy du senast använde i fliken. Delade rapporter öppnas på `/report/{token}` och återställningsmejl länkar till `/reset-password?token=…`. Äldre länkar med `?report=` eller `?resetToken=` skickas vidare till de nya adresserna. Projektväljaren finns på ett ställe, i sidhuvudet. Långa tavlor och backloggar (fler än 60 objekt i en kolumn eller lista) visas i ett rullningsbart område där bara de synliga korten finns i sidan. Se [ADR 0013](adr/0013-router-query-and-typed-lexicon.md).


Språk och tema delas av inloggningen och arbetsytan och sätts innan gränssnittet renderas. Plural och datum använder förkompilerade ICU-meddelanden för svenska och engelska. Vid byte av vy flyttas tangentbordsfokus till sidrubriken; skip-länken går direkt till huvudinnehållet.

## Shared design foundations (A7)

Theme colours, typography and spacing live in `shared/styles/tokens.css`; responsive breakpoints are defined once in `shared/styles/breakpoints.css`. Feature styles sit with their feature and share one documented cascade. Inter/Manrope fonts are self-hosted, with local fallbacks and no Google Fonts stylesheet request.

Burndown uses the shared line chart. The chart catalogue also includes bars, scatter plots, histograms and cumulative flow. Every chart can show its full values as a table and supports arrow/Home/End exploration through its data selector. Loading, empty and error states share semantic components. See [ADR 0018](adr/0018-design-system-charts-and-visual-baselines.md) and the [visual test instructions](testing.md#design-system-and-visual-baselines-a7).
