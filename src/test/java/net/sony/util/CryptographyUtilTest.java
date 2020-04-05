package net.sony.util;

import com.google.common.primitives.Bytes;
import net.sony.dpt.command.register.HashRequest;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

import static net.sony.util.ByteUtils.bytesToHex;
import static net.sony.util.ByteUtils.hexToByte;
import static org.junit.Assert.*;


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

        Map<String, Object> expected = new HashMap<>();
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

        Map<String, Object> actual = hashRequest.asMap();

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

    @Test
    public void generateNonceShouldBeCorrectSize() {
        CryptographyUtil cryptographyUtil = new CryptographyUtil();
        for (int i = 0; i < 2048; i++) {
            byte[] result = cryptographyUtil.generateNonce(i);
            assertEquals(i, result.length);
        }
    }

    @Test
    public void generateNonceShouldBeRandom() {
        CryptographyUtil cryptographyUtil = new CryptographyUtil();
        Set<String> generatedNonces = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            byte[] result = cryptographyUtil.generateNonce(2048);
            String descriptor = ByteUtils.bytesToHex(result);
            assertFalse(generatedNonces.contains(descriptor));
            generatedNonces.add(descriptor);
        }
    }

    @Test
    public void wrapShouldNotRegress() throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        CryptographyUtil cryptographyUtil = new CryptographyUtil() {
            @Override
            public byte[] generateNonce(int size) {
                return hexToByte("AEEFDDCCDC1545A1454545EFEFEF4544");
            }
        };
        byte[] data = ByteUtils.hexToByte("ABABABABAABABABACCDDEFFFEDDBBBCBCBAAADEEEF");
        byte[] authKey = ByteUtils.hexToByte("EEFFEAVAAVCDDDEEFFAADEDEDEDFFFAA");
        byte[] keyWrapKey = ByteUtils.hexToByte("EFEFABABACDCDCBABABEFEFEEFABABAB");

        byte[] result = cryptographyUtil.wrap(data, authKey, keyWrapKey);
        assertEquals(
                "F5D9A4759330EB01D439FAABB74740D6551AFB7C28A5E60A4671C27A9F1274ABAEEFDDCCDC1545A1454545EFEFEF4544",
                bytesToHex(result));
    }

    @Test
    public void unwrapShouldRevertWrap() throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        CryptographyUtil cryptographyUtil = new CryptographyUtil() {
            @Override
            public byte[] generateNonce(int size) {
                return hexToByte("AEEFDDCCDC1545A1454545EFEFEF4544");
            }
        };
        byte[] data = ByteUtils.hexToByte("ABABABABAABABABACCDDEFFFEDDBBBCBCBAAADEEEF");
        byte[] authKey = ByteUtils.hexToByte("EEFFEAVAAVCDDDEEFFAADEDEDEDFFFAA");
        byte[] keyWrapKey = ByteUtils.hexToByte("EFEFABABACDCDCBABABEFEFEEFABABAB");

        byte[] wrap = cryptographyUtil.wrap(data, authKey, keyWrapKey);
        byte[] unwrap = cryptographyUtil.unwrap(wrap, authKey, keyWrapKey);

        String actual = bytesToHex(unwrap);
        assertEquals(bytesToHex(data), actual);
    }

    @Test
    public void publickKeyShouldExportToPem() throws IOException {
        CryptographyUtil cryptographyUtil = new CryptographyUtil();

        PublicKey publicKey = new PublicKey() {
            @Override
            public String getAlgorithm() {
                return "RSA";
            }

            @Override
            public String getFormat() {
                return "NA";
            }

            @Override
            public byte[] getEncoded() {
                return hexToByte("AABBCCDDEEFF001122445533557899ABCDDE");
            }
        };
        String pem = cryptographyUtil.exportPublicKeyToPEM(publicKey);
        assertEquals(
                "-----BEGIN PUBLIC KEY-----\n" +
                        "qrvM3e7/ABEiRFUzVXiZq83e\n" +
                        "-----END PUBLIC KEY-----\n", pem);
    }

    @Test
    public void privateKeyShouldExportToPem() throws IOException, GeneralSecurityException {
        CryptographyUtil cryptographyUtil = new CryptographyUtil();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048); // Default exponent is 65537
        KeyPair pair = kpg.generateKeyPair();
        String pem = cryptographyUtil.exportPrivateKeyToPEM(pair.getPrivate());
        byte[] expectedPrivateKey = pair.getPrivate().getEncoded();
        byte[] actualPrivateKey = cryptographyUtil.readPrivateKeyFromPEM(pem).getEncoded();

        assertArrayEquals(expectedPrivateKey, actualPrivateKey);
    }

    @Test
    public void verifySignatureSHA256() throws Exception {
        String privateKeyHex = "308204BD020100300D06092A864886F70D0101010500048204A7308204A3020100028201010092D1745A32A207B65D781523FBCBCF2DCDDAD174A1A0A89F2A244D7AA8A1EFE0F3F9D32E4F27C333E85D711FD2AABAA37198A230A1BDE398627523F2FE485715F79C31F270025B4EA25F87B6415AC2D1A11FF9B989B376DEBC675C6F33D77D4A3D394F82D1CBB8A97D10121537BE0B19C366F82AF3C7DBBC31B0B716F0AD608B0F29556A48F83D4DE92AED90BA56A493958FD9F12D0861CD544D61796E2776CB8209C1B1E23895F060C8B49A8D93ECC7C4BF1F1456ED6901B58ECD337AB67B6F99EB0749C1666E891DE68591561BF7039CC120A9776EB4198442378A80470F21C78DC9E9CE18E71E1973CC12D2E88661B5E05C11F850021F721740482020DA59020301000102820100591EF7C800D0466A36D6BBCE79FC3FA9083A79C6988E138D7A614AFED7FA64C8629115D6188A847DAFE178D7DE6370A3E242CAC1468D23E8CE6B590519C203CAFBE13E9871D19C67613D27FE4431B9ECD227BCC919836CF6CBDADA4B4E66D2510C550BA4D781187919C7759297A1AECF56C3DC8506321D7A619769AC6D3071574AC27D4E3A3D8BA571A0FB29F47E3B7434D70ACF8B3C4D22944CC128BF2418A557D1478704974B47CBF51E0B3B5DE4F041339954CD72D3E1B686FC0CC08841933DBDE0340C1EDEC0AF1C5E38AD160BD39F18881212624CC5FD86442CEEE496FA3DBCC95498A0BF89873C3DE544A532052AFA36794F7A93F47CF2B502AF40AE6102818100DDAE469F55E53BBFF39AC8257F29996E2EB659C369DF9F704EE4DAC17E45D951A20D49EFE0EA8EAD3A47E1C4095D5E749FC69B8609049DC491592D64F36F0572D792860244B9DED495E85CEA70C7D56C1612C80647FA39D296A5B218AC9CD3FAC0F2CA3B77738391CA82575465B778FFF93121FCA94229366D0D41282D1AD55502818100A98C334CFE0A8333B4A166619E8ED8166162778D4F5ACBC40991D47BAD5655F921C63CBBC32722B85D1AF822252C3821EA4BF5E0F6D8603BA5580F9FCE5ABD5B02F8EE82361D31F98DFF13AE0478407831B96834D8C262D367631570E1633A31D8515C9ECAA61BC84A87BBC067B16D7B15B613CC319EEC565881D16017A7F0F5028180093BFB4123E8DAA652557E44E199300500F923A01A46F0735336014ED21DC2C1BDC863EE1426712F1220706D241EB9928E1D4DD93582F5B77C7E847F920C6BE3AECB31BDE27303AF43575C977F7F338ABF18A5306DCB24A17B1907E4333C8D3002DD9A4303E4D1F43EC55331F6D2BFBD99F9CBFAA46A57212745C8130E5DF1C90281804B7B8087617E5AA51560D9CCD223742E9A9294F913802FC18A25237D2051949B029F58009BC47B9FAACBCBF69FA80D218446E7238DA20D4DE0B1D0DAEBAAD82C81A943BE32CB52A970BF440AB030BD3B05A02EB5805F22524DEFFDE6B06155D245250BE022064BCE22E844FD46ECE5F9EB539182D20097E56527FE881260115502818100A494A1435574C4497D175B94AC4D604172FACB2C21EA7C2FD294A1600960AE92BC1FE1F7D8598075DD28DD43C92AF311D8BA650A3619BEFB4BCF715DE52ECBD99924D73829176E10E15778976928FD079AB35466D7D45E44D3D96F4D851669860C3F12BAA0DEC05F61C6DC853749B7476273CE55FA6DEB3866C6EB6ADAD63FCE";
        byte[] privateKey = hexToByte(privateKeyHex);

        byte[] input = hexToByte("AABBCCDDEEABCDE12457896321456987ACCDEB");

        CryptographyUtil cryptographyUtil = new CryptographyUtil() {
            @Override
            public PrivateKey readPrivateKeyFromPEM(String pemFile) throws GeneralSecurityException {
                return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKey));
            }
        };

        String actual = bytesToHex(cryptographyUtil.signSHA256RSA(input, null));

        assertEquals(
                "00C58E874270674BE5AA58B6E8DB423EBB5D9703509EF1D212F6B1096C5EC524EA70D36770BF9E6C54CB4C6B429BAA4CDC5DF80699A686B4D0E0529F137F9CA1016066B378E566CADA9DEB051D00BCE58E600057299C78672F5A669B371FB67F28B2528200DD7BC2C32EED87B6015EF874FA1E3B501DDC56DD9228F4CFF3992DDD667BC1596852702AA1A25DD07927E8B4BACF7A2327572D3A4DD5A20960D9D96C0A887880A2C8E3177B97DDEB7FB6983DA37187D8EECEF3F8B09593C0347869054BE6A53D69D1ECB0F633E16E4D1C78A338D4E7E42E74186A12009D02A1DEBF6BCE1D91E1C2AB1984E1850F543900A5261579E6F0ED67644DB65CD8CC4A6FB9",
                actual
        );
    }

    @Test
    public void unwrappedKwaShouldVerify() throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        CryptographyUtil cryptographyUtil = new CryptographyUtil() {
            @Override
            public byte[] generateNonce(int size) {
                return hexToByte("AEEFDDCCDC1545A1454545EFEFEF4544");
            }
        };
        byte[] data = ByteUtils.hexToByte("ABABABABAABABABACCDDEFFFEDDBBBCBCBAAADEEEF");
        byte[] authKey = ByteUtils.hexToByte("EEFFEAVAAVCDDDEEFFAADEDEDEDFFFAA");
        byte[] keyWrapKey = ByteUtils.hexToByte("EFEFABABACDCDCBABABEFEFEEFABABAB");

        byte[] wrap = cryptographyUtil.wrap(data, authKey, keyWrapKey);
        wrap[0] = ++wrap[0];
        try {
            cryptographyUtil.unwrap(wrap, authKey, keyWrapKey);
            Assert.fail();
        } catch (IllegalStateException e) {
            assertEquals("Unwrapped kwa does not match", e.getMessage());
        }
    }

}
