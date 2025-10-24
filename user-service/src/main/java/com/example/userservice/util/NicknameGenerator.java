package com.example.userservice.util;

import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class NicknameGenerator {

    private final UserRepository userRepository;

    private static final List<String> ADJECTIVES = List.of(
            "행복한", "용감한", "귀여운", "빠른", "신비한", "슬기로운", "똑똑한", "자유로운",
            "따뜻한", "차가운", "기분좋은", "멋진", "자상한", "든든한", "활발한", "새침한",
            "다정한", "재미있는", "유쾌한", "겸손한", "강력한", "빛나는", "든든한", "현명한",
            "성실한", "단단한", "끈기있는", "용서하는", "정직한", "사려깊은", "활기찬"
    );

    private static final List<String> NOUNS = List.of(
            "호랑이", "고래", "다람쥐", "여우", "판다", "사자", "참새", "너구리",
            "독수리", "곰", "코끼리", "펭귄", "토끼", "늑대", "수달", "고양이",
            "강아지", "사슴", "치타", "문어", "햄스터", "오리", "비버", "너구리",
            "돌고래", "앵무새", "여우원숭이", "고슴도치", "물개", "북극곰"
    );

    private static final Random random = new Random();

    public String generateUniqueNickname() {
        String nickname;
        do {
            nickname = generateRandomNickname();
        } while (userRepository.existsByNickname(nickname));
        return nickname;
    }

    private String generateRandomNickname() {
        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String noun = NOUNS.get(random.nextInt(NOUNS.size()));
        int number = random.nextInt(999); // 0~998

        return String.format("%s%s#%04d", adjective, noun, number);
    }

}
