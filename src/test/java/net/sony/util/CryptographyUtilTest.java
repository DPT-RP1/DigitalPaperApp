package net.sony.util;

import com.google.common.primitives.Bytes;
import net.sony.dpt.command.register.HashRequest;
import org.junit.Test;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static net.sony.util.ByteUtils.hexToByte;
import static org.junit.Assert.assertEquals;


public class CryptographyUtilTest {

    /**
     * We protect against changes in the underlying method
     */
    @Test
    public void verifyHashStability() throws NoSuchAlgorithmException, InvalidKeyException {
        BigInteger privateKey = new BigInteger("50614096391648629485607403163385118879437263429458678389725629493477950995502");
        byte[] nonce2 = {29, -40, 91, -44, 83, 80, 127, -126, 3, 84, -92, 76, 111, -107, -124, -62};

        String nonce1b64 = "hn4v6BLaRl+UWIRfXs9VHA==";
        String macb64 = "rImV9/iZ";
        String ybb64 = "AN2q7dz8gtIrs1OL2bMO8w/B8ohHGNOjKDIhVfrRN7YKdctiRFYjiqDeK4cB9ZqdarwkN4UQD+pJZ5iCPwgoCED82ZGCGhUgQjqceOmUxrAInZBkOqrk8hmmvaYVETagwmVZDuZ9iB9GkDMzICJWQcJaQ0BEPftkx/dxcky6KIBkifmCO8TejDnrSwoeJUwbfvf7+gG+efsZcI5twyz0OJWhW6urx3RZs0R3GKW3cw65NVEQ+wt5WO4dQ32bkUdz1sWBPtJ39cwBJxU4CmDz9AUv2Y5h2DHXxZZOaAGAhOvjPVtWX0yDnbz1VouljnIBEb6+J47PfZTLua5YMc58svs=";

        Map<String, String> expected = new HashMap<>();
        expected.put("a", "hn4v6BLaRl+UWIRfXs9VHA==");
        expected.put("b", "Hdhb1FNQf4IDVKRMb5WEwg==");
        expected.put("c", "rImV9/iZ");
        expected.put("d", "AG+urFlkQ+ZLD/i8SVn4EEWF8ilj0vXhntnjpvpmTEM9RF+nwYrdbrO797OiTFZPsDFrN9aYTpta2s7ef+2JvyYW1ztxQw+y/cvoun2wjSGrTQLnaHpY1ShxOPD4CdZ//apXgqdp+S5tEm6LyYT5NLEWw3zSabohT67S/SURkzIEXQruS3LOKDZZ5y/ie3dCXNnYsh0dhINhDs/zF+uhTKfBALfQyQLf00/f/nZTUz67184HO7+cMA2FCd41AXAuSOKLdohq9/4QB0do9IVydCXAQJIed2wovt/CJ5m/HLmpyqm9EQq+h1r/8b+tHuBdoBZ0flp2NZd7rowu0geWGDA=");
        expected.put("e", "T69GcvoZvRTKWqcDgXqh7P8mIo+wvkEEwpK3BH+jrRg=");

        DiffieHelman dh = new DiffieHelman(DiffieHelman.Prime.P_2048_BIT, privateKey);
        CryptographyUtil cryptographyUtil = new CryptographyUtil();

        byte[] nonce1 = Base64.getDecoder().decode(nonce1b64);
        byte[] mac = Base64.getDecoder().decode(macb64);

        byte[] otherContribution = Base64.getDecoder().decode(ybb64);
        otherContribution = BigIntegerUtils.projectArray(otherContribution, 256);

        byte[] publicKey = dh.generatePublicKey();

        byte[] sharedKey = dh.generateSharedKey(otherContribution);

        HashRequest hashRequest = cryptographyUtil.generateHash(
                sharedKey,
                nonce1,
                mac,
                otherContribution,
                nonce2,
                publicKey);

        Map<String, String> actual = hashRequest.asMap();

        assertEquals(expected, actual);
    }

