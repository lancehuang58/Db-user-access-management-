package com.delta.dms.ops.dbaccess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for MariaDB user information.
 * Contains user account details from mysql.user table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MariaDBUserInfo {

    /**
     * The username
     */
    private String username;

    /**
     * The host pattern for this user (%, localhost, IP, etc.)
     */
    private String host;

    /**
     * Whether the account is locked (Y/N)
     */
    private String accountLocked;

    /**
     * Whether the password is expired (Y/N)
     */
    private String passwordExpired;

    /**
     * Check if account is locked.
     */
    public boolean isAccountLocked() {
        return "Y".equalsIgnoreCase(accountLocked);
    }

    /**
     * Check if password is expired.
     */
    public boolean isPasswordExpired() {
        return "Y".equalsIgnoreCase(passwordExpired);
    }

    @Override
    public String toString() {
        return String.format("MariaDBUser[username=%s, host=%s, locked=%s, passwordExpired=%s]",
            username, host, accountLocked, passwordExpired);
    }
}
