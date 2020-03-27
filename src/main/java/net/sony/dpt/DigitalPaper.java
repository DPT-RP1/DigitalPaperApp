package net.sony.dpt;

import com.google.common.primitives.Bytes;
import net.sony.util.*;

import javax.crypto.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

import static net.sony.util.SimpleHttpClient.fromJSON;

public class DigitalPaper {

    private final String addr;

    private static final String REG_PIN_URL = "/register/pin";
    private static final String REG_HASH_URL = "/register/hash";
    private static final String REG_CA_URL = "/register/ca";
    private static final String REG_URL = "/register";
    private static final String REG_CLEANUP_URL = "/register/cleanup";

    private static final int REG_PORT = 8080;

    private String registerPinUrl;
    private String registerHashUrl;
    private String registerCaUrl;
    private String registerUrl;
    private String cleanupUrl;

    private final SimpleHttpClient simpleHttpClient;
    private final CryptographyUtil cryptographyUtil;
    private final DiffieHelman diffieHelman;
    private final Base64.Decoder b64decoder;

    public DigitalPaper(String addr,
                        Base64.Decoder b64decoder,
                        SimpleHttpClient simpleHttpClient,
                        DiffieHelman diffieHelman,
                        CryptographyUtil cryptographyUtil) {
        this.addr = addr;
        this.simpleHttpClient = simpleHttpClient;
        this.cryptographyUtil = cryptographyUtil;
        this.diffieHelman = diffieHelman;
        this.b64decoder = b64decoder;

        setupUrls();
    }

    private void setupUrls() {
        String registrationUrl = "http://" + addr + ":" + REG_PORT;
        registerPinUrl = registrationUrl + REG_PIN_URL;
        registerHashUrl = registrationUrl + REG_HASH_URL;
        registerCaUrl = registrationUrl + REG_CA_URL;
        registerUrl = registrationUrl + REG_URL;
        cleanupUrl = registrationUrl + REG_CLEANUP_URL;
    }

    /**
     * Gets authentication info from a DPT-RP1.  You can call this BEFORE
     * DigitalPaper.authenticate()
     * <p>
     * Sets (ca, priv_key, client_id):
     * - ca: a PEM-encoded X.509 server certificate, issued by the CA
     * on the device
     * - priv_key: a PEM-encoded 2048-bit RSA private key
     * - client_id: the client id
     */
    public RegistrationResponse register() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {

        System.out.println("Cleaning up...");
        String cleanupResponse = simpleHttpClient.put(cleanupUrl);
        System.out.println(cleanupResponse);

        System.out.println("Requesting PIN...");

        PinResponse pinResponse = PinResponse.fromJson(simpleHttpClient.post(registerPinUrl));
        byte[] nonce1 = pinResponse.getNonce1();
        byte[] mac = pinResponse.getMac();
        byte[] otherContribution = pinResponse.getOtherContribution();

        byte[] nonce2 = cryptographyUtil.generateNonce(16);
        byte[] publicKey = diffieHelman.generatePublicKey();
        byte[] sharedKey = diffieHelman.generateSharedKey(otherContribution);
        otherContribution = BigIntegerUtils.projectArray(otherContribution, 256);

        HashRequest hashRequest = cryptographyUtil.generateHash(
                sharedKey,
                nonce1,
                mac,
                otherContribution,
                nonce2,
                publicKey
        );

        byte[] authKey = hashRequest.getAuthKey();
        byte[] m2hmac = hashRequest.getM2hmac();
        byte[] keyWrapKey = hashRequest.getKeyWrapKey();

        System.out.println("Encoding nonce...");
        HashResponse hashResponse = HashResponse.fromJson(simpleHttpClient.post(registerHashUrl, hashRequest.asMap()));

        byte[] returnedNonce2 = hashResponse.getReturnedNonce2();
        byte[] eHash = hashResponse.geteHash();
        byte[] m3hmac = hashResponse.getM3hmac();

        if (!Arrays.equals(
                returnedNonce2,
                nonce2)) {
            throw new IllegalStateException("The nonce N2 doesn't match: generated[" + ByteUtils.bytesToHex(nonce2) + "] vs returned[" + ByteUtils.bytesToHex(returnedNonce2) + "]");
        }

        byte[] hmac = cryptographyUtil.hmac(authKey, Bytes.concat(nonce1, nonce2, mac, publicKey, m2hmac, nonce2, eHash));

        if (!Arrays.equals(m3hmac, hmac)) {
            throw new IllegalStateException("M3 HMAC doesn't match: generated[" + ByteUtils.bytesToHex(hmac) + "] vs returned[" + ByteUtils.bytesToHex(m3hmac) + "]");
        }

        System.out.println("Please enter the PIN shown on the DPT-RP1: ");
        Scanner scanner = new Scanner(System.in);
        String pin = scanner.next();

        byte[] psk = cryptographyUtil.hmac(authKey, pin.getBytes(StandardCharsets.UTF_8));
        byte[] rs = cryptographyUtil.generateNonce(16);
        byte[] rHash = cryptographyUtil.hmac(authKey, Bytes.concat(rs, psk, otherContribution, publicKey));

        byte[] wrappedRs = cryptographyUtil.wrap(rs, authKey, keyWrapKey);

        byte[] m4hmac = cryptographyUtil.hmac(authKey, Bytes.concat(nonce2, eHash, m3hmac, nonce1, rHash, wrappedRs));

        Map<String, String> m4 = new HashMap<>();
        m4.put("a", Base64.getEncoder().encodeToString(nonce1));
        m4.put("b", Base64.getEncoder().encodeToString(rHash));
        m4.put("d", Base64.getEncoder().encodeToString(wrappedRs));
        m4.put("e", Base64.getEncoder().encodeToString(m4hmac));

        System.out.println("Getting certificate from device CA...");
        Map<String, String> m5 = fromJSON(simpleHttpClient.post(registerCaUrl, m4));

        returnedNonce2 = b64decoder.decode(m5.get("a"));
        if (!Arrays.equals(returnedNonce2, nonce2)) {
            throw new IllegalStateException("The nonce N2 doesn't match: generated[" + ByteUtils.bytesToHex(nonce2) + "] vs returned[" + ByteUtils.bytesToHex(returnedNonce2) + "]");
        }

        byte[] wrappedEsCert = b64decoder.decode(m5.get("d"));
        byte[] m5hmac = b64decoder.decode(m5.get("e"));

        if (!Arrays.equals(m5hmac, cryptographyUtil.hmac(authKey, Bytes.concat(nonce1, rHash, wrappedRs, m4hmac, nonce2, wrappedEsCert)))) {
            throw new IllegalStateException("M5 HMAC doesn't match!");
        }

        byte[] esCert = cryptographyUtil.unwrap(wrappedEsCert, authKey, keyWrapKey);
        byte[] es = new byte[16];
        System.arraycopy(esCert, 0, es, 0, 16);

        byte[] cert = new byte[esCert.length - 16];
        System.arraycopy(esCert, 16, cert, 0, esCert.length - 16);

        byte[] eHashCheck = cryptographyUtil.hmac(authKey, Bytes.concat(es, psk, otherContribution, publicKey));
        if (!Arrays.equals(eHashCheck, eHash)) {
            throw new IllegalStateException("eHash does not match!");
        }

        System.out.println("Generating RSA2048 keys");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048); // Default exponent is 65537
        KeyPair rsaKeyPair = kpg.generateKeyPair();

