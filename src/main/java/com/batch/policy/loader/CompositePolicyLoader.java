package com.batch.policy.loader;

import com.batch.policy.PolicyLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * =====================================================================================
 * [컴포지트 정책 로더 (Composite Policy Loader)]
 * -------------------------------------------------------------------------------------
 * 💡 역할:
 * 1. 외부 파일 시스템 경로 우선 탐색
 * 2. 클래스패스 리소스 (src/main/resources) 로딩
 * 3. 프로젝트 루트 경로 로딩
 * =====================================================================================
 */
public class CompositePolicyLoader implements PolicyLoader {

    @Override
    public String load(String location) {
        if (location == null || location.isEmpty()) return null;

        // 1. 외부 파일 시스템 경로 확인
        File file = new File(location);
        if (file.exists() && file.isFile()) {
            try {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }

        // 파일명만 추출하여 클래스패스 탐색에 활용
        String fileName = file.getName();

        // 2. 클래스패스 리소스 확인
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}

        // 3. 프로젝트 루트 디렉터리 확인
        File rootFile = new File(fileName);
        if (rootFile.exists() && rootFile.isFile()) {
            try {
                return Files.readString(rootFile.toPath(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }

        return null;
    }
}
