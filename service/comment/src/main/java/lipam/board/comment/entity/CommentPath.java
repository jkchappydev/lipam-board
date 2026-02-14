package lipam.board.comment.entity;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentPath {

    private String path;

    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int DEPTH_CHUNK_SIZE = 5; // 1뎁스 경로를 나타내는 문자 갯수
    private static final int MAX_DEPTH = 5; // 최대 뎁스
    // MIN_CHUNK = "00000"
    private static final String MIN_CHUNK = String.valueOf(CHARSET.charAt(0)).repeat(DEPTH_CHUNK_SIZE);
    // MAX_CHUNK = "zzzzz"
    private static final String MAX_CHUNK = String.valueOf(CHARSET.charAt(CHARSET.length() - 1)).repeat(DEPTH_CHUNK_SIZE);

    public static CommentPath create(String path) {
        if (isDepthOverflowed(path)) {
            throw new IllegalStateException("Depth overflow");
        }

        CommentPath commentPath = new CommentPath();
        commentPath.path = path;

        return commentPath;
    }

    private static boolean isDepthOverflowed(String path) {
        return calDepth(path) > MAX_DEPTH;
    }

    private static int calDepth(String path) {
        // 25개의 문자열로 이루어진 경로 = 25 / 5 = 5뎁스
        return path.length() / DEPTH_CHUNK_SIZE;
    }

    public int getDepth() {
        return calDepth(path);
    }

    public boolean isRoot() {
        return calDepth(path) == 1;
    }

    public String getParentPath() {
        return path.substring(0, path.length() - DEPTH_CHUNK_SIZE); // 주어진 문자열 경로에서 끝의 5개를 잘라내면 해당 경로의 부모경로
    }

    public CommentPath createChileCommentPath(String descendantsTopPath) {
        if (descendantsTopPath == null) {
            return CommentPath.create(path + MIN_CHUNK);
        }

        String childrenTopPath = findChildrenTopPath(descendantsTopPath);
        return CommentPath.create(increase(childrenTopPath));
    }

    private String findChildrenTopPath(String descendantsTopPath) {
        return descendantsTopPath.substring(0, (getDepth() + 1) * DEPTH_CHUNK_SIZE);
    }

    private String increase(String path) {
        // 00000 00000 -> 끝의 5개 잘라낸 값을 찾은 뒤 -> +1
        String lastChunk = path.substring(path.length() - DEPTH_CHUNK_SIZE);
        if (isChunkOverflowed(lastChunk)) {
            throw new IllegalStateException("chunk overflow");
        }

        // 62진수 -> 10진수로 변환 후  + 1 -> 다시 62진수로 변환
        int charsetLength = CHARSET.length(); // 62진수

        int value = 0; // lastChunk 를 10진수로 변환할 값을 담을 변수
        for (char ch : lastChunk.toCharArray()) {
            value = value * charsetLength + CHARSET.indexOf(ch);
        }

        value = value + 1; // 1증가 후

        // 다시 10 진수로 변환
        String result = "";
        for (int i = 0; i < DEPTH_CHUNK_SIZE; i++) {
            result = CHARSET.charAt(value % charsetLength) + result;
            value /= charsetLength;
        }

        return path.substring(0, path.length() - DEPTH_CHUNK_SIZE) + result; // 부모경로에 구한 자식경로 더한다.
    }

    private boolean isChunkOverflowed(String lastChunk) {
        return MAX_CHUNK.equals(lastChunk);
    }

}