        String keyPubC = cryptographyUtil.exportPublicKeyToPEM(rsaKeyPair.getPublic());

        String selfDeviceId = UUID.randomUUID().toString();
        System.out.println("Device ID: " + selfDeviceId);

        byte[] wrappedDIDKPUBC = cryptographyUtil.wrap(
                Bytes.concat(
                        selfDeviceId.getBytes(StandardCharsets.UTF_8),
                        keyPubC.getBytes(StandardCharsets.UTF_8)
                ),
                authKey,
                keyWrapKey
        );

        byte[] m6hmac = cryptographyUtil.hmac(authKey, Bytes.concat(nonce2, wrappedEsCert, m5hmac, nonce1, wrappedDIDKPUBC));

        Map<String, String> m6 = new HashMap<>();
        m6.put("a", Base64.getEncoder().encodeToString(nonce1));
        m6.put("d", Base64.getEncoder().encodeToString(wrappedDIDKPUBC));
        m6.put("e", Base64.getEncoder().encodeToString(m6hmac));

        System.out.println("Registering device...");
        String registerResponse = simpleHttpClient.post(registerUrl, m6);

        System.out.println(registerResponse);

        System.out.println("Cleaning up...");
        cleanupResponse = simpleHttpClient.put(cleanupUrl);
        System.out.println(cleanupResponse);

        String certificate = new String(cert, StandardCharsets.UTF_8);
        String privateKey = cryptographyUtil.exportPrivateKeyToPEM(rsaKeyPair.getPrivate());

        return new RegistrationResponse(certificate, privateKey, selfDeviceId);
    }


    /*

        return (cert.decode('utf-8'),
                new_key.exportKey("PEM").decode('utf-8'),
                selfDeviceId.decode('utf-8'))
     */

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException, NoSuchPaddingException, BadPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException {
        RegistrationResponse registrationResponse = new DigitalPaper(
                "192.168.0.48", 
                Base64.getDecoder(),
                new SimpleHttpClient(), 
                new DiffieHelman(), 
                new CryptographyUtil()).register();
        System.out.println(registrationResponse);
    }

}
