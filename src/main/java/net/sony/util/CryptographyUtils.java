package net.sony.util;

import com.google.common.primitives.Bytes;
import net.sony.dpt.command.register.HashRequest;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.digests.GeneralDigest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class CryptographyUtils {

    private static final String PKCS_1_PEM_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PKCS_1_PEM_FOOTER = "-----END RSA PRIVATE KEY-----";
    private static final String PKCS_8_PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS_8_PEM_FOOTER = "-----END PRIVATE KEY-----";

    public byte[] generateNonce(int size) {
        byte[] nonce = new byte[size];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    public byte[] deriveKey(byte[] sharedKey, byte[] salt) {
        GeneralDigest algorithm = new SHA256Digest();
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(algorithm);
        gen.init(sharedKey, salt, 10000);
        return ((KeyParameter) gen.generateDerivedParameters(48 * 8)).getKey();
    }

    public byte[] hmac(byte[] authKey, byte[] update) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmac = Mac.getInstance("HMACSHA256");
        hmac.init(new SecretKeySpec(authKey, "HMACSHA256"));

        hmac.update(update);

        return hmac.doFinal();
    }

    public HashRequest generateHash(byte[] sharedKey,
                                    byte[] nonce1,
                                    byte[] mac,
                                    byte[] otherContribution,
                                    byte[] nonce2,
                                    byte[] publicKey
    ) throws NoSuchAlgorithmException, InvalidKeyException {

        byte[] salt = Bytes.concat(nonce1, mac, nonce2);

        byte[] derivedKey = deriveKey(sharedKey, salt);

        byte[] authKey = new byte[32];
        byte[] keyWrapKey = new byte[16];
        System.arraycopy(derivedKey, 0, authKey, 0, 32);
        System.arraycopy(derivedKey, 32, keyWrapKey, 0, 16);

        byte[] h2hmac = hmac(authKey, Bytes.concat(nonce1, mac, otherContribution, nonce1, nonce2, mac, publicKey));

        return new HashRequest(authKey, keyWrapKey, h2hmac, nonce1, nonce2, publicKey, mac);
    }

    public byte[] wrap(byte[] data, byte[] authKey, byte[] keyWrapKey) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {
        byte[] hmac = hmac(authKey, data);
        byte[] kwa = new byte[8];
        System.arraycopy(hmac, 0, kwa, 0, 8);
        byte[] iv = generateNonce(16);

        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);
        SecretKeySpec skey = new SecretKeySpec(keyWrapKey, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, skey, ivSpec);

        return Bytes.concat(
                cipher.doFinal(Bytes.concat(data, kwa)),
                iv
        );
    }

    public byte[] decryptAES(byte[] data, byte[] key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);
        SecretKeySpec skey = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, skey, ivSpec);

        return cipher.doFinal(data);
    }

    public byte[] unwrap(byte[] data, byte[] authKey, byte[] keyWrapKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        byte[] iv = new byte[16];
        System.arraycopy(data, data.length - 16, iv, 0, 16);

        byte[] actualData = new byte[data.length - 16];
        System.arraycopy(data, 0, actualData, 0, data.length - 16);

        byte[] unwrapped = decryptAES(actualData, keyWrapKey, iv);
        byte[] kwa = new byte[8];
        byte[] actualUnwrapped = new byte[unwrapped.length - 8];

        System.arraycopy(unwrapped, unwrapped.length - 8, kwa, 0, 8);
        System.arraycopy(unwrapped, 0, actualUnwrapped, 0, unwrapped.length - 8);

        byte[] localKwaHmac = hmac(authKey, Bytes.concat(actualUnwrapped));
        byte[] localKwa = new byte[8];
        System.arraycopy(localKwaHmac, 0, localKwa, 0, 8);

        if (!Arrays.equals(kwa, localKwa)) {
            throw new IllegalStateException("Unwrapped kwa does not match");
        }
        return actualUnwrapped;
    }

    public String exportPublicKeyToPEM(PublicKey publicKey) throws IOException {
        StringWriter writer = new StringWriter();
        PemWriter pemWriter = new PemWriter(writer);
        pemWriter.writeObject(new PemObject("PUBLIC KEY", publicKey.getEncoded()));
        pemWriter.flush();
        pemWriter.close();
        return writer.toString();
    }

    public String exportPrivateKeyToPEM(PrivateKey privateKey) throws IOException {
        StringWriter writer = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(new JcaPKCS8Generator(privateKey, null));
        }
        return writer.toString();
    }

    public PrivateKey readPkcs8PrivateKey(String pemFile) throws GeneralSecurityException {
        String privateKeyB64 = pemFile
                .replaceAll(PKCS_8_PEM_FOOTER, "")
                .replaceAll(PKCS_8_PEM_HEADER, "")
                .replaceAll("\n", "");
        byte[] decoded = Base64.getMimeDecoder().decode(privateKeyB64);

        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private Object parsePcks1Key(String pemFile) throws IOException {
        PEMParser pemParser = new PEMParser(new StringReader(pemFile));
        return pemParser.readObject();
    }

    public PrivateKey readPkcs1PrivateKey(String pemFile) throws IOException {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());
        KeyPair kp = converter.getKeyPair((PEMKeyPair) parsePcks1Key(pemFile));
        return kp.getPrivate();
    }

    public KeyPair readPkcs1KeyPair(String privatePemFile, String publicPemFile) throws IOException {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());
        KeyPair privateKeyPair = converter.getKeyPair((PEMKeyPair) parsePcks1Key(privatePemFile));
        PublicKey publicKey = converter.getPublicKey((SubjectPublicKeyInfo) parsePcks1Key(publicPemFile));
        return new KeyPair(publicKey, privateKeyPair.getPrivate());
    }

    public PrivateKey readPrivateKeyFromPEM(String pemFile) throws GeneralSecurityException {
        return readPkcs8PrivateKey(pemFile);
    }

    public byte[] signSHA256RSA(byte[] input, String privateKeyPem) throws Exception {
        PrivateKey key = readPrivateKeyFromPEM(privateKeyPem);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(input);
        return signature.sign();
    }

    public boolean verifySignature(byte[] input, byte[] signature, PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(input);
        return sig.verify(signature);
    }

    public byte[] decryptRsa(PrivateKey privateKey, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }
}
