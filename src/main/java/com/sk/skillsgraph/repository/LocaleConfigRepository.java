package com.sk.skillsgraph.repository;

import com.sk.skillsgraph.domain.LocaleConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocaleConfigRepository extends JpaRepository<LocaleConfigEntity, String> {
}
