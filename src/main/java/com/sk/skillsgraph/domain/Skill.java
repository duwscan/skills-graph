package com.sk.skillsgraph.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("Skill")
public class Skill {

    @Id
    private String id;
    private String externalId;
    private String canonicalName;
    private String slug;
    private String status;
    private String category;
    private Integer version;
    private String source;
    private Instant createdAt;
    private Instant updatedAt;

    @Relationship(type = "HAS_ALIAS", direction = Relationship.Direction.OUTGOING)
    private Set<Alias> aliases = new HashSet<>();

    @Relationship(type = "PARENT_OF", direction = Relationship.Direction.OUTGOING)
    private Set<Skill> children = new HashSet<>();

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private Set<Skill> relatedSkills = new HashSet<>();

    @Relationship(type = "REQUIRES", direction = Relationship.Direction.OUTGOING)
    private Set<Skill> requiredSkills = new HashSet<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<Alias> getAliases() {
        return aliases;
    }

    public void setAliases(Set<Alias> aliases) {
        this.aliases = aliases;
    }

    public Set<Skill> getChildren() {
        return children;
    }

    public void setChildren(Set<Skill> children) {
        this.children = children;
    }

    public Set<Skill> getRelatedSkills() {
        return relatedSkills;
    }

    public void setRelatedSkills(Set<Skill> relatedSkills) {
        this.relatedSkills = relatedSkills;
    }

    public Set<Skill> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(Set<Skill> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }
}
