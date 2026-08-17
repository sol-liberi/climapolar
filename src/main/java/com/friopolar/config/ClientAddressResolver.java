package com.friopolar.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Resuelve X-Forwarded-For solamente cuando el salto inmediato es confiable. */
public final class ClientAddressResolver {
    private static final int MAX_FORWARDED_HEADER_LENGTH = 1_024;
    private static final int MAX_PROXY_HOPS = 20;

    private final List<IpAddressMatcher> trustedProxies;

    public ClientAddressResolver(String configuredTrustedProxies) {
        trustedProxies = Arrays.stream(configuredTrustedProxies == null ? new String[0] : configuredTrustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeAddress(request.getRemoteAddr());
        if (!isTrusted(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()
                || forwardedFor.length() > MAX_FORWARDED_HEADER_LENGTH) {
            return remoteAddress;
        }

        List<String> chain = new ArrayList<>();
        for (String value : forwardedFor.split(",")) {
            if (chain.size() >= MAX_PROXY_HOPS) {
                return remoteAddress;
            }
            String address = normalizeAddress(value.trim());
            if (!isIpLiteral(address)) {
                return remoteAddress;
            }
            chain.add(address);
        }
        chain.add(remoteAddress);

        // Se recorre desde el servidor hacia el cliente. El primer salto no
        // confiable es la dirección que no pudo ser agregada por nuestro proxy.
        for (int index = chain.size() - 1; index >= 0; index--) {
            String address = chain.get(index);
            if (!isTrusted(address)) {
                return address;
            }
        }
        return chain.get(0);
    }

    private boolean isTrusted(String address) {
        if (!isIpLiteral(address)) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return false;
    }

    private String normalizeAddress(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.strip();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        InetAddress parsed = parseIpLiteral(normalized);
        return parsed == null ? normalized : parsed.getHostAddress();
    }

    private boolean isIpLiteral(String value) {
        return parseIpLiteral(value) != null;
    }

    private InetAddress parseIpLiteral(String value) {
        if (value == null || value.isBlank() || value.length() > 45) return null;

        boolean ipv6 = value.indexOf(':') >= 0;
        if (ipv6) {
            // El filtro previo evita que InetAddress intente resolver un hostname.
            // Los identificadores de zona no forman parte de X-Forwarded-For y se
            // rechazan para que una misma IP no tenga múltiples claves de límite.
            if (!value.matches("[0-9a-fA-F:.]+")) return null;
        } else if (!isValidIpv4(value)) {
            return null;
        }

        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isValidIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                if (!octet.matches("\\d{1,3}")) {
                    return false;
                }
                int octetValue = Integer.parseInt(octet);
                if (octetValue < 0 || octetValue > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }
}
