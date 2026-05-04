package com.web.mapper;

import com.web.pojo.EmailVerificationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface EmailVerificationCodeMapper {
    int insert(EmailVerificationCode code);

    EmailVerificationCode findLatestUsable(
            @Param("email") String email,
            @Param("purpose") String purpose,
            @Param("now") LocalDateTime now);

    int countCreatedSince(
            @Param("email") String email,
            @Param("purpose") String purpose,
            @Param("since") LocalDateTime since);

    int markUsed(@Param("id") Long id);

    int incrementAttemptCount(@Param("id") Long id);
}
