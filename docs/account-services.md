# Gmail och Google-inloggning

Gmail (för återställningsmejl) och Google-inloggning är valfria. Utan dem fungerar konton med e-post och lösenord som vanligt.

Google-profilen skapas först efter att servern har kontrollerat ID-token och att e-postadressen är verifierad.

## 1. Gmail

Aktivera tvåstegsverifiering på Google-kontot och skapa ett app-lösenord för SprintFlow. Använd app-lösenordet, inte kontots vanliga lösenord. Vissa konton tillåter inte app-lösenord; se [Googles instruktioner](https://support.google.com/mail/answer/185833).

Kör från projektmappen:

```powershell
.\configure-account-services.ps1
```

Scriptet visar inte app-lösenordet när du skriver och sparar det endast i den lokala `.env`-filen. SMTP använder `smtp.gmail.com`, port `587` och STARTTLS. `.env` ska inte läggas i GitHub.

## 2. Google OAuth

Skapa en OAuth-klient av typen **Web application** i Google Cloud och konfigurera appens namn och målgrupp. Lägg till dessa JavaScript-origins för lokal användning:

```text
http://localhost
http://localhost:8081
http://127.0.0.1
http://127.0.0.1:8081
```

Om du använder frontendens utvecklingsserver, lägg även till dess exakta origin. Ange klient-ID:t i konfigurationsscriptet. Den här implementationen verifierar ID-token på servern; den behöver ingen klienthemlighet i frontend. Följ [Googles aktuella installationsguide](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid).

## 3. Starta och verifiera

```powershell
.\start.cmd
```

Öppna **Profil → Inställningar → Kontotjänster**. Statusen anger om inställningarna finns, inte om Google/Gmail har godkänt dem.

Verifiera därefter med ett eget testkonto:

1. Begär återställning via **Glömt lösenord**, öppna mejlet och byt lösenord.
2. Kontrollera att länken inte kan återanvändas.
3. Logga in med den officiella Google-knappen på den konfigurerade origin-adressen.

Automatiska tester använder en simulerad mejltjänst och skickar inga riktiga mejl. Lösenord och client secret ska inte läggas i dokumentation eller GitHub. Den här Google-inloggningen använder Client ID och behöver ingen client secret.
