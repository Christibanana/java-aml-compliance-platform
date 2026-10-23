import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const screenshots = resolve(process.cwd(), '../docs/optimization/screenshots');
const unique = () => randomUUID().replaceAll('-', '').slice(0, 16);

async function login(page: Page) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: '用户名' }).fill(process.env.AML_E2E_USERNAME!);
  await page.locator('input[type="password"]').fill(process.env.AML_E2E_PASSWORD!);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('heading', { name: 'AML 统计仪表盘' })).toBeVisible();
}

async function capture(page: Page, name: string) {
  const path = resolve(screenshots, name);
  mkdirSync(dirname(path), { recursive: true });
  await page.screenshot({ path, fullPage: true, animations: 'disabled' });
}

async function createSyntheticAccount(request: APIRequestContext, token: string) {
  const id = unique();
  const headers = { Authorization: `Bearer ${token}` };
  const customerNo = `E2E-C-${id}`;
  const accountNo = `E2E-A-${id}`;
  const customerName = `Synthetic E2E ${id}`;
  const customer = await request.post('/api/customers', {
    headers,
    data: { customerNo, customerType: 'INDIVIDUAL', fullName: customerName,
      countryCode: 'CN', riskLevel: 'LOW' },
  });
  expect(customer.ok(), await customer.text()).toBeTruthy();
  const customerId = (await customer.json()).id as string;
  const account = await request.post('/api/accounts', {
    headers,
    data: { accountNo, currency: 'CNY', customerId, relationType: 'OWNER', ownershipPercent: 100 },
  });
  expect(account.ok(), await account.text()).toBeTruthy();
  return { customerNo, customerName, accountNo };
}

test('登录、进入仪表盘、退出登录', async ({ page }) => {
  await login(page);
  await expect(page.getByText('所有指标均由后端实时查询数据库')).toBeVisible();
  await capture(page, 'frontend-e2e-login-dashboard.png');
  await page.getByRole('button', { name: '退出' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => localStorage.getItem('aml_token'))).toBeNull();
});

test('创建客户、创建账户、查询关联信息', async ({ page }) => {
  const id = unique();
  const customerNo = `E2E-C-${id}`;
  const accountNo = `E2E-A-${id}`;
  const customerName = `Synthetic E2E ${id}`;
  await login(page);
  await page.getByRole('menuitem', { name: '客户管理' }).click();
  await page.getByRole('button', { name: '新增客户' }).click();
  const customerDialog = page.getByRole('dialog', { name: '新增客户' });
  await customerDialog.getByRole('textbox', { name: '客户编号' }).fill(customerNo);
  await customerDialog.getByRole('textbox', { name: '客户名称' }).fill(customerName);
  await customerDialog.getByRole('button', { name: '保存' }).click();
  await expect(customerDialog).toBeHidden();
  await page.getByPlaceholder('客户编号或名称').fill(customerNo);
  await page.getByRole('button', { name: '查询' }).click();
  const customerRow = page.getByRole('row').filter({ hasText: customerNo });
  await expect(customerRow).toContainText(customerName);
  await customerRow.getByRole('button', { name: '查看账户' }).click();
  await expect(page.getByRole('heading', { name: '账户管理' })).toBeVisible();
  await page.getByRole('button', { name: '新增账户' }).click();
  const accountDialog = page.getByRole('dialog', { name: '新增账户' });
  await accountDialog.getByRole('textbox', { name: '账号' }).fill(accountNo);
  await accountDialog.getByRole('button', { name: '保存' }).click();
  await expect(accountDialog).toBeHidden();
  const accountRow = page.getByRole('row').filter({ hasText: accountNo });
  await expect(accountRow).toContainText(customerName);
  await expect(accountRow).toContainText('OWNER');
  await capture(page, 'frontend-e2e-customer-account.png');
});

test('导入模拟交易、执行 R001、生成并查看预警', async ({ page, request }) => {
  await login(page);
  const token = await page.evaluate(() => localStorage.getItem('aml_token'));
  expect(token).toBeTruthy();
  const { customerNo, customerName, accountNo } = await createSyntheticAccount(request, token!);
  const rulesResponse = await request.get('/api/rules', { headers: { Authorization: `Bearer ${token}` } });
  expect(rulesResponse.ok()).toBeTruthy();
  const rules = await rulesResponse.json() as Array<{ code: string; enabled: boolean; parameters: { amountThreshold?: number } }>;
  const r001 = rules.find(rule => rule.code === 'AML-R001');
  expect(r001?.enabled).toBe(true);
  const threshold = Number(r001?.parameters.amountThreshold);
  expect(threshold).toBeGreaterThan(0);
  expect(threshold).toBeLessThan(9_999_999);
  const externalId = `E2E-T-${unique()}`;
  const amount = (threshold + 1).toFixed(2);
  const csv = `externalId,accountNo,counterpartyAccount,direction,amount,currency,transactionTime,transactionType,description\n${externalId},${accountNo},SYNTHETIC-COUNTERPARTY,IN,${amount},CNY,${new Date().toISOString()},TRANSFER,E2E synthetic only\n`;

  await page.getByRole('menuitem', { name: '交易流水' }).click();
  await page.locator('input[type="file"]').setInputFiles({
    name: 'e2e-synthetic-transactions.csv', mimeType: 'text/csv', buffer: Buffer.from(csv, 'utf8'),
  });
  await page.getByRole('button', { name: '执行导入' }).click();
  await expect(page.locator('.import-result')).toContainText('成功 1');
  await expect(page.getByRole('row').filter({ hasText: externalId })).toBeVisible();

  await page.getByRole('menuitem', { name: '规则管理' }).click();
  for (const code of ['AML-R002', 'AML-R003']) {
    const row = page.getByRole('row').filter({ hasText: code });
    await row.getByRole('button', { name: '停用' }).click();
    await expect(page.getByRole('row').filter({ hasText: code }).getByRole('button', { name: '启用' })).toBeVisible();
  }
  const execution = page.waitForResponse(response => response.url().endsWith('/api/rules/execute') && response.request().method() === 'POST');
  await page.getByRole('button', { name: '执行规则检测' }).click();
  const executionResponse = await execution;
  expect(executionResponse.status()).toBe(200);
  const result = await executionResponse.json() as { rules: Array<{ code: string; newHits: number }> };
  expect(result.rules.find(rule => rule.code === 'AML-R001')?.newHits).toBeGreaterThanOrEqual(1);

  await page.getByRole('menuitem', { name: '预警中心' }).click();
  await page.getByPlaceholder('预警号 / 客户').fill(customerNo);
  await page.getByRole('button', { name: '查询' }).click();
  const alertRow = page.getByRole('row').filter({ hasText: 'AML-R001' });
  await expect(alertRow).toContainText(customerName);
  await alertRow.click();
  const drawer = page.getByRole('dialog', { name: '预警审核' });
  await expect(drawer).toContainText(externalId);
  await page.waitForTimeout(500);
  await capture(page, 'frontend-e2e-r001-alert.png');
});
