package io.figchain.client.transport;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureGenerationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;

public class Ed25519Algorithm extends Algorithm {

    private final EdECPrivateKey privateKey;
    private final EdECPublicKey publicKey;

    public Ed25519Algorithm(EdECPublicKey publicKey, EdECPrivateKey privateKey) {
        super("EdDSA", "Ed25519");
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public Ed25519Algorithm(EdECPrivateKey privateKey) {
        this(null, privateKey);
    }

    @Override
    public void verify(DecodedJWT jwt) throws SignatureVerificationException {
        if (publicKey == null) {
            throw new SignatureVerificationException(this); // Just standard error
        }
        try {
            byte[] signatureBytes = java.util.Base64.getUrlDecoder().decode(jwt.getSignature());
            byte[] contentBytes = (jwt.getHeader() + "." + jwt.getPayload()).getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(contentBytes);

            if (!signature.verify(signatureBytes)) {
                 throw new SignatureVerificationException(this);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IllegalArgumentException e) {
             throw new SignatureVerificationException(this, e);
        }
    }

    @Override
    public byte[] sign(byte[] contentBytes) throws SignatureGenerationException {
        if (privateKey == null) {
            throw new SignatureGenerationException(this, new IllegalStateException("Missing private key"));
        }
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(contentBytes);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new SignatureGenerationException(this, e);
        }
    }
}
