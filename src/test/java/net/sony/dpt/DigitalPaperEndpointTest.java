package net.sony.dpt;

import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class DigitalPaperEndpointTest {

    @Test
    public void insecureAndSecureUrlShouldGenerate() throws URISyntaxException {
        DigitalPaperEndpoint digitalPaperEndpoint = new DigitalPaperEndpoint("JUNIT", null);
        URI secured = digitalPaperEndpoint.getSecuredURI();
        URI insecured = digitalPaperEndpoint.getInsecureURI();

        Assert.assertEquals(secured.getHost(), insecured.getHost());
        Assert.assertEquals("JUNIT", secured.getHost());
        Assert.assertNotEquals(secured.getPort(), insecured.getPort());
        Assert.assertEquals("JUNIT", digitalPaperEndpoint.getAddr());
    }

}
