package com.example.userservice.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.from-name}")
    private String fromName;

    public void sendVerificationCode(String toEmail, String code) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true, // 멀티파트(HTML/첨부파일)
                    StandardCharsets.UTF_8.name()
            );

            // 1. 보낸 사람 설정 (회신 불가능)
            helper.setFrom(fromAddress, fromName);

            // 2. 받는 사람 설정
            helper.setTo(toEmail);

            // 3. 제목 설정
            helper.setSubject("[LinkFolio] 회원가입 인증 코드");

            // 4. 본문 설정 (true 플래그로 HTML 활성화)
            String htmlBody = createHtmlVerificationEmail(code); // HTML 본문 생성
            helper.setText(htmlBody, true); // true: 이 텍스트가 HTML임을 알림

            javaMailSender.send(mimeMessage);
            log.info("{}로 인증 코드 발송 성공: {}", toEmail, code);
        } catch (Exception e) {
            log.error("{}로 인증 코드 발송 실패", toEmail, e);
            throw new RuntimeException("메일 발송 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * [수정] 발송할 HTML 본문을 생성하는 헬퍼 메서드
     */
    private String createHtmlVerificationEmail(String code) {
        // 사용자가 제공한 문구 및 Copyright 추가
        return "<html>"
                + "<body style='font-family: Arial, sans-serif; margin: 40px;'>"
                + "  <div style='border: 1px solid #ddd; padding: 20px; max-width: 600px; margin: auto;'>"
                + "    <h2 style='color: #333;'>LinkFolio 회원가입 인증 코드</h2>"
                + "    <p>LinkFolio 서비스에 가입해 주셔서 감사합니다.</p>"
                + "    <p>아래 인증 코드를 3분 이내에 입력해주세요. 만약 본인이 요청하지 않은 경우, 이 메일은 무시하셔도 됩니다.</p>"
                + "    <div style='background-color: #f4f4f4; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; letter-spacing: 3px; margin: 20px 0;'>"
                +      code
                + "    </div>"
                + "    <hr style='border: 0; border-top: 1px solid #eee; margin-top: 20px;'>"
                + "    <p style='font-size: 12px; color: #888;'>"
                + "      본 이메일은 발신 전용이므로 회신이 불가능합니다."
                + "    </p>"
                + "    <p style='font-size: 12px; color: #aaa; margin-top: 20px; border-top: 1px solid #eee; padding-top: 20px; text-align: center;'>"
                + "      © 2025 LinkFolio. All rights reserved."
                + "    </p>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }

}
