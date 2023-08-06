package run.halo.s3os;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class LinkRequest {
    private String policyName;
    private List<String> objectKeys;
}