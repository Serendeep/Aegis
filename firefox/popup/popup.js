"use strict";

const viewNoVault = document.getElementById("view-no-vault");
const viewLocked = document.getElementById("view-locked");
const viewUnlocked = document.getElementById("view-unlocked");
const passwordInput = document.getElementById("password-input");
const unlockForm = document.getElementById("unlock-form");
const unlockBtn = document.getElementById("unlock-btn");
const unlockError = document.getElementById("unlock-error");
const lockBtn = document.getElementById("lock-btn");
const searchInput = document.getElementById("search-input");
const entriesList = document.getElementById("entries-list");
const noMatches = document.getElementById("no-matches");
const openSettings = document.getElementById("open-settings");

let allEntries = [];
let matchedEntries = [];
let otpTimers = [];
let currentUrl = "";

function showView(view) {
    viewNoVault.hidden = true;
    viewLocked.hidden = true;
    viewUnlocked.hidden = true;
    view.hidden = false;
}

async function init() {
    const status = await browser.runtime.sendMessage({ type: "getStatus" });

    if (!status.hasVault) {
        showView(viewNoVault);
        return;
    }

    if (!status.isUnlocked) {
        showView(viewLocked);
        passwordInput.focus();
        return;
    }

    showView(viewUnlocked);
    await loadEntries();
}

async function loadEntries() {
    // Get current tab URL
    try {
        const tabs = await browser.tabs.query({ active: true, currentWindow: true });
        if (tabs[0] && tabs[0].url) {
            currentUrl = tabs[0].url;
        }
    } catch (e) {
        // No tab access
    }

    // Get matching entries for current URL
    if (currentUrl) {
        const result = await browser.runtime.sendMessage({
            type: "getMatchingEntries",
            url: currentUrl
        });
        matchedEntries = result.entries || [];
    }

    // Get all entries for search
    const allResult = await browser.runtime.sendMessage({ type: "getAllEntries" });
    allEntries = allResult.entries || [];

    renderEntries(matchedEntries.length > 0 ? matchedEntries : allEntries);
}

function renderEntries(entries) {
    clearOtpTimers();
    entriesList.innerHTML = "";
    noMatches.hidden = entries.length > 0;

    for (const entry of entries) {
        entriesList.appendChild(createEntryCard(entry));
    }
}

function createEntryCard(entry) {
    const card = document.createElement("div");
    card.className = "entry-card";

    const info = document.createElement("div");
    info.className = "entry-info";

    const issuer = document.createElement("div");
    issuer.className = "entry-issuer";
    issuer.textContent = entry.issuer || entry.name || "Unknown";

    const name = document.createElement("div");
    name.className = "entry-name";
    name.textContent = entry.issuer ? entry.name : "";

    info.appendChild(issuer);
    if (name.textContent) {
        info.appendChild(name);
    }

    const otpSection = document.createElement("div");
    otpSection.className = "entry-otp";

    const codeEl = document.createElement("span");
    codeEl.className = "otp-code";
    codeEl.textContent = "------";
    codeEl.title = "Click to copy";

    otpSection.appendChild(codeEl);

    if (entry.type === "totp" || entry.type === "steam") {
        const ring = createCountdownRing();
        otpSection.appendChild(ring.container);
        startOtpRefresh(entry, codeEl, ring);
    } else {
        fetchOtp(entry, codeEl);
    }

    codeEl.addEventListener("click", () => copyToClipboard(codeEl));

    card.appendChild(info);
    card.appendChild(otpSection);
    return card;
}

function createCountdownRing() {
    const container = document.createElement("div");
    container.className = "countdown-ring";

    const circumference = 2 * Math.PI * 10;

    container.innerHTML = `
        <svg viewBox="0 0 28 28">
            <circle class="bg" cx="14" cy="14" r="10"/>
            <circle class="fg" cx="14" cy="14" r="10"
                stroke-dasharray="${circumference}"
                stroke-dashoffset="0"/>
        </svg>
        <span class="countdown-text"></span>
    `;

    return {
        container,
        fg: container.querySelector(".fg"),
        text: container.querySelector(".countdown-text"),
        circumference
    };
}

