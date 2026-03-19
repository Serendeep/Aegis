"use strict";

const fileInput = document.getElementById("file-input");
const importBtn = document.getElementById("import-btn");
const removeBtn = document.getElementById("remove-btn");
const statusNoVault = document.getElementById("status-no-vault");
const statusHasVault = document.getElementById("status-has-vault");
const vaultInfo = document.getElementById("vault-info");
const errorMsg = document.getElementById("error-msg");
const successMsg = document.getElementById("success-msg");

function showError(msg) {
    errorMsg.textContent = msg;
    errorMsg.hidden = false;
    successMsg.hidden = true;
}

function showSuccess(msg) {
    successMsg.textContent = msg;
    successMsg.hidden = false;
    errorMsg.hidden = true;
}

function clearMessages() {
    errorMsg.hidden = true;
    successMsg.hidden = true;
}

function validateVaultJson(data) {
    if (typeof data.version !== "number") {
        return "Missing or invalid 'version' field";
    }
    if (data.version > 1) {
        return "Unsupported vault version: " + data.version;
    }
    if (!data.header) {
        return "Missing 'header' field";
    }
    // Encrypted vault must have slots and params
    if (data.header.slots && data.header.params) {
        const hasPasswordSlot = data.header.slots.some(s => s.type === 1);
        if (!hasPasswordSlot) {
            return "No password slot found — biometric-only vaults are not supported";
        }
        if (!data.db || typeof data.db !== "string") {
            return "Missing encrypted 'db' field";
        }
    } else if (!data.db) {
        return "Missing 'db' field";
    }
    return null;
}

async function updateUI() {
    clearMessages();
    const result = await browser.storage.local.get("vault");
    if (result.vault) {
        statusNoVault.hidden = true;
        statusHasVault.hidden = false;
        removeBtn.hidden = false;

        const data = typeof result.vault === "string" ? JSON.parse(result.vault) : result.vault;
        const encrypted = !!(data.header && data.header.slots && data.header.params);
        vaultInfo.textContent = encrypted ? "Encrypted vault imported" : "Plaintext vault imported";
    } else {
        statusNoVault.hidden = false;
        statusHasVault.hidden = true;
        removeBtn.hidden = true;
    }
}

fileInput.addEventListener("change", async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    clearMessages();

    try {
        const text = await file.text();
        const data = JSON.parse(text);

        const error = validateVaultJson(data);
        if (error) {
            showError(error);
            return;
        }

        await browser.storage.local.set({ vault: data });
        showSuccess("Vault imported successfully");
        updateUI();
    } catch (err) {
        showError("Failed to parse file: " + err.message);
    }

    // Reset file input so the same file can be re-selected
    fileInput.value = "";
});

removeBtn.addEventListener("click", async () => {
    await browser.storage.local.remove("vault");
    // Lock the vault in background
    browser.runtime.sendMessage({ type: "lock" }).catch(() => {});
    showSuccess("Vault removed");
    updateUI();
});

updateUI();