    @Test
    public void verifyDerivedKeyAlgo() {
        String expectedHex = "e3ec138c347e705e2e6110a601dcdc12d1bd5e0780bde38edcd320e74e67e010155dc7c1cb47233ae0bf7d9d66d22ffd".toUpperCase();

        byte[] sharedKey = hexToByte("c28f73d4c22f6341a2f9a631d91d746c337586f39202105a18d08a06cdabcc4c57c4f88d7a1637c9cf9b5a9fb50e236c9c3e2bd257bb78019e42f832ef1d6fc7b55e222fd6a1dde489bae30c6f91027d7ac7be4a296a0e90172037c86079b2b73f7501be9c8fe815c11a54642e94c0ae797f5812c130bd5f4afb4084c9f15f07eae32b1cafb4bc31fd200ae097bb3a8ee6a672ed12f80444bc07153cf8029fe78f6ba56383d7de3bf06907935005f5eef939e73d8324bf25292b1ae8f91725fd9138fa4175a68a1dc796d12860f3e9eb9cd7d48620872df58e7dcfca2615465329c9d7598182633abaab3ef45af0f3f6d3378bd57d11027990f9a47b3deafa48");
        byte[] salt = hexToByte("867e2fe812da465f9458845f5ecf551cac8995f7f8991dd85bd453507f820354a44c6f9584c2");

        CryptographyUtil cryptographyUtil = new CryptographyUtil();
        String actualHex = ByteUtils.bytesToHex(
                cryptographyUtil.deriveKey(sharedKey, salt)
        );

        assertEquals(expectedHex, actualHex);
    }

    @Test
    public void verifyHMACInput() {
        String expectedHex = "867e2fe812da465f9458845f5ecf551cac8995f7f899ddaaeddcfc82d22bb3538bd9b30ef30fc1f2884718d3a328322155fad137b60a75cb624456238aa0de2b8701f59a9d6abc243785100fea496798823f08280840fcd991821a1520423a9c78e994c6b0089d90643aaae4f219a6bda6151136a0c265590ee67d881f4690333320225641c25a4340443dfb64c7f771724cba28806489f9823bc4de8c39eb4b0a1e254c1b7ef7fbfa01be79fb19708e6dc32cf43895a15bababc77459b3447718a5b7730eb9355110fb0b7958ee1d437d9b914773d6c5813ed277f5cc012715380a60f3f4052fd98e61d831d7c5964e68018084ebe33d5b565f4c839dbcf5568ba58e720111bebe278ecf7d94cbb9ae5831ce7cb2fb867e2fe812da465f9458845f5ecf551c1dd85bd453507f820354a44c6f9584c2ac8995f7f899006faeac596443e64b0ff8bc4959f8104585f22963d2f5e19ed9e3a6fa664c433d445fa7c18add6eb3bbf7b3a24c564fb0316b37d6984e9b5adacede7fed89bf2616d73b71430fb2fdcbe8ba7db08d21ab4d02e7687a58d5287138f0f809d67ffdaa5782a769f92e6d126e8bc984f934b116c37cd269ba214faed2fd25119332045d0aee4b72ce283659e72fe27b77425cd9d8b21d1d8483610ecff317eba14ca7c100b7d0c902dfd34fdffe7653533ebbd7ce073bbf9c300d8509de3501702e48e28b76886af7fe10074768f485727425c040921e776c28bedfc22799bf1cb9a9caa9bd110abe875afff1bfad1ee05da016747e5a7635977bae8c2ed207961830".toUpperCase();

        byte[] nonce2 = {29, -40, 91, -44, 83, 80, 127, -126, 3, 84, -92, 76, 111, -107, -124, -62};

        String nonce1b64 = "hn4v6BLaRl+UWIRfXs9VHA==";
        String macb64 = "rImV9/iZ";
        String ybb64 = "AN2q7dz8gtIrs1OL2bMO8w/B8ohHGNOjKDIhVfrRN7YKdctiRFYjiqDeK4cB9ZqdarwkN4UQD+pJZ5iCPwgoCED82ZGCGhUgQjqceOmUxrAInZBkOqrk8hmmvaYVETagwmVZDuZ9iB9GkDMzICJWQcJaQ0BEPftkx/dxcky6KIBkifmCO8TejDnrSwoeJUwbfvf7+gG+efsZcI5twyz0OJWhW6urx3RZs0R3GKW3cw65NVEQ+wt5WO4dQ32bkUdz1sWBPtJ39cwBJxU4CmDz9AUv2Y5h2DHXxZZOaAGAhOvjPVtWX0yDnbz1VouljnIBEb6+J47PfZTLua5YMc58svs=";

        byte[] nonce1 = Base64.getDecoder().decode(nonce1b64);
        byte[] mac = Base64.getDecoder().decode(macb64);
        byte[] otherContribution = Base64.getDecoder().decode(ybb64);
        otherContribution = BigIntegerUtils.projectArray(otherContribution, 256);

        BigInteger privateKey = new BigInteger("50614096391648629485607403163385118879437263429458678389725629493477950995502");
        byte[] publicKey = new DiffieHelman(DiffieHelman.Prime.P_2048_BIT, privateKey).generatePublicKey();
        publicKey = BigIntegerUtils.projectArray(publicKey, 257);
        publicKey[0] = (byte) 0;

        String actualHex = ByteUtils.bytesToHex(Bytes.concat(nonce1, mac, otherContribution, nonce1, nonce2, mac, publicKey));

        assertEquals(expectedHex, actualHex);
    }

