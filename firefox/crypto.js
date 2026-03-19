// Flick Firefox — Vault decryption and OTP generation
// Ported from Aegis/Flick Java sources + docs/decrypt.py

"use strict";

// --- Encoding utilities ---

function hexToBytes(hex) {
    if (!hex || hex.length % 2 !== 0) {
        throw new Error("Invalid hex string");
    }
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < hex.length; i += 2) {
        bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16);
    }
    return bytes;
}

function base64ToBytes(b64) {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
}

const BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function base32Decode(str) {
    str = str.toUpperCase().replace(/=+$/, "");
    let bits = 0;
    let value = 0;
    let index = 0;
    const output = new Uint8Array(Math.ceil(str.length * 5 / 8));

    for (let i = 0; i < str.length; i++) {
        const charIndex = BASE32_ALPHABET.indexOf(str[i]);
        if (charIndex === -1) {
            throw new Error("Invalid base32 character: " + str[i]);
        }
        value = (value << 5) | charIndex;
        bits += 5;
        if (bits >= 8) {
            output[index++] = (value >>> (bits - 8)) & 0xff;
            bits -= 8;
        }
    }
    return output.slice(0, index);
}

// --- SCrypt key derivation ---

async function deriveKey(password, salt, N, r, p) {
    const passwordBytes = new TextEncoder().encode(password);
    // scrypt-js exposes scrypt on globalThis (loaded as background script)
    const key = await scrypt.scrypt(passwordBytes, salt, N, r, p, 32);
    return new Uint8Array(key);
}

// --- AES-GCM decryption via Web Crypto ---

async function aesGcmDecrypt(ciphertext, key, nonce, tag) {
    // Web Crypto expects ciphertext || tag concatenated
    const combined = new Uint8Array(ciphertext.length + tag.length);
    combined.set(ciphertext);
    combined.set(tag, ciphertext.length);

    const cryptoKey = await crypto.subtle.importKey(
        "raw", key, { name: "AES-GCM" }, false, ["decrypt"]
    );

    const plaintext = await crypto.subtle.decrypt(
        { name: "AES-GCM", iv: nonce, tagLength: 128 },
        cryptoKey,
        combined
    );

    return new Uint8Array(plaintext);
}

// --- Vault decryption orchestrator ---

async function decryptVault(vaultJson, password) {
    if (typeof vaultJson === "string") {
        vaultJson = JSON.parse(vaultJson);
    }

    if (vaultJson.version > 1) {
        throw new Error("Unsupported vault version: " + vaultJson.version);
    }

    const header = vaultJson.header;
    const slots = header.slots;
    const params = header.params;

    if (!slots || !params) {
        // Plaintext vault
        return vaultJson.db;
    }

    const passwordSlots = slots.filter(s => s.type === 1);
    if (passwordSlots.length === 0) {
        throw new Error("No password slots found in vault");
    }

    let masterKey = null;

    for (const slot of passwordSlots) {
        try {
            const salt = hexToBytes(slot.salt);
            const derivedKey = await deriveKey(password, salt, slot.n, slot.r, slot.p);

            const encryptedKey = hexToBytes(slot.key);
            const keyNonce = hexToBytes(slot.key_params.nonce);
            const keyTag = hexToBytes(slot.key_params.tag);

            masterKey = await aesGcmDecrypt(encryptedKey, derivedKey, keyNonce, keyTag);
            break;
        } catch (e) {
            // Wrong password or slot, try next
        }
    }

    if (!masterKey) {
        throw new Error("Unable to decrypt vault with the given password");
    }

    const dbCiphertext = base64ToBytes(vaultJson.db);
    const dbNonce = hexToBytes(params.nonce);
    const dbTag = hexToBytes(params.tag);

    const plaintext = await aesGcmDecrypt(dbCiphertext, masterKey, dbNonce, dbTag);
    const dbJson = new TextDecoder().decode(plaintext);

    return JSON.parse(dbJson);
}

