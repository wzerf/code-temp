/**
 * 本地密钥 / 会话落盘：热重载后复用，避免前端 1006 被迫重登。
 */

import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, beforeEach, describe, expect, it } from "vite-plus/test";

import {
  createSession,
  getSessionPrivateKeyPem,
  resetSessionsForTest,
  revokeSession,
} from "../utils/session-utils";
import {
  getEncryptKeyPair,
  getRsaKeyFilePath,
  setEncryptKeyPairForTest,
} from "../utils/security/keys";

const ENV_KEYS = [
  "SECURITY_LOCAL_DATA_DIR",
  "SECURITY_RSA_KEY_FILE",
  "SECURITY_RSA_PUBLIC_KEY",
  "SECURITY_RSA_PRIVATE_KEY",
] as const;

describe("local persist (rsa + sessions)", () => {
  const savedEnv: Record<string, string | undefined> = {};
  let tempDir = "";

  beforeEach(() => {
    for (const k of ENV_KEYS) {
      savedEnv[k] = process.env[k];
      delete process.env[k];
    }
    tempDir = mkdtempSync(join(tmpdir(), "mock-local-"));
    process.env.SECURITY_LOCAL_DATA_DIR = tempDir;
    setEncryptKeyPairForTest(null);
    resetSessionsForTest();
  });

  afterEach(() => {
    setEncryptKeyPairForTest(null);
    resetSessionsForTest();
    for (const k of ENV_KEYS) {
      if (savedEnv[k] === undefined) delete process.env[k];
      else process.env[k] = savedEnv[k];
    }
    if (tempDir) {
      rmSync(tempDir, { recursive: true, force: true });
      tempDir = "";
    }
  });

  it("生成全局密钥并写入文件，清空缓存后可复读同一钥", () => {
    const first = getEncryptKeyPair();
    const keyFile = getRsaKeyFilePath();
    const disk = JSON.parse(readFileSync(keyFile, "utf8")) as {
      publicKeyBase64: string;
      privateKeyPem: string;
    };
    expect(disk.publicKeyBase64).toBe(first.publicKeyBase64);
    expect(disk.privateKeyPem).toBe(first.privateKeyPem);

    setEncryptKeyPairForTest(null);
    const second = getEncryptKeyPair();
    expect(second.publicKeyBase64).toBe(first.publicKeyBase64);
    expect(second.privateKeyPem).toBe(first.privateKeyPem);
  });

  it("SECURITY_RSA_* 优先于本地文件", () => {
    const generated = getEncryptKeyPair();
    setEncryptKeyPairForTest(null);

    process.env.SECURITY_RSA_PUBLIC_KEY = "env-public-key-base64";
    process.env.SECURITY_RSA_PRIVATE_KEY =
      "-----BEGIN PRIVATE KEY-----\nenv\n-----END PRIVATE KEY-----";

    const fromEnv = getEncryptKeyPair();
    expect(fromEnv.publicKeyBase64).toBe("env-public-key-base64");
    expect(fromEnv.privateKeyPem).toContain("BEGIN PRIVATE KEY");
    expect(fromEnv.publicKeyBase64).not.toBe(generated.publicKeyBase64);
  });

  it("登录会话（含会话钥）落盘，进程内重置后可恢复", () => {
    const created = createSession({ id: 1, username: "admin" });
    expect(created.accessToken).toBeTruthy();
    expect(created.publicKey.length).toBeGreaterThan(100);
    expect(getSessionPrivateKeyPem(created.accessToken)).toBeTruthy();

    resetSessionsForTest();
    expect(getSessionPrivateKeyPem(created.accessToken)).toBeTruthy();

    revokeSession(created.accessToken);
    resetSessionsForTest();
    expect(getSessionPrivateKeyPem(created.accessToken)).toBeNull();
  });
});
