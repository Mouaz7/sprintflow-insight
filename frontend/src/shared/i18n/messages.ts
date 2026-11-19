import type { Language } from '../../api/contracts';
import { defineLexicon, translator } from './lexicon';

// Keep the original message in state and translate when rendering, so switching
// language also updates an error that is already visible. Never translate user data.
const swedish: Record<string, string> = {
  'Use a field name of 1–80 characters.': 'Ange ett fältnamn med 1–80 tecken.',
  'Choose a field type.': 'Välj en fälttyp.',
  'Use at most 30 options of 1–80 characters each.':
    'Ange högst 30 val med 1–80 tecken vardera.',
  'Field options must be unique.': 'Varje fältval måste vara unikt.',
  'A select field needs at least one option.':
    'Ett valfält behöver minst ett val.',
  'Only select fields have options.': 'Endast valfält kan ha val.',
  'A project can have at most 20 custom fields.':
    'Ett projekt kan ha högst 20 anpassade fält.',
  'Create a new field to change its type.':
    'Skapa ett nytt fält för att byta typ.',
  'An option is still used by a task, including recycled tasks.':
    'Valet används fortfarande av en uppgift, även uppgifter i papperskorgen räknas.',
  'Provide a complete snapshot of at most 20 custom values.':
    'Ange samtliga värden, högst 20 anpassade fält.',
  'Provide each custom field at most once.':
    'Ange varje anpassat fält högst en gång.',
  'Custom field not found in this project.':
    'Det anpassade fältet finns inte i projektet.',
  'Choose a current member of this project.':
    'Välj en nuvarande medlem i projektet.',
  'Choose a custom field for this condition.':
    'Välj ett anpassat fält för villkoret.',
  'Custom field values cannot contain null characters.':
    'Fältvärden får inte innehålla nulltecken.',
  'A custom field value can be at most 2000 characters.':
    'Ett fältvärde får innehålla högst 2000 tecken.',
  "Choose one of this field's current options.":
    'Välj ett av fältets nuvarande val.',
  'Use a decimal number with at most 18 whole digits and 6 decimal places.':
    'Ange ett tal med högst 18 heltalssiffror och 6 decimaler.',
  'Use a real date in YYYY-MM-DD format, with year 0001–9999.':
    'Ange ett giltigt datum i formatet ÅÅÅÅ-MM-DD med år 0001–9999.',
  'Choose both a custom field and an operator.':
    'Välj både ett anpassat fält och ett villkor.',
  'A filter value can be at most 200 characters.':
    'Ett filtervärde får innehålla högst 200 tecken.',
  'Choose a valid field and an operator supported by its type.':
    'Välj ett giltigt fält och ett villkor som stöds av fälttypen.',
  'Provide a value only when the selected operator needs one.':
    'Ange ett värde endast när det valda villkoret behöver ett.',
  'Choose a project before filtering by a custom field.':
    'Välj ett projekt när du filtrerar på ett anpassat fält.',

  'Enter a valid date with a four-digit year.':
    'Ange ett giltigt datum med fyrsiffrigt år.',
  'This item was changed by someone else. Reload it and review your changes.':
    'Någon annan har ändrat objektet. Läs in den senaste versionen och granska dina ändringar.',
  'Reload this item before changing it; If-Match is required.':
    'Läs in den senaste versionen innan du ändrar objektet.',
  'File checks are busy. Please try again.':
    'Filkontrollen är upptagen. Försök igen om en stund.',
  'The file is damaged or exceeds the supported document limits.':
    'Filen är skadad eller överskrider dokumentgränserna.',
  'Choose a valid JPG, PNG, GIF, WebP or PDF file.':
    'Välj en giltig JPG-, PNG-, GIF-, WebP- eller PDF-fil.',
  'Story points must be between 0 and 1000.':
    'Uppgiftspoäng måste vara ett heltal mellan 0 och 1000.',
  'Item is already in the recycle bin.':
    'Objektet finns redan i papperskorgen.',
  'Item is not in the recycle bin.': 'Objektet finns inte i papperskorgen.',
  'Restore or unarchive the project first.':
    'Återställ projektet eller ta bort arkiveringen först.',

  'Invalid preference settings.':
    'Kontrollera dina inställningar och försök igen.',
  'Complete all required checklist steps before marking the task Done.':
    'Slutför alla obligatoriska steg innan uppgiften markeras som klar.',
  'Labels must be unique.': 'Varje etikett får bara förekomma en gång.',
  'Checklist steps must have unique IDs.':
    'Checklistans steg måste vara unika. Uppdatera sidan och försök igen.',
  'Comments must be 1000 characters or fewer.':
    'Kommentaren får vara högst 1000 tecken.',
  'You can only edit your own comments.':
    'Du kan bara redigera dina egna kommentarer.',
  'Unarchive this project before making changes.':
    'Återaktivera projektet innan du gör ändringar.',
  'Project is in the recycle bin.':
    'Projektet finns i papperskorgen. Återställ det först.',
  'Item not found.': 'Objektet kunde inte hittas.',
  'Only the project owner can manage deleted projects.':
    'Endast projektägaren kan hantera borttagna projekt.',
  'Move this item to the recycle bin first.':
    'Flytta objektet till papperskorgen först.',
  'Restore the parent story or sprint first.':
    'Återställ den tillhörande berättelsen eller sprinten först.',
  'Choose a valid project template.': 'Välj en giltig projektmall.',
  'Project key must be 2–10 letters A–Z.':
    'Projektnyckeln måste bestå av 2–10 bokstäver mellan A och Z (inte Å, Ä eller Ö).',
  'This project key is already in use.':
    'Projektnyckeln används redan. Välj en annan.',
  'Choose a sprint in this project.': 'Välj en sprint från det här projektet.',
  'Choose a valid retrospective category.':
    'Välj en giltig kategori för retrospektivet.',
  'You can only edit your own retrospective entries.':
    'Du kan bara redigera dina egna retrospektivanteckningar.',
  'A retrospective entry cannot move to another sprint.':
    'En retrospektivanteckning kan inte flyttas till en annan sprint.',
  'This report link is invalid or has expired.':
    'Rapportlänken är ogiltig eller har gått ut.',
  'The reset email could not be sent. Ask the administrator to check the email service and try again.':
    'Återställningsmejlet kunde inte skickas. Be administratören kontrollera e-posttjänsten och försök igen.',
  'Google sign-in is not configured yet.':
    'Google-inloggning är inte konfigurerad ännu.',
  'Google sign-in could not be loaded.':
    'Google-inloggningen kunde inte laddas. Försök igen.',
  'A project with this name already exists.':
    'Det finns redan ett projekt med det här namnet.',
  'Project not found.': 'Projektet finns inte längre. Uppdatera sidan.',
  'Only the project owner can delete this project.':
    'Endast projektägaren kan ta bort projektet.',
  'You do not have access to this project.':
    'Du har inte åtkomst till projektet.',
  'Viewers cannot change this project.':
    'Användare med läsbehörighet kan inte ändra projektet.',
  'Only an owner can manage team members.':
    'Endast projektägaren kan utföra den här åtgärden.',
  'Only an owner or admin can manage project settings and members.':
    'Endast projektägaren eller en administratör kan ändra projektinställningar och medlemmar.',
  'Create an account for this email before inviting it.':
    'Användaren behöver skapa ett konto med den här e-postadressen innan du kan bjuda in personen.',
  'Transfer ownership before removing the owner.':
    'Överför ägarskapet innan du tar bort ägaren.',
  'Choose a project member to receive ownership.':
    'Välj en projektmedlem som ska ta över ägarskapet.',
  'The new owner must have an account.': 'Den nya ägaren måste ha ett konto.',
  'The new owner must already be a project member.':
    'Den nya ägaren måste redan vara medlem i projektet.',
  'Role must be Admin, Member, or Viewer. Transfer project ownership to appoint a new owner.':
    'Välj Administratör, Medlem eller Läsbehörighet. Överför ägarskapet för att utse en ny ägare.',
  'Some information is missing or invalid. Check the form and try again.':
    'Information saknas eller är ogiltig. Kontrollera formuläret och försök igen.',
  'Your session may have expired. Sign in again and retry.':
    'Din session kan ha gått ut. Logga in igen och försök på nytt.',
  'You don’t have permission to do that. Ask a project owner or admin for access.':
    'Du saknar behörighet. Be projektägaren eller en administratör om åtkomst.',
  'We couldn’t find that item. Refresh the page and try again.':
    'Objektet kunde inte hittas. Uppdatera sidan och försök igen.',
  'This change conflicts with newer information. Refresh the page and try again.':
    'Informationen har ändrats. Uppdatera sidan och försök igen.',
  'Too many requests. Wait a moment, then try again.':
    'För många försök. Vänta en stund och försök igen.',
  'SprintFlow couldn’t complete that request right now. Please try again in a moment.':
    'SprintFlow kunde inte slutföra åtgärden. Försök igen om en stund.',
  'We couldn’t complete that request. Please try again.':
    'Åtgärden kunde inte slutföras. Försök igen.',
  'Could not connect to SprintFlow. Check your internet connection and try again.':
    'Det gick inte att ansluta till SprintFlow. Kontrollera anslutningen och försök igen.',
  'Could not load the workspace. Please check the connection and try again.':
    'Arbetsytan kunde inte laddas. Kontrollera anslutningen och försök igen.',
  'Sign in to continue.': 'Logga in för att fortsätta.',
  'Access denied. Refresh the page and try again.':
    'Åtkomst nekad. Uppdatera sidan och försök igen.',
  'Invalid input.': 'Kontrollera uppgifterna i formuläret.',
  'This record conflicts with existing data.':
    'Uppgifterna krockar med information som redan finns. Uppdatera sidan och försök igen.',
  'Email is already registered.':
    'E-postadressen är redan registrerad. Logga in eller återställ lösenordet.',
  'Incorrect email or password.':
    'Fel e-postadress eller lösenord. Skapa ett konto om du inte redan har ett.',
  'We couldn’t sign you in. If you haven’t created an account yet, choose Create account. If you already have an account, check your email and password or reset your password.':
    'Vi kunde inte logga in dig. Om du inte har ett konto ännu, välj Skapa konto. Kontrollera annars e-post och lösenord eller återställ lösenordet.',
  'Current password is incorrect.': 'Ditt nuvarande lösenord är felaktigt.',
  'User not found.': 'Kontot kunde inte hittas.',
  'Email is required.': 'Ange din e-postadress.',
  'Email must be valid.': 'Ange en giltig e-postadress.',
  'Current email is required.': 'Ange din nuvarande e-postadress.',
  'Current email must be valid.': 'Ange en giltig nuvarande e-postadress.',
  'Owner email must be valid.': 'Ägarens e-postadress måste vara giltig.',
  'Name is required.': 'Ange ditt namn.',
  'Name must be 80 characters or fewer.': 'Namnet får vara högst 80 tecken.',
  'Role must be 80 characters or fewer.':
    'Yrkesrollen får vara högst 80 tecken.',
  'About text must be 500 characters or fewer.':
    'Beskrivningen får vara högst 500 tecken.',
  'Password is required.': 'Ange ditt lösenord.',
  'Current password is required.': 'Ange ditt nuvarande lösenord.',
  'New password is required.': 'Ange ett nytt lösenord.',
  'Password must contain at least 6 characters.':
    'Lösenordet måste innehålla minst 6 tecken.',
  'New password must contain at least 6 characters.':
    'Det nya lösenordet måste innehålla minst 6 tecken.',
  'You can only change the password for your signed-in account.':
    'Du kan bara ändra lösenordet för kontot du är inloggad på.',
  'You can only edit your signed-in profile.':
    'Du kan bara redigera profilen för kontot du är inloggad på.',
  'You can only delete your signed-in account.':
    'Du kan bara ta bort kontot du är inloggad på.',
  'Delete or transfer your projects before deleting this account.':
    'Ta bort dina projekt eller överför ägarskapet innan du tar bort kontot.',
  'Profile image is too large.':
    'Profilbilden är för stor. Välj en mindre bild.',
  'Profile image must be a PNG, JPEG, WEBP or GIF file.':
    'Välj en profilbild i PNG-, JPEG-, WebP- eller GIF-format.',
  'This reset link is invalid or has expired.':
    'Återställningslänken är ogiltig eller har gått ut. Begär en ny länk.',
  'Reset link is required.': 'En återställningslänk krävs. Begär en ny länk.',
  'Password-reset emails are temporarily unavailable. The app administrator must configure the email service. Please try again later.':
    'E-post för lösenordsåterställning är inte tillgänglig just nu. Administratören behöver konfigurera e-posttjänsten. Försök igen senare.',
  'Google login is not configured yet.':
    'Google-inloggning är inte konfigurerad ännu.',
  'Google could not verify this account.':
    'Google kunde inte verifiera kontot. Försök igen.',
  'Google credential is required.':
    'Google-inloggningen saknar verifieringsuppgifter. Försök igen.',
  'Recovery code is required.': 'Ange din återställningskod.',
  'Recovery code must contain 6 to 100 characters.':
    'Återställningskoden måste innehålla 6 till 100 tecken.',
  'Email or recovery code is incorrect.':
    'E-postadressen eller återställningskoden är felaktig.',
  'Recovery code is incorrect.': 'Återställningskoden är felaktig.',
  'No recovery code is saved for this account. Log in and add one in Profile.':
    'Kontot saknar återställningskod. Logga in och lägg till en i profilen.',
  'Project name is required.': 'Ange ett projektnamn.',
  'Project is required.': 'Välj ett projekt.',
  'Task title is required.': 'Ange en titel för uppgiften.',
  'Story title is required.': 'Ange en titel för berättelsen.',
  'Sprint name is required.': 'Ange ett namn för sprinten.',
  'Acceptance criteria are required.': 'Ange acceptanskriterier.',
  'Story points cannot be negative.': 'Poängen får inte vara negativa.',
  'Story points cannot exceed 100.': 'Poängen får inte överstiga 100.',
  'Story points must be 1, 2, 3, 5, or 8.': 'Välj 1, 2, 3, 5 eller 8 poäng.',
  'Priority must be between 1 and 3.': 'Välj en prioritet mellan 1 och 3.',
  'Business value must be between 1 and 5.':
    'Affärsvärdet måste vara mellan 1 och 5.',
  'A story cannot move to another project.':
    'En berättelse kan inte flyttas till ett annat projekt.',
  'Choose a sprint in the same project as this story.':
    'Välj en sprint från samma projekt som berättelsen.',
  'Choose an existing project for this task.':
    'Välj ett befintligt projekt för uppgiften.',
  'Choose an existing project for this sprint.':
    'Välj ett befintligt projekt för sprinten.',
  'A task cannot move to another project.':
    'En uppgift kan inte flyttas till ett annat projekt.',
  'A sprint cannot move to another project.':
    'En sprint kan inte flyttas till ett annat projekt.',
  'Provide a reason for blocking this task.':
    'Ange varför uppgiften är blockerad.',
  'Choose a sprint in the same project as this task.':
    'Välj en sprint från samma projekt som uppgiften.',
  'Choose a story in the same project as this task.':
    'Välj en berättelse från samma projekt som uppgiften.',
  'A task must use the same sprint as its user story.':
    'Uppgiften måste tillhöra samma sprint som dess berättelse.',
  'Assign this task to a member of the same project.':
    'Tilldela uppgiften till en medlem i samma projekt.',
  'Status must be To Do, In Progress, Review, or Done.':
    'Välj Att göra, Pågående, Granskning eller Klart.',
  'Status must be Planned, Active, Completed, or Cancelled.':
    'Välj Planerad, Aktiv, Avslutad eller Avbruten.',
  'Sprint start and end dates are required.':
    'Ange sprintens start- och slutdatum.',
  'Sprint end date must be on or after its start date.':
    'Slutdatumet måste vara samma dag som eller efter startdatumet.',
  'Only one sprint can be active in a project at a time.':
    'Endast en sprint kan vara aktiv åt gången i ett projekt.',
  'Comment cannot be empty.': 'Kommentaren får inte vara tom.',
  'Update summary is required.': 'Ange en text för uppdateringen.',
  'Update summary must be 500 characters or fewer.':
    'Uppdateringen får vara högst 500 tecken.',
  'Attachment link must start with https:// or http://.':
    'Länken måste börja med https:// eller http://.',
  'Choose a JPG, PNG, GIF, WebP or PDF file.':
    'Välj en JPG-, PNG-, GIF-, WebP- eller PDF-fil.',
  'File must be between 1 byte and 15 MB.':
    'Filen får inte vara tom och får vara högst 15 MB.',
  'Could not store the attachment.': 'Bilagan kunde inte sparas. Försök igen.',
  'Could not delete project.': 'Projektet kunde inte tas bort. Försök igen.',
  'Could not save project.': 'Projektet kunde inte sparas. Försök igen.',
  'Tasks have waited 24 hours or more in Review.':
    'Uppgifter har väntat på granskning i minst 24 timmar.',
  'Too much work is active at once.': 'För många uppgifter pågår samtidigt.',
  'No current delivery risk was detected.':
    'Ingen aktuell leveransrisk upptäcktes.',
};

