import { expect, test } from "@playwright/test";

test("startsidan laddas", async ({ page }) => {
  await page.goto("/");

  await page.getByRole('link', { name: "Logga in" }).click();

  await expect(page).toHaveURL(/login/);

  await page.goBack();
  
  await expect(page).toHaveURL("/");

  await page.getByRole("button", { name: "Kom igång med företagskredit"}).click();

  await expect(page).toHaveURL(/login/);

  await page.getByLabel('Organisationsnummer').fill("556000-1234");

  await expect(page.getByLabel("Organisationsnummer")).toHaveValue("556000-1234");

  await page.getByRole("button", { name: "Logga in med BankID"}).click();

  await expect(page).toHaveURL(/dashboard/);

  await page.getByRole("button", { name: "Visa"}).first().click();

  await expect(page).toHaveURL(/status/);

  await page.getByRole("button", { name: "Ladda upp"}).click();

  await expect(page).toHaveURL(/documents/);

  await page.getByRole("button", { name: "Tillbaka till ansökan"}).click();

  await expect(page).toHaveURL(/application/);

  await page.getByRole("button", { name: "Visa"}).first().click();

  await expect(page).toHaveURL(/status/);

  await page.goBack();

  await page.getByRole("button", { name: "Dokument"}).first().click();

  await expect(page).toHaveURL(/documents/);

  await page.goBack();

  await page.getByRole("button", { name: "Ny ansökan"}).click();

  await expect(page).toHaveURL(/apply/);

  await page.getByRole("button", { name: "Nästa"}).click();

  await page.getByRole("button", { name: "Tillbaka"}).click();

  await page.getByRole("button", { name: "Nästa"}).click();

  await page.getByLabel('Eget kapital (SEK)').fill("500000");

  await expect(page.getByLabel("Eget kapital (SEK)")).toHaveValue("500000");

  await page.getByLabel('Totalt kapital (SEK)').fill("500000");

  await expect(page.getByLabel("Totalt kapital (SEK)")).toHaveValue("500000");

  await page.getByLabel('Omsättningstillgångar (SEK)').fill("500000");

  await expect(page.getByLabel("Omsättningstillgångar (SEK)")).toHaveValue("500000");

  await page.getByLabel('Kortfristiga skulder (SEK)').fill("500000");

  await expect(page.getByLabel("Kortfristiga skulder (SEK)")).toHaveValue("500000");

  await page.getByLabel('Totala skulder (SEK)').fill("500000");

  await expect(page.getByLabel("Totala skulder (SEK)")).toHaveValue("500000");

  await page.getByLabel('Rörelseresultat (SEK)').fill("500000");

  await expect(page.getByLabel("Rörelseresultat (SEK)")).toHaveValue("500000");

  await page.getByLabel('Nettoomsättning (SEK)').fill("5000000");

  await expect(page.getByLabel("Nettoomsättning (SEK)")).toHaveValue("5000000");

  await page.getByRole("button", { name: "Nästa"}).click();

  await page.getByLabel('Önskat kreditbelopp (SEK)').fill("5000000");

  await expect(page.getByLabel("Önskat kreditbelopp (SEK)")).toHaveValue("5000000");

  await page.getByLabel('Syfte med krediten').fill("Jag ska köpa wingar för pengarna!");

  await expect(page.getByLabel("Syfte med krediten")).toHaveValue("Jag ska köpa wingar för pengarna!");

  await page.getByRole("button", { name: "Granska ansökan"}).click();

  await page.getByLabel('Jag intygar att de finansiella uppgifterna är korrekta och hämtade från senaste årsredovisningen.').click();

  await page.getByRole("button", { name: "Skicka in ansökan"}).click();

  await expect(page).toHaveURL(/status/);

  await page.getByRole('link' , {name: 'Startsida'}).click();

  await expect(page).toHaveURL(/dashboard/);

  await page.getByRole('button', { name: 'Logga ut'}).click();

  await expect(page).toHaveURL(/login/);
});