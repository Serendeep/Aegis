package com.serendeep.flick.crypto;

public class CryptResult {
    private final byte[] _data;
    private final CryptParameters _params;

    public CryptResult(byte[] data, CryptParameters params) {
        _data = data;
        _params = params;
    }

    public byte[] getData() {
        return _data;
    }

    public CryptParameters getParams() {
        return _params;
    }
}
