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

## Adresser per vy (A6)

Varje vy har en egen adress som går att ladda om, bokmärka och dela: `/overview`, `/projects`, `/backlog`, `/sprints`, `/tasks`, `/metrics`, `/updates`, `/profile`, `/calendar`, `/retrospective` och `/trash`. `/` öppnar den vy du senast använde i fliken. Delade rapporter öppnas på `/report/{token}` och återställningsmejl länkar till `/reset-password?token=…`. Äldre länkar med `?report=` eller `?resetToken=` skickas vidare till de nya adresserna. Projektväljaren finns på ett ställe, i sidhuvudet. Långa tavlor och backloggar (fler än 60 objekt i en kolumn eller lista) visas i ett rullningsbart område där bara de synliga korten finns i sidan. Se [ADR 0013](adr/0013-router-query-and-typed-lexicon.md).
