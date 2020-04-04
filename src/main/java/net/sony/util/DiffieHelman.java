package net.sony.util;
/*
 *			   Apache License
 *         Version 2.0, January 2004
 *     Copyright 2015 Amirali Sanatinia
 */


import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Class to represent the Diffie-Hellman key exchange protocol
 */
public class DiffieHelman {
    /* RFC 3526 - More Modular Exponential (MODP) Diffie-Hellman groups for
     * Internet Key Exchange (IKE) https://tools.ietf.org/html/rfc3526
     */

    private final Prime prime;
    private final BigInteger privateKey;

    public DiffieHelman(Prime prime) {
        this.prime = prime;
        this.privateKey = generateRandomPrivateKey();
    }

    public DiffieHelman(Prime prime, BigInteger privateKey) {
        this.prime = prime;
        this.privateKey = privateKey;
    }


    public DiffieHelman() {
        this(Prime.P_2048_BIT);
    }

    private BigInteger generateRandomPrivateKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return new BigInteger(1, bytes);
    }

    public byte[] generatePublicKey() {
        byte[] publicKey = BigInteger.TWO.modPow(privateKey, prime.value).toByteArray();
        publicKey = BigIntegerUtils.projectArray(publicKey, 257);
        publicKey[0] = (byte) 0;
        return publicKey;
    }

    public boolean checkOtherPublicKey(BigInteger otherContribution) {
        // check if the other public key is valid based on NIST SP800-56
        // 2 <= g^b <= p-2 and Lagrange for safe primes (g^bq)=1, q=(p-1)/2
        if (BigInteger.TWO.compareTo(otherContribution) <= 0 && otherContribution.compareTo(prime.value.subtract(BigInteger.TWO)) <= 0) {
            return otherContribution.modPow(prime.value.subtract(BigInteger.ONE).divide(BigInteger.TWO), prime.value).equals(BigInteger.ONE);
        }
        return false;
    }

    /**
     * Return g ^ ab mod p
     */
    public BigInteger generateSharedKeyInt(BigInteger otherContribution) {
        if (checkOtherPublicKey(otherContribution)) {
            return otherContribution.modPow(privateKey, prime.value);
        } else {
            throw new IllegalArgumentException("Bad public key from other party");
        }
    }

    public byte[] generateSharedKey(byte[] otherContribution) {
        byte[] sharedKey = generateSharedKeyInt(new BigInteger(1, otherContribution)).toByteArray();
        return BigIntegerUtils.projectArray(sharedKey, 256);
    }

    public enum Prime {

        P_2048_BIT("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF");

        private final BigInteger value;

        Prime(String value) {
            this.value = new BigInteger(value, 16);
        }
    }
}
