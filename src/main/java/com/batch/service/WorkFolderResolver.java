package com.batch.service;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * =====================================================================================
 * [작업 대상 폴더 탐색 및 결정 엔진 (SRP): WorkFolderResolver]
 * -------------------------------------------------------------------------------------
 * 💡 역할 및 책임:
 * 1. 4대 조건 기반 분석 대상 폴더 결정:
 *    - 조건 1: 지정된 경로에 로그 파일(.log)이 직접 존재하는 경우
 *    - 조건 2: 지정된 폴더 하위의 6자리 날짜 포맷(\\d{6}) 서브폴더 중 최신 폴더 탐색
 *    - 조건 3: 유효한 로그 파일이 없는 경우 실패 리포트 대상 폴더 결정
 *    - 조건 4: 6자리 날짜 포맷(\\d{6}) 입력 시 기본 경로(baseFolder) 하위 탐색
 * 2. 독립된 단위 테스트 가능:
 *    - 서비스 레이어와 완전히 분리되어 다양한 파일 시스템 조건에 대한 격리 테스트가 가능합니다.
 * =====================================================================================
 */
public class WorkFolderResolver {

    /**
     * 대상 로그 폴더 결정
     *
     * @param logFileSrc 사용자 지정 입력 경로 (폴더 경로 또는 6자리 날짜)
     * @param baseFolder 환경 설정 기본 폴더 경로 (base.folder)
     * @return 결정된 File 객체 (미발견 시 null)
     */
    public File resolve(String logFileSrc, String baseFolder) {
        // 조건 4. logFileSrc 값으로 6자리 날짜 포맷(\\d{6})이 설정된 경우: 기본경로의 하위폴더 대상
        if (logFileSrc != null && logFileSrc.matches("\\d{6}")) {
            File dateFolder = new File(baseFolder, logFileSrc);
            if (dateFolder.exists() && hasLogFiles(dateFolder)) {
                return dateFolder;
            }
            return dateFolder.exists() ? dateFolder : null;
        }

        // 파라미터가 지정된 경우
        if (logFileSrc != null && !logFileSrc.isEmpty()) {
            File targetDir = new File(logFileSrc);
            if (!targetDir.isAbsolute() && !targetDir.exists()) {
                File underBase = new File(baseFolder, logFileSrc);
                if (underBase.exists()) {
                    targetDir = underBase;
                }
            }

            if (targetDir.exists() && targetDir.isDirectory()) {
                // 조건 1. logFileSrc 해당 위치 폴더에 로그 파일이 존재하는 경우
                if (hasLogFiles(targetDir)) {
                    return targetDir;
                }

                // 조건 2. logFileSrc 폴더에 로그 파일이 없는 경우: 내부 6자리 날짜 포맷(\\d{6}) 중 최신 폴더 탐색
                File latestDateFolder = getLatestDateFolder(targetDir);
                if (latestDateFolder != null && hasLogFiles(latestDateFolder)) {
                    return latestDateFolder;
                }

                // 최신 날짜 폴더가 존재는 하나 로그 파일이 없는 경우 해당 폴더 반환 (실패 리포트용)
                if (latestDateFolder != null) {
                    return latestDateFolder;
                }

                return targetDir;
            }
            return targetDir;
        }

        // 파라미터 미지정 시: 기본경로(base.folder)의 최신 날짜 폴더 탐색
        File baseDir = new File(baseFolder);
        if (baseDir.exists() && baseDir.isDirectory()) {
            if (hasLogFiles(baseDir)) {
                return baseDir;
            }
            File latestBaseDateFolder = getLatestDateFolder(baseDir);
            if (latestBaseDateFolder != null) {
                return latestBaseDateFolder;
            }
        }

        return null;
    }

    /**
     * 폴더 내에 .log 파일이 1개 이상 존재하는지 확인
     */
    public boolean hasLogFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        File[] logs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".log"));
        return logs != null && logs.length > 0;
    }

    /**
     * 최신 날짜(6자리 숫자, \\d{6}) 폴더 조회
     */
    public File getLatestDateFolder(File parent) {
        if (parent == null || !parent.exists() || !parent.isDirectory()) return null;
        File[] dirs = parent.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return null;

        return Arrays.stream(dirs)
                .filter(d -> d.getName().matches("\\d{6}"))
                .max(Comparator.comparingInt(d -> Integer.parseInt(d.getName())))
                .orElse(null);
    }

    /**
     * 분석 대상 폴더 이름 결정 (리포트용)
     */
    public String determineFolderName(File folder, String logFileSrc, String baseFolder) {
        if (folder != null) {
            return folder.getName();
        }
        if (logFileSrc != null && !logFileSrc.isEmpty()) {
            File f = new File(logFileSrc);
            return f.getName().isEmpty() ? logFileSrc : f.getName();
        }
        File base = new File(baseFolder);
        return base.getName().isEmpty() ? "배치로그" : base.getName();
    }
}
