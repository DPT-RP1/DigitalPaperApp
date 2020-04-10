package net.sony.util;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class SSLFactory {

    private static final String TEMPORARY_KEY_PASSWORD = "changeit";

    private final SSLContext sslContext;
    private final CryptographyUtils cryptographyUtils;

    public SSLFactory(final String certificatePem, final String privateKeyPem, final CryptographyUtils cryptographyUtils) throws GeneralSecurityException, IOException {
        this.cryptographyUtils = cryptographyUtils;

        KeyStore keyStore = getKeyStore(certificatePem, privateKeyPem);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, TEMPORARY_KEY_PASSWORD.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();

        TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init(keyStore);
        TrustManager[] trustManagers = tmfactory.getTrustManagers();

        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
    }

    private KeyStore getKeyStore(String certificatePem, String privateKeyPem) throws GeneralSecurityException, IOException {
        PrivateKey privateKey = cryptographyUtils.readPrivateKeyFromPEM(privateKeyPem);

        Certificate caCertificate = loadCertificate(certificatePem);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca-cert", caCertificate);
        keyStore.setCertificateEntry("client-cert", caCertificate);
        keyStore.setKeyEntry("client-key", privateKey, TEMPORARY_KEY_PASSWORD.toCharArray(), new Certificate[]{caCertificate});
        return keyStore;

    }

    private Certificate loadCertificate(String certificatePem) throws IOException, GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
        final byte[] content = readPemContent(certificatePem);
        return certificateFactory.generateCertificate(new ByteArrayInputStream(content));
    }

    private byte[] readPemContent(String pem) throws IOException {
        final byte[] content;
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            final PemObject pemObject = pemReader.readPemObject();
            content = pemObject.getContent();
        }
        return content;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }
}