async function startOtpRefresh(entry, codeEl, ring) {
    let entryPeriod = 30;

    async function refresh() {
        try {
            const result = await browser.runtime.sendMessage({
                type: "generateOTP",
                uuid: entry.uuid
            });

            if (result.error) {
                codeEl.textContent = "Error";
                return;
            }

            if (result.period) entryPeriod = result.period;
            codeEl.textContent = formatCode(result.code);
            updateCountdown(ring, entryPeriod, result.secondsRemaining);
        } catch (e) {
            codeEl.textContent = "Error";
        }
    }

    await refresh();

    const timer = setInterval(async () => {
        const now = Math.floor(Date.now() / 1000);
        const remaining = entryPeriod - (now % entryPeriod);

        updateCountdown(ring, entryPeriod, remaining);

        if (remaining === entryPeriod) {
            await refresh();
        }
    }, 1000);

    otpTimers.push(timer);
}

function updateCountdown(ring, period, remaining) {
    const fraction = remaining / period;
    const offset = ring.circumference * (1 - fraction);
    ring.fg.style.strokeDashoffset = offset;
    ring.text.textContent = remaining;

    ring.fg.classList.remove("warning", "critical");
    if (remaining <= 5) {
        ring.fg.classList.add("critical");
    } else if (remaining <= 10) {
        ring.fg.classList.add("warning");
    }
}

async function fetchOtp(entry, codeEl) {
    try {
        const result = await browser.runtime.sendMessage({
            type: "generateOTP",
            uuid: entry.uuid
        });
        if (result.error) {
            codeEl.textContent = "Error";
        } else {
            codeEl.textContent = formatCode(result.code);
        }
    } catch (e) {
        codeEl.textContent = "Error";
    }
}

function formatCode(code) {
    if (!code) return "------";
    // Group digits: 123456 -> 123 456
    if (/^\d+$/.test(code) && code.length >= 6) {
        const mid = Math.ceil(code.length / 2);
        return code.substring(0, mid) + " " + code.substring(mid);
    }
    return code;
}

async function copyToClipboard(codeEl) {
    const text = codeEl.textContent.replace(/\s/g, "");
    if (!text || text === "------" || text === "Error") return;

    try {
        await navigator.clipboard.writeText(text);
        codeEl.classList.add("copied");
        setTimeout(() => codeEl.classList.remove("copied"), 1000);
    } catch (e) {
        // Fallback
        const ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
        codeEl.classList.add("copied");
        setTimeout(() => codeEl.classList.remove("copied"), 1000);
    }
}

function clearOtpTimers() {
    for (const t of otpTimers) {
        clearInterval(t);
    }
    otpTimers = [];
}

// --- Event handlers ---

unlockForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    unlockError.hidden = true;

    const password = passwordInput.value;
    if (!password) return;

    unlockBtn.disabled = true;
    unlockBtn.textContent = "Decrypting...";

    try {
        const result = await browser.runtime.sendMessage({
            type: "unlock",
            password
        });

        if (result.success) {
            passwordInput.value = "";
            showView(viewUnlocked);
            await loadEntries();
        } else {
            unlockError.textContent = result.error || "Wrong password";
            unlockError.hidden = false;
            passwordInput.select();
        }
    } catch (e) {
        unlockError.textContent = "Decryption failed";
        unlockError.hidden = false;
    }

    unlockBtn.disabled = false;
    unlockBtn.textContent = "Unlock";
});

lockBtn.addEventListener("click", async () => {
    await browser.runtime.sendMessage({ type: "lock" });
    clearOtpTimers();
    showView(viewLocked);
    passwordInput.focus();
});

searchInput.addEventListener("input", () => {
    const query = searchInput.value.toLowerCase().trim();
    if (!query) {
        renderEntries(matchedEntries.length > 0 ? matchedEntries : allEntries);
        return;
    }

    const filtered = allEntries.filter(e => {
        const issuer = (e.issuer || "").toLowerCase();
        const name = (e.name || "").toLowerCase();
        return issuer.includes(query) || name.includes(query);
    });

    renderEntries(filtered);
});

openSettings.addEventListener("click", () => {
    browser.runtime.openOptionsPage();
    window.close();
});

// Listen for lock events from background
browser.runtime.onMessage.addListener((msg) => {
    if (msg.type === "locked") {
        clearOtpTimers();
        showView(viewLocked);
        passwordInput.focus();
    }
});

init();
