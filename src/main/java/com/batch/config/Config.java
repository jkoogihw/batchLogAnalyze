package com.batch.config;

import com.batch.exception.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 애플리케이션 설정 관리
 * 
 * application.properties 파일에서 설정을 로드합니다.
 */
public class Config {
    
    private static final Properties properties;

    static {
        properties = loadProperties();
    }

    /**
     * application.properties 파일 로드
     */
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new ConfigurationException("application.properties 파일을 찾을 수 없습니다.");
            }
            props.load(input);
        } catch (IOException e) {
            throw new ConfigurationException("application.properties 로드 실패: " + e.getMessage(), e);
        }
        return props;
    }

    /**
     * 설정값 조회 (필수)
     */
    public static String get(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new ConfigurationException("필수 설정값이 없습니다: " + key);
        }
        return value;
    }

    /**
     * 설정값 조회 (선택, 기본값 지원)
     */
    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * 모든 설정값 조회
     */
    public static Properties getAll() {
        return properties;
    }

    /**
     * 설정값이 존재하는지 확인
     */
    public static boolean exists(String key) {
        return properties.containsKey(key);
    }

    /**
     * 테스트용: 임시 설정값 주입
     */
    protected static void setForTest(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * 테스트용: 임시 설정값 제거
     */
    protected static void clearForTest() {
        properties.clear();
    }
}
