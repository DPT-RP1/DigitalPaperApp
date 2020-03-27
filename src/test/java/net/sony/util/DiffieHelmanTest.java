package net.sony.util;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Base64;

import static org.junit.Assert.assertTrue;

public class DiffieHelmanTest {

    @Test
    public void generateConsitently256bitArraysWith0InFront() {
        for (int i = 0; i < 1000; i++) {
            DiffieHelman dh = new DiffieHelman();
            byte[] pubKey = dh.generatePublicKey();

            assertTrue("The generated value at " + i + " is larger than 256 bytes (" + pubKey.length + ")",
                    pubKey.length <= 256 || (pubKey.length == 257 && pubKey[0] == 0));
        }

        String ybb64 = "AN2q7dz8gtIrs1OL2bMO8w/B8ohHGNOjKDIhVfrRN7YKdctiRFYjiqDeK4cB9ZqdarwkN4UQD+pJZ5iCPwgoCED82ZGCGhUgQjqceOmUxrAInZBkOqrk8hmmvaYVETagwmVZDuZ9iB9GkDMzICJWQcJaQ0BEPftkx/dxcky6KIBkifmCO8TejDnrSwoeJUwbfvf7+gG+efsZcI5twyz0OJWhW6urx3RZs0R3GKW3cw65NVEQ+wt5WO4dQ32bkUdz1sWBPtJ39cwBJxU4CmDz9AUv2Y5h2DHXxZZOaAGAhOvjPVtWX0yDnbz1VouljnIBEb6+J47PfZTLua5YMc58svs=";
        byte[] yb = Base64.getDecoder().decode(ybb64);

        for (int i = 0; i < 1000; i++) {
            DiffieHelman dh = new DiffieHelman();
            byte[] sharedKey = dh.generateSharedKey(yb);

            assertTrue("The generated value at " + i + " is larger than 256 bytes (" + sharedKey.length + ")",
                    sharedKey.length <= 256 || (sharedKey.length == 257 && sharedKey[0] == 0));
        }
    }

}