    @Test
    public void verifyHMacResult() throws InvalidKeyException, NoSuchAlgorithmException {
        String expectedHmac = "4faf4672fa19bd14ca5aa703817aa1ecff26228fb0be4104c292b7047fa3ad18".toUpperCase();

        String updateHex = "867e2fe812da465f9458845f5ecf551cac8995f7f899ddaaeddcfc82d22bb3538bd9b30ef30fc1f2884718d3a328322155fad137b60a75cb624456238aa0de2b8701f59a9d6abc243785100fea496798823f08280840fcd991821a1520423a9c78e994c6b0089d90643aaae4f219a6bda6151136a0c265590ee67d881f4690333320225641c25a4340443dfb64c7f771724cba28806489f9823bc4de8c39eb4b0a1e254c1b7ef7fbfa01be79fb19708e6dc32cf43895a15bababc77459b3447718a5b7730eb9355110fb0b7958ee1d437d9b914773d6c5813ed277f5cc012715380a60f3f4052fd98e61d831d7c5964e68018084ebe33d5b565f4c839dbcf5568ba58e720111bebe278ecf7d94cbb9ae5831ce7cb2fb867e2fe812da465f9458845f5ecf551c1dd85bd453507f820354a44c6f9584c2ac8995f7f899006faeac596443e64b0ff8bc4959f8104585f22963d2f5e19ed9e3a6fa664c433d445fa7c18add6eb3bbf7b3a24c564fb0316b37d6984e9b5adacede7fed89bf2616d73b71430fb2fdcbe8ba7db08d21ab4d02e7687a58d5287138f0f809d67ffdaa5782a769f92e6d126e8bc984f934b116c37cd269ba214faed2fd25119332045d0aee4b72ce283659e72fe27b77425cd9d8b21d1d8483610ecff317eba14ca7c100b7d0c902dfd34fdffe7653533ebbd7ce073bbf9c300d8509de3501702e48e28b76886af7fe10074768f485727425c040921e776c28bedfc22799bf1cb9a9caa9bd110abe875afff1bfad1ee05da016747e5a7635977bae8c2ed207961830".toUpperCase();
        String derivedKeyHex = "e3ec138c347e705e2e6110a601dcdc12d1bd5e0780bde38edcd320e74e67e010155dc7c1cb47233ae0bf7d9d66d22ffd".toUpperCase();

        byte[] update = ByteUtils.hexToByte(updateHex);
        byte[] authKey = Arrays.copyOfRange(ByteUtils.hexToByte(derivedKeyHex), 0, 32);

        CryptographyUtil cryptographyUtil = new CryptographyUtil();
        String actualHmac = ByteUtils.bytesToHex(cryptographyUtil.hmac(authKey, update));

        assertEquals(expectedHmac, actualHmac);
    }
}
