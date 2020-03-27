package net.sony.dpt;

public class RegistrationResponse {
    /**
     * a PEM-encoded X.509 server certificate, issued by the CA on the device
     */
    private String pemCertificate;

    /**
     * a PEM-encoded 2048-bit RSA private key
     */
    private String privateKey;

    /**
     * The ID of the client, wanting to connect to the reader.
     */
    private String clientId;

    public RegistrationResponse(String pemCertificate, String privateKey, String clientId) {
        this.pemCertificate = pemCertificate;
        this.privateKey = privateKey;
        this.clientId = clientId;
    }

    public String getPemCertificate() {
        return pemCertificate;
    }

    public void setPemCertificate(String pemCertificate) {
        this.pemCertificate = pemCertificate;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public String toString() {
        return "Registration{" +
                "\npemCertificate='" + pemCertificate + '\'' +
                ", \nprivateKey='" + privateKey + '\'' +
                ", \nclientId='" + clientId + '\'' +
                '}';
    }
}
