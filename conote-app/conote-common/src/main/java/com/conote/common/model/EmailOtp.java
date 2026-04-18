package com.conote.common.model;

import com.conote.common.enums.OtpPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailOtp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "otp_id")
    private Long otpId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private String email;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "is_used")
    private Boolean used;

    @Enumerated(EnumType.STRING)
    @Column
    private OtpPurpose purpose;

    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}


