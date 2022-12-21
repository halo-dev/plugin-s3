package run.halo.s3os;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlUtilsTest {

    @Test
    void testRemoveHttpPrefix() {
        assert UrlUtils.removeHttpPrefix(null) == null;
        assert UrlUtils.removeHttpPrefix("http://www.example.com").equals("www.example.com");
        assert UrlUtils.removeHttpPrefix("https://www.example.com").equals("www.example.com");
    }
}