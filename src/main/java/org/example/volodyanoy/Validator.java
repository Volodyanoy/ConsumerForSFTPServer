package org.example.volodyanoy;

public class Validator {


    public boolean isValidIp(String ip) {
        String ipRegex = "^(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}$";
        return ip.matches(ipRegex);
    }

    public boolean isValidDomain(String domain) {
        String domainRegex = "^(?![0-9.]+$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*\\.[A-Za-z]{2,}$";
        return domain.matches(domainRegex);
    }
}
