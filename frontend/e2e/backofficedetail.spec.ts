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
// It also waits for the loading message to disappear before continuing.
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

// Tests that the loading message is displayed while the application
// is being fetched from the API.
// The API request is kept pending so we can reliably check the loading state.
test("detaljsidan visar laddningsstatus", async ({ page }) => {
  await loginAsCaseWorker(page);

  await page.route("**/api/v1/applications/*", async () => {
    await new Promise(() => {});
  });

  await page
    .getByRole("button", { name: "Granska" })
    .first()
    .click();

  await expect(page).toHaveURL(/\/backoffice\/.+/);

  await expect(
    page.getByText("Laddar ansökan...", { exact: true }),
  ).toBeVisible();
});

// Tests that the correct error message is displayed when the application
// cannot be fetched from the API.
// A HTTP 500 error is simulated to represent a server-side failure.
test("detaljsidan visar felmeddelande om ansökan inte kan hämtas", async ({
  page,
}) => {
  await loginAsCaseWorker(page);

  await page.route("**/api/v1/applications/*", async (route) => {
    await route.fulfill({
      status: 500,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Server error",
      }),
    });
  });

  await page
    .getByRole("button", { name: "Granska" })
    .first()
    .click();

  await expect(page).toHaveURL(/\/backoffice\/.+/);

  // Check that the error-state heading is displayed.
  await expect(
    page.getByText("Kunde inte hämta ansökan", { exact: true }),
  ).toBeVisible();

  // Check that the error message returned by applicationApi is displayed.
  await expect(
    page.getByText("Kunde inte hämta ansökan.", { exact: true }),
  ).toBeVisible();
});

// Tests that the case worker can use the back button
// to return to the backoffice application queue.
test("handläggaren kan gå tillbaka till handläggarkön", async ({
  page,
}) => {
  await openFirstApplication(page);

  // Find the specific back button on the application detail page.
  const backButton = page.getByRole("button", {
    name: "← Handläggarkö",
  });

  await expect(backButton).toBeVisible();

  await backButton.click();

  // Verify that the user is navigated back to the application queue.
  await expect(page).toHaveURL(/\/backoffice$/);
});

// Tests that the application detail page displays
// the correct application heading and application ID.
test("detaljsidan visar ansökningsrubriken", async ({ page }) => {
  await openFirstApplication(page);

  await expect(
    page.getByRole("heading", { name: "Ansökan #1" }),
  ).toBeVisible();
});

// Tests that the application detail page displays
// the current status of the application.
test("detaljsidan visar ansökans status", async ({ page }) => {
  await openFirstApplication(page);

  await expect(
    page.getByText("Under granskning", { exact: true }).first(),
  ).toBeVisible();
});