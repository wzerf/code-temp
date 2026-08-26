/**
 * 全局 RSA 密钥对（进程内缓存 + 本地文件持久化）。
 * 优先级：内存缓存 → SECURITY_RSA_* 环境变量 → `.local/rsa-keypair.json` → 生成并落盘。
 * hybrid 下也可由 {@link setEncryptKeyPair} 注入从 java 拉取的密钥对（不写本地文件）。
 */

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { isAbsolute, resolve } from "node:path";

import { ensureLocalDataDir, getLocalDataDir } from "../local-data";
import { generateRsaKeyPair, type RsaKeyPairPem } from "./crypto";

const DEFAULT_KEY_FILE_NAME = "rsa-keypair.json";

let cached: RsaKeyPairPem | null = null;

interface PersistedKeyPairFile {
  publicKeyBase64?: string;
  privateKeyPem?: string;
}

/** 解析密钥文件路径；可用 SECURITY_RSA_KEY_FILE 覆盖。 */
export function getRsaKeyFilePath(): string {
  const raw = process.env.SECURITY_RSA_KEY_FILE?.trim();
  if (raw) {
    return isAbsolute(raw) ? raw : resolve(process.cwd(), raw);
  }
  return resolve(getLocalDataDir(), DEFAULT_KEY_FILE_NAME);
}

function normalizePair(pub: string, priv: string): RsaKeyPairPem {
  const publicKeyRaw = pub.replace(/\\n/g, "\n").trim();
  const privateKeyPem = priv.replace(/\\n/g, "\n");
  return {
    publicKeyBase64: publicKeyRaw.includes("BEGIN")
      ? spkiPemToBase64(publicKeyRaw)
      : publicKeyRaw.replace(/\s/g, ""),
    privateKeyPem,
  };
}

function readKeyPairFromFile(path: string): RsaKeyPairPem | null {
  if (!existsSync(path)) return null;
  try {
    const raw = readFileSync(path, "utf8");
    const json = JSON.parse(raw) as PersistedKeyPairFile;
    const pub = json.publicKeyBase64?.trim();
    // 私钥保留原文换行（勿 trim 尾部 \\n，与 generateRsaKeyPair / Java 导出一致）
    const priv = json.privateKeyPem;
    if (!pub || !priv?.trim()) return null;
    return normalizePair(pub, priv);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.warn("[security] 读取本地 RSA 密钥文件失败，将重新生成:", path, msg);
    return null;
  }
}

function writeKeyPairToFile(path: string, pair: RsaKeyPairPem): void {
  try {
    ensureLocalDataDir(resolve(path, ".."));
    const payload: PersistedKeyPairFile = {
      publicKeyBase64: pair.publicKeyBase64,
      privateKeyPem: pair.privateKeyPem,
    };
    writeFileSync(path, `${JSON.stringify(payload, null, 2)}\n`, "utf8");
    console.info("[security] 已写入本地 RSA 密钥文件:", path);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.warn("[security] 写入本地 RSA 密钥文件失败:", path, msg);
  }
}

export function getEncryptKeyPair(): RsaKeyPairPem {
  if (cached) return cached;

  const envPub = process.env.SECURITY_RSA_PUBLIC_KEY?.trim();
  const envPriv = process.env.SECURITY_RSA_PRIVATE_KEY?.trim();
  if (envPub && envPriv) {
    cached = normalizePair(envPub, envPriv);
    return cached;
  }

  const keyFile = getRsaKeyFilePath();
  const fromFile = readKeyPairFromFile(keyFile);
  if (fromFile) {
    cached = fromFile;
    console.info("[security] 已从本地文件加载 RSA 密钥:", keyFile);
    return cached;
  }

  cached = generateRsaKeyPair();
  writeKeyPairToFile(keyFile, cached);
  return cached;
}

/**
 * 注入密钥对（覆盖当前缓存）。
 * hybrid：java-key-sync 在本地生成前调用；须在首次业务解密前完成。
 * 不写本地文件（java 源密钥由 java 侧稳定）。
 */
export function setEncryptKeyPair(pair: RsaKeyPairPem): void {
  cached = pair;
}

/** 测试用：注入或重置密钥对。 */
export function setEncryptKeyPairForTest(pair: RsaKeyPairPem | null): void {
  cached = pair;
}

function spkiPemToBase64(pem: string): string {
  return pem
    .replace(/-----BEGIN PUBLIC KEY-----/g, "")
    .replace(/-----END PUBLIC KEY-----/g, "")
    .replace(/\s/g, "");
}
