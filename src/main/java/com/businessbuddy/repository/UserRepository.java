package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<User> findByBusinessIdOrderByFullName(Long businessId);

    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    @Query("select u from User u left join fetch u.business order by u.email")
    List<User> findAllWithBusiness();
}
