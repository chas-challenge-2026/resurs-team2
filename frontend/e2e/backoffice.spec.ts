import { expect, test, type Page } from "@playwright/test";

// Helper function that logs in as a case worker.
// Used by tests that need to access the backoffice.
async function loginAsCaseWorker(page: Page) {
  await page.goto("/login");

  await page.getByText("Handläggare", { exact: true }).click();

  await page.getByLabel("E-postadress").fill("karin@resurs.se");
  await page.getByLabel("Lösenord").fill("password123");

  await page.getByRole("button", { name: "Logga in" }).click();

  await expect(page).toHaveURL(/\/backoffice/);
}

// Helper function that logs in and opens the first application.
// It waits for the application loading message to disappear before continuing.
async function openFirstApplication(page: Page) {
  await loginAsCaseWorker(page);

  await page
    .getByRole("button", { name: "Granska" })
    .first()
    .click();

  await expect(page).toHaveURL(/\/backoffice\/.+/);

  await expect(
    page.getByText("Laddar ansökan...", { exact: true }),
  ).toBeHidden();
}

// Tests that the backoffice application queue is loaded
// and that the "Handläggarkö" heading is visible.
test("handläggarkön laddas", async ({ page }) => {
  await loginAsCaseWorker(page);

  await expect(
    page.getByRole("heading", { name: "Handläggarkö" }),
  ).toBeVisible();
});

// Tests that the case worker can open an application
// from the backoffice application queue.
test("handläggaren kan öppna en ansökan", async ({ page }) => {
  await loginAsCaseWorker(page);

  // Find the first "Granska" button in the application queue.
  const reviewButton = page
    .getByRole("button", { name: "Granska" })
    .first();

  await expect(reviewButton).toBeVisible();

  await reviewButton.click();

  // Verify that the user is navigated to an application detail page.
  await expect(page).toHaveURL(/\/backoffice\/.+/);
});

// Tests that the application detail page displays
// all the main sections needed by the case worker.
test("detaljsidan visar viktig information", async ({ page }) => {
  await openFirstApplication(page);

  await expect(
    page.getByRole("heading", { name: "Ansökan #1" }),
  ).toBeVisible();

  await expect(
    page.getByText("Företagsuppgifter", { exact: true }),
  ).toBeVisible();

  await expect(
    page.getByText("Kreditdetaljer", { exact: true }),
  ).toBeVisible();

  await expect(
    page.getByText("Scoringresultat", { exact: true }),
  ).toBeVisible();

  await expect(
    page.getByText("Fatta beslut", { exact: true }),
  ).toBeVisible();

  await expect(
    page.getByText("Uppladdade dokument", { exact: true }),
  ).toBeVisible();

  await expect(
    page.getByText("Händelselogg", { exact: true }),
  ).toBeVisible();
});

// Tests that the case worker can expand
// and view the financial information section.
test("handläggaren kan visa finansiella uppgifter", async ({ page }) => {
  await openFirstApplication(page);

  const financialButton = page.getByRole("button", {
    name: "Visa finansiella uppgifter",
  });

  await expect(financialButton).toBeVisible();

  await financialButton.click();

  // Verify that the financial information section is displayed.
  await expect(
    page.getByText("Finansiella nyckeltal", { exact: true }),
  ).toBeVisible();
});

// Tests that the expected financial values
// are displayed when the financial information is expanded.
test("visar finansiella värden", async ({ page }) => {
  await openFirstApplication(page);

  await page
    .getByRole("button", { name: "Visa finansiella uppgifter" })
    .click();

  // Find all number inputs containing the financial values.
  const inputs = page.locator('input[type="number"]');

  // Verify that all seven expected financial fields are displayed.
  await expect(inputs).toHaveCount(7);

  await expect(inputs.nth(0)).toHaveValue("2500000");
  await expect(inputs.nth(1)).toHaveValue("5000000");
  await expect(inputs.nth(2)).toHaveValue("1200000");
  await expect(inputs.nth(3)).toHaveValue("800000");
  await expect(inputs.nth(4)).toHaveValue("2500000");
  await expect(inputs.nth(5)).toHaveValue("700000");
  await expect(inputs.nth(6)).toHaveValue("8000000");
});

// Tests that the financial values are read-only
// and cannot be edited by the case worker.
test("finansiella uppgifter är skrivskyddade", async ({ page }) => {
  await openFirstApplication(page);

  await page
    .getByRole("button", { name: "Visa finansiella uppgifter" })
    .click();

  const inputs = page.locator('input[type="number"]');

  await expect(inputs).toHaveCount(7);

  // Verify that every financial input has the readonly attribute.
  for (let i = 0; i < 7; i++) {
    await expect(inputs.nth(i)).toHaveAttribute("readonly", "");
  }
});

