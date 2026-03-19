// Flick Firefox — Content script
// Detects OTP input fields and provides auto-fill

"use strict";

(function () {
    const FILL_BUTTON_CLASS = "flick-otp-fill";
    let fillButtons = [];
    let observer = null;

    function findOtpFields() {
        const fields = new Set();

        // Priority 1: autocomplete="one-time-code"
        document.querySelectorAll('input[autocomplete="one-time-code"]')
            .forEach(el => fields.add(el));

        // Priority 2: name contains otp/totp/2fa/mfa
        document.querySelectorAll('input[name*="otp"], input[name*="totp"], input[name*="2fa"], input[name*="mfa"]')
            .forEach(el => fields.add(el));

        // Priority 3: id contains otp/totp/2fa/mfa
        document.querySelectorAll('input[id*="otp"], input[id*="totp"], input[id*="2fa"], input[id*="mfa"]')
            .forEach(el => fields.add(el));

        // Priority 4: placeholder with "code" and maxlength 6 or 8
        document.querySelectorAll('input[placeholder]').forEach(el => {
            const ph = el.placeholder.toLowerCase();
            const ml = el.getAttribute("maxlength");
            if (ph.includes("code") && (ml === "6" || ml === "8")) {
                fields.add(el);
            }
        });

        // Filter out hidden/disabled
        return Array.from(fields).filter(el =>
            el.offsetParent !== null && !el.disabled && el.type !== "hidden"
        );
    }

    function findSplitOtpFields() {
        // Groups of single-char inputs (maxlength=1) that are siblings
        const candidates = document.querySelectorAll('input[maxlength="1"]');
        if (candidates.length < 6) return [];

        // Group by parent
        const groups = new Map();
        candidates.forEach(el => {
            if (el.offsetParent === null || el.disabled) return;
            const parent = el.parentElement;
            if (!groups.has(parent)) groups.set(parent, []);
            groups.get(parent).push(el);
        });

        // Return groups of 6 or 8
        const result = [];
        for (const [, inputs] of groups) {
            if (inputs.length === 6 || inputs.length === 8) {
                result.push(inputs);
            }
        }
        return result;
    }

    function createFillButton(targetField, splitGroup) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = FILL_BUTTON_CLASS;
        btn.textContent = "OTP";
        btn.title = "Fill OTP from Flick";

        Object.assign(btn.style, {
            position: "absolute",
            zIndex: "999999",
            background: "#5c7cfa",
            color: "#fff",
            border: "none",
            borderRadius: "4px",
            padding: "2px 8px",
            fontSize: "11px",
            fontWeight: "600",
            cursor: "pointer",
            lineHeight: "20px",
            fontFamily: "sans-serif",
            boxShadow: "0 2px 6px rgba(0,0,0,0.3)"
        });

        btn.addEventListener("click", async (e) => {
            e.preventDefault();
            e.stopPropagation();
            await fillOtp(targetField, splitGroup);
        });

        return btn;
    }

    function positionButton(btn, field) {
        const rect = field.getBoundingClientRect();
        btn.style.top = (window.scrollY + rect.top + (rect.height - 20) / 2) + "px";
        btn.style.left = (window.scrollX + rect.right - 40) + "px";
    }

    async function fillOtp(field, splitGroup) {
        try {
            const status = await browser.runtime.sendMessage({ type: "getStatus" });
            if (!status.isUnlocked) return;

            const url = window.location.href;
            const matches = await browser.runtime.sendMessage({
                type: "getMatchingEntries",
                url
            });

            if (!matches.entries || matches.entries.length === 0) return;

            // Use first match
            const entry = matches.entries[0];
            const otp = await browser.runtime.sendMessage({
                type: "generateOTP",
                uuid: entry.uuid
            });

            if (otp.error) return;

            const code = otp.code;

            if (splitGroup) {
                // Distribute digits across split inputs
                for (let i = 0; i < splitGroup.length && i < code.length; i++) {
                    setInputValue(splitGroup[i], code[i]);
                }
            } else {
                setInputValue(field, code);
            }
        } catch (e) {
            // Silently fail
        }
    }

    function setInputValue(input, value) {
        const nativeSetter = Object.getOwnPropertyDescriptor(
            HTMLInputElement.prototype, "value"
        ).set;
        nativeSetter.call(input, value);
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function cleanupButtons() {
        for (const btn of fillButtons) {
            btn.remove();
        }
        fillButtons = [];
    }

    async function scan() {
        cleanupButtons();

        // Check if vault is unlocked
        let status;
        try {
            status = await browser.runtime.sendMessage({ type: "getStatus" });
        } catch (e) {
            return;
        }
        if (!status.isUnlocked || !status.hasVault) return;

        // Check if there are matching entries for this site
        const matches = await browser.runtime.sendMessage({
            type: "getMatchingEntries",
            url: window.location.href
        });
        if (!matches.entries || matches.entries.length === 0) return;

        // Find standard OTP fields
        const fields = findOtpFields();
        for (const field of fields) {
            const btn = createFillButton(field, null);
            document.body.appendChild(btn);
            positionButton(btn, field);
            fillButtons.push(btn);
        }

        // Find split OTP field groups
        const splitGroups = findSplitOtpFields();
        for (const group of splitGroups) {
            const btn = createFillButton(group[0], group);
            document.body.appendChild(btn);
            positionButton(btn, group[0]);
            fillButtons.push(btn);
        }
    }

    // Listen for vault state changes from background
    browser.runtime.onMessage.addListener((msg) => {
        if (msg.type === "vaultUnlocked") {
            // Vault just unlocked — re-scan for OTP fields
            scan();
        } else if (msg.type === "vaultLocked") {
            // Vault locked — remove all fill buttons
            cleanupButtons();
        }
    });

    // Initial scan
    scan();

    // Watch for DOM changes (SPAs adding login forms)
    observer = new MutationObserver(() => {
        // Debounce
        if (observer._timeout) clearTimeout(observer._timeout);
        observer._timeout = setTimeout(scan, 500);
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });

    // Reposition buttons on scroll/resize
    window.addEventListener("scroll", () => {
        const fields = findOtpFields();
        const splitGroups = findSplitOtpFields();
        const allFields = [...fields, ...splitGroups.map(g => g[0])];
        for (let i = 0; i < fillButtons.length && i < allFields.length; i++) {
            positionButton(fillButtons[i], allFields[i]);
        }
    }, { passive: true });
})();
