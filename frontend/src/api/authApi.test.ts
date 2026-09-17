import { authApi } from "./authApi";

/**
 * The backend serializes the Spring-security role name verbatim, i.e.
 * "CASE_WORKER" for case workers. The frontend models roles as
 * "COMPANY" | "CASEWORKER" — a mismatch here made the session restore on page
 * reload fall through to clearSession() and log case workers out (the company
 * path worked by coincidence, since both sides spell "COMPANY").
 */
describe("authApi role normalization", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const stubFetch = (body: unknown) =>
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => body,
      } as unknown as Response),
    );

  it("maps a case worker refresh response to the frontend CASEWORKER role", async () => {
    stubFetch({
      accessToken: "access-1",
      refreshToken: "refresh-2",
      role: "CASE_WORKER",
      name: "Karin Handläggare",
    });

    const tokens = await authApi.refresh("whatever");

    expect(tokens.role).toBe("CASEWORKER");
    expect(tokens.role).not.toBe("CASE_WORKER");
    expect(tokens.name).toBe("Karin Handläggare");
  });

  it("leaves the COMPANY role untouched", async () => {
    stubFetch({
      accessToken: "access-1",
      refreshToken: "refresh-2",
      role: "COMPANY",
      name: "Malmö Fastigheter AB",
    });

    const tokens = await authApi.refresh("whatever");

    expect(tokens.role).toBe("COMPANY");
  });

  it("normalizes case worker logins too", async () => {
    stubFetch({
      accessToken: "access-1",
      refreshToken: "refresh-2",
      role: "CASE_WORKER",
      name: "Karin Handläggare",
    });

    const tokens = await authApi.loginCaseWorker({
      email: "karin@resurs.se",
      password: "password123",
    });

    expect(tokens.role).toBe("CASEWORKER");
  });
});