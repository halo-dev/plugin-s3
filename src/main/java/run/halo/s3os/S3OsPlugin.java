package run.halo.s3os;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * @author johnniang
 * @since 2.0.0
 */
@Component
public class S3OsPlugin extends BasePlugin {

    public S3OsPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
