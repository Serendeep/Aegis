package com.serendeep.flick.crypto;

public class CryptParameters {
    private final byte[] _nonce;
    private final byte[] _tag;

    public CryptParameters(byte[] nonce, byte[] tag) {
        _nonce = nonce;
        _tag = tag;
    }

    public byte[] getNonce() {
        return _nonce;
    }

    public byte[] getTag() {
        return _tag;
    }
}
