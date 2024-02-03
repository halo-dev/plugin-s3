import type { Attachment } from "@halo-dev/api-client";
import { definePlugin } from "@halo-dev/console-shared";
import S3Link from "./views/S3Link.vue";
import S3Unlink from "./views/S3Unlink.vue";
import type { Ref } from "vue";
import { markRaw } from "vue";
import CarbonFolderDetailsReference from "~icons/carbon/folder-details-reference";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "ToolsRoot",
      route: {
        path: "s3-link",
        name: "S3Link",
        component: S3Link,
        meta: {
          title: "S3 关联",
          description: "提供将 S3 存储桶中的文件关联到 Halo 中的功能。",
          searchable: true,
          permissions: ["plugin:s3os:link"],
          menu: {
            name: "S3 关联",
            icon: markRaw(CarbonFolderDetailsReference),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {
    "attachment:list-item:operation:create": (attachment: Ref<Attachment>) => {
      return [
        {
          priority: 21,
          component: markRaw(S3Unlink),
          permissions: ["plugin:s3os:unlink"],
          props: {
            attachment: attachment,
          },
          hidden: !(
            attachment.value.metadata.annotations &&
            attachment.value.metadata.annotations[
              "s3os.plugin.halo.run/object-key"
            ]
          ),
        },
      ];
    },
  },
});
