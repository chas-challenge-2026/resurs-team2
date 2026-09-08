const ACCESS_TOKEN_KEY = "accessToken";

export async function apiFetch(
  url: string,
  options: RequestInit = {},
): Promise<Response> {
  const accessToken = sessionStorage.getItem(ACCESS_TOKEN_KEY);

  const headers = new Headers(options.headers);

  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  return fetch(url, {
    ...options,
    headers,
  });
}
