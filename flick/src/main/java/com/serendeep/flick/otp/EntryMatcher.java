package com.serendeep.flick.otp;

import com.serendeep.flick.vault.VaultEntry;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EntryMatcher {
    private final List<VaultEntry> _entries;

    private static final Map<String, String> PACKAGE_MAP = new HashMap<>();
    private static final Map<String, String> DOMAIN_MAP = new HashMap<>();

    static {
        PACKAGE_MAP.put("com.github", "github");
        PACKAGE_MAP.put("com.google.android", "google");
        PACKAGE_MAP.put("com.amazon", "amazon");
        PACKAGE_MAP.put("com.discord", "discord");
        PACKAGE_MAP.put("com.twitter", "twitter");
        PACKAGE_MAP.put("com.facebook", "facebook");
        PACKAGE_MAP.put("com.microsoft", "microsoft");
        PACKAGE_MAP.put("com.dropbox", "dropbox");
        PACKAGE_MAP.put("com.slack", "slack");
        PACKAGE_MAP.put("com.reddit", "reddit");
        PACKAGE_MAP.put("org.telegram", "telegram");
        PACKAGE_MAP.put("com.spotify", "spotify");
        PACKAGE_MAP.put("com.twitch", "twitch");
        PACKAGE_MAP.put("com.snapchat", "snapchat");
        PACKAGE_MAP.put("com.linkedin", "linkedin");
        PACKAGE_MAP.put("com.paypal", "paypal");
        PACKAGE_MAP.put("com.stripe", "stripe");
        PACKAGE_MAP.put("com.coinbase", "coinbase");
        PACKAGE_MAP.put("com.binance", "binance");
        PACKAGE_MAP.put("com.cloudflare", "cloudflare");

        DOMAIN_MAP.put("x.com", "twitter");
        DOMAIN_MAP.put("twitter.com", "twitter");
        DOMAIN_MAP.put("accounts.google.com", "google");
        DOMAIN_MAP.put("login.microsoftonline.com", "microsoft");
        DOMAIN_MAP.put("signin.aws.amazon.com", "amazon");
    }

    public EntryMatcher(List<VaultEntry> entries) {
        _entries = entries;
    }

    public List<VaultEntry> matchByPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return Collections.emptyList();
        }

        String pkg = packageName.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, String> entry : PACKAGE_MAP.entrySet()) {
            if (pkg.startsWith(entry.getKey())) {
                List<VaultEntry> matches = findByIssuerKeyword(entry.getValue());
                if (!matches.isEmpty()) {
                    return matches;
                }
            }
        }

        String keyword = extractKeywordFromPackage(pkg);
        if (keyword != null) {
            return findByIssuerKeyword(keyword);
        }

        return Collections.emptyList();
    }

    public List<VaultEntry> matchByDomain(String domainOrUrl) {
        if (domainOrUrl == null || domainOrUrl.isEmpty()) {
            return Collections.emptyList();
        }

        String domain = extractDomain(domainOrUrl.toLowerCase(Locale.ROOT));
        if (domain == null) {
            return Collections.emptyList();
        }

        if (DOMAIN_MAP.containsKey(domain)) {
            List<VaultEntry> matches = findByIssuerKeyword(DOMAIN_MAP.get(domain));
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        String sld = getSecondLevelDomain(domain);

        if (DOMAIN_MAP.containsKey(sld)) {
            List<VaultEntry> matches = findByIssuerKeyword(DOMAIN_MAP.get(sld));
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        String keyword = sld.contains(".") ? sld.substring(0, sld.indexOf('.')) : sld;

        List<String> keywords = new ArrayList<>();
        keywords.add(keyword);
        String[] words = keyword.split("-");
        if (words.length > 1) {
            for (int i = words.length - 1; i >= 0; i--) {
                if (words[i].length() >= 4) {
                    keywords.add(words[i]);
                }
            }
        }

        // Issuer-first pass across all variants
        for (String kw : keywords) {
            List<VaultEntry> matches = findByIssuerKeyword(kw, false);
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        // Fall back to name matches
        for (String kw : keywords) {
            List<VaultEntry> matches = findByIssuerKeyword(kw, true);
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        return Collections.emptyList();
    }

    private List<VaultEntry> findByIssuerKeyword(String keyword) {
        return findByIssuerKeyword(keyword, true);
    }

    private List<VaultEntry> findByIssuerKeyword(String keyword, boolean includeNameMatches) {
        List<VaultEntry> issuerMatches = new ArrayList<>();
        List<VaultEntry> nameMatches = new ArrayList<>();
        String kw = normalize(keyword);

        for (VaultEntry entry : _entries) {
            String issuer = normalize(entry.getIssuer());
            boolean issuerMatch = !issuer.isEmpty()
                    && (issuer.equals(kw) || issuer.contains(kw) || kw.contains(issuer));
            if (issuerMatch) {
                issuerMatches.add(entry);
                continue;
            }

            if (includeNameMatches) {
                String name = normalize(entry.getName());
                boolean nameMatch = !name.isEmpty()
                        && (name.contains(kw) || kw.contains(name));
                if (nameMatch) {
                    nameMatches.add(entry);
                }
            }
        }

        issuerMatches.addAll(nameMatches);
        return issuerMatches;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String extractKeywordFromPackage(String pkg) {
        String[] parts = pkg.split("\\.");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    private String extractDomain(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            try {
                return new URI(input).getHost();
            } catch (Exception e) {
                return null;
            }
        }

        int slashIdx = input.indexOf('/');
        return slashIdx > 0 ? input.substring(0, slashIdx) : input;
    }

    private String getSecondLevelDomain(String domain) {
        String[] parts = domain.split("\\.");
        if (parts.length >= 2) {
            StringBuilder sb = new StringBuilder();
            sb.append(parts[parts.length - 2]).append(".").append(parts[parts.length - 1]);
            return sb.toString();
        }
        return domain;
    }
}
