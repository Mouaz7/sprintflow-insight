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

## Optimerad sprintplanering (C1)

**Föreslå sprint** på en planerad eller aktiv sprint jämför vilka berättelser som kan läggas till från backloggen. Välj poäng eller hela timmar, ändra kapacitet i scenariot och lås berättelser in eller ut. Referensen är sprintens inställda kapacitet efter att pågående åtaganden räknats av. Förslaget sparar inga ändringar i projektet.

- **Villkor:** förslaget håller sig inom den totala kapaciteten och inom varje medlems kapacitet för arbete som redan har en ansvarig. Oallokerat arbete ingår i teamets kapacitet. En story väljs bara om alla dess föregångare redan är klara eller också väljs. Du kan låsa in en story (den måste med, tillsammans med dess föregångare) eller låsa ut den (den och allt som beror på den stannar utanför).
- **Svar:** förslaget anger det totala värdet och om det är bevisat optimalt. Om tidsgränsen på 2 sekunder nås visas bästa hittade förslag, en övre gräns och gap när sådana finns. Annars visas det giriga reservförslaget.
- **Förklaring:** du ser hur mycket av varje kapacitet som används och vilka kapaciteter som begränsar förslaget. För varje story utanför sprinten visas varför: utlåst, saknat beroende, inte plats, lägre värde per poäng än det som valdes, eller inget tillfört värde.
- **Omöjliga lås:** om låsen inte går att uppfylla, till exempel en inlåst story som beror på en utlåst story eller inlåsta stories som tillsammans överskrider kapaciteten, föreslås ingen sprint. I stället visas vilka lås som krockar.

Metoden och baslinjerna beskrivs i [ADR 0011](adr/0011-cp-sat-sprint-optimisation.md). [ADR 0029](adr/0029-sprint-planning-scenarios.md) beskriver hur sprintdata blir ett scenario. [Benchmarkresultat](evaluation/sprint-plan-solvers.md) går att återskapa med fast seed.

## Sprintrisk för uppgifter (C3)

Öppna **Sprintrisk** på en oavslutad uppgift i en pågående sprint. Panelen visar sannolikheten att uppgiften är oavslutad vid sprintslutet och upp till tre faktorer som höjer eller sänker risken. Modellkällan visar om sannolikheten bygger på projektets historiska sprintutfall eller på uppskattningen vid begränsad historik. Ägare, administratörer, medlemmar och läsare kan öppna panelen.

En nattlig körning observerar aktuellt arbete och tränar en separat modell per projekt. Bedömningen gäller uppgiften och dess arbetsflöde. Modellen använder exempelvis blockeringar, beroenden och tid i kolumnen. Den använder inga personidentiteter. [ADR 0030](adr/0030-nightly-project-task-risk.md) beskriver när historiken blir träningsunderlag.

## Dubblettförslag och flödessignaler (C4)

När du skriver titeln på en ny uppgift kan **Möjliga dubbletter** visa upp till fem liknande uppgifter från det valda projektet. Du kan fortsätta skapa uppgiften. Förslagen jämför titlar och kan missa dubbletter som beskrivs med andra ord.

Under **Flödesanalys** öppnar du **Signaler för inflöde och genomströmning**. Tabellen visar sju hela UTC-dagar och de kontrollgränser som räknats från de föregående 28. Projektägaren får en notis när gårdagens värde passerar en gräns och signalen är ny. Notiser följer kontots inställningar. [Metod och datakrav](adr/0031-duplicate-and-flow-signals.md).

## Automationsregler (B15)

Välj projekt under **Projekt** och öppna **Automationsregler** i projektets medlemsinställningar. Ägare och administratörer kan skapa, redigera, aktivera och ta bort regler. Välj en utlösare, ett valfritt villkor och en åtgärd i regelbyggaren. Arkiverade projekt har läsbar regel- och körhistorik.

En regel kan reagera på att en uppgift byter kolumn, att en pull request mergas eller att en uppgift blir förfallen. Åtgärden kan tilldela en projektmedlem, lägga till en etikett, flytta uppgiften, kommentera eller notifiera ägaren. **Körlogg** visar utförda, överhoppade och misslyckade regler. En kedja får köra högst tre regler och använder aldrig samma regel två gånger.

