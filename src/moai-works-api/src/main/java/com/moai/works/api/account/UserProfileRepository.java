package com.moai.works.api.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

  @Query("select u from UserProfile u where lower(u.email) = lower(?1)")
  Optional<UserProfile> findByEmail(String email);

  /** 개인 워크스페이스를 만들 때 같은 계정의 동시 요청을 줄 세운다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserProfile u where u.id = ?1")
  Optional<UserProfile> lockById(UUID id);

  List<UserProfile> findByStatusOrderBySignedUpAtAsc(AccountStatus status, Pageable page);
}
