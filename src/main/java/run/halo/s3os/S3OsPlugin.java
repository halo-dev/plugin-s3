package run.halo.s3os;

import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;

/**
 * @author johnniang
 * @since 2.0.0
 */
@Component
public class S3OsPlugin extends BasePlugin {

    public S3OsPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