// --- Parse vault entries ---

function parseEntries(db) {
    if (!db || !db.entries) {
        return [];
    }
    const entries = [];
    for (const raw of db.entries) {
        try {
            entries.push(parseEntry(raw));
        } catch (e) {
            // Skip unparseable entries (forward compatibility)
        }
    }
    return entries;
}

function parseEntry(raw) {
    const info = raw.info;
    return {
        uuid: raw.uuid,
        type: raw.type,
        name: raw.name || "",
        issuer: raw.issuer || "",
        note: raw.note || "",
        favorite: raw.favorite || false,
        groups: raw.groups || [],
        info: {
            secret: info.secret,
            algo: info.algo || "SHA1",
            digits: info.digits || 6,
            period: info.period || 30,
            counter: info.counter || 0,
            pin: info.pin || ""
        }
    };
}

// --- OTP generation ---

const ALGO_MAP = {
    "SHA1": "SHA-1",
    "SHA256": "SHA-256",
    "SHA512": "SHA-512"
};

async function hmac(algo, key, data) {
    const webCryptoAlgo = ALGO_MAP[algo] || algo;
    const hmacKey = await crypto.subtle.importKey(
        "raw", key,
        { name: "HMAC", hash: { name: webCryptoAlgo } },
        false, ["sign"]
    );
    const sig = await crypto.subtle.sign("HMAC", hmacKey, data);
    return new Uint8Array(sig);
}

function longToBytes(value) {
    const bytes = new Uint8Array(8);
    const view = new DataView(bytes.buffer);
    // JS bitwise ops are 32-bit, use BigInt for 64-bit
    view.setBigUint64(0, BigInt(value));
    return bytes;
}

async function generateRawCode(secret, algo, counter) {
    const counterBytes = longToBytes(counter);
    const hash = await hmac(algo, secret, counterBytes);

    const offset = hash[hash.length - 1] & 0xf;
    return ((hash[offset] & 0x7f) << 24)
        | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8)
        | (hash[offset + 3] & 0xff);
}

async function generateHOTP(secret, counter, digits, algo) {
    const code = await generateRawCode(secret, algo, counter);
    const truncated = code % Math.pow(10, digits);
    return String(truncated).padStart(digits, "0");
}

async function generateTOTP(secret, period, digits, algo, time) {
    if (time === undefined) {
        time = Math.floor(Date.now() / 1000);
    }
    const counter = Math.floor(time / period);
    return generateHOTP(secret, counter, digits, algo);
}

const STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY";

async function generateSteamOTP(secret, period, time) {
    if (time === undefined) {
        time = Math.floor(Date.now() / 1000);
    }
    const counter = Math.floor(time / period);
    let code = await generateRawCode(secret, "SHA1", counter);

    let result = "";
    for (let i = 0; i < 5; i++) {
        result += STEAM_ALPHABET[code % STEAM_ALPHABET.length];
        code = Math.floor(code / STEAM_ALPHABET.length);
    }
    return result;
}

async function generateOTP(entry, time) {
    const info = entry.info;
    const secret = base32Decode(info.secret);
    const algo = info.algo || "SHA1";
    const digits = info.digits || 6;
    const period = info.period || 30;
    const now = time !== undefined ? time : Math.floor(Date.now() / 1000);

    switch (entry.type) {
        case "totp":
            return {
                code: await generateTOTP(secret, period, digits, algo, now),
                period,
                secondsRemaining: period - (now % period)
            };
        case "hotp":
            return {
                code: await generateHOTP(secret, info.counter || 0, digits, algo),
                period: null,
                secondsRemaining: null
            };
        case "steam":
            return {
                code: await generateSteamOTP(secret, period, now),
                period,
                secondsRemaining: period - (now % period)
            };
        default:
            throw new Error("Unsupported OTP type: " + entry.type);
    }
}
