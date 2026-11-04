import {
  ALLOWLISTED_SECRET_ID,
  NOT_ALLOWLISTED_SECRET_ID,
  getTrivoreClientCredentials,
  type TrivoreClientCredentials,
} from './secrets';

/** Performs the client_credentials grant against the Trivore token endpoint. */
export async function requestAccessToken(creds: TrivoreClientCredentials): Promise<string> {
  const body = new URLSearchParams({
    grant_type: creds.grantType,
    client_id: creds.clientId,
    client_secret: creds.clientSecret,
    scope: creds.scope,
  });

  const response = await fetch(`https://${creds.idHost}/openid/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  const payload = (await response.json().catch(() => undefined)) as
    | { access_token?: string }
    | undefined;

  if (!response.ok || !payload?.access_token) {
    // Never include the request body: it holds the client secret.
    throw new Error(`Trivore token request failed (status ${response.status}).`);
  }
  return payload.access_token;
}

async function tokenOrOverride(overrideName: string, secretId: string): Promise<string> {
  const override = process.env[overrideName];
  if (override) {
    return override;
  }
  return requestAccessToken(await getTrivoreClientCredentials(secretId));
}

/** Token of the allowlisted test-automation client. `TRIVORE_ACCESS_TOKEN` overrides it. */
export function getAllowlistedToken(): Promise<string> {
  return tokenOrOverride('TRIVORE_ACCESS_TOKEN', ALLOWLISTED_SECRET_ID);
}

/** Token of the client that is on no allowlist. `TRIVORE_ACCESS_TOKEN_NOT_ALLOWLISTED` overrides it. */
export function getNotAllowlistedToken(): Promise<string> {
  return tokenOrOverride('TRIVORE_ACCESS_TOKEN_NOT_ALLOWLISTED', NOT_ALLOWLISTED_SECRET_ID);
}
