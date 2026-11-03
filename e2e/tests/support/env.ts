export type VacoEnv = 'local' | 'dev';

const BASE_URLS: Record<VacoEnv, string> = {
  local: 'http://localhost:8080/api',
  dev: 'https://digitraffic-tis-dev.aws.fintraffic.cloud/api',
};

/** Environment under test. `VACO_ENV` selects it. Only `local` and `dev` are allowed. */
export function vacoEnv(): VacoEnv {
  const env = process.env.VACO_ENV ?? 'local';
  if (env !== 'local' && env !== 'dev') {
    throw new Error(`VACO_ENV must be local or dev, got "${env}".`);
  }
  return env;
}

/** Base URL, ending in `/api`. */
export function baseUrl(): string {
  return BASE_URLS[vacoEnv()];
}
