// Flick Firefox — Background script
// State management, lock timer, message routing, entry matching

"use strict";

const LOCK_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

const state = {
    entries: null,
    isUnlocked: false,
    lockTimer: null
};

// --- Domain matching (ported from EntryMatcher.java) ---

const DOMAIN_MAP = {
    "x.com": "twitter",
    "twitter.com": "twitter",
    "accounts.google.com": "google",
    "login.microsoftonline.com": "microsoft",
    "signin.aws.amazon.com": "amazon",
    "github.com": "github",
    "gitlab.com": "gitlab",
    "login.yahoo.com": "yahoo",
    "signin.ebay.com": "ebay",
    "discord.com": "discord",
    "store.steampowered.com": "steam"
};

function normalize(s) {
    if (!s) return "";
    return s.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function extractDomain(input) {
    if (!input) return null;
    input = input.toLowerCase();
    try {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return new URL(input).hostname;
        }
    } catch (e) {
        // fall through
    }
    const slashIdx = input.indexOf("/");
    return slashIdx > 0 ? input.substring(0, slashIdx) : input;
}

function getSecondLevelDomain(domain) {
    const parts = domain.split(".");
    if (parts.length >= 2) {
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
    return domain;
}

function findByIssuerKeyword(entries, keyword, includeNameMatches) {
    const kw = normalize(keyword);
    if (!kw) return [];

    const issuerMatches = [];
    const nameMatches = [];

    for (const entry of entries) {
        const issuer = normalize(entry.issuer);
        if (issuer && (issuer === kw || issuer.includes(kw) || kw.includes(issuer))) {
            issuerMatches.push(entry);
            continue;
        }

        if (includeNameMatches) {
            const name = normalize(entry.name);
            if (name && (name.includes(kw) || kw.includes(name))) {
                nameMatches.push(entry);
            }
        }
    }

    return issuerMatches.concat(nameMatches);
}

function matchByDomain(entries, domainOrUrl) {
    if (!domainOrUrl || !entries) return [];

    const input = domainOrUrl.toLowerCase();
    const domain = extractDomain(input);
    if (!domain) return [];

    // Check full domain in DOMAIN_MAP
    if (DOMAIN_MAP[domain]) {
        const matches = findByIssuerKeyword(entries, DOMAIN_MAP[domain], true);
        if (matches.length > 0) return matches;
    }

    const sld = getSecondLevelDomain(domain);

    // Check SLD in DOMAIN_MAP
    if (DOMAIN_MAP[sld]) {
        const matches = findByIssuerKeyword(entries, DOMAIN_MAP[sld], true);
        if (matches.length > 0) return matches;
    }

    // Extract keyword from SLD
    const keyword = sld.includes(".") ? sld.substring(0, sld.indexOf(".")) : sld;

    const keywords = [keyword];
    const words = keyword.split("-");
    if (words.length > 1) {
        for (let i = words.length - 1; i >= 0; i--) {
            if (words[i].length >= 4) {
                keywords.push(words[i]);
            }
        }
    }

    // Issuer-first pass
    for (const kw of keywords) {
        const matches = findByIssuerKeyword(entries, kw, false);
        if (matches.length > 0) return matches;
    }

    // Name fallback
    for (const kw of keywords) {
        const matches = findByIssuerKeyword(entries, kw, true);
        if (matches.length > 0) return matches;
    }

    return [];
}

// --- Badge management ---

async function updateBadgeForTab(tabId) {
    if (!state.isUnlocked || !state.entries) {
        browser.browserAction.setBadgeText({ text: "", tabId });
        return;
    }

    try {
        const tab = await browser.tabs.get(tabId);
        if (!tab.url || tab.url.startsWith("about:") || tab.url.startsWith("moz-extension:")) {
            browser.browserAction.setBadgeText({ text: "", tabId });
            return;
        }

        const matches = matchByDomain(state.entries, tab.url);
        if (matches.length > 0) {
            browser.browserAction.setBadgeText({ text: String(matches.length), tabId });
            browser.browserAction.setBadgeBackgroundColor({ color: "#5c7cfa", tabId });
        } else {
            browser.browserAction.setBadgeText({ text: "", tabId });
        }
    } catch (e) {
        // Tab may have been closed
    }
}

async function updateAllBadges() {
    try {
        const tabs = await browser.tabs.query({});
        for (const tab of tabs) {
            updateBadgeForTab(tab.id);
        }
    } catch (e) {
        // Ignore
    }
}

function notifyContentScripts(type) {
    browser.tabs.query({}).then(tabs => {
        for (const tab of tabs) {
            browser.tabs.sendMessage(tab.id, { type }).catch(() => {});
        }
    }).catch(() => {});
}

// --- Badge listeners ---

browser.tabs.onActivated.addListener(info => {
    updateBadgeForTab(info.tabId);
});

browser.tabs.onUpdated.addListener((tabId, changeInfo) => {
    if (changeInfo.url || changeInfo.status === "complete") {
        updateBadgeForTab(tabId);
    }
});

// --- Lock timer ---

function refreshLockTimer() {
    if (state.lockTimer) {
        clearTimeout(state.lockTimer);
    }
    state.lockTimer = setTimeout(lock, LOCK_TIMEOUT_MS);
}

function lock() {
    state.entries = null;
    state.isUnlocked = false;
    if (state.lockTimer) {
        clearTimeout(state.lockTimer);
        state.lockTimer = null;
    }
    updateAllBadges();
    notifyContentScripts("vaultLocked");
    // Notify any open popups
    browser.runtime.sendMessage({ type: "locked" }).catch(() => {});
}

// --- Message handler ---

browser.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    // Refresh timer on any interaction
    if (state.isUnlocked) {
        refreshLockTimer();
    }

    handleMessage(msg).then(sendResponse).catch(err => {
        sendResponse({ error: err.message });
    });

    return true; // async response
});

