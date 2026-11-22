# Moderatorsguide för SUS-studien

## Inför varje session

- Använd samma SprintFlow-version, webbläsare, språk och startvy för varje deltagare. Skapa ett separat demoprojekt med en förberedd uppgift som heter `Granska leveransen` i kolumnen Att göra. Använd exempeldata.
- Verifiera att deltagarens demokonto får skapa och flytta uppgifter. Ha en separat, uppkopplad webbläsarsession som får redigera samma projekt.
- Öppna den uppkopplade uppgiftstavlan i deltagarens session så att appen hinner spara den för offlinearbete. Kontrollera att du kan växla deltagarens flik mellan online och offline i webbläsarens nätverkspanel.
- Läs upp samma introduktion. ”Vi utvärderar hur SprintFlow fungerar i fyra vanliga situationer. Deltagandet är frivilligt. Gör uppgifterna på det sätt du själv skulle välja.”

## Uppgifter att läsa upp

| Uppgift | Deltagarens instruktion | Kontroll för moderatorn |
| --- | --- | --- |
| 1 | Skapa en uppgift som heter `Förbered uppföljningen`. | Uppgiften syns i rätt demoprojekt. |
| 2 | Markera att arbetet med `Förbered uppföljningen` har påbörjats. | Uppgiften hamnar i Pågår. |
| 3 | Hitta projektets mätvärden för att få en överblick över hur arbetet går. | Deltagaren öppnar Mätvärden för demoprojektet. |
| 4 | Flytta `Granska leveransen` till Granskning. En kollega har samtidigt ändrat uppgiften. Behåll kollegans version när SprintFlow visar konflikten. | Kontrollera först att den lokala flytten köas. Verifiera sedan att kollegans version ligger kvar på servern och att konflikten försvinner från kön. |

Inför uppgift 4 öppnar moderatorn den sparade uppgiftstavlan och försätter deltagarens flik i offline-läge. När deltagaren har köat flytten ändrar moderatorn uppgiftens titel i den uppkopplade sessionen. Återanslut därefter deltagarens flik. Ge samma information om uppkopplingen till alla deltagare.

Anteckna per uppgift om deltagaren kom i mål själv, kom i mål med hjälp eller avbröt. Använd samma ledtråd om en deltagare fastnar. Samla korta observationer om arbetsflödet utan namn, mejladresser eller andra uppgifter som identifierar deltagaren.

## Efter uppgifterna

Ge deltagaren det etablerade SUS-formuläret med tio påståenden i originalordning. Använd samma formulärspråk för hela gruppen och samla svaren före diskussionen efter sessionen. Skalan går från 1, instämmer inte alls, till 5, instämmer helt. [Brookes SUS-instrument](https://www.taylorfrancis.com/chapters/edit/10.1201/9781498710411-35/sus-quick-dirty-usability-scale-john-brooke).

För över ett fullständigt formulär per rad till en kommaseparerad fil utanför repot. Filen innehåller enbart svarsvärdena och den här rubrikraden.

```csv
q1,q2,q3,q4,q5,q6,q7,q8,q9,q10
```

Kör utvärderingen med minst fem kompletta formulär.

```bash
EVAL_SUS_CSV=/sökväg/till/sus-svar.csv make eval
```

Redovisa gruppens storlek, valt formulärspråk och aggregerade resultat från `artifacts/evaluation/sus.json`. Dokumentera eventuella avvikelser från sessionens upplägg. [Utvärderingsrapporten](report.md) · [Svensk startguide](../getting-started.sv.md).

De fyra uppgifterna har provkörts i Chromium med två separata demowebbläsarsessioner. Det tekniska provet verifierade uppgiftens tillstånd på servern efter konfliktvalet. Deltagarobservationer och SUS-svar samlas in under studien.