**Regeltext** visar DSL-versionen. Du kan ange upp till fem villkor. Alla villkor behöver vara uppfyllda. Använd fält-ID:t för ett anpassat fält. Textvärden sätts inom citattecken.

```text
when task-moved to "Review"
if label "customer"
if field 42 greater-than "3"
then comment "Ready for review."
```

`when overdue` kontrolleras kvartvis. `when pull-request-merged` använder projektets GitHub-webhook. Skapa en webhook för ditt `owner/repository` och kopiera adress och engångshemlighet till GitHubs webhookinställningar. Välj JSON och händelsen Pull requests. Skriv en uppgiftsnyckel, exempelvis `SF-123`, i PR-titeln eller grennamnet. [Webhooks i GitHub](https://docs.github.com/en/webhooks/using-webhooks/creating-webhooks) och [ADR 0032](adr/0032-automation-rules.md).

## Adresser per vy (A6)

Varje vy har en egen adress som går att ladda om, bokmärka och dela: `/overview`, `/projects`, `/backlog`, `/sprints`, `/tasks`, `/metrics`, `/updates`, `/profile`, `/calendar`, `/retrospective` och `/trash`. `/` öppnar den vy du senast använde i fliken. Delade rapporter öppnas på `/report/{token}` och återställningsmejl länkar till `/reset-password?token=…`. Äldre länkar med `?report=` eller `?resetToken=` skickas vidare till de nya adresserna. Projektväljaren finns på ett ställe, i sidhuvudet. Långa tavlor och backloggar (fler än 60 objekt i en kolumn eller lista) visas i ett rullningsbart område där bara de synliga korten finns i sidan. Se [ADR 0013](adr/0013-router-query-and-typed-lexicon.md).


Språk och tema delas av inloggningen och arbetsytan och sätts innan gränssnittet renderas. Plural och datum använder förkompilerade ICU-meddelanden för svenska och engelska. Vid byte av vy flyttas tangentbordsfokus till sidrubriken; skip-länken går direkt till huvudinnehållet.

## Shared design foundations (A7)

Theme colours, typography and spacing live in `shared/styles/tokens.css`; responsive breakpoints are defined once in `shared/styles/breakpoints.css`. Feature styles sit with their feature and share one documented cascade. Inter/Manrope fonts are self-hosted, with local fallbacks and no Google Fonts stylesheet request.

Burndown uses the shared line chart. The chart catalogue also includes bars, scatter plots, histograms and cumulative flow. Every chart can show its full values as a table and supports arrow/Home/End exploration through its data selector. Loading, empty and error states share semantic components. See [ADR 0018](adr/0018-design-system-charts-and-visual-baselines.md) and the [visual test instructions](testing.md#design-system-and-visual-baselines-a7).

## Liveuppdateringar och närvaro (B1)

Tavlan, öppna kommentarstrådar och notisklockan uppdateras när andra arbetar i samma projekt. Du behöver inte ladda om sidan. Egna osparade utkast behålls när nya data hämtas. Även ändringar som berör flera uppgifter, exempelvis när en story flyttas till papperskorgen, uppdaterar projektets vyer.

Sidhuvudet visar andra projektmedlemmars initialer. Håll pekaren över närvaron för namn och vilken uppgift de har öppen; skärmläsare får samma information. Flera flikar sammanförs till en person. Indikatorn visar om liveuppdateringarna är anslutna eller återansluter. Efter ett avbrott hämtas missade händelser automatiskt; om historiken har gallrats uppdateras projektets data i stället.

Alla läsroller, inklusive Viewer och medlemmar i arkiverade projekt, kan se närvaro och rapportera vilken uppgift de tittar på. Det ger ingen skrivrätt. Närvaron innehåller visningsnamn och uppgiftsreferenser, aldrig e-post eller uppgiftstext. En instans stöds nu; flera instanser kräver C6:s externa broker enligt [ADR 0017](adr/0017-realtime-stomp-and-sequence-numbers.md).


## Epics, färdplan och beroenden (B3)

Öppna **Färdplan** i projektet. Skapa en epic med namn, beskrivning, färg och valfria planerade datum. **Storyns epic** kopplar en story till en epic; **Ingen epic** tar bort kopplingen utan att ändra sprint eller uppgifter. Radering av en epic behåller dess arbete.

Tidslinjen visar epics, sprintar, framsteg och pilar mellan epics vars uppgifter har beroenden. Rulla med tangentbordet eller öppna tabellen för samtliga datum och framsteg. Fokusera på en epic för dess egen prognos. Framsteg räknas som klara storypoäng: en story behöver minst en uppgift och alla dess uppgifter ska vara klara.

**Lägg till beroende** väljer först uppgiften som måste bli klar, sedan den väntande uppgiften. Cykler avvisas och visar uppgiftskedjan. Tavlan markerar väntande uppgifter separat från manuella blockeringar. När blockeraren blir klar försvinner beroendeblockeringen. Tavlans filter för manuellt blockerade uppgifter och dess export behåller den manuella blockeringens betydelse. Färdplanen varnar när arbete i den aktiva sprinten väntar på oavslutat arbete utanför sprinten.

**Uppskatta en uppgift** anger optimistisk, troligaste och pessimistisk tid i arbetsdagar (0–1000, högst två decimaler, stigande ordning). PERT visar den förväntade kritiska vägen, förväntat slutdatum och en ungefärlig sannolikhet till epicens måldatum. Oavslutade blockerare utanför epicen räknas med. Saknade estimat eller stories utan uppgifter döljer slutdatum och sannolikhet; sidan visar vad som saknas. Helger räknas bort, men inte helgdagar. Oberoende tider och en normalfördelningsapproximation antas; nära parallella vägar kan ge en optimistisk sannolikhet. Använd prognosen som planeringsstöd.

Viewer och arkiverade projekt kan läsa färdplanen. Ägare, administratörer och medlemmar i aktiva projekt kan ändra den. Versionskonflikter behåller formulärets utkast; kopiera utkastet och öppna posten igen för att jämföra med senaste versionen. Sökresultat för epics öppnar rätt epic på färdplanen. Se [ADR 0019](adr/0019-epics-dependencies-and-pert.md) för beräkningar och API-kontraktet för gränser och versionskrav.

## Flödesanalys och WIP-gränser (B5)

Öppna **Flödesanalys** och välj 7, 30, 90 eller 180 kalenderdagar. Kumulativt flöde visar hur uppgifterna fördelas mellan kolumner vid varje UTC-dags slut. Fördelningarna visar cykel- och ledtid med 50:e, 85:e och 95:e percentiler. Pågående uppgifter visas separat med aktiv ålder och kolumnålder, så långsamt oavslutat arbete inte försvinner bakom statistiken över avslut.

Cykeltid omfattar den senaste sammanhängande perioden Pågår/Granskning som slutar i Klar. Tillbaka till Att göra eller återöppning startar en ny period; byte mellan aktiva kolumner behåller perioden. Ledtid börjar när uppgiften skapas. Historik som saknas visas som en täckningsvarning; dagens kolumnantal räknar fortfarande alla synliga uppgifter. Diagrammen använder projektets nuvarande arbetsmängd, så senare radering eller papperskorg kan påverka äldre antal.

Ägare och administratörer kan välja **Ange WIP-gränser**. Ange 1–999 uppgifter per kolumn eller lämna tomt för att ta bort gränsen. Gränserna varnar men hindrar inte flytt. Tavlans WIP-antal gäller hela kolumnen även när korten filtreras. Arkiverade projekt och läsroller behåller tillgång till analysen. Vid en versionskonflikt finns utkastet kvar; jämför med senaste inställningarna innan du sparar igen.

XmR-diagrammet visar variation och statistiska signaler. Gränserna är beskrivande och beräknas från den valda perioden; en signal bevisar ingen särskild orsak. Little's lag hjälper er att granska om pågående arbete, avslutstakt och cykeltid använder samma processgräns. En avvikelse kan bero på periodens gränser, återöppningar eller förändrat inflöde. Se [metodbeslutet](adr/0020-flow-history-wip-and-statistical-review.md) för antaganden och begränsningar.

## Leveransprognoser (B6)

Öppna **Prognoser** i projektet. Välj uppgifter per hel historisk vecka eller storypoäng per avslutad sprint. Ett epic-val avgränsar det återstående arbetet; kapacitetshistoriken kommer fortfarande från hela projektet. Läs varningen om parallellt arbete innan du tolkar en epic-prognos.

Lämna mängder tomma för registrerat återstående arbete, eller ange 0–10 000 enheter. **Uppdatera prognosen** tillämpar frågan och sparar den i sidans adress för omladdning och delning. Byte av underlag eller epic rensar egna mängder; byte av projekt återställer frågan. Alla läsroller, även Viewer och medlemmar i arkiverade projekt, får utforska prognoser utan att ändra sparat arbete.

Datumen för 50/85/95 procent täcker motsvarande andel simulerade utfall. Mängderna till ett datum är försiktiga miniminivåer: högre procent ger lägre mängd. Histogrammen har tabellalternativ och tangentbordsstyrning. Den aktiva sprintens registrerade återstående mängd och slutdatum är standardmål; annars används fyra perioder framåt. Sannolikheten bedömer mängden, inte om ett kvalitativt sprintmål eller acceptanskriterier uppfylls.

Modellen drar 10 000 gemensamma framtida förlopp. Kalenderdagar omfattar helger, och leveransen antas vara jämnt fördelad inom perioden. Färre än sex perioder, saknad historik, stories utan uppgifter och nollpoängsarbete ger varningar. Ofullständiga förlopp räknas fortfarande med; ett saknat datum betyder att den valda sannolikheten inte nås inom femårshorisonten. Historiken och antagandena kan vara missvisande vid förändrad kapacitet eller beroenden. Se [metoden](metrics-definition.md#delivery-forecasts-b6) och [ADR 0021](adr/0021-joint-bootstrap-delivery-forecasts.md).

## Planning poker (B7)

Öppna **Planning poker** (`/poker`) i ett valt projekt. Ägare, administratörer och medlemmar kan starta en session för en berättelse i backloggen. Välj Fibonacci, T-shirt eller 2–20 unika egna kort i stigande uppskattningsordning. `?` och `☕` avstår från uppskattningen; Fibonacci-kortet 13 påminner om att dela upp berättelsen.

Alla deltagare röstar självständigt och kan ändra eller ta tillbaka sin egen röst. Servern visar vilka som röstat och den egna rösten. Andras kort och statistik lämnar servern först när spelledaren, ägaren eller en administratör avslöjar rundan. Läsbehörighet ger möjlighet att följa sessionen; arkiverade projekt är läsbara.

Efter avslöjandet visas kortfördelning, lägsta/högsta kort, lägre median, enighet och numeriskt medelvärde när det är meningsfullt. Avståenden ingår inte i uppskattningsstatistiken. Diskutera skillnaderna och starta en ny runda vid behov. Tidigare avslöjade rundor finns kvar. T-shirt och egna kort får ingen automatisk poängmappning: teamet väljer uttryckligen 1, 2, 3, 5 eller 8 överenskomna poäng. Sparandet uppdaterar berättelsen och aktivitetsloggen atomiskt. Ett lokalt utkast bevaras om någon ändrar berättelsen; ladda om uppskattningen innan du försöker spara igen.

Sessionslänkar använder `/poker?session=<id>`; deltagarna väljer samma projekt innan de öppnar länken. Realtidsmeddelanden innehåller bara ändringsmetadata; API:t avgör vad varje läsare får se. Aktiv session uppdateras även var femte sekund som reserv när realtid saknas. Varje kommando anger rundan för att fördröjda anrop inte ska påverka en senare runda. Avbrytande raderar den aktuella rundans ännu dolda röster. Högst 100 öppna sessioner per projekt, en per berättelse och 50 rundor per session tillåts. Listan visar högst 20 nyligen ändrade avslutade sessioner. Se [ADR 0023](adr/0023-planning-poker.md).

## Tidrapportering och kapacitet (B4)

Öppna **Tid och kapacitet** (`/time`) i ett valt projekt. **Logga tid** registrerar 1–1440 minuter på en uppgift och arbetsdag, med en valfri anteckning. Starta i stället en **timer** för att mäta pågående arbete. Varje konto har högst en timer i alla projekt. När den stoppas avrundas tiden uppåt till hela minuter, minst en minut och högst 24 timmar. Pågående minuter ingår först när timern stoppas. Om du förlorat skrivrätten till en gammal timers projekt tar en ny timer bort den gamla utan att logga minuterna.

Du kan rätta eller radera dina egna poster. Ägare och administratörer kan även hantera andras poster. Läsbehörighet och arkiverade projekt ger läsåtkomst. Efter en versionskonflikt finns utkastet kvar; stäng och öppna redigeraren igen för att granska den senaste versionen.

Rapporten **Uppskattat mot faktiskt** låter dig ange tidsuppskattningar per uppgift och exportera CSV. En tom uppskattning betyder att uppgiften inte uppskattats; noll är ett uttryckligt nollvärde. Avvikelsen jämför bara uppskattade uppgifter, samtidigt som alla loggade minuter visas separat. Uppgifter i papperskorgen och pågående timers räknas inte. Urvalet följer uppgiftens nuvarande projekt och sprint, så flytt och papperskorg kan ändra rapporten bakåt i tiden. Tidsuppskattningen är separat från story points och PERT-uppskattningar i arbetsdagar.

Välj en sprint för **Sprintkapacitet**. Ange arbetsminuter per dag, tillgänglighet i procent, valfria poäng för en hel sprint och frånvarodatum. Kapaciteten räknar vardagar från måndag till fredag, inklusive sprintens start och slut. Dubbletter, helger och datum utanför sprinten räknas inte som extra frånvaro. Ange röda dagar manuellt. Klara uppgifter ingår fortfarande i sprintens planerade arbete. Varningar visar överbokning, saknad kapacitet, uppskattningar som saknas och otilldelat arbete. Medlemmar ändrar sin egen kapacitet; ägare och administratörer kan ändra teamets.

Tidposter och kapacitet sparas på servern och uppdateras mellan deltagarnas webbläsare. Radering av ett konto anonymiserar färdigloggade timmar; de finns kvar i projektets totalsummor. Se [ADR 0024](adr/0024-time-tracking-and-capacity.md).

## Personliga dashboards (B18)

Öppna **Dashboard** i projektets navigering. Varje konto har en egen layout per projekt; även läsbehörighet ger möjlighet att anpassa den egna vyn. **Anpassa dashboard** väljer widgets, ändrar deras ordning med upp/ned-knappar och väljer breda kort. Spara för att behålla layouten efter omladdning. Standardlayouten kan återställas i redigeraren; en tom layout går också att spara. På mobil staplas alla kort i samma ordning.

Sprintvalet styr burndown och sprintmål. Kumulativt flöde och WIP visar projektets senaste 30 dagar, prognosen använder historisk genomströmning för projektets oavslutade arbete och **Mina öppna uppgifter** visar högst 20 av dina tilldelade oavslutade uppgifter. Diagrammens datatabeller visar samma värden utan grafik. Saknad historik visas uttryckligen; prognoser är simulerade utfall och ska läsas tillsammans med varningarna i den detaljerade prognosvyn.

Layouten delas aldrig med andra konton. Ändringar i två fönster skyddas med versionskontroll: vid konflikt finns utkastet kvar; stäng och öppna redigeraren igen för att läsa den senaste layouten. Arkiverade projekt går att läsa men layouten kan inte ändras där. Återställ projektet innan du anpassar vyn.

## Omnämnanden och e-postsammanfattningar (B8)

I en kommentar kan du skriva `@namn` eller välja ett förslag för att infoga en hel adress, exempelvis `@anna@example.com`. Kortnamnet är e-postadressens del före `@` och måste vara entydigt i projektet. Pil ned öppnar förslagen, piltangenterna flyttar mellan dem och Enter väljer. Bara projektmedlemmar kan aviseras; tvetydiga namn, utomstående, vanliga e-postadresser utan omnämnandemarkör och text i slutna bakåtcitat ger inga omnämnanden. Högst 20 olika namn behandlas per kommentar. Du aviseras inte om dina egna omnämnanden.

En nyskriven eller redigerad kommentar aviserar nytillagda mottagare. Att behålla samma omnämnande vid en redigering skickar ingen ny omnämnandeavisering. Aviseringen kommer via den befintliga notisklockan och liveuppdateringen, även till medlemmar med läsbehörighet. Kommentarer kan fortfarande bara skrivas i aktiva projekt av användare med skrivrätt.

Under **Profil → Inställningar → E-postsammanfattningar** väljer du Av, Dagligen eller Veckovis och en IANA-tidszon, till exempel `Europe/Stockholm`. Dagliga sammanfattningar planeras klockan 08.00 och veckovisa på måndagar klockan 08.00 i den valda tidszonen. Inställningen är avstängd från början. Den sparas även utan SMTP, men inga mejl skickas innan e-post är konfigurerad. Avstängda aviseringar pausar även sammanfattningarna. Ett fel eller en versionskonflikt lämnar utkastet kvar; läs uttryckligen in sparade inställningar efter en konflikt.

Sammanfattningen innehåller högst 50 olästa uppdateringar som du fortfarande har åtkomst till. Länken i mejlet öppnar en bekräftelsesida där sammanfattningar kan stoppas utan inloggning; att bara öppna länken ändrar inget. Länken gäller i 90 dagar och kan inte stänga av en senare återaktivering. Aviseringarna i appen ändras inte av avregistreringen. Läs mer om leverans- och låssemantik i [ADR 0026](adr/0026-mentions-and-email-summaries.md).

## B16 — project custom fields

Open **Tasks → Custom fields** (`/fields`). Owners and administrators can create up to 20 fields per active project: Text, Number, Date, Select or Project member. Names are unique ignoring case. Types stay fixed after saving. Select fields allow up to 30 unique options; an option used by a live or recycled task cannot be removed. Deleting a field asks for confirmation and removes its saved values.

A saved task's **Custom fields** action opens its values. Owner/Admin/Member can change them; Viewer and archived-project readers can inspect them. Empty values clear fields. Numbers preserve up to 18 whole digits and 6 decimal places, including values beyond JavaScript's integer precision. Dates use ISO years 0001–9999. Member choices refer to current project membership: removing a member clears their custom user assignments; rejoining does not restore those assignments.

The board and search palette offer a project field, a type-supported operator and, when needed, a value. Search still needs text or a task key and field-filtered search returns tasks from the active project. Text comparisons distinguish case; Contains treats `%` and `_` literally. Unset values match only **Is not set**, including when using **Does not equal**. Filter operands have a 200-character limit. Saved board filters include the custom condition. CSV/PDF links keep the same condition. CSV adds `Custom: <name>` columns, preserves exact numbers and uses current member labels with spreadsheet-formula escaping.

Values share the task revision with ordinary task editing. A simultaneous change returns a conflict, keeps the draft, and offers an explicit reload with a discard confirmation. Opening another project starts a separate editor. Field definitions have independent revisions. Recycled tasks keep values for restoration; permanent removal deletes them. Metadata changes invalidate project queries through existing realtime events without creating status-history transitions or including field contents in activity summaries.

B16 exposes the typed `FieldCondition` contract and pure matcher for B15 automation conditions. Configuring and executing automation rules belongs to B15.

## Import, kontoexport och granskningslogg (B19)

**Import och granskning** låter projektmedlemmar förhandsgranska CSV/Jira JSON, koppla fält och statusar och bekräfta en atomär import av högst 200 uppgifter. Befintliga källnycklar hoppas över. Ägare/administratörer kan bevara sammanfattade historiska tidpunkter och läsa den separata, skrivskyddade granskningsloggen med 90 dagars retention. Kontoarkivet hämtas via **Profil → Inställningar → Exportera ditt konto**. [Format, begränsningar och Apache-datasetprocedur](import-export.md).

## Organizations and teams

Open **Organizations** and choose a personal or shared organization. Its projects, members, teams and templates are linked under `/o/{slug}`. The project list shows projects you can access. Select **Open task board** to continue working in that project.

Owners and admins can create teams, add existing organization members, save project starters and issue invitation links. The owner manages admins and can transfer a shared organization to a member or admin. A member can create organization projects and use saved starters. A viewer can read the directory, teams and their accessible projects.

In **Invitations**, enter the recipient's email and role, then copy the link. The recipient signs in with that email and chooses **Accept invitation**. Links expire after seven days and can be revoked. An organization membership grants access to its directory and teams. Manage individual project roles in the project's own settings.

See [organization isolation](adr/0034-organization-scope.md) and [the REST contract](openapi.yaml).