async function handleMessage(msg) {
    switch (msg.type) {
        case "getStatus": {
            const vault = await browser.storage.local.get("vault");
            return {
                isUnlocked: state.isUnlocked,
                hasVault: !!vault.vault
            };
        }

        case "unlock": {
            const vault = await browser.storage.local.get("vault");
            if (!vault.vault) {
                return { success: false, error: "No vault imported" };
            }

            try {
                const db = await decryptVault(vault.vault, msg.password);
                state.entries = parseEntries(db);
                state.isUnlocked = true;
                refreshLockTimer();
                updateAllBadges();
                notifyContentScripts("vaultUnlocked");
                return { success: true, entryCount: state.entries.length };
            } catch (e) {
                return { success: false, error: e.message };
            }
        }

        case "lock": {
            lock();
            return { success: true };
        }

        case "getMatchingEntries": {
            if (!state.isUnlocked || !state.entries) {
                return { entries: [] };
            }
            const matches = matchByDomain(state.entries, msg.url);
            return {
                entries: matches.map(e => ({
                    uuid: e.uuid,
                    issuer: e.issuer,
                    name: e.name,
                    type: e.type
                }))
            };
        }

        case "generateOTP": {
            if (!state.isUnlocked || !state.entries) {
                return { error: "Vault is locked" };
            }
            const entry = state.entries.find(e => e.uuid === msg.uuid);
            if (!entry) {
                return { error: "Entry not found" };
            }
            return await generateOTP(entry);
        }

        case "getAllEntries": {
            if (!state.isUnlocked || !state.entries) {
                return { entries: [] };
            }
            return {
                entries: state.entries.map(e => ({
                    uuid: e.uuid,
                    issuer: e.issuer,
                    name: e.name,
                    type: e.type
                }))
            };
        }

        default:
            return { error: "Unknown message type: " + msg.type };
    }
}
