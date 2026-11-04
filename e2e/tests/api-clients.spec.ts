import { test, expect, type APIRequestContext, request as playwrightRequest } from '@playwright/test';
import { baseUrl, vacoEnv } from './support/env';
import { getAllowlistedToken, getNotAllowlistedToken } from './support/trivore-auth';

const BUSINESS_ID = '9999999-2';
const FINTRAFFIC_BUSINESS_ID = '2942108-7';
const FINAL_STATES = ['success', 'warnings', 'errors', 'failed', 'cancelled'];
const POLL_INTERVAL_MS = 10_000;
const POLL_LIMIT_MS = 10 * 60_000;
const TEARDOWN_LIMIT_MS = 5 * 60_000;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

interface EntryResource {
  data: { publicId: string; status: string };
}

test.describe.serial('VACO API with Trivore ID client credentials', () => {
  let api: APIRequestContext;
  let authHeaders: Record<string, string>;
  let publicId: string;

  test.beforeAll(async () => {
    test.setTimeout(120_000);
    const token = await getAllowlistedToken();
    authHeaders = { Authorization: `Bearer ${token}` };
    api = await playwrightRequest.newContext({ baseURL: `${baseUrl()}/` });

    // A company left by a crashed run is removed first. 404 means there is none.
    const removed = await api.delete(`ui/admin/companies/${BUSINESS_ID}`, { headers: authHeaders });
    expect([204, 404]).toContain(removed.status());

    const created = await api.post('ui/admin/companies', {
      headers: authHeaders,
      data: { businessId: BUSINESS_ID, name: 'VACO E2E', publish: false },
    });
    expect(created.status()).toBe(200);

    // A company gets the generic rulesets only through a partnership with the company that owns them.
    // Fintraffic owns them, so it is partner A (parent) and the test company is partner B (child).
    // The company delete removes the partnership too.
    const partnership = await api.post('ui/admin/partnership', {
      headers: authHeaders,
      data: { partnerABusinessId: FINTRAFFIC_BUSINESS_ID, partnerBBusinessId: BUSINESS_ID },
    });
    expect(partnership.status()).toBe(200);
  });

  test.afterAll(async () => {
    test.setTimeout(TEARDOWN_LIMIT_MS + 60_000);
    const deadline = Date.now() + TEARDOWN_LIMIT_MS;
    let status = 0;
    while (Date.now() < deadline) {
      const response = await api.delete(`ui/admin/companies/${BUSINESS_ID}`, { headers: authHeaders });
      status = response.status();
      // 409 means entries are still processing. Wait and try again.
      if (status !== 409) {
        break;
      }
      await sleep(POLL_INTERVAL_MS);
    }
    expect(status).toBe(204);
    await api.dispose();
  });

  test('spec 1: an allowlisted client submits an entry, reads it and downloads a package', async () => {
    test.setTimeout(POLL_LIMIT_MS + 120_000);

    const created = await api.post('queue', {
      headers: authHeaders,
      data: {
        format: 'gtfs',
        name: 'vaco-e2e',
        url: `${baseUrl()}/static/testfiles/vaco-e2e-gtfs.zip`,
        businessId: BUSINESS_ID,
        validations: [{ name: 'gtfs.canonical' }],
        sendNotifications: false,
      },
    });
    expect(created.status()).toBe(200);
    publicId = ((await created.json()) as EntryResource).data.publicId;

    const deadline = Date.now() + POLL_LIMIT_MS;
    let status = '';
    while (Date.now() < deadline) {
      const entry = await api.get(`queue/${publicId}`, { headers: authHeaders });
      expect(entry.status()).toBe(200);
      status = ((await entry.json()) as EntryResource).data.status.toLowerCase();
      if (FINAL_STATES.includes(status)) {
        break;
      }
      await sleep(POLL_INTERVAL_MS);
    }
    expect(FINAL_STATES).toContain(status);
    if (vacoEnv() === 'local') {
      // A local VACO has no rule runner, so the entry does not finish as success or warnings.
      // There is therefore no package to download.
      // The processing result is asserted in dev only.
      return;
    }
    expect(['success', 'warnings']).toContain(status);

    const download = await api.get(`packages/${publicId}/prepare.download/result`, { headers: authHeaders });
    expect(download.status()).toBe(200);
    const body = await download.body();
    // A ZIP file starts with the bytes "PK".
    expect(body.subarray(0, 2).toString('latin1')).toBe('PK');
  });

  test('spec 2: a client that is not on the allowlist gets 401', async () => {
    const token = await getNotAllowlistedToken();
    const response = await api.get(`queue/${publicId ?? 'unknown'}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(401);
  });

  test('spec 3: a call without a token gets 401', async () => {
    const response = await api.get(`queue/${publicId ?? 'unknown'}`);
    expect(response.status()).toBe(401);
  });

  test('spec 4: the badge is public and returns an SVG', async () => {
    const response = await api.get(`badge/${publicId ?? 'unknown'}`);
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('image/svg+xml');
  });
});
