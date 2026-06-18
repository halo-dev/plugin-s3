import { rsbuildConfig } from "@halo-dev/ui-plugin-bundler-kit";
import { pluginSass } from "@rsbuild/plugin-sass";
import { UnoCSSRspackPlugin } from "@unocss/webpack/rspack";
import Icons from "unplugin-icons/rspack";

export default rsbuildConfig({
  rsbuild: {
    resolve: {
      alias: {
        "@": "./src",
      },
    },
    plugins: [pluginSass()],
    tools: {
      rspack: {
        plugins: [
          Icons({ compiler: "vue3" }),
          UnoCSSRspackPlugin({
            configFile: "./uno.config.ts",
          }),
        ],
      },
    },
  },
});
