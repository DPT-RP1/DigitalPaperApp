package net.sony.util;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import javax.naming.ConfigurationException;
import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class SSLFactory {

    private static final String TEMPORARY_KEY_PASSWORD = "changeit";

    private SSLContext sslContext;
    private SSLSocketFactory sslSocketFactory;

    public SSLFactory(String certificatePem, String privateKeyPem) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, ConfigurationException, UnrecoverableKeyException {
        KeyStore keyStore = getKeyStore(certificatePem, privateKeyPem);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, TEMPORARY_KEY_PASSWORD.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();

        TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init(keyStore);
        TrustManager[] trustManagers = tmfactory.getTrustManagers();

        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        sslSocketFactory = sslContext.getSocketFactory();
    }

    private KeyStore getKeyStore(String certificatePem, String privateKeyPem) throws ConfigurationException {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyPem);
            Certificate caCertificate = loadCertificate(certificatePem);

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca-cert", caCertificate);
            keyStore.setCertificateEntry("client-cert", caCertificate);
            keyStore.setKeyEntry("client-key", privateKey, TEMPORARY_KEY_PASSWORD.toCharArray(), new Certificate[]{caCertificate});
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new ConfigurationException("Cannot build keystore");
        }
    }

    private Certificate loadCertificate(String certificatePem) throws IOException, GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
        final byte[] content = readPemContent(certificatePem);
        return certificateFactory.generateCertificate(new ByteArrayInputStream(content));
    }

    private PrivateKey loadPrivateKey(String privateKeyPem) throws IOException, GeneralSecurityException {
        return pemLoadPrivateKeyEncoded(privateKeyPem);
    }

    private byte[] readPemContent(String pem) throws IOException {
        final byte[] content;
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            final PemObject pemObject = pemReader.readPemObject();
            content = pemObject.getContent();
        }
        return content;
    }

    private static PrivateKey pemLoadPrivateKeyEncoded(String privateKeyPem) throws GeneralSecurityException, IOException {
        // PKCS#8 format
        final String PEM_PRIVATE_START = "-----BEGIN PRIVATE KEY-----";
        final String PEM_PRIVATE_END = "-----END PRIVATE KEY-----";

        if (privateKeyPem.contains(PEM_PRIVATE_START)) { // PKCS#8 format
            privateKeyPem = privateKeyPem.replace(PEM_PRIVATE_START, "").replace(PEM_PRIVATE_END, "");
            privateKeyPem = privateKeyPem.replaceAll("\\s", "");
            byte[] pkcs8EncodedKey = Base64.getDecoder().decode(privateKeyPem);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8EncodedKey));
        }

        throw new GeneralSecurityException("Not supported format of a private key");
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }
}
