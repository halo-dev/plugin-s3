import { definePlugin } from "@halo-dev/console-shared";
import type { PluginTab } from "@halo-dev/console-shared";
import S3Link from "./views/S3Link.vue";
import S3Unlink from "./views/S3Unlink.vue"
import { markRaw } from "vue";

import type { Attachment } from "./interface";
import type { Ref } from "vue";

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    "plugin:self:tabs:create": (): PluginTab[] => {
      return [
        {
          id: "s3-link",
          label: "关联S3文件",
          component: markRaw(S3Link),
          permissions: ["plugin:s3os:link"]
        },
      ];
    },
    // @ts-ignore
    "attachment:list-item:operation:create": (attachment: Ref<Attachment>) => {
      return [
        {
          priority: 21,
          component: markRaw(S3Unlink),
          permissions: ["plugin:s3os:unlink"],
          props: {
            "attachment": attachment,
          },
          hidden: !(attachment.value.metadata.annotations && attachment.value.metadata.annotations["s3os.plugin.halo.run/object-key"])
        },
      ];
    },
  },
});
