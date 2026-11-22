# Starta SprintFlow

## 1. Förbered datorn

Installera Git och Docker Desktop på Windows/macOS, eller Docker Engine med Compose på Linux. Docker sköter Java, Node och PostgreSQL åt dig.

## 2. Hämta och starta

Hämta projektet:

```powershell
git clone https://github.com/Mouaz7/sprintflow-insight.git
cd sprintflow-insight
```

På Windows kör du `.\start.cmd` i PowerShell. På Linux, macOS och WSL kör du `bash start.sh`. Starten skapar inställningarna och bygger appen automatiskt. Windows/macOS-starten öppnar Docker Desktop om den är avstängd. I WSL används Windows-starten om Docker inte kan nås direkt och Windows-program kan köras från WSL.

Första gången skapas `.env` med ett slumpmässigt databaslösenord. Docker hämtar byggmiljöerna och bygger webbappen. Det kan ta flera minuter och kräver internet. När du ser **SprintFlow is ready**, öppna **http://localhost:8081**. Därefter kan du stänga terminalen.

## 3. Skapa ditt första konto

Välj **Create account**, registrera dig och skapa ett projekt. Du behöver inte konfigurera Google eller Gmail för att använda vanliga konton. Du kan byta språk och tema i toppmenyn.

### Arbeta i rätt projekt

Välj projektet i toppmenyn. Backlog, sprintar, uppgifter och statistik hör till det
valda projektet. I backloggen väljer du **Manuell ordning** och flyttar berättelser
med upp-/nedknapparna; ordningen sparas. Sprintfiltret visar en viss sprintbacklog
eller oplanerade berättelser. Under Uppgifter exporterar **Exportera filtrerade
uppgifter** precis det aktuella urvalet till CSV eller PDF, med filtren angivna i filen.

### Installera appen och använd den offline

Öppna SprintFlow via HTTPS eller på localhost och använd webbläsarens installationsmeny. Besök vyn du vill läsa offline medan nätet fungerar. I **Uppgifter** kan du köa en flytt eller en ny kommentar. Under **Tid och kapacitet** kan du köa en manuell tidsregistrering. När anslutningen återkommer synkas ändringarna. Om servern har en nyare uppgiftsversion visar bannern dina val. [Guide till offlinefunktionen](workspace-features.md#installera-appen-och-arbeta-offline-b13).

## 4. Stoppa och starta igen

Kör kommandona i projektmappen:

```powershell
docker compose stop
# Starta igen:
docker compose up -d --wait
# Bygg om efter kodändringar eller ändrad .env:
.\start.cmd
```

Dina projekt ligger i databasvolymen `sprintflow-insight_sprintflow-postgres`. Uppladdade filer ligger i `backend/data`. Båda sparas vid omstart. Behåll `.env` och säkerhetskopiera databasen och filerna. Använd inte `docker compose down -v` för att stoppa: det raderar databasvolymen.

## 5. Google och Gmail (valfritt)

```powershell
.\configure-account-services.ps1
.\start.cmd
```

Skriptet frågar efter din Gmail-adress, ett Gmail-app-lösenord och ett Google OAuth Web-klient-ID. Lösenordet matas in dolt och sparas bara lokalt. Använd aldrig ditt vanliga Google-lösenord. Se [kontotjänster](account-services.md).

## 6. Om något inte startar

```powershell
docker compose ps
docker compose logs --tail 100 app postgres
```

Kontrollera att Docker Desktop körs och att port 8081 är ledig. Om databasen nekar anslutning, kontrollera att `.env` fortfarande har lösenordet som användes när databasen skapades. Vid problem med mejl eller Google, kontrollera kontotjänsternas status i profilen.

## Utvecklarläge

Med Node 24 och Java 25 installerat kan du köra:

```powershell
.\start.cmd -Native
```

Behåll terminalen öppen och stoppa med Ctrl+C. Läget använder samma databas och uppladdningsmapp som Docker. [Huvudguiden](../README.md) beskriver tester, konfiguration och Linux-behörigheter.

## Drift

Composeprofilen `observability` startar Prometheus, Grafana och Tempo i den lokala miljön. Använd Kubernetes-guiden när appen ska köras med flera repliker och delad databas. [Observerbarhetsprofil](adr/0039-observability-and-slos.md) · [Kubernetes-guide](adr/0040-kubernetes-and-cluster-recovery.md).
