package com.eastapp.backend.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eastapp.bootstrap")
public class BootstrapProperties {

    private boolean enabled = true;
    private String companyCode = "EAST";
    private String companyName = "The East";
    private String employeeId = "E0001";
    private String fullName = "Jenssen";
    private String phoneE164;
    private String password = "1111";
    private boolean secondaryHeadEnabled = true;
    private String secondaryHeadEmployeeId = "E0002";
    private String secondaryHeadFullName = "Nicky Chang";
    private String secondaryHeadPhoneE164 = "+60165076207";
    private String secondaryHeadPassword = "2222";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneE164() {
        return phoneE164;
    }

    public void setPhoneE164(String phoneE164) {
        this.phoneE164 = phoneE164;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSecondaryHeadEnabled() {
        return secondaryHeadEnabled;
    }

    public void setSecondaryHeadEnabled(boolean secondaryHeadEnabled) {
        this.secondaryHeadEnabled = secondaryHeadEnabled;
    }

    public String getSecondaryHeadEmployeeId() {
        return secondaryHeadEmployeeId;
    }

    public void setSecondaryHeadEmployeeId(String secondaryHeadEmployeeId) {
        this.secondaryHeadEmployeeId = secondaryHeadEmployeeId;
    }

    public String getSecondaryHeadFullName() {
        return secondaryHeadFullName;
    }

    public void setSecondaryHeadFullName(String secondaryHeadFullName) {
        this.secondaryHeadFullName = secondaryHeadFullName;
    }

    public String getSecondaryHeadPhoneE164() {
        return secondaryHeadPhoneE164;
    }

    public void setSecondaryHeadPhoneE164(String secondaryHeadPhoneE164) {
        this.secondaryHeadPhoneE164 = secondaryHeadPhoneE164;
    }

    public String getSecondaryHeadPassword() {
        return secondaryHeadPassword;
    }

    public void setSecondaryHeadPassword(String secondaryHeadPassword) {
        this.secondaryHeadPassword = secondaryHeadPassword;
    }
}
