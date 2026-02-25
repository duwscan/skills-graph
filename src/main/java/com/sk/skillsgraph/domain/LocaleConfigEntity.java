package com.sk.skillsgraph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "locale_config")
public class LocaleConfigEntity {

    @Id
    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "coverage_pct", nullable = false)
    private int coveragePct;

    public LocaleConfigEntity() {
    }

    public LocaleConfigEntity(String locale, String displayName, boolean isActive, int coveragePct) {
        this.locale = locale;
        this.displayName = displayName;
        this.isActive = isActive;
        this.coveragePct = coveragePct;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getCoveragePct() {
        return coveragePct;
    }

    public void setCoveragePct(int coveragePct) {
        this.coveragePct = coveragePct;
    }
}
