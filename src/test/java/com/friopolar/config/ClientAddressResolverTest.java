package com.friopolar.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAddressResolverTest {
    private final ClientAddressResolver resolver = new ClientAddressResolver("127.0.0.1,10.0.0.0/8");

    @Test
    void ignoraForwardedForCuandoElClienteNoEsUnProxyConfiable() {
        var request = request("198.51.100.60", "203.0.113.10");

        assertEquals("198.51.100.60", resolver.resolve(request));
    }

    @Test
    void obtieneElPrimerSaltoNoConfiableDetrasDeLosProxies() {
        var request = request("10.0.0.2", "203.0.113.10, 10.0.0.3");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void rechazaUnaCadenaForwardedForMalformada() {
        var request = request("127.0.0.1", "dirección-inválida");

        assertEquals("127.0.0.1", resolver.resolve(request));
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
