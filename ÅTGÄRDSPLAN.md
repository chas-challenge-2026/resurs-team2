# Åtgärdsplan

## Feedaback från kund

### Vem får ändra kreditvärderingen? + Logga ändringar av kreditvärderingar

Dessa ändringarna är filbaserade och kräver att man har tillgång till servern.

För att logga ändringar krävs att en admin panel skapas och att värdena hamnar i databasen. Man kan även hasha filen och spara i databasen för att kolla efter manipulation.

Detta är ett Devops ärende och inte inom scope för projektet.

### Vem får signera en ansökan? Verifiera att den personen som signerar faktiskt har behörighet

Har lagt till att personnummer ska anges vid inloggning. Detta är en “dummy” funktion som kollar att personnr får signera åt org nr. Kan kopplas upp måt riktig implementation senare.

### Exponera inte handläggare för kunden

Handläggarinformationen har tagits bort from responses.

### Gör separationen mellan handläggarens dashboard och kundens dashboard mer tydlig

Frontenden har uppdaterats för att vara ännu tydligare.

### Förtydliga hur/vad som händer ifall fel person loggar in

Problemet förstås inte utan är troligen bara en missuppfattning.
