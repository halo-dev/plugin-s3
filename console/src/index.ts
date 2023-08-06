import {definePlugin} from "@halo-dev/console-shared";
import type {PluginTab} from "@halo-dev/console-shared";
import HomeView from "./views/HomeView.vue";
import {markRaw} from "vue";

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    "plugin:self:tabs:create": () : PluginTab[] => {
      return [
        {
          id: "s3-link",
          label: "关联S3文件",
          component: markRaw(HomeView),
          permissions: []
        },
      ];
    },
  },
});
