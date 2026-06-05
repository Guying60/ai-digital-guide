package com.guying.context;

public class AdminContext {
    private static final ThreadLocal<Long> ADMIN_ID = new ThreadLocal<>();

    public static void setAdminId(Long adminId) { ADMIN_ID.set(adminId); }
    public static Long getAdminId() { return ADMIN_ID.get(); }
    public static void clear() { ADMIN_ID.remove(); }
}
