package run.halo.s3os;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlUtilsTest {

    @Test
    void testRemoveHttpPrefix() {
        assert UrlUtils.removeHttpPrefix(null) == null;
        assert UrlUtils.removeHttpPrefix("http://www.example.com").equals("www.example.com");
        assert UrlUtils.removeHttpPrefix("https://www.example.com").equals("www.example.com");
    }

    @Test
    public void testFindUrlSuffix() {
        List<S3OsProperties.urlSuffixItem> urlSuffixList = List.of(
            new S3OsProperties.urlSuffixItem("jpg,png,gif", "?imageMogr2/format/webp"),
            new S3OsProperties.urlSuffixItem("pdf", "?123=123"),
            new S3OsProperties.urlSuffixItem("jpg", "?456=456")
        );

        // 测试文件名为"example.jpg"，期望匹配到"?imageMogr2/format/webp"，只匹配第一个后缀
        String fileName1 = "example.jpg";
        String result1 = UrlUtils.findUrlSuffix(urlSuffixList, fileName1);
        assertEquals("?imageMogr2/format/webp", result1);

        // 测试文件名为"Document.PDF"，期望匹配到"?123=123"，不区分大小写
        String fileName2 = "Document.PDF";
        String result2 = UrlUtils.findUrlSuffix(urlSuffixList, fileName2);
        assertEquals("?123=123", result2);

        // 测试文件名为"unknown.txt"，期望没有匹配项，返回null
        String fileName3 = "unknown.txt";
        String result3 = UrlUtils.findUrlSuffix(urlSuffixList, fileName3);
        assertNull(result3);

        // 测试无后缀文件名"example"，期望没有匹配项，返回null
        String fileName4 = "example";
        String result4 = UrlUtils.findUrlSuffix(urlSuffixList, fileName4);
        assertNull(result4);

        // 测试空文件名，期望返回null
        String fileName5 = "";
        String result5 = UrlUtils.findUrlSuffix(urlSuffixList, fileName5);
        assertNull(result5);

        // 测试urlSuffixList为null，期望返回null
        String fileName6 = "example";
        String result6 = UrlUtils.findUrlSuffix(null, fileName6);
        assertNull(result6);
    }
}