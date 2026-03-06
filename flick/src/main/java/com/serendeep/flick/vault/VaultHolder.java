package com.serendeep.flick.vault;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VaultHolder {
    private static final long GRACE_PERIOD_MS = 5 * 60 * 1000;

    private List<VaultEntry> _entries = Collections.emptyList();
    private boolean _loaded;
    private boolean _unlocked;
    private long _lastUnlockTime;

    private final CopyOnWriteArrayList<LockStateListener> _lockListeners = new CopyOnWriteArrayList<>();

    public interface LockStateListener {
        void onUnlocked();
        void onLocked();
    }

    public void setEntries(List<VaultEntry> entries) {
        _entries = Collections.unmodifiableList(entries);
        _loaded = true;
    }

    public List<VaultEntry> getEntries() {
        return _entries;
    }

    public boolean isLoaded() {
        return _loaded;
    }

    public void clear() {
        _entries = Collections.emptyList();
        _loaded = false;
        lock();
    }

    public void unlock() {
        _unlocked = true;
        _lastUnlockTime = System.currentTimeMillis();
        for (LockStateListener l : _lockListeners) {
            l.onUnlocked();
        }
    }

    public void lock() {
        if (!_unlocked) return;
        _unlocked = false;
        _lastUnlockTime = 0;
        for (LockStateListener l : _lockListeners) {
            l.onLocked();
        }
    }

    public boolean isUnlocked() {
        if (!_unlocked) return false;
        if (System.currentTimeMillis() - _lastUnlockTime > GRACE_PERIOD_MS) {
            lock();
            return false;
        }
        return true;
    }

    public void refreshUnlock() {
        if (_unlocked) {
            _lastUnlockTime = System.currentTimeMillis();
        }
    }

    public void addLockStateListener(LockStateListener listener) {
        _lockListeners.add(listener);
    }

    public void removeLockStateListener(LockStateListener listener) {
        _lockListeners.remove(listener);
    }
}
