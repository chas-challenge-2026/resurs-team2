import { StrictMode, useContext } from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

import { authApi } from "../api/authApi";
import { AuthProvider } from "./AuthProvider";
import { AuthContext } from "./AuthContext";

vi.mock("../api/authApi", () => ({
  authApi: {
    me: vi.fn(),
    getCurrentCompany: vi.fn(),
    loginCompany: vi.fn(),
    loginCaseWorker: vi.fn(),
    logout: vi.fn(),
  },
}));

const companyResponse = {
  orgNumber: "556000-1234",
  name: "Malmö Fastigheter AB",
};

function Probe() {
  const { user, isLoading } = useContext(AuthContext)!;

  return (
    <div>
      <span data-testid="user">{user?.name ?? "none"}</span>
      <span data-testid="loading">{isLoading ? "loading" : "loaded"}</span>
    </div>
  );
}

describe("AuthProvider session restore on page reload", () => {
  let container: HTMLDivElement;
  let root: Root;

  const renderApp = async () => {
    await act(async () => {
      root = createRoot(container);
      root.render(
        <StrictMode>
          <AuthProvider>
            <Probe />
          </AuthProvider>
        </StrictMode>,
      );
    });
  };

  const flush = async () => {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  };

  beforeEach(() => {
    (
      globalThis as Record<string, unknown>
    ).IS_REACT_ACT_ENVIRONMENT = true;

    document.body.innerHTML = "";
    container = document.createElement("div");
    document.body.appendChild(container);

    vi.unstubAllGlobals();
    vi.mocked(authApi.me).mockReset();
    vi.mocked(authApi.getCurrentCompany).mockReset();
  });

  afterEach(() => {
    root?.unmount();
  });

  it("restores a company session on page reload", async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      role: "COMPANY",
      name: "Malmö Fastigheter AB",
    });

    vi.mocked(authApi.getCurrentCompany).mockResolvedValue(companyResponse);

    await renderApp();
    await flush();

    expect(authApi.me).toHaveBeenCalled();
    expect(authApi.getCurrentCompany).toHaveBeenCalled();

    expect(container.textContent).toContain("Malmö Fastigheter AB");
    expect(container.textContent).toContain("loaded");
  });

  it("restores a case worker session on page reload", async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      role: "CASEWORKER",
      name: "Karin Handläggare",
    });

    await renderApp();
    await flush();

    expect(authApi.me).toHaveBeenCalled();
    expect(authApi.getCurrentCompany).not.toHaveBeenCalled();

    expect(container.textContent).toContain("Karin Handläggare");
    expect(container.textContent).toContain("loaded");
  });

  it("clears the session when no active session exists", async () => {
    vi.mocked(authApi.me).mockRejectedValue(
      new Error("Ingen aktiv session."),
    );

    await renderApp();
    await flush();

    expect(authApi.me).toHaveBeenCalled();
    expect(authApi.getCurrentCompany).not.toHaveBeenCalled();

    expect(container.textContent).toContain("none");
    expect(container.textContent).toContain("loaded");
  });
});