// Tests that the case worker can collapse
// and hide the financial information section.
test("handläggaren kan dölja finansiella uppgifter", async ({ page }) => {
  await openFirstApplication(page);

  await page
    .getByRole("button", { name: "Visa finansiella uppgifter" })
    .click();

  await expect(
    page.getByText("Finansiella nyckeltal", { exact: true }),
  ).toBeVisible();

  // Click the button used to hide the financial information.
  await page
    .getByRole("button", { name: "Dölj finansiella uppgifter" })
    .click();

  // Verify that the financial information is no longer visible.
  await expect(
    page.getByText("Finansiella nyckeltal", { exact: true }),
  ).toBeHidden();
});

// Tests that the case worker can click the approve button.
// This test currently verifies that the approval action can be triggered.
test("handläggaren kan godkänna en ansökan", async ({ page }) => {
  await openFirstApplication(page);

  const approveButton = page.getByRole("button", { name: "Godkänn" });

  await expect(approveButton).toBeVisible();

  await approveButton.click();
});

// Tests that the uploaded documents section
// is visible and displays the expected empty state.
test("handläggaren kan se uppladdade dokument", async ({ page }) => {
  await openFirstApplication(page);

  await expect(
    page.getByText("Uppladdade dokument", { exact: true }),
  ).toBeVisible();

  // Verify that the application currently has no uploaded documents.
  await expect(
    page.getByText("Inga dokument.", { exact: true }),
  ).toBeVisible();
});

// Tests that the event log is visible
// and contains the expected application events.
test("handläggaren kan se händelseloggen", async ({ page }) => {
  await openFirstApplication(page);

  await expect(
    page.getByText("Händelselogg", { exact: true }),
  ).toBeVisible();

  // Verify that the main expected events are displayed.
  await expect(
    page.getByText("Ansökan skapades", { exact: true }),
  ).toBeVisible();

  await expect(
    page.getByText("Automatisk scoring genomfördes", { exact: true }),
  ).toBeVisible();

  await expect(
    page.getByText("Worker assigned", { exact: true }),
  ).toBeVisible();
});

// Tests that the event log contains detailed information
// about the application, scoring result, and assigned worker.
test("händelseloggen visar detaljer", async ({ page }) => {
  await openFirstApplication(page);

  const eventLog = page.getByText("Händelselogg", { exact: true });

  await expect(eventLog).toBeVisible();

  // Verify that the organization number is displayed in the event log.
  await expect(
    page.getByText("556000-1234", { exact: true }),
  ).toBeVisible();

  // Verify that the scoring result is displayed.
  await expect(
    page.getByText("REVIEW", { exact: true }),
  ).toBeVisible();

  // Verify that the assigned case worker is displayed.
  await expect(
    page.getByText("Karin Handläggare", { exact: true }),
  ).toBeVisible();
});

// Tests that entering text in the comment field
// updates the character counter.
test("kommentarsfältet uppdaterar teckenräknaren", async ({ page }) => {
  await openFirstApplication(page);

  const commentField = page.getByLabel("Kommentar");

  await expect(commentField).toBeVisible();

  // Verify the initial character count.
  await expect(
    page.getByText("0/500", { exact: true }),
  ).toBeVisible();

  await commentField.fill("Testkommentar");

  // Verify that the counter updates to match the entered text.
  await expect(
    page.getByText("13/500", { exact: true }),
  ).toBeVisible();
});

// Tests that the comment field limits the input
// to a maximum of 500 characters.
test("kommentarsfältet begränsas till 500 tecken", async ({ page }) => {
  await openFirstApplication(page);

  const commentField = page.getByLabel("Kommentar");

  await expect(commentField).toBeVisible();

  // Try to enter 600 characters.
  await commentField.fill("A".repeat(600));

  // Verify that only 500 characters are accepted.
  await expect(commentField).toHaveValue("A".repeat(500));

  // Verify that the character counter displays the maximum length.
  await expect(
    page.getByText("500/500", { exact: true }),
  ).toBeVisible();
});

// Tests that the case worker can log out
// and is redirected back to the login page.
test("handläggaren kan logga ut", async ({ page }) => {
  await loginAsCaseWorker(page);

  const logoutButton = page.getByRole("button", { name: "Logga ut" });

  await expect(logoutButton).toBeVisible();

  await logoutButton.click();

  // Verify that the user is redirected to the login page.
  await expect(page).toHaveURL(/\/login/);
});