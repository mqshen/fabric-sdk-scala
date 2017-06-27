package com.ynet.belink.common.security.auth;


import java.security.Principal;

public class BelinkPrincipal implements Principal {
    public static final String SEPARATOR = ":";
    public static final String USER_TYPE = "User";
    public final static BelinkPrincipal ANONYMOUS = new BelinkPrincipal(BelinkPrincipal.USER_TYPE, "ANONYMOUS");

    private String principalType;
    private String name;

    public BelinkPrincipal(String principalType, String name) {
        if (principalType == null || name == null) {
            throw new IllegalArgumentException("principalType and name can not be null");
        }
        this.principalType = principalType;
        this.name = name;
    }

    public static BelinkPrincipal fromString(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("expected a string in format principalType:principalName but got " + str);
        }

        String[] split = str.split(SEPARATOR, 2);

        if (split == null || split.length != 2) {
            throw new IllegalArgumentException("expected a string in format principalType:principalName but got " + str);
        }

        return new BelinkPrincipal(split[0], split[1]);
    }

    @Override
    public String toString() {
        return principalType + SEPARATOR + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BelinkPrincipal)) return false;

        BelinkPrincipal that = (BelinkPrincipal) o;

        if (!principalType.equals(that.principalType)) return false;
        return name.equals(that.name);

    }

    @Override
    public int hashCode() {
        int result = principalType.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getPrincipalType() {
        return principalType;
    }
}



