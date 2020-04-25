package net.sony.dpt.command.register;

import com.google.common.primitives.Bytes;
import net.sony.dpt.network.SimpleHttpClient;
import net.sony.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.UUID;

public class RegisterCommand {

    private static final String REG_PIN_URL = "/register/pin";
    private static final String REG_HASH_URL = "/register/hash";
    private static final String REG_CA_URL = "/register/ca";
    private static final String REG_URL = "/register";
    private static final String REG_CLEANUP_URL = "/register/cleanup";
    private static final int REG_PORT = 8080;
    private final String addr;
    private final SimpleHttpClient simpleHttpClient;
    private final CryptographyUtils cryptographyUtils;
    private final DiffieHelman diffieHelman;
    private final LogWriter logWriter;
    private final InputReader inputReader;
    private String registerPinUrl;
    private String registerHashUrl;
    private String registerCaUrl;
    private String registerUrl;
    private String cleanupUrl;

    public RegisterCommand(String addr,
                           SimpleHttpClient simpleHttpClient,
                           DiffieHelman diffieHelman,
                           CryptographyUtils cryptographyUtils,
                           LogWriter logWriter,
                           InputReader inputReader) {
        this.addr = addr;
        this.simpleHttpClient = simpleHttpClient;
        this.cryptographyUtils = cryptographyUtils;
        this.diffieHelman = diffieHelman;
        this.logWriter = logWriter;
        this.inputReader = inputReader;

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

    public void cleanup() throws IOException, InterruptedException {
        logWriter.log("Cleaning up...");
        String cleanupResponse = simpleHttpClient.put(cleanupUrl);
        logWriter.log(cleanupResponse);
    }

    public PinResponse requestPin() throws IOException, InterruptedException {
        logWriter.log("Requesting PIN...");
        return PinResponse.fromJson(simpleHttpClient.post(registerPinUrl));
    }

    public HashRequest buildHashRequest(PinResponse pinResponse, byte[] nonce2) throws InvalidKeyException, NoSuchAlgorithmException {
        byte[] nonce1 = pinResponse.getNonce1();
        byte[] mac = pinResponse.getMac();

        byte[] publicKey = diffieHelman.generatePublicKey();
        byte[] sharedKey = diffieHelman.generateSharedKey(pinResponse.getRawOtherContribution());

        return cryptographyUtils.generateHash(
                sharedKey,
                nonce1,
                mac,
                pinResponse.getOtherContribution(),
                nonce2,
                publicKey
        );
    }

    public HashResponse encodeNonce(PinResponse pinResponse, HashRequest hashRequest) throws IOException, InterruptedException, InvalidKeyException, NoSuchAlgorithmException {

        logWriter.log("Encoding nonce...");
        HashResponse response = HashResponse.fromJson(simpleHttpClient.post(registerHashUrl, hashRequest.asMap()));
        response.setHashRequest(hashRequest);

        if (!Arrays.equals(response.getReturnedNonce2(), hashRequest.getNonce2())) {
            throw new IllegalStateException("The nonce N2 doesn't match: generated[" + ByteUtils.bytesToHex(hashRequest.getNonce2()) + "] vs returned[" + ByteUtils.bytesToHex(response.getReturnedNonce2()) + "]");
        }

        byte[] hmac = cryptographyUtils.hmac(hashRequest.getAuthKey(),
                Bytes.concat(
                        pinResponse.getNonce1(),
                        hashRequest.getNonce2(),
                        pinResponse.getMac(),
                        hashRequest.getPublicKey(),
                        hashRequest.getM2hmac(),
                        hashRequest.getNonce2(),
                        response.geteHash()
                )
        );

        if (!Arrays.equals(response.getM3hmac(), hmac)) {
            throw new IllegalStateException("M3 HMAC doesn't match: generated[" + ByteUtils.bytesToHex(hmac) + "] vs returned[" + ByteUtils.bytesToHex(response.getM3hmac()) + "]");
        }

        return response;
    }

    public KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        logWriter.log("Generating RSA2048 keys");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048); // Default exponent is 65537
        return kpg.generateKeyPair();
    }

    public void registerDevice(KeyPair rsaKeyPair, String selfDeviceId, PinResponse pinResponse, HashRequest hashRequest, RegisterCaResponse registerCaResponse) throws NoSuchAlgorithmException, IOException, InterruptedException, IllegalBlockSizeException, InvalidAlgorithmParameterException, BadPaddingException, NoSuchPaddingException, InvalidKeyException {

        String keyPubC = cryptographyUtils.exportPublicKeyToPEM(rsaKeyPair.getPublic());


        logWriter.log("Device ID: " + selfDeviceId);

        byte[] wrappedDIDKPUBC = cryptographyUtils.wrap(
                Bytes.concat(
                        selfDeviceId.getBytes(StandardCharsets.UTF_8),
                        keyPubC.getBytes(StandardCharsets.UTF_8)
                ),
                hashRequest.getAuthKey(),
                hashRequest.getKeyWrapKey()
        );

        byte[] m6hmac = cryptographyUtils.hmac(hashRequest.getAuthKey(),
                Bytes.concat(
                        hashRequest.getNonce2(),
                        registerCaResponse.getWrappedEsCert(),
                        registerCaResponse.getM5hmac(),
                        pinResponse.getNonce1(),
                        wrappedDIDKPUBC
                )
        );

        RegistrationRequest registrationRequest = new RegistrationRequest(pinResponse.getNonce1(), wrappedDIDKPUBC, m6hmac);

        logWriter.log("Registering device...");
        String registerResponse = simpleHttpClient.post(registerUrl, registrationRequest.asMap());

        logWriter.log(registerResponse);
    }

    public String readDevicePin() {
        logWriter.log("Please enter the PIN shown on the DPT-RP1: ");
        return inputReader.read();
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

        cleanup();

        PinResponse pinResponse = requestPin();

        byte[] nonce2 = cryptographyUtils.generateNonce(16);
        HashRequest hashRequest = buildHashRequest(pinResponse, nonce2);
        HashResponse hashResponse = encodeNonce(pinResponse, hashRequest);

        String pin = readDevicePin();

        RegisterCaResponse registerCaResponse = registerCa(pinResponse, hashRequest, hashResponse, pin);

        KeyPair rsaKeyPair = generateRSAKeyPair();
        String selfDeviceId = UUID.randomUUID().toString();
        registerDevice(rsaKeyPair, selfDeviceId, pinResponse, hashRequest, registerCaResponse);

        cleanup();

        String certificate = new String(registerCaResponse.getCert(), StandardCharsets.UTF_8);
        String privateKey = cryptographyUtils.exportPrivateKeyToPEM(rsaKeyPair.getPrivate());

        return new RegistrationResponse(certificate, privateKey, selfDeviceId);
    }

    public RegisterCaResponse registerCa(PinResponse pinResponse, HashRequest hashRequest, HashResponse hashResponse, String pin) throws InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, IOException, InterruptedException {
        byte[] authKey = hashRequest.getAuthKey();
        byte[] otherContribution = pinResponse.getOtherContribution();
        byte[] publicKey = hashRequest.getPublicKey();
        byte[] keyWrapKey = hashRequest.getKeyWrapKey();
        byte[] nonce2 = hashRequest.getNonce2();
        byte[] nonce1 = pinResponse.getNonce1();


        byte[] psk = cryptographyUtils.hmac(authKey, pin.getBytes(StandardCharsets.UTF_8));
        byte[] rs = cryptographyUtils.generateNonce(16);
        byte[] rHash = cryptographyUtils.hmac(authKey, Bytes.concat(rs, psk, otherContribution, publicKey));

        byte[] wrappedRs = cryptographyUtils.wrap(rs, authKey, keyWrapKey);

        byte[] m4hmac = cryptographyUtils.hmac(authKey, Bytes.concat(nonce2, hashResponse.geteHash(), hashResponse.getM3hmac(), nonce1, rHash, wrappedRs));

        logWriter.log("Getting certificate from device CA...");
        RegisterCaResponse registerCaResponse = RegisterCaResponse.fromJson(simpleHttpClient.post(registerCaUrl, new RegisterCaRequest(nonce1, rHash, wrappedRs, m4hmac).asMap()));

        if (!Arrays.equals(registerCaResponse.getReturnedNonce2(), nonce2)) {
            throw new IllegalStateException("The nonce N2 doesn't match: generated[" + ByteUtils.bytesToHex(nonce2) + "] vs returned[" + ByteUtils.bytesToHex(registerCaResponse.getReturnedNonce2()) + "]");
        }

        byte[] wrappedEsCert = registerCaResponse.getWrappedEsCert();
        byte[] m5hmac = registerCaResponse.getM5hmac();

        if (!Arrays.equals(m5hmac, cryptographyUtils.hmac(authKey, Bytes.concat(nonce1, rHash, wrappedRs, m4hmac, nonce2, wrappedEsCert)))) {
            throw new IllegalStateException("M5 HMAC doesn't match!");
        }

        byte[] esCert = cryptographyUtils.unwrap(wrappedEsCert, authKey, keyWrapKey);
        byte[] es = new byte[16];
        System.arraycopy(esCert, 0, es, 0, 16);

        byte[] cert = new byte[esCert.length - 16];
        System.arraycopy(esCert, 16, cert, 0, esCert.length - 16);

        byte[] eHashCheck = cryptographyUtils.hmac(authKey, Bytes.concat(es, psk, otherContribution, publicKey));
        if (!Arrays.equals(eHashCheck, hashResponse.geteHash())) {
            throw new IllegalStateException("eHash does not match!");
        }

        registerCaResponse.setCert(cert);
        return registerCaResponse;
    }

}
