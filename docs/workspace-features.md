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
| Tillgänglighet | Namngivna kontroller, tangentbord, fokus, 44 px kontroller, befintligt stöd för minskad rörelse och temakontroller |
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

## Optimerad sprintplanering (C1, under arbete)

"Föreslå sprint" ska välja de stories som ger mest affärsvärde inom teamets kapacitet. Funktionens kärna finns nu i backend. API och knapp i sprintplaneringen kommer i ett senare steg.

- **Villkor:** förslaget håller sig inom den totala kapaciteten och, om den anges, inom varje medlems kapacitet. En story väljs bara om alla dess föregångare redan är klara eller också väljs. Du kan låsa in en story (den måste med, tillsammans med dess föregångare) eller låsa ut den (den och allt som beror på den stannar utanför).
- **Svar:** förslaget anger det totala värdet och om det är bevisat optimalt. Om tidsgränsen på 2 sekunder nås visas i stället en övre gräns och ett optimalitetsgap.
- **Förklaring:** du ser hur mycket av varje kapacitet som används och vilka kapaciteter som begränsar förslaget. För varje story utanför sprinten visas varför: utlåst, saknat beroende, inte plats, lägre värde per poäng än det som valdes, eller inget tillfört värde.
- **Omöjliga lås:** om låsen inte går att uppfylla, till exempel en inlåst story som beror på en utlåst story eller inlåsta stories som tillsammans överskrider kapaciteten, föreslås ingen sprint. I stället visas vilka lås som krockar.

Metoden, baslinjerna (girig och dynamisk programmering) och begränsningarna beskrivs i [ADR 0011](adr/0011-cp-sat-sprint-optimisation.md).

## Realtid och närvaro (B1)

Tavlan uppdateras automatiskt när någon annan i projektet skapar, flyttar, ändrar eller slänger en uppgift, kommenterar eller byter status på en sprint. Du behöver inte ladda om sidan. Överst i arbetsytan visas avatarer för de projektmedlemmar som har projektet öppet just nu.

- **Anslutning:** webbläsaren öppnar en WebSocket mot `/ws` med den vanliga inloggningen. Ingen extra inloggning eller token behövs. Utloggning, lösenordsbyte eller att du tas bort ur projektet stänger anslutningen inom 30 sekunder.
- **Vad som skickas:** bara vad som ändrades (typ, id, version och ett sekvensnummer), aldrig innehållet. Webbläsaren hämtar sedan det nya innehållet via API:t med dina vanliga behörigheter.
- **Avbrott:** tappas anslutningen försöker webbläsaren igen efter 1, 2, 4 … upp till 30 sekunder. Efter återanslutning hämtas det som missades via `GET /api/projects/{id}/events?after=<senaste sekvensnummer>`. Är luckan för stor eller äldre än 7 dagar laddas projektet om i stället.
- **Begränsningar:** en server i taget tills C6 inför en extern meddelandebroker. Att slänga en hel story eller sprint med dess uppgifter skickar ännu ingen uppdatering. Kommentarstråden i en öppen uppgift uppdateras när den öppnas igen.

Arkitekturen beskrivs i [ADR 0017](adr/0017-realtime-stomp-and-sequence-numbers.md).
