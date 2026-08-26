/**
 * mock 本地持久化目录（`.local/`）。
 * 存放开发态密钥/会话等，默认 gitignore，避免 Nitro 热重载后密钥刷新迫使前端重登。
 */

import { mkdirSync } from "node:fs";
import { dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/** backend-mock-template 包根目录 */
export const MOCK_PACKAGE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const DEFAULT_LOCAL_DIR = resolve(MOCK_PACKAGE_ROOT, ".local");

/** 解析本地数据目录；可用 SECURITY_LOCAL_DATA_DIR 覆盖（测试用）。 */
export function getLocalDataDir(): string {
  const raw = process.env.SECURITY_LOCAL_DATA_DIR?.trim();
  if (raw) {
    return isAbsolute(raw) ? raw : resolve(process.cwd(), raw);
  }
  return DEFAULT_LOCAL_DIR;
}

/** 确保目录存在。 */
export function ensureLocalDataDir(dir: string = getLocalDataDir()): string {
  mkdirSync(dir, { recursive: true });
  return dir;
}
