import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';

/**
 * Reads the Trivore client-credentials secrets from the VACO dev account.
 * Both secrets have the keys client-id, client-secret, grant-type, id-host and scope.
 */

export interface TrivoreClientCredentials {
  clientId: string;
  clientSecret: string;
  grantType: string;
  idHost: string;
  scope: string;
}

export const ALLOWLISTED_SECRET_ID = '/test-automation/credentials';
export const NOT_ALLOWLISTED_SECRET_ID = '/test-automation/credentials-not-allowlisted';

let secretsClient: SecretsManagerClient | undefined;

function client(): SecretsManagerClient {
  secretsClient ??= new SecretsManagerClient({ region: 'eu-north-1' });
  return secretsClient;
}

export async function getTrivoreClientCredentials(secretId: string): Promise<TrivoreClientCredentials> {
  const response = await client().send(new GetSecretValueCommand({ SecretId: secretId }));
  if (!response.SecretString) {
    throw new Error(`Secret "${secretId}" has no SecretString value.`);
  }
  const secret = JSON.parse(response.SecretString) as Record<string, string>;
  for (const field of ['client-id', 'client-secret', 'grant-type', 'id-host', 'scope']) {
    if (!secret[field]) {
      throw new Error(`Secret "${secretId}" is missing required field "${field}".`);
    }
  }
  return {
    clientId: secret['client-id'],
    clientSecret: secret['client-secret'],
    grantType: secret['grant-type'],
    idHost: secret['id-host'],
    scope: secret['scope'],
  };
}