export function localizeMessage(message: string, language: Language): string {
  if (language !== 'SV') return message;
  if (swedish[message]) return swedish[message];
  if (
    /^This (project|sprint|story|task|item) was changed by someone else\. Refresh and try again\.$/.test(
      message,
    )
  )
    return 'Informationen har ändrats av någon annan. Uppdatera sidan och försök igen.';
  const blocked = message.match(/^(\d+) blocked task\(s\) need attention\.$/);
  if (blocked) return `${blocked[1]} blockerade uppgifter behöver åtgärdas.`;
  const assignment = message.match(/^You were assigned task “(.+)”\.$/);
  if (assignment) return `Du har tilldelats uppgiften “${assignment[1]}”.`;
  const mention = message.match(/^Mentioned you in a comment on “(.+)”\.$/);
  if (mention) return `Du nämndes i en kommentar på “${mention[1]}”.`;
  const comment = message.match(/^New comment on “(.+)”\.$/);
  if (comment) return `Ny kommentar på “${comment[1]}”.`;
  const reminder = message.match(
    /^Reminder: “(.+)” (is overdue|is due today)\.$/,
  );
  if (reminder)
    return `Påminnelse: “${reminder[1]}” ${reminder[2] === 'is overdue' ? 'är försenad' : 'förfaller idag'}.`;
  return message;
}

const roles = defineLexicon({
  EN: { Owner: 'Owner', Admin: 'Admin', Member: 'Member', Viewer: 'Viewer' },
  SV: {
    Owner: 'Ägare',
    Admin: 'Administratör',
    Member: 'Medlem',
    Viewer: 'Läsbehörighet',
  },
});

export function roleLabel(role: string, language: Language): string {
  return role in roles.EN
    ? translator(roles, language)(role as keyof typeof roles.EN)
    : role;
}
